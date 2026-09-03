import { supabase } from '@/lib/supabase';
import type { PlanStatus, PlanTier, BillingCycle } from '@/lib/types/database';

export async function fetchPlanStatus(organizationId: string) {
  const { data, error } = await supabase.rpc('get_plan_status', {
    p_organization_id: organizationId,
  });

  return { status: (data as PlanStatus | null) ?? null, error };
}

export async function requestPlanUpgrade(
  organizationId: string,
  requestedPlan: PlanTier,
  billingPeriod: BillingCycle,
  note: string | null
) {
  const { data, error } = await supabase.rpc('request_plan_upgrade', {
    p_organization_id: organizationId,
    p_requested_plan: requestedPlan,
    p_billing_period: billingPeriod,
    p_note: note,
  });

  return { intent: data, error };
}
