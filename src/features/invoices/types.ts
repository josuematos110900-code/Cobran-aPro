import type { InvoiceStatus } from '@/lib/types/database';

export { PAYMENT_METHOD_LABELS, PAYABLE_METHOD_OPTIONS } from '@/features/payments/types';

export type InvoicePeriod = 'all' | 'this_month' | 'last_month' | 'next_30_days';

export interface InvoiceFilters {
  clientSearch: string;
  invoiceNumberSearch: string;
  status: InvoiceStatus | 'all';
  period: InvoicePeriod;
  page: number;
  pageSize: number;
}

export interface InvoiceFormValues {
  client_id: string;
  description: string;
  amount: string;
  currency: string;
  due_date: string;
  reference: string;
}

export function emptyInvoiceForm(defaultCurrency: string): InvoiceFormValues {
  return {
    client_id: '',
    description: '',
    amount: '',
    currency: defaultCurrency,
    due_date: '',
    reference: '',
  };
}

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  pending: 'Pendente',
  paid: 'Paga',
  overdue: 'Atrasada',
  cancelled: 'Cancelada',
};

export const CURRENCY_OPTIONS = ['AOA', 'BRL', 'EUR', 'USD'];
