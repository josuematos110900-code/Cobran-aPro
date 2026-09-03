import { supabase } from '@/lib/supabase';

export interface OnboardingProgress {
  hasClient: boolean;
  hasService: boolean;
  hasInvoice: boolean;
  hasPayment: boolean;
}

async function hasAnyRow(table: 'clients' | 'services' | 'invoices' | 'payments', organizationId: string) {
  const { count } = await supabase
    .from(table)
    .select('id', { count: 'exact', head: true })
    .eq('organization_id', organizationId);

  return (count ?? 0) > 0;
}

export async function fetchOnboardingProgress(organizationId: string): Promise<OnboardingProgress> {
  const [hasClient, hasService, hasInvoice, hasPayment] = await Promise.all([
    hasAnyRow('clients', organizationId),
    hasAnyRow('services', organizationId),
    hasAnyRow('invoices', organizationId),
    hasAnyRow('payments', organizationId),
  ]);

  return { hasClient, hasService, hasInvoice, hasPayment };
}

export interface RecentOverdueInvoice {
  id: string;
  invoice_number: string;
  client_name: string;
  amount: number;
  currency: string;
  due_date: string;
}

export async function fetchRecentOverdueInvoices(organizationId: string) {
  const { data, error } = await supabase.rpc('get_recent_overdue_invoices', {
    p_organization_id: organizationId,
    p_limit: 5,
  });

  return { invoices: (data ?? []) as RecentOverdueInvoice[], error };
}
