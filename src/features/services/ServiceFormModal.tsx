import { useEffect, useState, type FormEvent } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { Service } from '@/lib/types/database';
import { createService, updateService } from './api';
import { validateServiceForm, type ServiceFormErrors } from './validation';
import {
  BILLING_PERIOD_LABELS,
  CURRENCY_OPTIONS,
  emptyServiceForm,
  SERVICE_STATUS_LABELS,
  type ServiceFormValues,
} from './types';

interface ServiceFormModalProps {
  open: boolean;
  organizationId: string;
  defaultCurrency: string;
  service: Service | null; // null = criar; preenchido = editar
  onClose: () => void;
  onSaved: (service: Service, mode: 'created' | 'updated') => void;
}

export function ServiceFormModal({
  open,
  organizationId,
  defaultCurrency,
  service,
  onClose,
  onSaved,
}: ServiceFormModalProps) {
  const [values, setValues] = useState<ServiceFormValues>(emptyServiceForm(defaultCurrency));
  const [errors, setErrors] = useState<ServiceFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;

    if (service) {
      setValues({
        name: service.name,
        description: service.description ?? '',
        price: String(service.price),
        currency: service.currency,
        billing_period: service.billing_period,
        status: service.status,
      });
    } else {
      setValues(emptyServiceForm(defaultCurrency));
    }
    setErrors({});
    setSubmitError(null);
  }, [open, service, defaultCurrency]);

  function setField<K extends keyof ServiceFormValues>(field: K, value: ServiceFormValues[K]) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);

    const validationErrors = validateServiceForm(values);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setLoading(true);

    if (service) {
      const { service: updated, error } = await updateService(service.id, values);
      setLoading(false);
      if (error || !updated) {
        setSubmitError('Não foi possível guardar as alterações. Tente novamente.');
        return;
      }
      onSaved(updated, 'updated');
    } else {
      const { service: created, error } = await createService(organizationId, values);
      setLoading(false);
      if (error || !created) {
        setSubmitError('Não foi possível criar o serviço. Tente novamente.');
        return;
      }
      onSaved(created, 'created');
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={service ? 'Editar serviço' : 'Novo serviço'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" form="service-form" loading={loading}>
            {service ? 'Guardar alterações' : 'Criar serviço'}
          </Button>
        </>
      }
    >
      <form id="service-form" onSubmit={handleSubmit} className="space-y-4">
        {submitError && <ErrorMessage message={submitError} />}

        <Input
          label="Nome"
          required
          value={values.name}
          onChange={(e) => setField('name', e.target.value)}
          error={errors.name}
          placeholder="Ex: Mensalidade Premium"
        />

        <Textarea
          label="Descrição"
          value={values.description}
          onChange={(e) => setField('description', e.target.value)}
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label="Preço"
            inputMode="decimal"
            required
            value={values.price}
            onChange={(e) => setField('price', e.target.value)}
            error={errors.price}
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

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Select
            label="Periodicidade"
            value={values.billing_period}
            onChange={(e) => setField('billing_period', e.target.value as ServiceFormValues['billing_period'])}
          >
            {Object.entries(BILLING_PERIOD_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          <Select
            label="Estado"
            value={values.status}
            onChange={(e) => setField('status', e.target.value as ServiceFormValues['status'])}
          >
            {Object.entries(SERVICE_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
      </form>
    </Modal>
  );
}
