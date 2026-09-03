import type { BillingPeriod, ServiceStatus } from '@/lib/types/database';

export interface ServiceFilters {
  search: string;
  status: ServiceStatus | 'all';
  page: number;
  pageSize: number;
}

export interface ServiceFormValues {
  name: string;
  description: string;
  price: string;
  currency: string;
  billing_period: BillingPeriod;
  status: ServiceStatus;
}

export function emptyServiceForm(defaultCurrency: string): ServiceFormValues {
  return {
    name: '',
    description: '',
    price: '',
    currency: defaultCurrency,
    billing_period: 'monthly',
    status: 'active',
  };
}

export const SERVICE_STATUS_LABELS: Record<ServiceStatus, string> = {
  active: 'Activo',
  inactive: 'Inactivo',
};

export const BILLING_PERIOD_LABELS: Record<BillingPeriod, string> = {
  weekly: 'Semanal',
  monthly: 'Mensal',
  quarterly: 'Trimestral',
  yearly: 'Anual',
};

export const CURRENCY_OPTIONS = ['AOA', 'BRL', 'EUR', 'USD'];
