import { useEffect, useState, type FormEvent } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { Client } from '@/lib/types/database';
import { createClient, updateClient } from './api';
import { validateClientForm, type ClientFormErrors } from './validation';
import { CLIENT_STATUS_LABELS, EMPTY_CLIENT_FORM, type ClientFormValues } from './types';

interface ClientFormModalProps {
  open: boolean;
  organizationId: string;
  client: Client | null; // null = criar novo; preenchido = editar
  onClose: () => void;
  onSaved: (client: Client, mode: 'created' | 'updated') => void;
}

export function ClientFormModal({
  open,
  organizationId,
  client,
  onClose,
  onSaved,
}: ClientFormModalProps) {
  const [values, setValues] = useState<ClientFormValues>(EMPTY_CLIENT_FORM);
  const [errors, setErrors] = useState<ClientFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;

    if (client) {
      setValues({
        name: client.name,
        phone: client.phone ?? '',
        email: client.email ?? '',
        address: client.address ?? '',
        notes: client.notes ?? '',
        status: client.status,
      });
    } else {
      setValues(EMPTY_CLIENT_FORM);
    }
    setErrors({});
    setSubmitError(null);
  }, [open, client]);

  function setField<K extends keyof ClientFormValues>(field: K, value: ClientFormValues[K]) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);

    const validationErrors = validateClientForm(values);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setLoading(true);

    if (client) {
      const { client: updated, error } = await updateClient(client.id, values);
      setLoading(false);
      if (error || !updated) {
        setSubmitError('Não foi possível guardar as alterações. Tente novamente.');
        return;
      }
      onSaved(updated, 'updated');
    } else {
      const { client: created, error } = await createClient(organizationId, values);
      setLoading(false);
      if (error || !created) {
        setSubmitError('Não foi possível criar o cliente. Tente novamente.');
        return;
      }
      onSaved(created, 'created');
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={client ? 'Editar cliente' : 'Novo cliente'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" form="client-form" loading={loading}>
            {client ? 'Guardar alterações' : 'Criar cliente'}
          </Button>
        </>
      }
    >
      <form id="client-form" onSubmit={handleSubmit} className="space-y-4">
        {submitError && <ErrorMessage message={submitError} />}

        <Input
          label="Nome completo"
          required
          value={values.name}
          onChange={(e) => setField('name', e.target.value)}
          error={errors.name}
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label="Telefone"
            type="tel"
            value={values.phone}
            onChange={(e) => setField('phone', e.target.value)}
            error={errors.phone}
            placeholder="+244 9XX XXX XXX"
          />
          <Input
            label="Email"
            type="email"
            value={values.email}
            onChange={(e) => setField('email', e.target.value)}
            error={errors.email}
          />
        </div>
        <Input
          label="Endereço"
          value={values.address}
          onChange={(e) => setField('address', e.target.value)}
        />
        <Textarea
          label="Observações"
          value={values.notes}
          onChange={(e) => setField('notes', e.target.value)}
        />
        <Select
          label="Estado"
          value={values.status}
          onChange={(e) => setField('status', e.target.value as ClientFormValues['status'])}
        >
          {Object.entries(CLIENT_STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </form>
    </Modal>
  );
}
