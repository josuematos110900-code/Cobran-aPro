import { supabase } from '@/lib/supabase';
import type { Invoice, Subscription, SubscriptionStatus } from '@/lib/types/database';
import type { SubscriptionFilters, SubscriptionFormValues } from './types';

export interface SubscriptionWithRelations extends Subscription {
  clients: { name: string } | null;
  services: { name: string } | null;
}

export async function fetchSubscriptions(organizationId: string, filters: SubscriptionFilters) {
  const { status, page, pageSize } = filters;
  const from = page * pageSize;
  const to = from + pageSize - 1;

  let query = supabase
    .from('subscriptions')
    .select('*, clients(name), services(name)', { count: 'exact' })
    .eq('organization_id', organizationId)
    .order('created_at', { ascending: false })
    .range(from, to);

  if (status !== 'all') {
    query = query.eq('status', status);
  }

  const { data, error, count } = await query;

  return {
    subscriptions: (data ?? []) as SubscriptionWithRelations[],
    totalCount: count ?? 0,
    error,
  };
}

export async function fetchSubscriptionById(subscriptionId: string) {
  const { data, error } = await supabase
    .from('subscriptions')
    .select('*, clients(name), services(name)')
    .eq('id', subscriptionId)
    .maybeSingle();

  return { subscription: data as SubscriptionWithRelations | null, error };
}

export async function fetchSubscriptionInvoices(subscriptionId: string) {
  const { data, error } = await supabase
    .from('invoices')
    .select('*')
    .eq('subscription_id', subscriptionId)
    .order('due_date', { ascending: false });

  return { invoices: (data ?? []) as Invoice[], error };
}

function toInsertPayload(organizationId: string, values: SubscriptionFormValues) {
  return {
    organization_id: organizationId,
    client_id: values.client_id,
    service_id: values.service_id,
    amount: Number(values.amount.replace(',', '.')),
    currency: values.currency,
    billing_period: values.billing_period,
    due_day: Number(values.due_day),
    start_date: values.start_date,
    end_date: values.end_date || null,
  };
}

export async function createSubscription(organizationId: string, values: SubscriptionFormValues) {
  const { data, error } = await supabase
    .from('subscriptions')
    .insert({ ...toInsertPayload(organizationId, values), status: 'active' })
    .select('*, clients(name), services(name)')
    .single();

  return { subscription: data as SubscriptionWithRelations | null, error };
}

export async function updateSubscription(subscriptionId: string, values: SubscriptionFormValues) {
  const { data, error } = await supabase
    .from('subscriptions')
    .update({
      client_id: values.client_id,
      service_id: values.service_id,
      amount: Number(values.amount.replace(',', '.')),
      currency: values.currency,
      billing_period: values.billing_period,
      due_day: Number(values.due_day),
      start_date: values.start_date,
      end_date: values.end_date || null,
    })
    .eq('id', subscriptionId)
    .select('*, clients(name), services(name)')
    .single();

  return { subscription: data as SubscriptionWithRelations | null, error };
}

export async function setSubscriptionStatus(subscriptionId: string, status: SubscriptionStatus) {
  const { error } = await supabase.from('subscriptions').update({ status }).eq('id', subscriptionId);
  return { error };
}
