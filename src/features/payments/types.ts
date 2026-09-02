import type { PaymentMethod } from '@/lib/types/database';

export type PaymentPeriod = 'all' | 'today' | 'this_month' | 'last_month';

export interface PaymentFilters {
  clientSearch: string;
  referenceSearch: string;
  method: PaymentMethod | 'all';
  period: PaymentPeriod;
  page: number;
  pageSize: number;
}

export interface RegisterPaymentFormValues {
  client_id: string;
  invoice_id: string;
  payment_method: PaymentMethod;
  transaction_reference: string;
  paid_at: string; // datetime-local string
  note: string;
}

export function emptyRegisterPaymentForm(preselectedClientId?: string): RegisterPaymentFormValues {
  // "YYYY-MM-DDTHH:mm" no fuso horário local, formato exigido por um
  // input datetime-local.
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const localNow = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(
    now.getHours()
  )}:${pad(now.getMinutes())}`;

  return {
    client_id: preselectedClientId ?? '',
    invoice_id: '',
    payment_method: 'cash',
    transaction_reference: '',
    paid_at: localNow,
    note: '',
  };
}

// Todos os métodos alguma vez válidos — usado para exibir/rotular
// pagamentos já registados (incluindo métodos legados como "mobile_money"
// ou "card" que já não são oferecidos como opção nova, mas podem existir
// em dados antigos).
export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  cash: 'Dinheiro',
  bank_transfer: 'Transferência bancária',
  mobile_money: 'Mobile money',
  card: 'Cartão',
  multicaixa: 'Multicaixa',
  multicaixa_express: 'Multicaixa Express',
  other: 'Outro',
};

// Os únicos métodos oferecidos ao registar um NOVO pagamento nesta fase.
export const PAYABLE_METHOD_OPTIONS: PaymentMethod[] = [
  'cash',
  'bank_transfer',
  'multicaixa',
  'multicaixa_express',
  'other',
];

export const PERIOD_LABELS: Record<PaymentPeriod, string> = {
  all: 'Todo o período',
  today: 'Hoje',
  this_month: 'Este mês',
  last_month: 'Mês passado',
};
