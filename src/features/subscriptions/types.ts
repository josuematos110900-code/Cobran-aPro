import type { BillingPeriod, SubscriptionStatus } from '@/lib/types/database';

export interface SubscriptionFilters {
  status: SubscriptionStatus | 'all';
  page: number;
  pageSize: number;
}

export interface SubscriptionFormValues {
  client_id: string;
  service_id: string;
  amount: string;
  currency: string;
  billing_period: BillingPeriod;
  due_day: string;
  start_date: string;
  end_date: string;
}

export function emptySubscriptionForm(defaultCurrency: string): SubscriptionFormValues {
  const today = new Date().toISOString().slice(0, 10);
  return {
    client_id: '',
    service_id: '',
    amount: '',
    currency: defaultCurrency,
    billing_period: 'monthly',
    due_day: String(new Date().getDate()),
    start_date: today,
    end_date: '',
  };
}

export const SUBSCRIPTION_STATUS_LABELS: Record<SubscriptionStatus, string> = {
  active: 'Activa',
  paused: 'Pausada',
  cancelled: 'Cancelada',
  ended: 'Concluída',
};

export const BILLING_PERIOD_LABELS: Record<BillingPeriod, string> = {
  weekly: 'Semanal',
  monthly: 'Mensal',
  quarterly: 'Trimestral',
  yearly: 'Anual',
};

export const CURRENCY_OPTIONS = ['AOA', 'BRL', 'EUR', 'USD'];
