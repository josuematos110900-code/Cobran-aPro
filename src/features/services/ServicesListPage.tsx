import { useCallback, useEffect, useMemo, useState } from 'react';
import { Search, Plus, Package, Pencil, Archive, MoreVertical } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Pagination } from '@/components/ui/Pagination';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import type { Service, ServiceStatus } from '@/lib/types/database';
import { archiveService, fetchServices } from './api';
import { ServiceFormModal } from './ServiceFormModal';
import { ServiceStatusBadge } from './ServiceStatusBadge';
import { BILLING_PERIOD_LABELS, SERVICE_STATUS_LABELS } from './types';

const PAGE_SIZE = 10;

function formatCurrency(value: number, currency: string) {
  try {
    return new Intl.NumberFormat('pt-AO', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(value);
  } catch {
    return `${value.toLocaleString('pt-AO')} ${currency}`;
  }
}

export function ServicesListPage() {
  const { organization } = useAuth();
  const { showToast } = useToast();

  const [services, setServices] = useState<Service[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ServiceStatus | 'all'>('all');
  const [page, setPage] = useState(0);

  const [formOpen, setFormOpen] = useState(false);
  const [editingService, setEditingService] = useState<Service | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Service | null>(null);
  const [archiving, setArchiving] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);

  useEffect(() => {
    const timeout = setTimeout(() => {
      setSearch(searchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(timeout);
  }, [searchInput]);

  useEffect(() => {
    if (!openMenuId) return;
    function handleClick() {
      setOpenMenuId(null);
    }
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [openMenuId]);

  const load = useCallback(async () => {
    if (!organization) return;
    setLoading(true);
    setError(null);

    const { services: fetched, totalCount: count, error: fetchError } = await fetchServices(
      organization.id,
      { search, status, page, pageSize: PAGE_SIZE }
    );

    if (fetchError) {
      setError('Não foi possível carregar os serviços.');
      setLoading(false);
      return;
    }

    setServices(fetched);
    setTotalCount(count);
    setLoading(false);
  }, [organization, search, status, page]);

  useEffect(() => {
    load();
  }, [load]);

  const hasActiveFilters = useMemo(() => search.trim() !== '' || status !== 'all', [search, status]);

  function handleCreateClick() {
    setEditingService(null);
    setFormOpen(true);
  }

  function handleEditClick(service: Service) {
    setEditingService(service);
    setFormOpen(true);
    setOpenMenuId(null);
  }

  function handleSaved(_service: Service, mode: 'created' | 'updated') {
    setFormOpen(false);
    showToast(mode === 'created' ? 'Serviço criado com sucesso.' : 'Serviço actualizado.');
    load();
  }

  async function handleConfirmArchive() {
    if (!archiveTarget) return;
    setArchiving(true);
    const { error: archiveError } = await archiveService(archiveTarget.id);
    setArchiving(false);

    if (archiveError) {
      showToast('Não foi possível arquivar o serviço.', 'error');
      return;
    }

    showToast('Serviço marcado como inactivo.');
    setArchiveTarget(null);
    load();
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Serviços</h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {loading ? 'A carregar…' : `${totalCount} serviço${totalCount === 1 ? '' : 's'}`}
            </p>
          </div>
          <Button onClick={handleCreateClick} className="w-full sm:w-auto">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Novo serviço
          </Button>
        </div>

        <Card className="mt-6 p-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Pesquisar serviços…"
                className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              />
            </div>
            <div className="sm:w-52">
              <Select
                aria-label="Filtrar por estado"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value as ServiceStatus | 'all');
                  setPage(0);
                }}
              >
                <option value="all">Todos os estados</option>
                {Object.entries(SERVICE_STATUS_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </div>
          </div>
        </Card>

        <Card className="mt-4 p-0">
          {error && (
            <div className="p-4">
              <ErrorMessage message={error} />
            </div>
          )}

          {loading ? (
            <div className="divide-y divide-slate-200 dark:divide-slate-800">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex items-center gap-4 p-4">
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-1/3" />
                    <Skeleton className="h-3 w-1/4" />
                  </div>
                  <Skeleton className="h-6 w-16 rounded-full" />
                </div>
              ))}
            </div>
          ) : services.length === 0 ? (
            <div className="p-4">
              <EmptyState
                icon={Package}
                title={hasActiveFilters ? 'Nenhum serviço encontrado' : 'Ainda não tem serviços'}
                description={
                  hasActiveFilters
                    ? 'Tente ajustar a pesquisa ou os filtros.'
                    : 'Crie o seu primeiro serviço para o poder associar a cobranças e subscrições.'
                }
                actionLabel={hasActiveFilters ? undefined : 'Novo serviço'}
                onAction={hasActiveFilters ? undefined : handleCreateClick}
              />
            </div>
          ) : (
            <>
              {/* Vista mobile: cards */}
              <div className="divide-y divide-slate-200 dark:divide-slate-800 md:hidden">
                {services.map((service) => (
                  <div key={service.id} className="flex flex-col gap-1.5 p-4">
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-slate-900 dark:text-white">{service.name}</span>
                      <ServiceStatusBadge status={service.status} />
                    </div>
                    <span className="text-sm text-slate-500 dark:text-slate-400">
                      {formatCurrency(service.price, service.currency)} ·{' '}
                      {BILLING_PERIOD_LABELS[service.billing_period]}
                    </span>
                    <div className="mt-1 flex gap-2">
                      <Button variant="secondary" onClick={() => handleEditClick(service)}>
                        <Pencil className="h-4 w-4" /> Editar
                      </Button>
                      {service.status === 'active' && (
                        <Button variant="danger" onClick={() => setArchiveTarget(service)}>
                          <Archive className="h-4 w-4" /> Arquivar
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {/* Vista desktop: tabela */}
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400">
                      <th className="px-4 py-3 font-medium">Nome</th>
                      <th className="px-4 py-3 font-medium">Preço</th>
                      <th className="px-4 py-3 font-medium">Periodicidade</th>
                      <th className="px-4 py-3 font-medium">Estado</th>
                      <th className="px-4 py-3 font-medium text-right">Acções</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {services.map((service) => (
                      <tr key={service.id} className="hover:bg-slate-50 dark:hover:bg-slate-900/50">
                        <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                          {service.name}
                        </td>
                        <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                          {formatCurrency(service.price, service.currency)}
                        </td>
                        <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                          {BILLING_PERIOD_LABELS[service.billing_period]}
                        </td>
                        <td className="px-4 py-3">
                          <ServiceStatusBadge status={service.status} />
                        </td>
                        <td className="px-4 py-3 text-right">
                          <div className="relative inline-block text-left">
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                setOpenMenuId(openMenuId === service.id ? null : service.id);
                              }}
                              aria-label="Mais acções"
                              className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
                            >
                              <MoreVertical className="h-4 w-4" />
                            </button>
                            {openMenuId === service.id && (
                              <div className="absolute right-0 z-10 mt-1 w-40 rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-800 dark:bg-slate-900">
                                <button
                                  onClick={() => handleEditClick(service)}
                                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                                >
                                  <Pencil className="h-4 w-4" /> Editar
                                </button>
                                {service.status === 'active' && (
                                  <button
                                    onClick={() => {
                                      setArchiveTarget(service);
                                      setOpenMenuId(null);
                                    }}
                                    className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/30"
                                  >
                                    <Archive className="h-4 w-4" /> Arquivar
                                  </button>
                                )}
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="px-4">
                <Pagination page={page} pageSize={PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} />
              </div>
            </>
          )}
        </Card>
      </div>

      {organization && (
        <ServiceFormModal
          open={formOpen}
          organizationId={organization.id}
          defaultCurrency={organization.currency}
          service={editingService}
          onClose={() => setFormOpen(false)}
          onSaved={handleSaved}
        />
      )}

      <ConfirmDialog
        open={Boolean(archiveTarget)}
        title="Arquivar serviço"
        description={`Tem a certeza que quer marcar "${archiveTarget?.name}" como inactivo? Subscrições existentes não são afectadas.`}
        confirmLabel="Arquivar"
        danger
        loading={archiving}
        onConfirm={handleConfirmArchive}
        onCancel={() => setArchiveTarget(null)}
      />
    </AppShell>
  );
}
