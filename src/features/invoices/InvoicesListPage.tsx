import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Plus, FileText, Wallet, Clock, AlertTriangle } from 'lucide-react';
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
import { supabase } from '@/lib/supabase';
import type { InvoiceStatus } from '@/lib/types/database';
import { fetchInvoices, type InvoiceWithClient } from './api';
import { InvoiceFormModal } from './InvoiceFormModal';
import { InvoiceStatusBadge } from './InvoiceStatusBadge';
import { INVOICE_STATUS_LABELS, type InvoicePeriod } from './types';

const PAGE_SIZE = 10;

const PERIOD_LABELS: Record<InvoicePeriod, string> = {
  all: 'Todo o período',
  this_month: 'Este mês',
  last_month: 'Mês passado',
  next_30_days: 'Próximos 30 dias',
};

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

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

interface DashboardTotals {
  total_received: number;
  total_pending: number;
  total_overdue: number;
}

export function InvoicesListPage() {
  const { organization } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [invoices, setInvoices] = useState<InvoiceWithClient[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [totals, setTotals] = useState<DashboardTotals | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [clientSearchInput, setClientSearchInput] = useState('');
  const [clientSearch, setClientSearch] = useState('');
  const [invoiceNumberSearchInput, setInvoiceNumberSearchInput] = useState('');
  const [invoiceNumberSearch, setInvoiceNumberSearch] = useState('');
  const [status, setStatus] = useState<InvoiceStatus | 'all'>('all');
  const [period, setPeriod] = useState<InvoicePeriod>('all');
  const [page, setPage] = useState(0);

  const [formOpen, setFormOpen] = useState(false);

  useEffect(() => {
    const timeout = setTimeout(() => {
      setClientSearch(clientSearchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(timeout);
  }, [clientSearchInput]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      setInvoiceNumberSearch(invoiceNumberSearchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(timeout);
  }, [invoiceNumberSearchInput]);

  const load = useCallback(async () => {
    if (!organization) return;
    setLoading(true);
    setError(null);

    const { invoices: fetched, totalCount: count, error: fetchError } = await fetchInvoices(
      organization.id,
      { clientSearch, invoiceNumberSearch, status, period, page, pageSize: PAGE_SIZE }
    );

    if (fetchError) {
      setError('Não foi possível carregar as cobranças.');
      setLoading(false);
      return;
    }

    setInvoices(fetched);
    setTotalCount(count);
    setLoading(false);

    const { data: totalsData } = await supabase
      .from('dashboard_totals')
      .select('total_received, total_pending, total_overdue')
      .eq('organization_id', organization.id)
      .maybeSingle();

    setTotals(
      (totalsData as DashboardTotals) ?? { total_received: 0, total_pending: 0, total_overdue: 0 }
    );
  }, [organization, clientSearch, invoiceNumberSearch, status, period, page]);

  useEffect(() => {
    load();
  }, [load]);

  const currency = organization?.currency ?? 'AOA';
  const hasActiveFilters = useMemo(
    () => clientSearch.trim() !== '' || invoiceNumberSearch.trim() !== '' || status !== 'all' || period !== 'all',
    [clientSearch, invoiceNumberSearch, status, period]
  );

  function handleSaved(_invoice: InvoiceWithClient, mode: 'created' | 'updated') {
    setFormOpen(false);
    showToast(mode === 'created' ? 'Cobrança criada com sucesso.' : 'Cobrança actualizada.');
    load();
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Cobranças</h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {loading ? 'A carregar…' : `${totalCount} cobrança${totalCount === 1 ? '' : 's'}`}
            </p>
          </div>
          <Button onClick={() => setFormOpen(true)} className="w-full sm:w-auto">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Nova cobrança
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
              <Clock className="h-4 w-4 text-amber-500" /> Total pendente
            </div>
            <div className="mt-2 text-xl font-bold text-amber-600 dark:text-amber-400">
              {totals ? formatCurrency(totals.total_pending, currency) : <Skeleton className="h-6 w-24" />}
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <AlertTriangle className="h-4 w-4 text-red-500" /> Total atrasado
            </div>
            <div className="mt-2 text-xl font-bold text-red-600 dark:text-red-400">
              {totals ? formatCurrency(totals.total_overdue, currency) : <Skeleton className="h-6 w-24" />}
            </div>
          </Card>
        </div>

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
                value={invoiceNumberSearchInput}
                onChange={(e) => setInvoiceNumberSearchInput(e.target.value)}
                placeholder="Nº da cobrança (ex: FAT-000012)…"
                className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              />
            </div>
            <Select
              aria-label="Filtrar por estado"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as InvoiceStatus | 'all');
                setPage(0);
              }}
            >
              <option value="all">Todos os estados</option>
              {Object.entries(INVOICE_STATUS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Filtrar por período"
              value={period}
              onChange={(e) => {
                setPeriod(e.target.value as InvoicePeriod);
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
          ) : invoices.length === 0 ? (
            <div className="p-4">
              <EmptyState
                icon={FileText}
                title={hasActiveFilters ? 'Nenhuma cobrança encontrada' : 'Ainda não tem cobranças'}
                description={
                  hasActiveFilters
                    ? 'Tente ajustar a pesquisa ou os filtros.'
                    : 'Crie a sua primeira cobrança para começar a acompanhar os pagamentos.'
                }
                actionLabel={hasActiveFilters ? undefined : 'Nova cobrança'}
                onAction={hasActiveFilters ? undefined : () => setFormOpen(true)}
              />
            </div>
          ) : (
            <>
              {/* Vista mobile: cards */}
              <div className="divide-y divide-slate-200 dark:divide-slate-800 md:hidden">
                {invoices.map((invoice) => (
                  <button
                    key={invoice.id}
                    onClick={() => navigate(`/invoices/${invoice.id}`)}
                    className="flex w-full flex-col gap-1.5 p-4 text-left"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-slate-900 dark:text-white">
                        {invoice.clients?.name ?? 'Cliente removido'}
                      </span>
                      <InvoiceStatusBadge status={invoice.status} />
                    </div>
                    <span className="text-sm text-slate-500 dark:text-slate-400">
                      {invoice.description || invoice.invoice_number} · vence em{' '}
                      {formatDate(invoice.due_date)}
                    </span>
                    <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">
                      {formatCurrency(invoice.amount, invoice.currency)}
                    </span>
                  </button>
                ))}
              </div>

              {/* Vista desktop: tabela */}
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400">
                      <th className="px-4 py-3 font-medium">Nº</th>
                      <th className="px-4 py-3 font-medium">Cliente</th>
                      <th className="px-4 py-3 font-medium">Descrição</th>
                      <th className="px-4 py-3 font-medium">Valor</th>
                      <th className="px-4 py-3 font-medium">Vencimento</th>
                      <th className="px-4 py-3 font-medium">Estado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {invoices.map((invoice) => (
                      <tr
                        key={invoice.id}
                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
                        onClick={() => navigate(`/invoices/${invoice.id}`)}
                      >
                        <td className="px-4 py-3 font-mono text-xs text-slate-500 dark:text-slate-400">
                          {invoice.invoice_number}
                        </td>
                        <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                          {invoice.clients?.name ?? 'Cliente removido'}
                        </td>
                        <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                          {invoice.description || '—'}
                        </td>
                        <td className="px-4 py-3 font-medium text-slate-700 dark:text-slate-300">
                          {formatCurrency(invoice.amount, invoice.currency)}
                        </td>
                        <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                          {formatDate(invoice.due_date)}
                        </td>
                        <td className="px-4 py-3">
                          <InvoiceStatusBadge status={invoice.status} />
                        </td>
                      </tr>
                    ))}
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
        <InvoiceFormModal
          open={formOpen}
          organizationId={organization.id}
          defaultCurrency={organization.currency}
          invoice={null}
          onClose={() => setFormOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </AppShell>
  );
}
