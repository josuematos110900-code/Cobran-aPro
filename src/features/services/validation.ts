import type { ServiceFormValues } from './types';

export type ServiceFormErrors = Partial<Record<keyof ServiceFormValues, string>>;

export function validateServiceForm(values: ServiceFormValues): ServiceFormErrors {
  const errors: ServiceFormErrors = {};

  if (!values.name.trim()) {
    errors.name = 'O nome é obrigatório.';
  }

  const trimmedPrice = values.price.trim().replace(',', '.');
  const price = Number(trimmedPrice);
  if (!trimmedPrice) {
    errors.price = 'O preço é obrigatório.';
  } else if (Number.isNaN(price)) {
    errors.price = 'Indique um preço numérico válido.';
  } else if (price < 0) {
    errors.price = 'O preço não pode ser negativo.';
  }

  if (!values.currency) {
    errors.currency = 'Seleccione uma moeda.';
  }

  return errors;
}
