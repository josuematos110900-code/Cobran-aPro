import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Cell, ResponsiveContainer } from 'recharts';
import { EmptyState } from '@/components/ui/EmptyState';
import { FileBarChart } from 'lucide-react';
import type { InvoiceStatusBreakdownRow } from './api';
import type { InvoiceStatus } from '@/lib/types/database';

const STATUS_LABELS: Record<InvoiceStatus, string> = {
  paid: 'Pagas',
  pending: 'Pendentes',
  overdue: 'Atrasadas',
  cancelled: 'Canceladas',
};

const STATUS_COLORS: Record<InvoiceStatus, string> = {
  paid: '#10b981',
  pending: '#f59e0b',
  overdue: '#ef4444',
  cancelled: '#94a3b8',
};

const STATUS_ORDER: InvoiceStatus[] = ['paid', 'pending', 'overdue', 'cancelled'];

interface InvoiceStatusChartProps {
  data: InvoiceStatusBreakdownRow[];
}

export function InvoiceStatusChart({ data }: InvoiceStatusChartProps) {
  const byStatus = new Map(data.map((row) => [row.status, row]));
  const chartData = STATUS_ORDER.map((status) => ({
    status,
    label: STATUS_LABELS[status],
    count: byStatus.get(status)?.invoice_count ?? 0,
  }));

  const hasAny = chartData.some((row) => row.count > 0);

  if (!hasAny) {
    return (
      <EmptyState
        icon={FileBarChart}
        title="Sem cobranças neste período"
        description="Não existem cobranças com vencimento no período seleccionado."
      />
    );
  }

  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" className="stroke-slate-200 dark:stroke-slate-800" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} allowDecimals={false} width={32} />
          <Tooltip formatter={(value: number) => [value, 'Cobranças']} />
          <Bar dataKey="count" radius={[4, 4, 0, 0]}>
            {chartData.map((row) => (
              <Cell key={row.status} fill={STATUS_COLORS[row.status]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
