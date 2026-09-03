import { supabase } from '@/lib/supabase';
import type { Invoice, PaymentMethod } from '@/lib/types/database';
import type { PaymentFilters, PaymentPeriod } from './types';

export interface PaymentWithRelations {
  id: string;
  organization_id: string;
  invoice_id: string;
  client_id: string;
  amount: number;
  currency: string;
  payment_method: PaymentMethod;
  transaction_reference: string | null;
  paid_at: string;
  status: 'completed' | 'refunded' | 'failed';
  note: string | null;
  created_at: string;
  invoices: { invoice_number: string; description: string | null } | null;
  clients: { name: string; phone: string | null } | null;
}

export interface PaymentTotals {
  total_received: number;
  total_this_month: number;
  total_today: number;
}

function periodRange(period: PaymentPeriod): { from: string | null; to: string | null } {
  const now = new Date();
  const startOfMonth = (y: number, m: number) => new Date(y, m, 1).toISOString();
  const endOfMonth = (y: number, m: number) => new Date(y, m + 1, 0, 23, 59, 59).toISOString();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString();
  const endOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59).toISOString();

  switch (period) {
    case 'today':
      return { from: startOfToday, to: endOfToday };
    case 'this_month':
      return { from: startOfMonth(now.getFullYear(), now.getMonth()), to: endOfMonth(now.getFullYear(), now.getMonth()) };
    case 'last_month': {
      const lastMonth = now.getMonth() - 1;
      const year = lastMonth < 0 ? now.getFullYear() - 1 : now.getFullYear();
      const month = (lastMonth + 12) % 12;
      return { from: startOfMonth(year, month), to: endOfMonth(year, month) };
    }
    default:
      return { from: null, to: null };
  }
}

export async function fetchPayments(organizationId: string, filters: PaymentFilters) {
  const { clientSearch, referenceSearch, method, period, page, pageSize } = filters;
  const from = page * pageSize;
  const to = from + pageSize - 1;

  const needsClientJoin = clientSearch.trim() !== '';

  let query = supabase
    .from('payments')
    .select(
      needsClientJoin
        ? '*, invoices(invoice_number, description), clients!inner(name, phone)'
        : '*, invoices(invoice_number, description), clients(name, phone)',
      { count: 'exact' }
    )
    .eq('organization_id', organizationId)
    .order('paid_at', { ascending: false })
    .range(from, to);

  if (method !== 'all') {
    query = query.eq('payment_method', method);
  }

  const trimmedReference = referenceSearch.trim().replace(/[%,]/g, '');
  if (trimmedReference) {
    query = query.ilike('transaction_reference', `%${trimmedReference}%`);
  }

  const trimmedClientSearch = clientSearch.trim().replace(/[%,]/g, '');
  if (trimmedClientSearch) {
    query = query.ilike('clients.name', `%${trimmedClientSearch}%`);
  }

  const { from: dateFrom, to: dateTo } = periodRange(period);
  if (dateFrom) query = query.gte('paid_at', dateFrom);
  if (dateTo) query = query.lte('paid_at', dateTo);

  const { data, error, count } = await query;

  return {
    payments: (data ?? []) as PaymentWithRelations[],
    totalCount: count ?? 0,
    error,
  };
}

export async function fetchPaymentTotals(organizationId: string) {
  const { data, error } = await supabase
    .from('payment_totals')
    .select('*')
    .eq('organization_id', organizationId)
    .maybeSingle();

  return {
    totals: (data as PaymentTotals | null) ?? { total_received: 0, total_this_month: 0, total_today: 0 },
    error,
  };
}

export async function fetchPayableInvoices(organizationId: string, clientId?: string) {
  let query = supabase
    .from('invoices')
    .select('*, clients(name)')
    .eq('organization_id', organizationId)
    .in('status', ['pending', 'overdue'])
    .order('due_date', { ascending: true });

  if (clientId) {
    query = query.eq('client_id', clientId);
  }

  const { data, error } = await query;

  return {
    invoices: (data ?? []) as (Invoice & { clients: { name: string } | null })[],
    error,
  };
}

export async function registerPayment(params: {
  invoiceId: string;
  paymentMethod: PaymentMethod;
  transactionReference: string | null;
  paidAt: string; // ISO
  note: string | null;
}) {
  const { data, error } = await supabase.rpc('mark_invoice_paid', {
    p_invoice_id: params.invoiceId,
    p_payment_method: params.paymentMethod,
    p_transaction_reference: params.transactionReference,
    p_paid_at: params.paidAt,
    p_note: params.note,
  });

  return { payment: data, error };
}
