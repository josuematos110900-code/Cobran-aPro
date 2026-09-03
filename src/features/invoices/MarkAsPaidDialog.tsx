import { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { Input } from '@/components/ui/Input';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { PaymentMethod } from '@/lib/types/database';
import { markInvoicePaid } from './api';
import { PAYABLE_METHOD_OPTIONS, PAYMENT_METHOD_LABELS } from './types';

interface MarkAsPaidDialogProps {
  open: boolean;
  invoiceId: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function MarkAsPaidDialog({ open, invoiceId, onClose, onSuccess }: MarkAsPaidDialogProps) {
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('cash');
  const [reference, setReference] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    setLoading(true);
    setError(null);

    const { error: payError } = await markInvoicePaid(invoiceId, paymentMethod, reference.trim() || null);

    setLoading(false);

    if (payError) {
      // A função no servidor devolve mensagens específicas (ex: "já foi
      // paga") — mostramo-las directamente, pois já estão em português e
      // são seguras para o utilizador final ver.
      setError(payError.message || 'Não foi possível registar o pagamento.');
      return;
    }

    setPaymentMethod('cash');
    setReference('');
    onSuccess();
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Marcar como pago"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button onClick={handleConfirm} loading={loading}>
            Confirmar pagamento
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {error && <ErrorMessage message={error} />}

        <Select
          label="Método de pagamento"
          value={paymentMethod}
          onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
        >
          {PAYABLE_METHOD_OPTIONS.map((value) => (
            <option key={value} value={value}>
              {PAYMENT_METHOD_LABELS[value]}
            </option>
          ))}
        </Select>

        <Input
          label="Referência da transacção (opcional)"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          placeholder="Ex: nº de comprovativo"
        />
      </div>
    </Modal>
  );
}
