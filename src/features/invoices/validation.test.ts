import { describe, expect, it } from 'vitest';
import { validateInvoiceForm } from './validation';
import type { InvoiceFormValues } from './types';

const base: InvoiceFormValues = {
  client_id: 'client-1',
  description: 'Mensalidade de Setembro',
  amount: '15000',
  currency: 'AOA',
  due_date: '2026-09-30',
  reference: '',
};

describe('validateInvoiceForm', () => {
  it('accepts a valid invoice', () => {
    expect(validateInvoiceForm(base)).toEqual({});
  });

  it('requires a client', () => {
    expect(validateInvoiceForm({ ...base, client_id: '' }).client_id).toBeDefined();
  });

  it('requires a positive amount', () => {
    expect(validateInvoiceForm({ ...base, amount: '0' }).amount).toBeDefined();
    expect(validateInvoiceForm({ ...base, amount: '-5' }).amount).toBeDefined();
    expect(validateInvoiceForm({ ...base, amount: '' }).amount).toBeDefined();
  });

  it('rejects a non-numeric amount', () => {
    expect(validateInvoiceForm({ ...base, amount: 'abc' }).amount).toBeDefined();
  });

  it('requires a due date', () => {
    expect(validateInvoiceForm({ ...base, due_date: '' }).due_date).toBeDefined();
  });
});
