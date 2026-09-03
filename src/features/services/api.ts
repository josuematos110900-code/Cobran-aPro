import { supabase } from '@/lib/supabase';
import type { Service, ServiceStatus } from '@/lib/types/database';
import type { ServiceFilters, ServiceFormValues } from './types';

export async function fetchServices(organizationId: string, filters: ServiceFilters) {
  const { search, status, page, pageSize } = filters;
  const from = page * pageSize;
  const to = from + pageSize - 1;

  let query = supabase
    .from('services')
    .select('*', { count: 'exact' })
    .eq('organization_id', organizationId)
    .order('created_at', { ascending: false })
    .range(from, to);

  if (status !== 'all') {
    query = query.eq('status', status);
  }

  const trimmedSearch = search.trim().replace(/[%,]/g, '');
  if (trimmedSearch) {
    query = query.ilike('name', `%${trimmedSearch}%`);
  }

  const { data, error, count } = await query;

  return {
    services: (data ?? []) as Service[],
    totalCount: count ?? 0,
    error,
  };
}

export async function fetchActiveServicesForSelect(organizationId: string) {
  const { data, error } = await supabase
    .from('services')
    .select('id, name, price, currency, billing_period')
    .eq('organization_id', organizationId)
    .eq('status', 'active')
    .order('name', { ascending: true });

  return {
    services: (data ?? []) as Pick<Service, 'id' | 'name' | 'price' | 'currency' | 'billing_period'>[],
    error,
  };
}

export async function createService(organizationId: string, values: ServiceFormValues) {
  const { data, error } = await supabase
    .from('services')
    .insert({
      organization_id: organizationId,
      name: values.name.trim(),
      description: values.description.trim() || null,
      price: Number(values.price.replace(',', '.')),
      currency: values.currency,
      billing_period: values.billing_period,
      status: values.status,
    })
    .select()
    .single();

  return { service: data as Service | null, error };
}

export async function updateService(serviceId: string, values: ServiceFormValues) {
  const { data, error } = await supabase
    .from('services')
    .update({
      name: values.name.trim(),
      description: values.description.trim() || null,
      price: Number(values.price.replace(',', '.')),
      currency: values.currency,
      billing_period: values.billing_period,
      status: values.status,
    })
    .eq('id', serviceId)
    .select()
    .single();

  return { service: data as Service | null, error };
}

export async function archiveService(serviceId: string) {
  const { error } = await supabase
    .from('services')
    .update({ status: 'inactive' as ServiceStatus })
    .eq('id', serviceId);

  return { error };
}
