import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Plus, Users, MoreVertical, Pencil, Archive, Eye } from 'lucide-react';
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
import type { Client, ClientStatus } from '@/lib/types/database';
import { archiveClient, fetchClientBalances, fetchClients } from './api';
import { ClientFormModal } from './ClientFormModal';
import { ClientStatusBadge } from './StatusBadge';
import { CLIENT_STATUS_LABELS, type ClientBalance } from './types';

const PAGE_SIZE = 10;

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

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

export function ClientsListPage() {
  const { organization } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [clients, setClients] = useState<Client[]>([]);
  const [balances, setBalances] = useState<Record<string, ClientBalance>>({});
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ClientStatus | 'all'>('all');
  const [page, setPage] = useState(0);

  const [formOpen, setFormOpen] = useState(false);
  const [editingClient, setEditingClient] = useState<Client | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Client | null>(null);
  const [archiving, setArchiving] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);

  useEffect(() => {
    if (!openMenuId) return;
    function handleClick() {
      setOpenMenuId(null);
    }
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [openMenuId]);

  // Debounce da pesquisa para não disparar uma query por cada tecla.
  useEffect(() => {
    const timeout = setTimeout(() => {
      setSearch(searchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(timeout);
  }, [searchInput]);

  const load = useCallback(async () => {
    if (!organization) return;
    setLoading(true);
    setError(null);

    const { clients: fetched, totalCount: count, error: fetchError } = await fetchClients(
      organization.id,
      { search, status, page, pageSize: PAGE_SIZE }
    );

    if (fetchError) {
      setError('Não foi possível carregar os clientes.');
      setLoading(false);
      return;
    }

    setClients(fetched);
    setTotalCount(count);

    const { balances: fetchedBalances } = await fetchClientBalances(fetched.map((c) => c.id));
    setBalances(Object.fromEntries(fetchedBalances.map((b) => [b.client_id, b])));

    setLoading(false);
  }, [organization, search, status, page]);

  useEffect(() => {
    load();
  }, [load]);

  const currency = organization?.currency ?? 'AOA';

  const hasActiveFilters = useMemo(() => search.trim() !== '' || status !== 'all', [search, status]);

  function handleCreateClick() {
    setEditingClient(null);
    setFormOpen(true);
  }

  function handleEditClick(client: Client) {
    setEditingClient(client);
    setFormOpen(true);
    setOpenMenuId(null);
  }

  function handleSaved(_client: Client, mode: 'created' | 'updated') {
    setFormOpen(false);
    showToast(mode === 'created' ? 'Cliente criado com sucesso.' : 'Cliente actualizado.');
    load();
  }

  async function handleConfirmArchive() {
    if (!archiveTarget) return;
    setArchiving(true);
    const { error: archiveError } = await archiveClient(archiveTarget.id);
    setArchiving(false);

    if (archiveError) {
      showToast('Não foi possível arquivar o cliente.', 'error');
      return;
    }

    showToast('Cliente arquivado.');
    setArchiveTarget(null);
    load();
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Clientes</h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {loading ? 'A carregar…' : `${totalCount} cliente${totalCount === 1 ? '' : 's'}`}
            </p>
          </div>
          <Button onClick={handleCreateClick} className="w-full sm:w-auto">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Novo cliente
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
                placeholder="Pesquisar por nome, telefone ou email…"
                className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              />
            </div>
            <div className="sm:w-52">
              <Select
                aria-label="Filtrar por estado"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value as ClientStatus | 'all');
                  setPage(0);
                }}
                className="sm:mt-0"
              >
                <option value="all">Todos os estados</option>
                {Object.entries(CLIENT_STATUS_LABELS).map(([value, label]) => (
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
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-4 p-4">
                  <Skeleton className="h-10 w-10 rounded-full" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-1/3" />
                    <Skeleton className="h-3 w-1/4" />
                  </div>
                  <Skeleton className="h-6 w-16 rounded-full" />
                </div>
              ))}
            </div>
          ) : clients.length === 0 ? (
            <div className="p-4">
              <EmptyState
                icon={Users}
                title={hasActiveFilters ? 'Nenhum cliente encontrado' : 'Ainda não tem clientes'}
                description={
                  hasActiveFilters
                    ? 'Tente ajustar a pesquisa ou os filtros.'
                    : 'Adicione o seu primeiro cliente para começar a gerir cobranças.'
                }
                actionLabel={hasActiveFilters ? undefined : 'Novo cliente'}
                onAction={hasActiveFilters ? undefined : handleCreateClick}
              />
            </div>
          ) : (
            <>
              {/* Vista mobile: cards */}
              <div className="divide-y divide-slate-200 dark:divide-slate-800 md:hidden">
                {clients.map((client) => {
                  const balance = balances[client.id];
                  const owed = balance ? balance.total_pending + balance.total_overdue : 0;
                  return (
                    <button
                      key={client.id}
                      onClick={() => navigate(`/clients/${client.id}`)}
                      className="flex w-full flex-col gap-1.5 p-4 text-left"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-medium text-slate-900 dark:text-white">{client.name}</span>
                        <ClientStatusBadge status={client.status} />
                      </div>
                      <span className="text-sm text-slate-500 dark:text-slate-400">
                        {client.phone || client.email || 'Sem contacto'}
                      </span>
                      {owed > 0 && (
                        <span className="text-sm font-medium text-red-600 dark:text-red-400">
                          Em dívida: {formatCurrency(owed, currency)}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>

              {/* Vista desktop: tabela */}
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400">
                      <th className="px-4 py-3 font-medium">Nome</th>
                      <th className="px-4 py-3 font-medium">Contacto</th>
                      <th className="px-4 py-3 font-medium">Estado</th>
                      <th className="px-4 py-3 font-medium">Criado em</th>
                      <th className="px-4 py-3 font-medium">Em dívida</th>
                      <th className="px-4 py-3 font-medium text-right">Acções</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {clients.map((client) => {
                      const balance = balances[client.id];
                      const owed = balance ? balance.total_pending + balance.total_overdue : 0;
                      return (
                        <tr
                          key={client.id}
                          className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
                          onClick={() => navigate(`/clients/${client.id}`)}
                        >
                          <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                            {client.name}
                          </td>
                          <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                            <div>{client.phone || '—'}</div>
                            <div className="text-xs">{client.email || ''}</div>
                          </td>
                          <td className="px-4 py-3">
                            <ClientStatusBadge status={client.status} />
                          </td>
                          <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                            {formatDate(client.created_at)}
                          </td>
                          <td className="px-4 py-3">
                            {owed > 0 ? (
                              <span className="font-medium text-red-600 dark:text-red-400">
                                {formatCurrency(owed, currency)}
                              </span>
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                            <div className="relative inline-block text-left">
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setOpenMenuId(openMenuId === client.id ? null : client.id);
                                }}
                                aria-label="Mais acções"
                                className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
                              >
                                <MoreVertical className="h-4 w-4" />
                              </button>
                              {openMenuId === client.id && (
                                <div className="absolute right-0 z-10 mt-1 w-44 rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-800 dark:bg-slate-900">
                                  <button
                                    onClick={() => navigate(`/clients/${client.id}`)}
                                    className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                                  >
                                    <Eye className="h-4 w-4" /> Ver perfil
                                  </button>
                                  <button
                                    onClick={() => handleEditClick(client)}
                                    className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                                  >
                                    <Pencil className="h-4 w-4" /> Editar
                                  </button>
                                  {client.status !== 'archived' && (
                                    <button
                                      onClick={() => {
                                        setArchiveTarget(client);
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
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="px-4">
                <Pagination
                  page={page}
                  pageSize={PAGE_SIZE}
                  totalCount={totalCount}
                  onPageChange={setPage}
                />
              </div>
            </>
          )}
        </Card>
      </div>

      {organization && (
        <ClientFormModal
          open={formOpen}
          organizationId={organization.id}
          client={editingClient}
          onClose={() => setFormOpen(false)}
          onSaved={handleSaved}
        />
      )}

      <ConfirmDialog
        open={Boolean(archiveTarget)}
        title="Arquivar cliente"
        description={`Tem a certeza que quer arquivar "${archiveTarget?.name}"? Pode reverter isto mais tarde ao editar o cliente.`}
        confirmLabel="Arquivar"
        danger
        loading={archiving}
        onConfirm={handleConfirmArchive}
        onCancel={() => setArchiveTarget(null)}
      />
    </AppShell>
  );
}
