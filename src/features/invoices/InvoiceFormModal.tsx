import { useEffect, useState, type FormEvent } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { Client } from '@/lib/types/database';
import { fetchClientsForSelect } from '@/features/clients/api';
import { createInvoice, updateInvoice, type InvoiceWithClient } from './api';
import { validateInvoiceForm, type InvoiceFormErrors } from './validation';
import { CURRENCY_OPTIONS, emptyInvoiceForm, type InvoiceFormValues } from './types';

interface InvoiceFormModalProps {
  open: boolean;
  organizationId: string;
  defaultCurrency: string;
  invoice: InvoiceWithClient | null; // null = criar; preenchido = editar
  preselectedClientId?: string;
  onClose: () => void;
  onSaved: (invoice: InvoiceWithClient, mode: 'created' | 'updated') => void;
}

export function InvoiceFormModal({
  open,
  organizationId,
  defaultCurrency,
  invoice,
  preselectedClientId,
  onClose,
  onSaved,
}: InvoiceFormModalProps) {
  const [values, setValues] = useState<InvoiceFormValues>(emptyInvoiceForm(defaultCurrency));
  const [errors, setErrors] = useState<InvoiceFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [clients, setClients] = useState<Pick<Client, 'id' | 'name' | 'phone'>[]>([]);
  const [clientsLoading, setClientsLoading] = useState(false);

  useEffect(() => {
    if (!open) return;

    if (invoice) {
      setValues({
        client_id: invoice.client_id,
        description: invoice.description ?? '',
        amount: String(invoice.amount),
        currency: invoice.currency,
        due_date: invoice.due_date,
        reference: invoice.reference ?? '',
      });
    } else {
      setValues({ ...emptyInvoiceForm(defaultCurrency), client_id: preselectedClientId ?? '' });
    }
    setErrors({});
    setSubmitError(null);
  }, [open, invoice, defaultCurrency, preselectedClientId]);

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

  function setField<K extends keyof InvoiceFormValues>(field: K, value: InvoiceFormValues[K]) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);

    const validationErrors = validateInvoiceForm(values);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setLoading(true);

    if (invoice) {
      const { invoice: updated, error } = await updateInvoice(invoice.id, values);
      setLoading(false);
      if (error || !updated) {
        setSubmitError('Não foi possível guardar as alterações. Tente novamente.');
        return;
      }
      onSaved(updated, 'updated');
    } else {
      const { invoice: created, error } = await createInvoice(organizationId, values);
      setLoading(false);
      if (error || !created) {
        setSubmitError('Não foi possível criar a cobrança. Tente novamente.');
        return;
      }
      onSaved(created, 'created');
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={invoice ? `Editar cobrança ${invoice.invoice_number}` : 'Nova cobrança'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" form="invoice-form" loading={loading}>
            {invoice ? 'Guardar alterações' : 'Criar cobrança'}
          </Button>
        </>
      }
    >
      <form id="invoice-form" onSubmit={handleSubmit} className="space-y-4">
        {submitError && <ErrorMessage message={submitError} />}

        <Select
          label="Cliente"
          required
          value={values.client_id}
          onChange={(e) => setField('client_id', e.target.value)}
          error={errors.client_id}
          disabled={clientsLoading}
        >
          <option value="">
            {clientsLoading ? 'A carregar clientes…' : 'Seleccione um cliente'}
          </option>
          {clients.map((client) => (
            <option key={client.id} value={client.id}>
              {client.name}
            </option>
          ))}
        </Select>

        <Textarea
          label="Descrição"
          value={values.description}
          onChange={(e) => setField('description', e.target.value)}
          placeholder="Ex: Mensalidade de Setembro"
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label="Valor"
            inputMode="decimal"
            required
            value={values.amount}
            onChange={(e) => setField('amount', e.target.value)}
            error={errors.amount}
            placeholder="0"
          />
          <Select
            label="Moeda"
            required
            value={values.currency}
            onChange={(e) => setField('currency', e.target.value)}
            error={errors.currency}
          >
            {CURRENCY_OPTIONS.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
        </div>

        <Input
          label="Data de vencimento"
          type="date"
          required
          value={values.due_date}
          onChange={(e) => setField('due_date', e.target.value)}
          error={errors.due_date}
        />

        <Input
          label="Referência (opcional)"
          value={values.reference}
          onChange={(e) => setField('reference', e.target.value)}
          placeholder="Ex: nº de recibo, referência de depósito"
        />
      </form>
    </Modal>
  );
}
