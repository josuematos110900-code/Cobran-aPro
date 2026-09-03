import type { ClientFormValues } from './types';

export type ClientFormErrors = Partial<Record<keyof ClientFormValues, string>>;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateClientForm(values: ClientFormValues): ClientFormErrors {
  const errors: ClientFormErrors = {};

  if (!values.name.trim()) {
    errors.name = 'O nome é obrigatório.';
  } else if (values.name.trim().length < 2) {
    errors.name = 'O nome deve ter pelo menos 2 caracteres.';
  }

  if (values.email.trim() && !EMAIL_REGEX.test(values.email.trim())) {
    errors.email = 'Indique um email válido.';
  }

  if (values.phone.trim() && values.phone.trim().replace(/[^0-9+]/g, '').length < 7) {
    errors.phone = 'Indique um número de telefone válido.';
  }

  if (!values.phone.trim() && !values.email.trim()) {
    errors.phone = 'Indique pelo menos um telefone ou email de contacto.';
  }

  return errors;
}
