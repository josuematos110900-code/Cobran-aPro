import { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Receipt, MessageCircle } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { PAYMENT_METHOD_LABELS } from './types';
import { ReceiptModal } from './ReceiptModal';
import type { PaymentWithRelations } from './api';
import { SendWhatsAppModal } from '@/features/reminders/SendWhatsAppModal';

interface PaymentDetailModalProps {
  open: boolean;
  payment: PaymentWithRelations | null;
  onClose: () => void;
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

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function PaymentDetailModal({ open, payment, onClose }: PaymentDetailModalProps) {
  const { organization } = useAuth();
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [whatsAppOpen, setWhatsAppOpen] = useState(false);

  if (!payment) return null;

  const clientName = payment.clients?.name ?? 'Cliente removido';
  const invoiceNumber = payment.invoices?.invoice_number ?? '—';
  const description = payment.invoices?.description ?? null;
  const clientPhone = payment.clients?.phone ?? null;

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        title="Detalhe do pagamento"
        footer={
          <>
            <Button variant="secondary" onClick={() => setReceiptOpen(true)}>
              <Receipt className="h-4 w-4" /> Ver recibo
            </Button>
            {clientPhone && (
              <Button onClick={() => setWhatsAppOpen(true)}>
                <MessageCircle className="h-4 w-4" /> Enviar confirmação
              </Button>
            )}
          </>
        }
      >
        <div className="space-y-3 text-sm">
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">Cliente</span>
            <span className="font-medium text-slate-900 dark:text-white">{clientName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">Cobrança</span>
            <span className="font-medium text-slate-900 dark:text-white">{invoiceNumber}</span>
          </div>
          {description && (
            <div className="flex justify-between">
              <span className="text-slate-500 dark:text-slate-400">Descrição</span>
              <span className="font-medium text-slate-900 dark:text-white">{description}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">Valor</span>
            <span className="font-bold text-emerald-600 dark:text-emerald-400">
              {formatCurrency(payment.amount, payment.currency)}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">Método</span>
            <span className="font-medium text-slate-900 dark:text-white">
              {PAYMENT_METHOD_LABELS[payment.payment_method]}
            </span>
          </div>
          {payment.transaction_reference && (
            <div className="flex justify-between">
              <span className="text-slate-500 dark:text-slate-400">Referência</span>
              <span className="font-medium text-slate-900 dark:text-white">
                {payment.transaction_reference}
              </span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">Data do pagamento</span>
            <span className="font-medium text-slate-900 dark:text-white">
              {formatDateTime(payment.paid_at)}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">Estado</span>
            <Badge color={payment.status === 'completed' ? 'green' : payment.status === 'refunded' ? 'amber' : 'red'}>
              {payment.status === 'completed' ? 'Concluído' : payment.status === 'refunded' ? 'Estornado' : 'Falhou'}
            </Badge>
          </div>
          {payment.note && (
            <div className="rounded-lg bg-slate-50 p-3 text-slate-600 dark:bg-slate-800/50 dark:text-slate-400">
              {payment.note}
            </div>
          )}
        </div>
      </Modal>

      <ReceiptModal
        open={receiptOpen}
        onClose={() => setReceiptOpen(false)}
        organizationName={organization?.name ?? ''}
        clientName={clientName}
        invoiceNumber={invoiceNumber}
        description={description}
        amount={payment.amount}
        currency={payment.currency}
        paymentMethod={payment.payment_method}
        transactionReference={payment.transaction_reference}
        paidAt={payment.paid_at}
      />

      {organization && clientPhone && (
        <SendWhatsAppModal
          open={whatsAppOpen}
          onClose={() => setWhatsAppOpen(false)}
          organizationId={organization.id}
          client={{ id: payment.client_id, name: clientName, phone: clientPhone }}
          mode={{
            kind: 'payment',
            invoice: { id: payment.invoice_id, invoice_number: invoiceNumber },
            payment: {
              amount: payment.amount,
              currency: payment.currency,
              paidAt: payment.paid_at,
              transactionReference: payment.transaction_reference,
              description,
            },
          }}
          onSent={() => setWhatsAppOpen(false)}
        />
      )}
    </>
  );
}
