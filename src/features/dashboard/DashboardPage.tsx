import { useEffect, useState } from 'react';
import { Wallet, Clock, AlertTriangle, Users, Bell } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/contexts/AuthContext';
import { fetchReminderTotals, type ReminderTotals } from '@/features/reminders/api';

interface DashboardTotals {
  total_received: number;
  total_pending: number;
  total_overdue: number;
  active_clients: number;
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

export function DashboardPage() {
  const { organization } = useAuth();
  const [totals, setTotals] = useState<DashboardTotals | null>(null);
  const [reminderTotals, setReminderTotals] = useState<ReminderTotals | null>(null);
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
          }
        );
      }

      const { totals: fetchedReminderTotals } = await fetchReminderTotals(organization!.id);
      if (isMounted) setReminderTotals(fetchedReminderTotals);

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

        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <SummaryCard
            icon={Bell}
            label="Lembretes enviados"
            value={loading ? null : String(reminderTotals?.total_sent ?? 0)}
            accent="text-brand-600 dark:text-brand-400"
          />
          <SummaryCard
            icon={Bell}
            label="Lembretes hoje"
            value={loading ? null : String(reminderTotals?.sent_today ?? 0)}
            accent="text-amber-600 dark:text-amber-400"
          />
          <SummaryCard
            icon={Bell}
            label="Lembretes este mês"
            value={loading ? null : String(reminderTotals?.sent_this_month ?? 0)}
            accent="text-emerald-600 dark:text-emerald-400"
          />
        </div>

        <Card className="mt-6">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">
            Cobranças de hoje
          </h2>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
            A lista de cobranças a vencer hoje aparecerá aqui assim que a
            secção de Cobranças estiver ligada aos dados reais.
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
