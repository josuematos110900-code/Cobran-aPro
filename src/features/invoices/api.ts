import { supabase } from '@/lib/supabase';
import type { Invoice, InvoiceStatus, PaymentMethod } from '@/lib/types/database';
import type { InvoiceFilters, InvoiceFormValues, InvoicePeriod } from './types';

export interface InvoiceWithClient extends Invoice {
  clients: { name: string } | null;
}

function periodRange(period: InvoicePeriod): { from: string | null; to: string | null } {
  const now = new Date();
  const startOfMonth = (y: number, m: number) => new Date(y, m, 1).toISOString().slice(0, 10);
  const endOfMonth = (y: number, m: number) => new Date(y, m + 1, 0).toISOString().slice(0, 10);

  switch (period) {
    case 'this_month':
      return { from: startOfMonth(now.getFullYear(), now.getMonth()), to: endOfMonth(now.getFullYear(), now.getMonth()) };
    case 'last_month': {
      const lastMonth = now.getMonth() - 1;
      const year = lastMonth < 0 ? now.getFullYear() - 1 : now.getFullYear();
      const month = (lastMonth + 12) % 12;
      return { from: startOfMonth(year, month), to: endOfMonth(year, month) };
    }
    case 'next_30_days': {
      const to = new Date(now);
      to.setDate(to.getDate() + 30);
      return { from: now.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
    }
    default:
      return { from: null, to: null };
  }
}

export async function fetchInvoices(organizationId: string, filters: InvoiceFilters) {
  const { clientSearch, invoiceNumberSearch, status, period, page, pageSize } = filters;
  const from = page * pageSize;
  const to = from + pageSize - 1;

  const needsClientJoin = clientSearch.trim() !== '';

  let query = supabase
    .from('invoices')
    .select(needsClientJoin ? '*, clients!inner(name)' : '*, clients(name)', { count: 'exact' })
    .eq('organization_id', organizationId)
    .order('due_date', { ascending: true })
    .range(from, to);

  if (status !== 'all') {
    query = query.eq('status', status);
  }

  const trimmedInvoiceNumber = invoiceNumberSearch.trim().replace(/[%,]/g, '');
  if (trimmedInvoiceNumber) {
    query = query.ilike('invoice_number', `%${trimmedInvoiceNumber}%`);
  }

  const trimmedClientSearch = clientSearch.trim().replace(/[%,]/g, '');
  if (trimmedClientSearch) {
    query = query.ilike('clients.name', `%${trimmedClientSearch}%`);
  }

  const { from: dateFrom, to: dateTo } = periodRange(period);
  if (dateFrom) query = query.gte('due_date', dateFrom);
  if (dateTo) query = query.lte('due_date', dateTo);

  const { data, error, count } = await query;

  return {
    invoices: (data ?? []) as InvoiceWithClient[],
    totalCount: count ?? 0,
    error,
  };
}

export async function fetchInvoiceById(invoiceId: string) {
  const { data, error } = await supabase
    .from('invoices')
    .select('*, clients(name, phone)')
    .eq('id', invoiceId)
    .maybeSingle();

  return { invoice: data as (InvoiceWithClient & { clients: { name: string; phone: string | null } | null }) | null, error };
}

export async function createInvoice(organizationId: string, values: InvoiceFormValues) {
  const { data: invoiceNumber, error: numberError } = await supabase.rpc('next_invoice_number', {
    p_organization_id: organizationId,
  });

  if (numberError || !invoiceNumber) {
    return { invoice: null, error: numberError };
  }

  const { data, error } = await supabase
    .from('invoices')
    .insert({
      organization_id: organizationId,
      client_id: values.client_id,
      invoice_number: invoiceNumber,
      description: values.description.trim() || null,
      amount: Number(values.amount.replace(',', '.')),
      currency: values.currency,
      due_date: values.due_date,
      reference: values.reference.trim() || null,
      status: 'pending',
    })
    .select('*, clients(name)')
    .single();

  return { invoice: data as InvoiceWithClient | null, error };
}

export async function updateInvoice(invoiceId: string, values: InvoiceFormValues) {
  const { data, error } = await supabase
    .from('invoices')
    .update({
      client_id: values.client_id,
      description: values.description.trim() || null,
      amount: Number(values.amount.replace(',', '.')),
      currency: values.currency,
      due_date: values.due_date,
      reference: values.reference.trim() || null,
    })
    .eq('id', invoiceId)
    .select('*, clients(name)')
    .single();

  return { invoice: data as InvoiceWithClient | null, error };
}

export async function cancelInvoice(invoiceId: string) {
  const { error } = await supabase
    .from('invoices')
    .update({ status: 'cancelled' as InvoiceStatus })
    .eq('id', invoiceId);

  return { error };
}

export async function markInvoicePaid(
  invoiceId: string,
  paymentMethod: PaymentMethod,
  transactionReference: string | null
) {
  const { data, error } = await supabase.rpc('mark_invoice_paid', {
    p_invoice_id: invoiceId,
    p_payment_method: paymentMethod,
    p_transaction_reference: transactionReference,
  });

  return { payment: data, error };
}
