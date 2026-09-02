import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { EmptyState } from '@/components/ui/EmptyState';
import { TrendingUp } from 'lucide-react';
import type { RevenuePoint } from './api';
import type { ReportGranularity } from './types';

interface RevenueChartProps {
  data: RevenuePoint[];
  currency: string;
  granularity: ReportGranularity;
}

function formatBucketLabel(iso: string, granularity: ReportGranularity) {
  const date = new Date(`${iso}T00:00:00`);
  if (granularity === 'month') {
    return date.toLocaleDateString('pt-AO', { month: 'short', year: '2-digit' });
  }
  return date.toLocaleDateString('pt-AO', { day: '2-digit', month: '2-digit' });
}

function formatCurrencyShort(value: number, currency: string) {
  try {
    return new Intl.NumberFormat('pt-AO', {
      style: 'currency',
      currency,
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(value);
  } catch {
    return String(value);
  }
}

export function RevenueChart({ data, currency, granularity }: RevenueChartProps) {
  const hasAnyRevenue = data.some((point) => point.total > 0);

  if (!hasAnyRevenue) {
    return (
      <EmptyState
        icon={TrendingUp}
        title="Sem receita neste período"
        description="Ainda não existem pagamentos registados para o período seleccionado."
      />
    );
  }

  const chartData = data.map((point) => ({
    label: formatBucketLabel(point.bucket, granularity),
    total: point.total,
  }));

  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" className="stroke-slate-200 dark:stroke-slate-800" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} interval="preserveStartEnd" />
          <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => formatCurrencyShort(v, currency)} width={56} />
          <Tooltip
            formatter={(value: number) => [formatCurrencyShort(value, currency), 'Receita']}
            labelFormatter={(label) => label}
          />
          <Line type="monotone" dataKey="total" stroke="#1f4fed" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
