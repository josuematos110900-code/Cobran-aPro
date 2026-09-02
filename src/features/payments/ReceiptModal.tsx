import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Printer } from 'lucide-react';
import type { PaymentMethod } from '@/lib/types/database';
import { PAYMENT_METHOD_LABELS } from './types';

interface ReceiptModalProps {
  open: boolean;
  onClose: () => void;
  organizationName: string;
  clientName: string;
  invoiceNumber: string;
  description: string | null;
  amount: number;
  currency: string;
  paymentMethod: PaymentMethod;
  transactionReference: string | null;
  paidAt: string;
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

// Recibo simples, só com dados reais (invoice + payment + cliente +
// organização) — nenhum valor inventado. A "impressão"/exportação para
// PDF usa a funcionalidade nativa do browser (window.print), em vez de
// gerar um ficheiro PDF fictício. Preparado para, no futuro, evoluir
// para um PDF gerado no servidor com identidade visual da organização.
export function ReceiptModal({
  open,
  onClose,
  organizationName,
  clientName,
  invoiceNumber,
  description,
  amount,
  currency,
  paymentMethod,
  transactionReference,
  paidAt,
}: ReceiptModalProps) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Recibo de pagamento"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Fechar
          </Button>
          <Button onClick={() => window.print()}>
            <Printer className="h-4 w-4" /> Imprimir / Guardar PDF
          </Button>
        </>
      }
    >
      <div className="space-y-4 text-sm">
        <div className="text-center">
          <p className="text-base font-bold text-slate-900 dark:text-white">{organizationName}</p>
          <p className="text-slate-500 dark:text-slate-400">Recibo de pagamento</p>
        </div>

        <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
          <div className="flex justify-between py-1">
            <span className="text-slate-500 dark:text-slate-400">Cliente</span>
            <span className="font-medium text-slate-900 dark:text-white">{clientName}</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-slate-500 dark:text-slate-400">Cobrança</span>
            <span className="font-medium text-slate-900 dark:text-white">{invoiceNumber}</span>
          </div>
          {description && (
            <div className="flex justify-between py-1">
              <span className="text-slate-500 dark:text-slate-400">Descrição</span>
              <span className="font-medium text-slate-900 dark:text-white">{description}</span>
            </div>
          )}
          <div className="flex justify-between py-1">
            <span className="text-slate-500 dark:text-slate-400">Valor pago</span>
            <span className="font-bold text-emerald-600 dark:text-emerald-400">
              {formatCurrency(amount, currency)}
            </span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-slate-500 dark:text-slate-400">Método</span>
            <span className="font-medium text-slate-900 dark:text-white">
              {PAYMENT_METHOD_LABELS[paymentMethod]}
            </span>
          </div>
          {transactionReference && (
            <div className="flex justify-between py-1">
              <span className="text-slate-500 dark:text-slate-400">Referência</span>
              <span className="font-medium text-slate-900 dark:text-white">{transactionReference}</span>
            </div>
          )}
          <div className="flex justify-between py-1">
            <span className="text-slate-500 dark:text-slate-400">Data do pagamento</span>
            <span className="font-medium text-slate-900 dark:text-white">{formatDateTime(paidAt)}</span>
          </div>
        </div>

        <p className="text-center text-xs text-slate-400">Documento gerado pelo CobrançaPro</p>
      </div>
    </Modal>
  );
}
