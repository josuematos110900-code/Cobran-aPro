import { EmptyState } from '@/components/ui/EmptyState';
import { Package } from 'lucide-react';
import type { ServiceBreakdownRow } from './api';

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

export function ServiceBreakdownTable({ services, currency }: { services: ServiceBreakdownRow[]; currency: string }) {
  return (
    <div>
      <p className="mb-3 text-xs text-slate-400">
        Só inclui receita de cobranças geradas por subscrições. Cobranças criadas manualmente não têm
        nenhum serviço associado na base de dados, por isso não entram nesta soma.
      </p>

      {services.length === 0 ? (
        <EmptyState
          icon={Package}
          title="Sem dados de serviços"
          description="Ainda não existem subscrições ou receita ligada a serviços neste período."
        />
      ) : (
        <>
          {/* Mobile: cards */}
          <div className="divide-y divide-slate-200 dark:divide-slate-800 md:hidden">
            {services.map((service) => (
              <div key={service.service_id} className="flex flex-col gap-1 py-3">
                <span className="font-medium text-slate-900 dark:text-white">{service.service_name}</span>
                <span className="text-sm text-slate-500 dark:text-slate-400">
                  Receita no período: {formatCurrency(service.revenue_in_period, currency)}
                </span>
                <span className="text-xs text-slate-400">
                  {service.subscriptions_active} activas de {service.subscriptions_total} · MRR estimado:{' '}
                  {formatCurrency(service.estimated_mrr, currency)}
                </span>
              </div>
            ))}
          </div>

          {/* Desktop: tabela */}
          <div className="hidden overflow-x-auto md:block">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400">
                  <th className="py-2 pr-4 font-medium">Serviço</th>
                  <th className="py-2 pr-4 font-medium">Receita no período</th>
                  <th className="py-2 pr-4 font-medium">Subscrições activas</th>
                  <th className="py-2 pr-4 font-medium">MRR estimado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {services.map((service) => (
                  <tr key={service.service_id}>
                    <td className="py-2 pr-4 font-medium text-slate-900 dark:text-white">
                      {service.service_name}
                    </td>
                    <td className="py-2 pr-4 text-emerald-600 dark:text-emerald-400">
                      {formatCurrency(service.revenue_in_period, currency)}
                    </td>
                    <td className="py-2 pr-4 text-slate-600 dark:text-slate-400">
                      {service.subscriptions_active} / {service.subscriptions_total}
                    </td>
                    <td className="py-2 pr-4 text-slate-600 dark:text-slate-400">
                      {formatCurrency(service.estimated_mrr, currency)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
