import type { InvoiceFormValues } from './types';

export type InvoiceFormErrors = Partial<Record<keyof InvoiceFormValues, string>>;

export function validateInvoiceForm(values: InvoiceFormValues): InvoiceFormErrors {
  const errors: InvoiceFormErrors = {};

  if (!values.client_id) {
    errors.client_id = 'Seleccione um cliente.';
  }

  const trimmedAmount = values.amount.trim().replace(',', '.');
  const amount = Number(trimmedAmount);
  if (!trimmedAmount) {
    errors.amount = 'O valor é obrigatório.';
  } else if (Number.isNaN(amount)) {
    errors.amount = 'Indique um valor numérico válido.';
  } else if (amount <= 0) {
    errors.amount = 'O valor deve ser maior que zero.';
  }

  if (!values.currency) {
    errors.currency = 'Seleccione uma moeda.';
  }

  if (!values.due_date) {
    errors.due_date = 'A data de vencimento é obrigatória.';
  }

  return errors;
}
