import { useEffect, useState, type FormEvent } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { Client, Invoice } from '@/lib/types/database';
import { fetchClientsForSelect } from '@/features/clients/api';
import { fetchPayableInvoices, registerPayment } from './api';
import { emptyRegisterPaymentForm, PAYABLE_METHOD_OPTIONS, PAYMENT_METHOD_LABELS } from './types';
import type { RegisterPaymentFormValues } from './types';

interface RegisterPaymentModalProps {
  open: boolean;
  organizationId: string;
  preselectedClientId?: string;
  onClose: () => void;
  onRegistered: () => void;
}

type PayableInvoice = Invoice & { clients: { name: string } | null };

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

export function RegisterPaymentModal({
  open,
  organizationId,
  preselectedClientId,
  onClose,
  onRegistered,
}: RegisterPaymentModalProps) {
  const [values, setValues] = useState<RegisterPaymentFormValues>(emptyRegisterPaymentForm(preselectedClientId));
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<{ client_id?: string; invoice_id?: string; paid_at?: string }>({});
  const [loading, setLoading] = useState(false);

  const [clients, setClients] = useState<Pick<Client, 'id' | 'name' | 'phone'>[]>([]);
  const [invoices, setInvoices] = useState<PayableInvoice[]>([]);
  const [clientsLoading, setClientsLoading] = useState(false);
  const [invoicesLoading, setInvoicesLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    setValues(emptyRegisterPaymentForm(preselectedClientId));
    setError(null);
    setFieldErrors({});
  }, [open, preselectedClientId]);

  useEffect(() => {
    if (!open) return;
    let isMounted = true;

    async function loadClients() {
      setClientsLoading(true);
      const { clients: fetched } = await fetchClientsForSelect(organizationId);
      if (isMounted) {
        setClients(fetched);
        setClientsLoading(false);
      }
    }

    loadClients();
    return () => {
      isMounted = false;
    };
  }, [open, organizationId]);

  useEffect(() => {
    if (!open || !values.client_id) {
      setInvoices([]);
      return;
    }
    let isMounted = true;

    async function loadInvoices() {
      setInvoicesLoading(true);
      const { invoices: fetched } = await fetchPayableInvoices(organizationId, values.client_id);
      if (isMounted) {
        setInvoices(fetched);
        setInvoicesLoading(false);
      }
    }

    loadInvoices();
    return () => {
      isMounted = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, organizationId, values.client_id]);

  const selectedInvoice = invoices.find((inv) => inv.id === values.invoice_id) ?? null;

  function setField<K extends keyof RegisterPaymentFormValues>(field: K, value: RegisterPaymentFormValues[K]) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  function handleClientChange(clientId: string) {
    setValues((prev) => ({ ...prev, client_id: clientId, invoice_id: '' }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    const errors: typeof fieldErrors = {};
    if (!values.client_id) errors.client_id = 'Seleccione um cliente.';
    if (!values.invoice_id) errors.invoice_id = 'Seleccione a cobrança a pagar.';
    if (!values.paid_at) errors.paid_at = 'Indique a data e hora do pagamento.';
    if (values.paid_at && new Date(values.paid_at) > new Date()) {
      errors.paid_at = 'A data do pagamento não pode ser no futuro.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setLoading(true);
    const { error: registerError } = await registerPayment({
      invoiceId: values.invoice_id,
      paymentMethod: values.payment_method,
      transactionReference: values.transaction_reference.trim() || null,
      paidAt: new Date(values.paid_at).toISOString(),
      note: values.note.trim() || null,
    });
    setLoading(false);

    if (registerError) {
      setError(registerError.message || 'Não foi possível registar o pagamento.');
      return;
    }

    onRegistered();
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Registar pagamento"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" form="register-payment-form" loading={loading}>
            Registar pagamento
          </Button>
        </>
      }
    >
      <form id="register-payment-form" onSubmit={handleSubmit} className="space-y-4">
        {error && <ErrorMessage message={error} />}

        <Select
          label="Cliente"
          required
          value={values.client_id}
          onChange={(e) => handleClientChange(e.target.value)}
          error={fieldErrors.client_id}
          disabled={clientsLoading}
        >
          <option value="">{clientsLoading ? 'A carregar…' : 'Seleccione um cliente'}</option>
          {clients.map((client) => (
            <option key={client.id} value={client.id}>
              {client.name}
            </option>
          ))}
        </Select>

        <Select
          label="Cobrança"
          required
          value={values.invoice_id}
          onChange={(e) => setField('invoice_id', e.target.value)}
          error={fieldErrors.invoice_id}
          disabled={!values.client_id || invoicesLoading}
        >
          <option value="">
            {!values.client_id
              ? 'Seleccione primeiro um cliente'
              : invoicesLoading
                ? 'A carregar…'
                : invoices.length === 0
                  ? 'Este cliente não tem cobranças por pagar'
                  : 'Seleccione a cobrança'}
          </option>
          {invoices.map((invoice) => (
            <option key={invoice.id} value={invoice.id}>
              {invoice.invoice_number} — {invoice.description || 'sem descrição'} —{' '}
              {formatCurrency(invoice.amount, invoice.currency)}
            </option>
          ))}
        </Select>

        {selectedInvoice && (
          <div className="rounded-lg bg-slate-50 p-3 text-sm dark:bg-slate-800/50">
            <p className="text-slate-500 dark:text-slate-400">
              Esta é a única forma de valor aceite nesta versão — pagamento integral da cobrança. O
              valor não é editável para garantir que não é registado um montante diferente do devido.
            </p>
            <p className="mt-2 text-lg font-bold text-emerald-600 dark:text-emerald-400">
              {formatCurrency(selectedInvoice.amount, selectedInvoice.currency)}
            </p>
          </div>
        )}

        <Select
          label="Método de pagamento"
          value={values.payment_method}
          onChange={(e) => setField('payment_method', e.target.value as RegisterPaymentFormValues['payment_method'])}
        >
          {PAYABLE_METHOD_OPTIONS.map((value) => (
            <option key={value} value={value}>
              {PAYMENT_METHOD_LABELS[value]}
            </option>
          ))}
        </Select>

        <Input
          label="Referência da transacção (opcional)"
          value={values.transaction_reference}
          onChange={(e) => setField('transaction_reference', e.target.value)}
          placeholder="Ex: nº de comprovativo"
        />

        <Input
          label="Data e hora do pagamento"
          type="datetime-local"
          required
          value={values.paid_at}
          onChange={(e) => setField('paid_at', e.target.value)}
          error={fieldErrors.paid_at}
          max={emptyRegisterPaymentForm().paid_at}
        />

        <Textarea
          label="Observação (opcional)"
          value={values.note}
          onChange={(e) => setField('note', e.target.value)}
        />
      </form>
    </Modal>
  );
}
