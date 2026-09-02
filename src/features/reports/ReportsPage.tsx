import { useCallback, useEffect, useState } from 'react';
import {
  Wallet,
  Receipt,
  Clock,
  AlertTriangle,
  Percent,
  Users,
  Repeat,
  Printer,
  Download,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import {
  fetchClientsForExport,
  fetchInvoiceStatusBreakdown,
  fetchInvoicesForExport,
  fetchOverdueTop,
  fetchPaymentsForExport,
  fetchReportSummary,
  fetchRevenueSeries,
  fetchServiceBreakdown,
  fetchTopClients,
  type InvoiceStatusBreakdownRow,
  type OverdueTopRow,
  type ReportSummary,
  type RevenuePoint,
  type ServiceBreakdownRow,
  type TopClientRow,
} from './api';
import { PeriodSelector } from './PeriodSelector';
import { RevenueChart } from './RevenueChart';
import { InvoiceStatusChart } from './InvoiceStatusChart';
import { TopClientsTable } from './TopClientsTable';
import { ServiceBreakdownTable } from './ServiceBreakdownTable';
import { OverdueTopList } from './OverdueTopList';
import {
  buildCsv,
  computePeriodRange,
  defaultGranularityForPreset,
  downloadCsv,
  toDateKey,
  type ReportGranularity,
  type ReportPeriodPreset,
} from './types';

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

export function ReportsPage() {
  const { organization } = useAuth();
  const { showToast } = useToast();
  const currency = organization?.currency ?? 'AOA';

  const [preset, setPreset] = useState<ReportPeriodPreset>('this_month');
  const [customFrom, setCustomFrom] = useState(toDateKey(new Date()));
  const [customTo, setCustomTo] = useState(toDateKey(new Date()));
  const [granularity, setGranularity] = useState<ReportGranularity>(defaultGranularityForPreset('this_month'));

  const [summary, setSummary] = useState<ReportSummary | null>(null);
  const [revenueSeries, setRevenueSeries] = useState<RevenuePoint[]>([]);
  const [statusBreakdown, setStatusBreakdown] = useState<InvoiceStatusBreakdownRow[]>([]);
  const [topClients, setTopClients] = useState<TopClientRow[]>([]);
  const [serviceBreakdown, setServiceBreakdown] = useState<ServiceBreakdownRow[]>([]);
  const [overdueTop, setOverdueTop] = useState<OverdueTopRow[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);

  const { from, to } = computePeriodRange(preset, customFrom, customTo);

  function handlePresetChange(newPreset: ReportPeriodPreset) {
    setPreset(newPreset);
    setGranularity(defaultGranularityForPreset(newPreset));
    if (newPreset === 'custom') {
      const today = toDateKey(new Date());
      setCustomFrom(today);
      setCustomTo(today);
    }
  }

  const load = useCallback(async () => {
    if (!organization) return;
    setLoading(true);
    setError(null);

    const [
      { summary: fetchedSummary, error: summaryError },
      { series: fetchedSeries },
      { breakdown: fetchedBreakdown },
      { clients: fetchedTopClients },
      { services: fetchedServices },
      { overdue: fetchedOverdue },
    ] = await Promise.all([
      fetchReportSummary(organization.id, from, to),
      fetchRevenueSeries(organization.id, from, to, granularity),
      fetchInvoiceStatusBreakdown(organization.id, from, to),
      fetchTopClients(organization.id, from, to),
      fetchServiceBreakdown(organization.id, from, to),
      fetchOverdueTop(organization.id),
    ]);

    if (summaryError) {
      setError('Não foi possível carregar os relatórios.');
      setLoading(false);
      return;
    }

    setSummary(fetchedSummary);
    setRevenueSeries(fetchedSeries);
    setStatusBreakdown(fetchedBreakdown);
    setTopClients(fetchedTopClients);
    setServiceBreakdown(fetchedServices);
    setOverdueTop(fetchedOverdue);
    setLoading(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [organization, from, to, granularity]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleExportPayments() {
    if (!organization) return;
    setExporting('payments');
    const { rows, error: exportError } = await fetchPaymentsForExport(organization.id, from, to);
    setExporting(null);

    if (exportError) {
      showToast('Não foi possível exportar os pagamentos.', 'error');
      return;
    }

    const csv = buildCsv(
      ['Data', 'Cliente', 'Cobrança', 'Valor', 'Moeda', 'Método', 'Referência'],
      rows.map((row: any) => [
        row.paid_at,
        row.clients?.name ?? '',
        row.invoices?.invoice_number ?? '',
        row.amount,
        row.currency,
        row.payment_method,
        row.transaction_reference ?? '',
      ])
    );
    downloadCsv(`pagamentos_${from}_a_${to}.csv`, csv);
  }

  async function handleExportInvoices() {
    if (!organization) return;
    setExporting('invoices');
    const { rows, error: exportError } = await fetchInvoicesForExport(organization.id, from, to);
    setExporting(null);

    if (exportError) {
      showToast('Não foi possível exportar as cobranças.', 'error');
      return;
    }

    const csv = buildCsv(
      ['Nº', 'Cliente', 'Descrição', 'Valor', 'Moeda', 'Vencimento', 'Estado'],
      rows.map((row: any) => [
        row.invoice_number,
        row.clients?.name ?? '',
        row.description ?? '',
        row.amount,
        row.currency,
        row.due_date,
        row.status,
      ])
    );
    downloadCsv(`cobrancas_${from}_a_${to}.csv`, csv);
  }

  async function handleExportClients() {
    if (!organization) return;
    setExporting('clients');
    const { rows, error: exportError } = await fetchClientsForExport(organization.id);
    setExporting(null);

    if (exportError) {
      showToast('Não foi possível exportar os clientes.', 'error');
      return;
    }

    const csv = buildCsv(
      ['Nome', 'Telefone', 'Email', 'Estado', 'Total pago', 'Total pendente', 'Total atrasado'],
      rows.map((row) => [
        row.name,
        row.phone ?? '',
        row.email ?? '',
        row.status,
        row.total_paid,
        row.total_pending,
        row.total_overdue,
      ])
    );
    downloadCsv(`clientes_${toDateKey(new Date())}.csv`, csv);
  }

  function handleExportSummary() {
    if (!summary) return;
    const csv = buildCsv(
      ['Indicador', 'Valor'],
      [
        ['Período', `${from} a ${to}`],
        ['Receita recebida', summary.revenue_received],
        ['Total cobrado', summary.total_billed],
        ['Total pendente', summary.total_pending],
        ['Total atrasado', summary.total_overdue],
        ['Taxa de pagamento (%)', summary.payment_rate],
        ['Clientes activos', summary.active_clients],
        ['Subscrições activas', summary.active_subscriptions],
      ]
    );
    downloadCsv(`relatorio_${from}_a_${to}.csv`, csv);
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8 print:p-0">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between print:hidden">
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Relatórios</h1>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={handleExportSummary}>
              <Download className="h-4 w-4" /> Exportar relatório
            </Button>
            <Button variant="secondary" onClick={() => window.print()}>
              <Printer className="h-4 w-4" /> Imprimir
            </Button>
          </div>
        </div>

        <div className="mt-4 print:hidden">
          <Card className="p-4">
            <PeriodSelector
              preset={preset}
              customFrom={customFrom}
              customTo={customTo}
              onPresetChange={handlePresetChange}
              onCustomChange={(newFrom, newTo) => {
                setCustomFrom(newFrom);
                setCustomTo(newTo);
              }}
            />
          </Card>
        </div>

        <p className="mt-2 hidden text-sm text-slate-500 print:block">
          Período: {from} a {to}
        </p>

        {error && (
          <div className="mt-4">
            <ErrorMessage message={error} />
          </div>
        )}

        {/* Indicadores principais */}
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <SummaryCard
            icon={Wallet}
            label="Receita recebida"
            value={loading ? null : formatCurrency(summary?.revenue_received ?? 0, currency)}
            accent="text-emerald-600 dark:text-emerald-400"
          />
          <SummaryCard
            icon={Receipt}
            label="Total cobrado"
            value={loading ? null : formatCurrency(summary?.total_billed ?? 0, currency)}
            accent="text-brand-600 dark:text-brand-400"
          />
          <SummaryCard
            icon={Clock}
            label="Total pendente"
            value={loading ? null : formatCurrency(summary?.total_pending ?? 0, currency)}
            accent="text-amber-600 dark:text-amber-400"
          />
          <SummaryCard
            icon={AlertTriangle}
            label="Total atrasado"
            value={loading ? null : formatCurrency(summary?.total_overdue ?? 0, currency)}
            accent="text-red-600 dark:text-red-400"
          />
          <SummaryCard
            icon={Percent}
            label="Taxa de pagamento"
            value={loading ? null : `${summary?.payment_rate ?? 0}%`}
            accent="text-brand-600 dark:text-brand-400"
          />
          <SummaryCard
            icon={Users}
            label="Clientes activos"
            value={loading ? null : String(summary?.active_clients ?? 0)}
            accent="text-slate-700 dark:text-slate-300"
          />
          <SummaryCard
            icon={Repeat}
            label="Subscrições activas"
            value={loading ? null : String(summary?.active_subscriptions ?? 0)}
            accent="text-slate-700 dark:text-slate-300"
          />
        </div>
        <p className="mt-2 text-xs text-slate-400 print:hidden">
          Taxa de pagamento = cobranças pagas ÷ emitidas (excluindo canceladas) neste período. Clientes e
          subscrições activas reflectem o estado actual, não o período seleccionado.
        </p>

        {/* Gráfico de receita */}
        <Card className="mt-6">
          <div className="flex items-center justify-between print:hidden">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Evolução da receita
            </h2>
            <div className="w-36">
              <Select
                aria-label="Agrupar por"
                value={granularity}
                onChange={(e) => setGranularity(e.target.value as ReportGranularity)}
              >
                <option value="day">Por dia</option>
                <option value="week">Por semana</option>
                <option value="month">Por mês</option>
              </Select>
            </div>
          </div>
          <div className="mt-3">
            {loading ? <Skeleton className="h-64 w-full" /> : <RevenueChart data={revenueSeries} currency={currency} granularity={granularity} />}
          </div>
        </Card>

        {/* Gráfico de cobranças por estado */}
        <Card className="mt-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Cobranças por estado
          </h2>
          <div className="mt-3">
            {loading ? <Skeleton className="h-64 w-full" /> : <InvoiceStatusChart data={statusBreakdown} />}
          </div>
        </Card>

        {/* Principais clientes */}
        <Card className="mt-4">
          <div className="flex items-center justify-between print:hidden">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Principais clientes
            </h2>
            <Button variant="ghost" onClick={handleExportClients} loading={exporting === 'clients'}>
              <Download className="h-4 w-4" /> CSV
            </Button>
          </div>
          <div className="mt-3">
            {loading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : (
              <TopClientsTable clients={topClients} currency={currency} />
            )}
          </div>
        </Card>

        {/* Receita por serviço */}
        <Card className="mt-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Serviços
          </h2>
          <div className="mt-3">
            {loading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : (
              <ServiceBreakdownTable services={serviceBreakdown} currency={currency} />
            )}
          </div>
        </Card>

        {/* Maiores valores em atraso */}
        <Card className="mt-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Maiores valores em atraso
          </h2>
          <div className="mt-3">
            {loading ? (
              <div className="space-y-2">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : (
              <OverdueTopList overdue={overdueTop} />
            )}
          </div>
        </Card>

        {/* Exportações */}
        <Card className="mt-4 print:hidden">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Exportar CSV
          </h2>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button variant="secondary" onClick={handleExportPayments} loading={exporting === 'payments'}>
              <Download className="h-4 w-4" /> Pagamentos
            </Button>
            <Button variant="secondary" onClick={handleExportInvoices} loading={exporting === 'invoices'}>
              <Download className="h-4 w-4" /> Cobranças
            </Button>
            <Button variant="secondary" onClick={handleExportClients} loading={exporting === 'clients'}>
              <Download className="h-4 w-4" /> Clientes
            </Button>
            <Button variant="secondary" onClick={handleExportSummary}>
              <Download className="h-4 w-4" /> Relatório filtrado
            </Button>
          </div>
          <p className="mt-2 text-xs text-slate-400">
            Os exports de pagamentos e cobranças usam exactamente o período seleccionado acima ({from} a{' '}
            {to}). O export de clientes reflecte o estado actual de todos os clientes da organização.
          </p>
        </Card>
      </div>
    </AppShell>
  );
}

function SummaryCard({
  icon: Icon,
  label,
  value,
  accent,
}: {
  icon: typeof Wallet;
  label: string;
  value: string | null;
  accent: string;
}) {
  return (
    <Card>
      <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
        <Icon className={`h-4 w-4 ${accent}`} aria-hidden="true" />
        {label}
      </div>
      <div className="mt-2 h-7">
        {value === null ? (
          <div className="h-6 w-24 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
        ) : (
          <span className={`text-xl font-bold ${accent}`}>{value}</span>
        )}
      </div>
    </Card>
  );
}
