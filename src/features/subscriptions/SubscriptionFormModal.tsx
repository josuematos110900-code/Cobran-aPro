import { useEffect, useState, type FormEvent } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { Client, Service } from '@/lib/types/database';
import { fetchClientsForSelect } from '@/features/clients/api';
import { fetchActiveServicesForSelect } from '@/features/services/api';
import { createSubscription, updateSubscription, type SubscriptionWithRelations } from './api';
import { validateSubscriptionForm, type SubscriptionFormErrors } from './validation';
import {
  BILLING_PERIOD_LABELS,
  CURRENCY_OPTIONS,
  emptySubscriptionForm,
  type SubscriptionFormValues,
} from './types';

interface SubscriptionFormModalProps {
  open: boolean;
  organizationId: string;
  defaultCurrency: string;
  subscription: SubscriptionWithRelations | null;
  preselectedClientId?: string;
  onClose: () => void;
  onSaved: (subscription: SubscriptionWithRelations, mode: 'created' | 'updated') => void;
}

export function SubscriptionFormModal({
  open,
  organizationId,
  defaultCurrency,
  subscription,
  preselectedClientId,
  onClose,
  onSaved,
}: SubscriptionFormModalProps) {
  const [values, setValues] = useState<SubscriptionFormValues>(emptySubscriptionForm(defaultCurrency));
  const [errors, setErrors] = useState<SubscriptionFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [clients, setClients] = useState<Pick<Client, 'id' | 'name' | 'phone'>[]>([]);
  const [services, setServices] = useState<Pick<Service, 'id' | 'name' | 'price' | 'currency' | 'billing_period'>[]>(
    []
  );
  const [optionsLoading, setOptionsLoading] = useState(false);

  useEffect(() => {
    if (!open) return;

    if (subscription) {
      setValues({
        client_id: subscription.client_id,
        service_id: subscription.service_id,
        amount: String(subscription.amount),
        currency: subscription.currency,
        billing_period: subscription.billing_period,
        due_day: String(subscription.due_day),
        start_date: subscription.start_date,
        end_date: subscription.end_date ?? '',
      });
    } else {
      setValues({
        ...emptySubscriptionForm(defaultCurrency),
        client_id: preselectedClientId ?? '',
      });
    }
    setErrors({});
    setSubmitError(null);
  }, [open, subscription, defaultCurrency, preselectedClientId]);

  useEffect(() => {
    if (!open) return;
    let isMounted = true;

    async function loadOptions() {
      setOptionsLoading(true);
      const [{ clients: fetchedClients }, { services: fetchedServices }] = await Promise.all([
        fetchClientsForSelect(organizationId),
        fetchActiveServicesForSelect(organizationId),
      ]);
      if (isMounted) {
        setClients(fetchedClients);
        setServices(fetchedServices);
        setOptionsLoading(false);
      }
    }

    loadOptions();
    return () => {
      isMounted = false;
    };
  }, [open, organizationId]);

  function setField<K extends keyof SubscriptionFormValues>(field: K, value: SubscriptionFormValues[K]) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  function handleServiceChange(serviceId: string) {
    const selected = services.find((s) => s.id === serviceId);
    setValues((prev) => ({
      ...prev,
      service_id: serviceId,
      // Pré-preenche valor, moeda e periodicidade a partir do serviço
      // seleccionado — o utilizador pode ainda ajustar manualmente.
      amount: selected ? String(selected.price) : prev.amount,
      currency: selected ? selected.currency : prev.currency,
      billing_period: selected ? selected.billing_period : prev.billing_period,
    }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);

    const validationErrors = validateSubscriptionForm(values);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setLoading(true);

    if (subscription) {
      const { subscription: updated, error } = await updateSubscription(subscription.id, values);
      setLoading(false);
      if (error || !updated) {
        setSubmitError('Não foi possível guardar as alterações. Tente novamente.');
        return;
      }
      onSaved(updated, 'updated');
    } else {
      const { subscription: created, error } = await createSubscription(organizationId, values);
      setLoading(false);
      if (error || !created) {
        setSubmitError('Não foi possível criar a subscrição. Tente novamente.');
        return;
      }
      onSaved(created, 'created');
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={subscription ? 'Editar cobrança recorrente' : 'Nova cobrança recorrente'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" form="subscription-form" loading={loading}>
            {subscription ? 'Guardar alterações' : 'Criar subscrição'}
          </Button>
        </>
      }
    >
      <form id="subscription-form" onSubmit={handleSubmit} className="space-y-4">
        {submitError && <ErrorMessage message={submitError} />}

        <Select
          label="Cliente"
          required
          value={values.client_id}
          onChange={(e) => setField('client_id', e.target.value)}
          error={errors.client_id}
          disabled={optionsLoading}
        >
          <option value="">{optionsLoading ? 'A carregar…' : 'Seleccione um cliente'}</option>
          {clients.map((client) => (
            <option key={client.id} value={client.id}>
              {client.name}
            </option>
          ))}
        </Select>

        <Select
          label="Serviço"
          required
          value={values.service_id}
          onChange={(e) => handleServiceChange(e.target.value)}
          error={errors.service_id}
          disabled={optionsLoading}
        >
          <option value="">{optionsLoading ? 'A carregar…' : 'Seleccione um serviço'}</option>
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </Select>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label="Valor"
            inputMode="decimal"
            required
            value={values.amount}
            onChange={(e) => setField('amount', e.target.value)}
            error={errors.amount}
          />
          <Select
            label="Moeda"
            required
            value={values.currency}
            onChange={(e) => setField('currency', e.target.value)}
          >
            {CURRENCY_OPTIONS.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Select
            label="Periodicidade"
            value={values.billing_period}
            onChange={(e) =>
              setField('billing_period', e.target.value as SubscriptionFormValues['billing_period'])
            }
          >
            {Object.entries(BILLING_PERIOD_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          <Input
            label={values.billing_period === 'weekly' ? 'Dia da semana (1=Seg…7=Dom)' : 'Dia de vencimento'}
            type="number"
            min={1}
            max={31}
            required
            value={values.due_day}
            onChange={(e) => setField('due_day', e.target.value)}
            error={errors.due_day}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label="Data inicial"
            type="date"
            required
            value={values.start_date}
            onChange={(e) => setField('start_date', e.target.value)}
            error={errors.start_date}
          />
          <Input
            label="Data final (opcional)"
            type="date"
            value={values.end_date}
            onChange={(e) => setField('end_date', e.target.value)}
            error={errors.end_date}
          />
        </div>
      </form>
    </Modal>
  );
}
