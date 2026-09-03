import { useEffect, useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Textarea';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { MessageCircle, Copy, Check } from 'lucide-react';
import { buildWhatsAppLink } from '@/lib/whatsapp';
import { recordReminder } from './api';
import {
  buildDefaultMessage,
  REMINDER_TYPE_LABELS,
  type InvoiceMessageData,
  type PaymentMessageData,
  type ReminderType,
} from './types';

interface SendWhatsAppModalClient {
  id: string;
  name: string;
  phone: string | null;
}

interface SendWhatsAppModalInvoice extends InvoiceMessageData {
  id: string;
  status: 'pending' | 'paid' | 'overdue' | 'cancelled';
}

type SendWhatsAppModalMode =
  | { kind: 'invoice'; invoice: SendWhatsAppModalInvoice }
  | { kind: 'payment'; invoice: { id: string; invoice_number: string }; payment: PaymentMessageData }
  | { kind: 'manual' };

interface SendWhatsAppModalProps {
  open: boolean;
  onClose: () => void;
  organizationId: string;
  client: SendWhatsAppModalClient;
  mode: SendWhatsAppModalMode;
  onSent?: () => void;
}

function defaultTypeForInvoice(status: string): ReminderType {
  return status === 'overdue' ? 'invoice_overdue' : 'invoice_due';
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
  return new Date(iso).toLocaleDateString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

const INVOICE_TYPE_OPTIONS: ReminderType[] = ['invoice_created', 'invoice_due', 'invoice_overdue'];

export function SendWhatsAppModal({ open, onClose, organizationId, client, mode, onSent }: SendWhatsAppModalProps) {
  const [reminderType, setReminderType] = useState<ReminderType>('manual');
  const [message, setMessage] = useState('');
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!open) return;

    let type: ReminderType = 'manual';
    let defaultMessage: string;

    if (mode.kind === 'invoice') {
      type = defaultTypeForInvoice(mode.invoice.status);
      defaultMessage = buildDefaultMessage(type, { clientName: client.name, invoice: mode.invoice });
    } else if (mode.kind === 'payment') {
      type = 'payment_confirmation';
      defaultMessage = buildDefaultMessage(type, { clientName: client.name, payment: mode.payment });
    } else {
      type = 'manual';
      defaultMessage = buildDefaultMessage(type, { clientName: client.name });
    }

    setReminderType(type);
    setMessage(defaultMessage);
    setPhoneError(null);
    setSaveError(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function handleTypeChange(newType: ReminderType) {
    setReminderType(newType);
    if (mode.kind === 'invoice') {
      setMessage(buildDefaultMessage(newType, { clientName: client.name, invoice: mode.invoice }));
    }
  }

  async function handleOpenWhatsApp() {
    setSaveError(null);
    setPhoneError(null);

    const linkResult = buildWhatsAppLink(client.phone, message);
    if (!linkResult.ok) {
      setPhoneError(linkResult.reason);
      return;
    }

    setLoading(true);

    const invoiceId = mode.kind === 'invoice' ? mode.invoice.id : mode.kind === 'payment' ? mode.invoice.id : null;

    const { error } = await recordReminder({
      organizationId,
      clientId: client.id,
      invoiceId,
      reminderType,
      message,
    });

    setLoading(false);

    if (error) {
      setSaveError('Não foi possível registar o lembrete, mas pode continuar a abrir o WhatsApp.');
    }

    // Abrimos o WhatsApp mesmo que o registo tenha falhado — a
    // comunicação com o cliente não deve ficar bloqueada por um problema
    // no nosso histórico interno.
    window.open(linkResult.url, '_blank');
    onSent?.();
  }

  async function handleCopyMessage() {
    try {
      await navigator.clipboard.writeText(message);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setSaveError('Não foi possível copiar a mensagem. Copie manualmente do campo acima.');
    }
  }

  const title =
    mode.kind === 'invoice'
      ? 'Enviar lembrete'
      : mode.kind === 'payment'
        ? 'Enviar confirmação de pagamento'
        : 'Enviar mensagem por WhatsApp';

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button onClick={handleOpenWhatsApp} loading={loading}>
            <MessageCircle className="h-4 w-4" /> Abrir WhatsApp
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {saveError && <ErrorMessage message={saveError} />}
        {phoneError && <ErrorMessage message={phoneError} />}

        <div className="rounded-lg bg-slate-50 p-3 text-sm dark:bg-slate-800/50">
          <div className="flex justify-between py-0.5">
            <span className="text-slate-500 dark:text-slate-400">Cliente</span>
            <span className="font-medium text-slate-900 dark:text-white">{client.name}</span>
          </div>
          {mode.kind === 'invoice' && (
            <>
              <div className="flex justify-between py-0.5">
                <span className="text-slate-500 dark:text-slate-400">Valor</span>
                <span className="font-medium text-slate-900 dark:text-white">
                  {formatCurrency(mode.invoice.amount, mode.invoice.currency)}
                </span>
              </div>
              <div className="flex justify-between py-0.5">
                <span className="text-slate-500 dark:text-slate-400">Vencimento</span>
                <span className="font-medium text-slate-900 dark:text-white">
                  {formatDate(mode.invoice.dueDate)}
                </span>
              </div>
              <div className="flex justify-between py-0.5">
                <span className="text-slate-500 dark:text-slate-400">Número da cobrança</span>
                <span className="font-medium text-slate-900 dark:text-white">{mode.invoice.invoiceNumber}</span>
              </div>
            </>
          )}
          {mode.kind === 'payment' && (
            <>
              <div className="flex justify-between py-0.5">
                <span className="text-slate-500 dark:text-slate-400">Valor</span>
                <span className="font-medium text-slate-900 dark:text-white">
                  {formatCurrency(mode.payment.amount, mode.payment.currency)}
                </span>
              </div>
              <div className="flex justify-between py-0.5">
                <span className="text-slate-500 dark:text-slate-400">Cobrança</span>
                <span className="font-medium text-slate-900 dark:text-white">{mode.invoice.invoice_number}</span>
              </div>
            </>
          )}
        </div>

        {mode.kind === 'invoice' && (
          <Select
            label="Tipo de mensagem"
            value={reminderType}
            onChange={(e) => handleTypeChange(e.target.value as ReminderType)}
          >
            {INVOICE_TYPE_OPTIONS.map((value) => (
              <option key={value} value={value}>
                {REMINDER_TYPE_LABELS[value]}
              </option>
            ))}
          </Select>
        )}

        <Textarea
          label="Mensagem (pode editar antes de enviar)"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          rows={7}
        />

        <Button type="button" variant="secondary" onClick={handleCopyMessage} className="w-full">
          {copied ? (
            <>
              <Check className="h-4 w-4 text-emerald-500" aria-hidden="true" /> Copiado
            </>
          ) : (
            <>
              <Copy className="h-4 w-4" aria-hidden="true" /> Copiar mensagem
            </>
          )}
        </Button>

        <p className="text-xs text-slate-400">
          Ao abrir o WhatsApp, guardamos apenas o registo de que esta mensagem foi preparada e o envio
          iniciado — nunca confirmamos entrega ou leitura.
        </p>
      </div>
    </Modal>
  );
}
