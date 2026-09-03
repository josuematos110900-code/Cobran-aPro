import type { SubscriptionFormValues } from './types';

export type SubscriptionFormErrors = Partial<Record<keyof SubscriptionFormValues, string>>;

export function validateSubscriptionForm(values: SubscriptionFormValues): SubscriptionFormErrors {
  const errors: SubscriptionFormErrors = {};

  if (!values.client_id) {
    errors.client_id = 'Seleccione um cliente.';
  }
  if (!values.service_id) {
    errors.service_id = 'Seleccione um serviço.';
  }

  const trimmedAmount = values.amount.trim().replace(',', '.');
  const amount = Number(trimmedAmount);
  if (!trimmedAmount) {
    errors.amount = 'O valor é obrigatório.';
  } else if (Number.isNaN(amount) || amount <= 0) {
    errors.amount = 'Indique um valor numérico maior que zero.';
  }

  const dueDay = Number(values.due_day);
  if (!values.due_day || Number.isNaN(dueDay) || dueDay < 1 || dueDay > 31) {
    errors.due_day = 'Indique um dia entre 1 e 31 (ou 1–7 para semanal).';
  }

  if (!values.start_date) {
    errors.start_date = 'A data inicial é obrigatória.';
  }

  if (values.end_date && values.start_date && values.end_date < values.start_date) {
    errors.end_date = 'A data final não pode ser anterior à data inicial.';
  }

  return errors;
}
