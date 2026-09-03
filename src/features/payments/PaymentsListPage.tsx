import { useCallback, useEffect, useMemo, useState } from 'react';
import { Search, Plus, Wallet, CalendarCheck, CalendarDays } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Pagination } from '@/components/ui/Pagination';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import type { PaymentMethod } from '@/lib/types/database';
import { fetchPaymentTotals, fetchPayments, type PaymentTotals, type PaymentWithRelations } from './api';
import { RegisterPaymentModal } from './RegisterPaymentModal';
import { PaymentDetailModal } from './PaymentDetailModal';
import { PAYABLE_METHOD_OPTIONS, PAYMENT_METHOD_LABELS, PERIOD_LABELS, type PaymentPeriod } from './types';

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

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function PaymentsListPage() {
  const { organization } = useAuth();
  const { showToast } = useToast();

  const [payments, setPayments] = useState<PaymentWithRelations[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [totals, setTotals] = useState<PaymentTotals | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [clientSearchInput, setClientSearchInput] = useState('');
  const [clientSearch, setClientSearch] = useState('');
  const [referenceSearchInput, setReferenceSearchInput] = useState('');
  const [referenceSearch, setReferenceSearch] = useState('');
  const [method, setMethod] = useState<PaymentMethod | 'all'>('all');
  const [period, setPeriod] = useState<PaymentPeriod>('all');
  const [page, setPage] = useState(0);

  const [registerOpen, setRegisterOpen] = useState(false);
  const [selectedPayment, setSelectedPayment] = useState<PaymentWithRelations | null>(null);

  const currency = organization?.currency ?? 'AOA';

  useEffect(() => {
    const timeout = setTimeout(() => {
      setClientSearch(clientSearchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(timeout);
  }, [clientSearchInput]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      setReferenceSearch(referenceSearchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(timeout);
  }, [referenceSearchInput]);

  const load = useCallback(async () => {
    if (!organization) return;
    setLoading(true);
    setError(null);

    const { payments: fetched, totalCount: count, error: fetchError } = await fetchPayments(organization.id, {
      clientSearch,
      referenceSearch,
      method,
      period,
      page,
      pageSize: PAGE_SIZE,
    });

    if (fetchError) {
      setError('Não foi possível carregar os pagamentos.');
      setLoading(false);
      return;
    }

    setPayments(fetched);
    setTotalCount(count);
    setLoading(false);

    const { totals: fetchedTotals } = await fetchPaymentTotals(organization.id);
    setTotals(fetchedTotals);
  }, [organization, clientSearch, referenceSearch, method, period, page]);

  useEffect(() => {
    load();
  }, [load]);

  const hasActiveFilters = useMemo(
    () => clientSearch.trim() !== '' || referenceSearch.trim() !== '' || method !== 'all' || period !== 'all',
    [clientSearch, referenceSearch, method, period]
  );

  function handleRegistered() {
    setRegisterOpen(false);
    showToast('Pagamento registado com sucesso.');
    load();
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Pagamentos</h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {loading ? 'A carregar…' : `${totalCount} pagamento${totalCount === 1 ? '' : 's'}`}
            </p>
          </div>
          <Button onClick={() => setRegisterOpen(true)} className="w-full sm:w-auto">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Registar pagamento
          </Button>
        </div>

        {/* Totais */}
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <Wallet className="h-4 w-4 text-emerald-500" /> Total recebido
            </div>
            <div className="mt-2 text-xl font-bold text-emerald-600 dark:text-emerald-400">
              {totals ? formatCurrency(totals.total_received, currency) : <Skeleton className="h-6 w-24" />}
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <CalendarDays className="h-4 w-4 text-brand-500" /> Este mês
            </div>
            <div className="mt-2 text-xl font-bold text-brand-600 dark:text-brand-400">
              {totals ? formatCurrency(totals.total_this_month, currency) : <Skeleton className="h-6 w-24" />}
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <CalendarCheck className="h-4 w-4 text-amber-500" /> Hoje
            </div>
            <div className="mt-2 text-xl font-bold text-amber-600 dark:text-amber-400">
              {totals ? formatCurrency(totals.total_today, currency) : <Skeleton className="h-6 w-24" />}
            </div>
          </Card>
        </div>
        <p className="mt-2 text-xs text-slate-400">
          Não existe "pagamentos pendentes" nesta página porque um pagamento só é registado quando já
          está concluído — os valores pendentes/atrasados de facturas ainda não pagas estão em Cobranças
          e no Dashboard.
        </p>

        {/* Filtros */}
        <Card className="mt-4 p-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                type="text"
                value={clientSearchInput}
                onChange={(e) => setClientSearchInput(e.target.value)}
                placeholder="Pesquisar por cliente…"
                className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              />
            </div>
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                type="text"
                value={referenceSearchInput}
                onChange={(e) => setReferenceSearchInput(e.target.value)}
                placeholder="Pesquisar por referência…"
                className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              />
            </div>
            <Select
              aria-label="Filtrar por método"
              value={method}
              onChange={(e) => {
                setMethod(e.target.value as PaymentMethod | 'all');
                setPage(0);
              }}
            >
              <option value="all">Todos os métodos</option>
              {PAYABLE_METHOD_OPTIONS.map((value) => (
                <option key={value} value={value}>
                  {PAYMENT_METHOD_LABELS[value]}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Filtrar por período"
              value={period}
              onChange={(e) => {
                setPeriod(e.target.value as PaymentPeriod);
                setPage(0);
              }}
            >
              {Object.entries(PERIOD_LABELS).map(([value, label]) => (
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
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-4 p-4">
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-1/3" />
                    <Skeleton className="h-3 w-1/4" />
                  </div>
                  <Skeleton className="h-6 w-20 rounded-full" />
                </div>
              ))}
            </div>
          ) : payments.length === 0 ? (
            <div className="p-4">
              <EmptyState
                icon={Wallet}
                title={hasActiveFilters ? 'Nenhum pagamento encontrado' : 'Ainda não tem pagamentos registados'}
                description={
                  hasActiveFilters
                    ? 'Tente ajustar a pesquisa ou os filtros.'
                    : 'Registe o primeiro pagamento associado a uma cobrança.'
                }
                actionLabel={hasActiveFilters ? undefined : 'Registar pagamento'}
                onAction={hasActiveFilters ? undefined : () => setRegisterOpen(true)}
              />
            </div>
          ) : (
            <>
              {/* Vista mobile: cards */}
              <div className="divide-y divide-slate-200 dark:divide-slate-800 md:hidden">
                {payments.map((payment) => (
                  <button
                    key={payment.id}
                    onClick={() => setSelectedPayment(payment)}
                    className="flex w-full flex-col gap-1.5 p-4 text-left"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-slate-900 dark:text-white">
                        {payment.clients?.name ?? 'Cliente removido'}
                      </span>
                      <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                        {formatCurrency(payment.amount, payment.currency)}
                      </span>
                    </div>
                    <span className="text-sm text-slate-500 dark:text-slate-400">
                      {payment.invoices?.invoice_number ?? '—'} · {PAYMENT_METHOD_LABELS[payment.payment_method]}
                    </span>
                    <span className="text-xs text-slate-400">{formatDateTime(payment.paid_at)}</span>
                  </button>
                ))}
              </div>

              {/* Vista desktop: tabela */}
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400">
                      <th className="px-4 py-3 font-medium">Data</th>
                      <th className="px-4 py-3 font-medium">Cliente</th>
                      <th className="px-4 py-3 font-medium">Cobrança</th>
                      <th className="px-4 py-3 font-medium">Método</th>
                      <th className="px-4 py-3 font-medium">Referência</th>
                      <th className="px-4 py-3 font-medium">Valor</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {payments.map((payment) => (
                      <tr
                        key={payment.id}
                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
                        onClick={() => setSelectedPayment(payment)}
                      >
                        <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                          {formatDateTime(payment.paid_at)}
                        </td>
                        <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                          {payment.clients?.name ?? 'Cliente removido'}
                        </td>
                        <td className="px-4 py-3 font-mono text-xs text-slate-500 dark:text-slate-400">
                          {payment.invoices?.invoice_number ?? '—'}
                        </td>
                        <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                          {PAYMENT_METHOD_LABELS[payment.payment_method]}
                        </td>
                        <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                          {payment.transaction_reference || '—'}
                        </td>
                        <td className="px-4 py-3 font-medium text-emerald-600 dark:text-emerald-400">
                          {formatCurrency(payment.amount, payment.currency)}
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
        <RegisterPaymentModal
          open={registerOpen}
          organizationId={organization.id}
          onClose={() => setRegisterOpen(false)}
          onRegistered={handleRegistered}
        />
      )}

      <PaymentDetailModal
        open={Boolean(selectedPayment)}
        payment={selectedPayment}
        onClose={() => setSelectedPayment(null)}
      />
    </AppShell>
  );
}
