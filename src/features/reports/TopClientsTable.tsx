import { useNavigate } from 'react-router-dom';
import { EmptyState } from '@/components/ui/EmptyState';
import { Users } from 'lucide-react';
import type { TopClientRow } from './api';

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

export function TopClientsTable({ clients, currency }: { clients: TopClientRow[]; currency: string }) {
  const navigate = useNavigate();

  if (clients.length === 0) {
    return (
      <EmptyState
        icon={Users}
        title="Sem movimento de clientes"
        description="Não existem cobranças ou pagamentos de clientes no período seleccionado."
      />
    );
  }

  return (
    <>
      {/* Mobile: cards */}
      <div className="divide-y divide-slate-200 dark:divide-slate-800 md:hidden">
        {clients.map((client) => (
          <button
            key={client.client_id}
            onClick={() => navigate(`/clients/${client.client_id}`)}
            className="flex w-full flex-col gap-1 py-3 text-left"
          >
            <span className="font-medium text-slate-900 dark:text-white">{client.client_name}</span>
            <span className="text-sm text-slate-500 dark:text-slate-400">
              Pago: {formatCurrency(client.total_paid, currency)} · {client.invoice_count} cobrança
              {client.invoice_count === 1 ? '' : 's'}
            </span>
            {(client.total_pending > 0 || client.total_overdue > 0) && (
              <span className="text-xs text-slate-400">
                Pendente: {formatCurrency(client.total_pending, currency)} · Atrasado:{' '}
                {formatCurrency(client.total_overdue, currency)}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Desktop: tabela */}
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400">
              <th className="py-2 pr-4 font-medium">Cliente</th>
              <th className="py-2 pr-4 font-medium">Total pago</th>
              <th className="py-2 pr-4 font-medium">Pendente</th>
              <th className="py-2 pr-4 font-medium">Atrasado</th>
              <th className="py-2 pr-4 font-medium">Cobranças</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
            {clients.map((client) => (
              <tr
                key={client.client_id}
                onClick={() => navigate(`/clients/${client.client_id}`)}
                className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
              >
                <td className="py-2 pr-4 font-medium text-slate-900 dark:text-white">{client.client_name}</td>
                <td className="py-2 pr-4 text-emerald-600 dark:text-emerald-400">
                  {formatCurrency(client.total_paid, currency)}
                </td>
                <td className="py-2 pr-4 text-amber-600 dark:text-amber-400">
                  {formatCurrency(client.total_pending, currency)}
                </td>
                <td className="py-2 pr-4 text-red-600 dark:text-red-400">
                  {formatCurrency(client.total_overdue, currency)}
                </td>
                <td className="py-2 pr-4 text-slate-600 dark:text-slate-400">{client.invoice_count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
