import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Wallet, Clock, AlertTriangle, Users, Bell, Repeat, TrendingUp } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/contexts/AuthContext';
import { fetchReminderTotals, type ReminderTotals } from '@/features/reminders/api';
import { fetchPaymentTotals, type PaymentTotals } from '@/features/payments/api';
import { fetchPlanStatus } from '@/features/billing/api';
import type { PlanStatus } from '@/lib/types/database';
import { fetchOnboardingProgress, fetchRecentOverdueInvoices, type OnboardingProgress, type RecentOverdueInvoice } from './api';
import { TrialBanner } from './TrialBanner';
import { QuickActions } from './QuickActions';
import { OnboardingChecklist } from './OnboardingChecklist';

interface DashboardTotals {
  total_received: number;
  total_pending: number;
  total_overdue: number;
  active_clients: number;
  invoices_this_month: number;
  active_subscriptions: number;
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

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('pt-AO', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function DashboardPage() {
  const { organization } = useAuth();
  const [totals, setTotals] = useState<DashboardTotals | null>(null);
  const [reminderTotals, setReminderTotals] = useState<ReminderTotals | null>(null);
  const [paymentTotals, setPaymentTotals] = useState<PaymentTotals | null>(null);
  const [planStatus, setPlanStatus] = useState<PlanStatus | null>(null);
  const [progress, setProgress] = useState<OnboardingProgress | null>(null);
  const [overdue, setOverdue] = useState<RecentOverdueInvoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!organization) return;

    let isMounted = true;

    async function load() {
      setLoading(true);
      const { data, error: fetchError } = await supabase
        .from('dashboard_totals')
        .select('*')
        .eq('organization_id', organization!.id)
        .maybeSingle();

      if (!isMounted) return;

      if (fetchError) {
        setError('Não foi possível carregar os dados do dashboard.');
      } else {
        setTotals(
          (data as DashboardTotals) ?? {
            total_received: 0,
            total_pending: 0,
            total_overdue: 0,
            active_clients: 0,
            invoices_this_month: 0,
            active_subscriptions: 0,
          }
        );
      }

      const [{ totals: fetchedReminderTotals }, { totals: fetchedPaymentTotals }, { status: fetchedPlanStatus }, fetchedProgress, { invoices: fetchedOverdue }] =
        await Promise.all([
          fetchReminderTotals(organization!.id),
          fetchPaymentTotals(organization!.id),
          fetchPlanStatus(organization!.id),
          fetchOnboardingProgress(organization!.id),
          fetchRecentOverdueInvoices(organization!.id),
        ]);

      if (!isMounted) return;

      setReminderTotals(fetchedReminderTotals);
      setPaymentTotals(fetchedPaymentTotals);
      setPlanStatus(fetchedPlanStatus);
      setProgress(fetchedProgress);
      setOverdue(fetchedOverdue);
      setLoading(false);
    }

    load();
    return () => {
      isMounted = false;
    };
  }, [organization]);

  const currency = organization?.currency ?? 'AOA';

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Dashboard</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Quem me deve dinheiro hoje? Quanto já recebi este mês?
        </p>

        {error && (
          <div className="mt-4">
            <ErrorMessage message={error} />
          </div>
        )}

        {planStatus && <TrialBanner status={planStatus} />}

        <QuickActions />

        {progress && <OnboardingChecklist progress={progress} />}

        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <SummaryCard
            icon={Wallet}
            label="Receita recebida"
            value={loading ? null : formatCurrency(totals?.total_received ?? 0, currency)}
            accent="text-emerald-600 dark:text-emerald-400"
          />
          <SummaryCard
            icon={Clock}
            label="Pendente"
            value={loading ? null : formatCurrency(totals?.total_pending ?? 0, currency)}
            accent="text-amber-600 dark:text-amber-400"
          />
          <SummaryCard
            icon={AlertTriangle}
            label="Atrasado"
            value={loading ? null : formatCurrency(totals?.total_overdue ?? 0, currency)}
            accent="text-red-600 dark:text-red-400"
          />
          <SummaryCard
            icon={Users}
            label="Clientes activos"
            value={loading ? null : String(totals?.active_clients ?? 0)}
            accent="text-brand-600 dark:text-brand-400"
          />
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <SummaryCard
            icon={TrendingUp}
            label="Recebido este mês"
            value={loading ? null : formatCurrency(paymentTotals?.total_this_month ?? 0, currency)}
            accent="text-emerald-600 dark:text-emerald-400"
          />
          <SummaryCard
            icon={Wallet}
            label="Cobranças este mês"
            value={loading ? null : String(totals?.invoices_this_month ?? 0)}
            accent="text-brand-600 dark:text-brand-400"
          />
          <SummaryCard
            icon={Repeat}
            label="Recorrências activas"
            value={loading ? null : String(totals?.active_subscriptions ?? 0)}
            accent="text-brand-600 dark:text-brand-400"
          />
          <SummaryCard
            icon={Bell}
            label="Lembretes este mês"
            value={loading ? null : String(reminderTotals?.sent_this_month ?? 0)}
            accent="text-amber-600 dark:text-amber-400"
          />
        </div>

        <Card className="mt-6">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">
              Vencidas recentemente
            </h2>
            <Link to="/invoices" className="text-xs font-medium text-brand-600 hover:underline dark:text-brand-400">
              Ver todas
            </Link>
          </div>

          {!loading && overdue.length === 0 && (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Sem cobranças atrasadas no momento — bom trabalho!
            </p>
          )}

          {overdue.length > 0 && (
            <ul className="mt-3 divide-y divide-slate-100 dark:divide-slate-800">
              {overdue.map((invoice) => (
                <li key={invoice.id} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <div>
                    <p className="font-medium text-slate-900 dark:text-white">{invoice.client_name}</p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {invoice.invoice_number} · venceu em {formatDate(invoice.due_date)}
                    </p>
                  </div>
                  <span className="font-semibold text-red-600 dark:text-red-400">
                    {formatCurrency(invoice.amount, invoice.currency)}
                  </span>
                </li>
              ))}
            </ul>
          )}
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
