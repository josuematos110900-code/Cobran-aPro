import { supabase } from '@/lib/supabase';
import type { ReminderFilters, ReminderPeriod, ReminderType } from './types';
import { REMINDER_STATUS } from './types';

export interface ReminderWithRelations {
  id: string;
  organization_id: string;
  client_id: string;
  invoice_id: string | null;
  channel: string;
  message: string;
  reminder_type: ReminderType;
  scheduled_at: string;
  sent_at: string | null;
  status: string;
  created_at: string;
  clients: { name: string; phone: string | null } | null;
  invoices: { invoice_number: string } | null;
}

export interface ReminderTotals {
  total_sent: number;
  sent_today: number;
  sent_this_month: number;
}

function periodRange(period: ReminderPeriod): { from: string | null; to: string | null } {
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

export async function fetchReminders(organizationId: string, filters: ReminderFilters) {
  const { clientSearch, type, period, page, pageSize } = filters;
  const from = page * pageSize;
  const to = from + pageSize - 1;

  const needsClientJoin = clientSearch.trim() !== '';

  let query = supabase
    .from('reminders')
    .select(
      needsClientJoin ? '*, clients!inner(name, phone), invoices(invoice_number)' : '*, clients(name, phone), invoices(invoice_number)',
      { count: 'exact' }
    )
    .eq('organization_id', organizationId)
    .order('created_at', { ascending: false })
    .range(from, to);

  if (type !== 'all') {
    query = query.eq('reminder_type', type);
  }

  const trimmedSearch = clientSearch.trim().replace(/[%,]/g, '');
  if (trimmedSearch) {
    query = query.ilike('clients.name', `%${trimmedSearch}%`);
  }

  const { from: dateFrom, to: dateTo } = periodRange(period);
  if (dateFrom) query = query.gte('created_at', dateFrom);
  if (dateTo) query = query.lte('created_at', dateTo);

  const { data, error, count } = await query;

  return {
    reminders: (data ?? []) as ReminderWithRelations[],
    totalCount: count ?? 0,
    error,
  };
}

export async function fetchReminderTotals(organizationId: string) {
  const { data, error } = await supabase
    .from('reminder_totals')
    .select('*')
    .eq('organization_id', organizationId)
    .maybeSingle();

  return {
    totals: (data as ReminderTotals | null) ?? { total_sent: 0, sent_today: 0, sent_this_month: 0 },
    error,
  };
}

export async function fetchClientReminders(clientId: string) {
  const { data, error } = await supabase
    .from('reminders')
    .select('*, invoices(invoice_number)')
    .eq('client_id', clientId)
    .order('created_at', { ascending: false });

  return { reminders: (data ?? []) as ReminderWithRelations[], error };
}

export async function fetchInvoiceReminders(invoiceId: string) {
  const { data, error } = await supabase
    .from('reminders')
    .select('*, clients(name, phone)')
    .eq('invoice_id', invoiceId)
    .order('created_at', { ascending: false });

  return { reminders: (data ?? []) as ReminderWithRelations[], error };
}

// Regista que o utilizador clicou para abrir o WhatsApp com esta
// mensagem. Nunca marca como "entregue" — ver REMINDER_STATUS.
export async function recordReminder(params: {
  organizationId: string;
  clientId: string;
  invoiceId: string | null;
  reminderType: ReminderType;
  message: string;
}) {
  const { error } = await supabase.from('reminders').insert({
    organization_id: params.organizationId,
    client_id: params.clientId,
    invoice_id: params.invoiceId,
    reminder_type: params.reminderType,
    channel: 'whatsapp',
    message: params.message,
    status: REMINDER_STATUS,
    sent_at: new Date().toISOString(),
  });

  return { error };
}
