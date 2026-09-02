import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, MessageCircle } from 'lucide-react';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/contexts/AuthContext';
import { SendWhatsAppModal } from '@/features/reminders/SendWhatsAppModal';
import type { OverdueTopRow } from './api';

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

export function OverdueTopList({ overdue }: { overdue: OverdueTopRow[] }) {
  const navigate = useNavigate();
  const { organization } = useAuth();
  const [whatsAppTarget, setWhatsAppTarget] = useState<OverdueTopRow | null>(null);

  if (overdue.length === 0) {
    return (
      <EmptyState
        icon={AlertTriangle}
        title="Sem cobranças em atraso"
        description="Não há nenhuma cobrança atrasada neste momento."
      />
    );
  }

  return (
    <>
      <div className="divide-y divide-slate-200 dark:divide-slate-800">
        {overdue.map((row) => (
          <div key={row.invoice_id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
            <button onClick={() => navigate(`/invoices/${row.invoice_id}`)} className="text-left">
              <div className="font-medium text-slate-900 dark:text-white">{row.client_name}</div>
              <div className="text-sm text-slate-500 dark:text-slate-400">
                {row.invoice_number} · {formatCurrency(row.amount, row.currency)} · vence em{' '}
                {formatDate(row.due_date)}
              </div>
              <div className="text-xs font-medium text-red-600 dark:text-red-400">
                {row.days_overdue} dia{row.days_overdue === 1 ? '' : 's'} em atraso
              </div>
            </button>
            {row.client_phone && (
              <Button variant="secondary" onClick={() => setWhatsAppTarget(row)}>
                <MessageCircle className="h-4 w-4" /> WhatsApp
              </Button>
            )}
          </div>
        ))}
      </div>

      {organization && whatsAppTarget && (
        <SendWhatsAppModal
          open={Boolean(whatsAppTarget)}
          onClose={() => setWhatsAppTarget(null)}
          organizationId={organization.id}
          client={{
            id: whatsAppTarget.client_id,
            name: whatsAppTarget.client_name,
            phone: whatsAppTarget.client_phone,
          }}
          mode={{
            kind: 'invoice',
            invoice: {
              id: whatsAppTarget.invoice_id,
              status: 'overdue',
              invoiceNumber: whatsAppTarget.invoice_number,
              amount: whatsAppTarget.amount,
              currency: whatsAppTarget.currency,
              dueDate: whatsAppTarget.due_date,
              description: null,
            },
          }}
          onSent={() => setWhatsAppTarget(null)}
        />
      )}
    </>
  );
}
