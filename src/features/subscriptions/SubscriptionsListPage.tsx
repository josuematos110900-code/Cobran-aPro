import { useCallback, useEffect, useMemo, useState } from 'react';
import { Plus, Repeat, Pause, Play, Ban, Pencil } from 'lucide-react';
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
import type { SubscriptionStatus } from '@/lib/types/database';
import { fetchSubscriptions, setSubscriptionStatus, type SubscriptionWithRelations } from './api';
import { SubscriptionFormModal } from './SubscriptionFormModal';
import { SubscriptionStatusBadge } from './SubscriptionStatusBadge';
import { BILLING_PERIOD_LABELS, SUBSCRIPTION_STATUS_LABELS } from './types';

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

function formatDate(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

export function SubscriptionsListPage() {
  const { organization } = useAuth();
  const { showToast } = useToast();

  const [subscriptions, setSubscriptions] = useState<SubscriptionWithRelations[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [status, setStatus] = useState<SubscriptionStatus | 'all'>('all');
  const [page, setPage] = useState(0);

  const [formOpen, setFormOpen] = useState(false);
  const [editingSubscription, setEditingSubscription] = useState<SubscriptionWithRelations | null>(null);
  const [cancelTarget, setCancelTarget] = useState<SubscriptionWithRelations | null>(null);
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!organization) return;
    setLoading(true);
    setError(null);

    const { subscriptions: fetched, totalCount: count, error: fetchError } = await fetchSubscriptions(
      organization.id,
      { status, page, pageSize: PAGE_SIZE }
    );

    if (fetchError) {
      setError('Não foi possível carregar as cobranças recorrentes.');
      setLoading(false);
      return;
    }

    setSubscriptions(fetched);
    setTotalCount(count);
    setLoading(false);
  }, [organization, status, page]);

  useEffect(() => {
    load();
  }, [load]);

  const hasActiveFilters = useMemo(() => status !== 'all', [status]);

  function handleCreateClick() {
    setEditingSubscription(null);
    setFormOpen(true);
  }

  function handleEditClick(subscription: SubscriptionWithRelations) {
    setEditingSubscription(subscription);
    setFormOpen(true);
  }

  function handleSaved(_subscription: SubscriptionWithRelations, mode: 'created' | 'updated') {
    setFormOpen(false);
    showToast(mode === 'created' ? 'Cobrança recorrente criada.' : 'Cobrança recorrente actualizada.');
    load();
  }

  async function handleTogglePause(subscription: SubscriptionWithRelations) {
    setActionLoadingId(subscription.id);
    const nextStatus = subscription.status === 'active' ? 'paused' : 'active';
    const { error: statusError } = await setSubscriptionStatus(subscription.id, nextStatus);
    setActionLoadingId(null);

    if (statusError) {
      showToast('Não foi possível actualizar o estado.', 'error');
      return;
    }

    showToast(nextStatus === 'paused' ? 'Subscrição pausada.' : 'Subscrição retomada.');
    load();
  }

  async function handleConfirmCancel() {
    if (!cancelTarget) return;
    setActionLoadingId(cancelTarget.id);
    const { error: statusError } = await setSubscriptionStatus(cancelTarget.id, 'cancelled');
    setActionLoadingId(null);

    if (statusError) {
      showToast('Não foi possível cancelar a subscrição.', 'error');
      return;
    }

    showToast('Subscrição cancelada.');
    setCancelTarget(null);
    load();
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Cobranças recorrentes</h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {loading ? 'A carregar…' : `${totalCount} subscri${totalCount === 1 ? 'ção' : 'ções'}`}
            </p>
          </div>
          <Button onClick={handleCreateClick} className="w-full sm:w-auto">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Nova cobrança recorrente
          </Button>
        </div>

        <Card className="mt-6 p-4">
          <div className="sm:w-52">
            <Select
              aria-label="Filtrar por estado"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as SubscriptionStatus | 'all');
                setPage(0);
              }}
            >
              <option value="all">Todos os estados</option>
              {Object.entries(SUBSCRIPTION_STATUS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
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
          ) : subscriptions.length === 0 ? (
            <div className="p-4">
              <EmptyState
                icon={Repeat}
                title={hasActiveFilters ? 'Nenhuma subscrição encontrada' : 'Ainda não tem cobranças recorrentes'}
                description={
                  hasActiveFilters
                    ? 'Tente ajustar o filtro de estado.'
                    : 'Crie uma cobrança recorrente para começar a gerar facturas automaticamente.'
                }
                actionLabel={hasActiveFilters ? undefined : 'Nova cobrança recorrente'}
                onAction={hasActiveFilters ? undefined : handleCreateClick}
              />
            </div>
          ) : (
            <>
              <div className="divide-y divide-slate-200 dark:divide-slate-800">
                {subscriptions.map((subscription) => (
                  <div
                    key={subscription.id}
                    className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-slate-900 dark:text-white">
                          {subscription.services?.name ?? 'Serviço removido'}
                        </span>
                        <SubscriptionStatusBadge status={subscription.status} />
                      </div>
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {subscription.clients?.name ?? 'Cliente removido'} ·{' '}
                        {formatCurrency(subscription.amount, subscription.currency)} /{' '}
                        {BILLING_PERIOD_LABELS[subscription.billing_period].toLowerCase()}
                      </p>
                      {subscription.status === 'active' && (
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          Próxima cobrança: {formatDate(subscription.next_billing_date)}
                        </p>
                      )}
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="secondary"
                        onClick={() => handleEditClick(subscription)}
                        disabled={subscription.status === 'cancelled' || subscription.status === 'ended'}
                      >
                        <Pencil className="h-4 w-4" /> Editar
                      </Button>
                      {(subscription.status === 'active' || subscription.status === 'paused') && (
                        <Button
                          variant="secondary"
                          onClick={() => handleTogglePause(subscription)}
                          loading={actionLoadingId === subscription.id}
                        >
                          {subscription.status === 'active' ? (
                            <>
                              <Pause className="h-4 w-4" /> Pausar
                            </>
                          ) : (
                            <>
                              <Play className="h-4 w-4" /> Retomar
                            </>
                          )}
                        </Button>
                      )}
                      {(subscription.status === 'active' || subscription.status === 'paused') && (
                        <Button variant="danger" onClick={() => setCancelTarget(subscription)}>
                          <Ban className="h-4 w-4" /> Cancelar
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              <div className="px-4">
                <Pagination page={page} pageSize={PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} />
              </div>
            </>
          )}
        </Card>
      </div>

      {organization && (
        <SubscriptionFormModal
          open={formOpen}
          organizationId={organization.id}
          defaultCurrency={organization.currency}
          subscription={editingSubscription}
          onClose={() => setFormOpen(false)}
          onSaved={handleSaved}
        />
      )}

      <ConfirmDialog
        open={Boolean(cancelTarget)}
        title="Cancelar cobrança recorrente"
        description={`Tem a certeza que quer cancelar esta subscrição de "${cancelTarget?.clients?.name ?? ''}"? Não serão geradas mais facturas a partir dela.`}
        confirmLabel="Cancelar subscrição"
        danger
        loading={actionLoadingId === cancelTarget?.id}
        onConfirm={handleConfirmCancel}
        onCancel={() => setCancelTarget(null)}
      />
    </AppShell>
  );
}
