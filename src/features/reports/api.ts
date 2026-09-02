import { supabase } from '@/lib/supabase';
import type { ReportGranularity } from './types';

export interface ReportSummary {
  revenue_received: number;
  total_billed: number;
  total_pending: number;
  total_overdue: number;
  invoices_emitted_count: number;
  invoices_paid_count: number;
  payment_rate: number;
  active_clients: number;
  active_subscriptions: number;
}

export interface RevenuePoint {
  bucket: string;
  total: number;
}

export interface InvoiceStatusBreakdownRow {
  status: string;
  invoice_count: number;
  total_amount: number;
}

export interface TopClientRow {
  client_id: string;
  client_name: string;
  total_paid: number;
  total_pending: number;
  total_overdue: number;
  invoice_count: number;
}

export interface ServiceBreakdownRow {
  service_id: string;
  service_name: string;
  subscriptions_total: number;
  subscriptions_active: number;
  estimated_mrr: number;
  revenue_in_period: number;
}

export interface OverdueTopRow {
  invoice_id: string;
  invoice_number: string;
  client_id: string;
  client_name: string;
  client_phone: string | null;
  amount: number;
  currency: string;
  due_date: string;
  days_overdue: number;
}

export async function fetchReportSummary(organizationId: string, from: string, to: string) {
  const { data, error } = await supabase.rpc('report_summary', {
    p_organization_id: organizationId,
    p_from: from,
    p_to: to,
  });

  const row = Array.isArray(data) ? data[0] : data;

  return {
    summary: (row as ReportSummary | null) ?? {
      revenue_received: 0,
      total_billed: 0,
      total_pending: 0,
      total_overdue: 0,
      invoices_emitted_count: 0,
      invoices_paid_count: 0,
      payment_rate: 0,
      active_clients: 0,
      active_subscriptions: 0,
    },
    error,
  };
}

export async function fetchRevenueSeries(
  organizationId: string,
  from: string,
  to: string,
  granularity: ReportGranularity
) {
  const { data, error } = await supabase.rpc('report_revenue_series', {
    p_organization_id: organizationId,
    p_from: from,
    p_to: to,
    p_granularity: granularity,
  });

  return { series: (data ?? []) as RevenuePoint[], error };
}

export async function fetchInvoiceStatusBreakdown(organizationId: string, from: string, to: string) {
  const { data, error } = await supabase.rpc('report_invoice_status_breakdown', {
    p_organization_id: organizationId,
    p_from: from,
    p_to: to,
  });

  return { breakdown: (data ?? []) as InvoiceStatusBreakdownRow[], error };
}

export async function fetchTopClients(organizationId: string, from: string, to: string, limit = 10) {
  const { data, error } = await supabase.rpc('report_top_clients', {
    p_organization_id: organizationId,
    p_from: from,
    p_to: to,
    p_limit: limit,
  });

  return { clients: (data ?? []) as TopClientRow[], error };
}

export async function fetchServiceBreakdown(organizationId: string, from: string, to: string) {
  const { data, error } = await supabase.rpc('report_service_breakdown', {
    p_organization_id: organizationId,
    p_from: from,
    p_to: to,
  });

  return { services: (data ?? []) as ServiceBreakdownRow[], error };
}

export async function fetchOverdueTop(organizationId: string, limit = 10) {
  const { data, error } = await supabase.rpc('report_overdue_top', {
    p_organization_id: organizationId,
    p_limit: limit,
  });

  return { overdue: (data ?? []) as OverdueTopRow[], error };
}

// ---------------------------------------------------------------------
// Exportações CSV — usam exactamente os mesmos filtros (organização +
// período) seleccionados na página no momento do clique. Nenhuma
// exportação busca dados "soltos" fora do que está no ecrã.
// ---------------------------------------------------------------------

export async function fetchPaymentsForExport(organizationId: string, from: string, to: string) {
  const { data, error } = await supabase
    .from('payments')
    .select('paid_at, amount, currency, payment_method, transaction_reference, clients(name), invoices(invoice_number)')
    .eq('organization_id', organizationId)
    .eq('status', 'completed')
    .gte('paid_at', `${from}T00:00:00`)
    .lte('paid_at', `${to}T23:59:59`)
    .order('paid_at', { ascending: true });

  return { rows: data ?? [], error };
}

export async function fetchInvoicesForExport(organizationId: string, from: string, to: string) {
  const { data, error } = await supabase
    .from('invoices')
    .select('invoice_number, due_date, amount, currency, status, description, clients(name)')
    .eq('organization_id', organizationId)
    .gte('due_date', from)
    .lte('due_date', to)
    .order('due_date', { ascending: true });

  return { rows: data ?? [], error };
}

export async function fetchClientsForExport(organizationId: string) {
  const { data: clients, error: clientsError } = await supabase
    .from('clients')
    .select('id, name, phone, email, status')
    .eq('organization_id', organizationId)
    .order('name', { ascending: true });

  if (clientsError || !clients) {
    return { rows: [], error: clientsError };
  }

  const { data: balances } = await supabase
    .from('client_balances')
    .select('*')
    .in(
      'client_id',
      clients.map((c) => c.id)
    );

  const balanceByClient = new Map(
    (balances ?? []).map((b) => [b.client_id, b as { total_paid: number; total_pending: number; total_overdue: number }])
  );

  const rows = clients.map((client) => ({
    ...client,
    total_paid: balanceByClient.get(client.id)?.total_paid ?? 0,
    total_pending: balanceByClient.get(client.id)?.total_pending ?? 0,
    total_overdue: balanceByClient.get(client.id)?.total_overdue ?? 0,
  }));

  return { rows, error: null };
}
