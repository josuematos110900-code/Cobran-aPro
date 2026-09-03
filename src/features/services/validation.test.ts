import { describe, expect, it } from 'vitest';
import { validateServiceForm } from './validation';
import type { ServiceFormValues } from './types';

const base: ServiceFormValues = {
  name: 'Mensalidade',
  description: '',
  price: '15000',
  currency: 'AOA',
  billing_period: 'monthly',
  status: 'active',
};

describe('validateServiceForm', () => {
  it('accepts a valid service', () => {
    expect(validateServiceForm(base)).toEqual({});
  });

  it('requires a name', () => {
    expect(validateServiceForm({ ...base, name: '' }).name).toBeDefined();
  });

  it('requires a numeric price', () => {
    expect(validateServiceForm({ ...base, price: 'abc' }).price).toBeDefined();
  });

  it('rejects a negative price', () => {
    expect(validateServiceForm({ ...base, price: '-10' }).price).toBeDefined();
  });

  it('accepts a comma decimal price', () => {
    expect(validateServiceForm({ ...base, price: '1500,50' })).toEqual({});
  });
});
