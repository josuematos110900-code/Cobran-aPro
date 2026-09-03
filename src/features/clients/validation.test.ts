import { describe, expect, it } from 'vitest';
import { validateClientForm } from './validation';
import type { ClientFormValues } from './types';

const base: ClientFormValues = {
  name: 'Maria Silva',
  phone: '923456789',
  email: '',
  address: '',
  notes: '',
  status: 'active',
};

describe('validateClientForm', () => {
  it('accepts a valid client with only a phone', () => {
    expect(validateClientForm(base)).toEqual({});
  });

  it('requires a name', () => {
    const errors = validateClientForm({ ...base, name: '' });
    expect(errors.name).toBeDefined();
  });

  it('requires at least a phone or an email', () => {
    const errors = validateClientForm({ ...base, phone: '', email: '' });
    expect(errors.phone).toBeDefined();
  });

  it('rejects an invalid email', () => {
    const errors = validateClientForm({ ...base, email: 'not-an-email' });
    expect(errors.email).toBeDefined();
  });

  it('accepts a client with only a valid email', () => {
    const errors = validateClientForm({ ...base, phone: '', email: 'cliente@exemplo.com' });
    expect(errors).toEqual({});
  });
});
