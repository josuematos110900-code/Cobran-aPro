import { supabase } from '@/lib/supabase';
import type { Client } from '@/lib/types/database';
import type { ClientBalance, ClientFilters, ClientFormValues } from './types';

export async function fetchClients(organizationId: string, filters: ClientFilters) {
  const { search, status, page, pageSize } = filters;
  const from = page * pageSize;
  const to = from + pageSize - 1;

  let query = supabase
    .from('clients')
    .select('*', { count: 'exact' })
    .eq('organization_id', organizationId)
    .order('created_at', { ascending: false })
    .range(from, to);

  if (status !== 'all') {
    query = query.eq('status', status);
  }

  const trimmedSearch = search.trim();
  if (trimmedSearch) {
    const term = trimmedSearch.replace(/[%,]/g, '');
    query = query.or(`name.ilike.%${term}%,phone.ilike.%${term}%,email.ilike.%${term}%`);
  }

  const { data, error, count } = await query;

  return {
    clients: (data ?? []) as Client[],
    totalCount: count ?? 0,
    error,
  };
}

export async function fetchClientBalances(clientIds: string[]) {
  if (clientIds.length === 0) return { balances: [] as ClientBalance[], error: null };

  const { data, error } = await supabase
    .from('client_balances')
    .select('*')
    .in('client_id', clientIds);

  return { balances: (data ?? []) as ClientBalance[], error };
}

export async function fetchClientById(clientId: string) {
  const { data, error } = await supabase
    .from('clients')
    .select('*')
    .eq('id', clientId)
    .maybeSingle();

  return { client: data as Client | null, error };
}

export async function fetchClientBalance(clientId: string) {
  const { data, error } = await supabase
    .from('client_balances')
    .select('*')
    .eq('client_id', clientId)
    .maybeSingle();

  return { balance: data as ClientBalance | null, error };
}

export async function createClient(organizationId: string, values: ClientFormValues) {
  const { data, error } = await supabase
    .from('clients')
    .insert({
      organization_id: organizationId,
      name: values.name.trim(),
      phone: values.phone.trim() || null,
      email: values.email.trim() || null,
      address: values.address.trim() || null,
      notes: values.notes.trim() || null,
      status: values.status,
    })
    .select()
    .single();

  return { client: data as Client | null, error };
}

export async function updateClient(clientId: string, values: ClientFormValues) {
  const { data, error } = await supabase
    .from('clients')
    .update({
      name: values.name.trim(),
      phone: values.phone.trim() || null,
      email: values.email.trim() || null,
      address: values.address.trim() || null,
      notes: values.notes.trim() || null,
      status: values.status,
    })
    .eq('id', clientId)
    .select()
    .single();

  return { client: data as Client | null, error };
}

export async function archiveClient(clientId: string) {
  const { error } = await supabase
    .from('clients')
    .update({ status: 'archived' })
    .eq('id', clientId);

  return { error };
}

export async function fetchClientInvoices(clientId: string) {
  const { data, error } = await supabase
    .from('invoices')
    .select('*')
    .eq('client_id', clientId)
    .order('due_date', { ascending: false });

  return { invoices: data ?? [], error };
}

export async function fetchClientPayments(clientId: string) {
  const { data, error } = await supabase
    .from('payments')
    .select('*, invoices(invoice_number, description)')
    .eq('client_id', clientId)
    .order('paid_at', { ascending: false });

  return { payments: data ?? [], error };
}

export async function fetchClientSubscriptions(clientId: string) {
  const { data, error } = await supabase
    .from('subscriptions')
    .select('*, clients(name), services(name)')
    .eq('client_id', clientId)
    .order('created_at', { ascending: false });

  return { subscriptions: data ?? [], error };
}

export async function fetchClientsForSelect(organizationId: string) {
  const { data, error } = await supabase
    .from('clients')
    .select('id, name, phone')
    .eq('organization_id', organizationId)
    .neq('status', 'archived')
    .order('name', { ascending: true });

  return { clients: (data ?? []) as Pick<Client, 'id' | 'name' | 'phone'>[], error };
}
