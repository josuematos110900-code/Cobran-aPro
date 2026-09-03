import { supabase } from '@/lib/supabase';
import type { Organization, OrganizationMemberWithProfile, OrgMemberRole, Settings } from '@/lib/types/database';
import type { CompanyFormValues, WhatsAppSettingsFormValues } from './types';

export async function updateCompany(organizationId: string, values: CompanyFormValues) {
  const { data, error } = await supabase
    .from('organizations')
    .update({
      name: values.name.trim(),
      business_type: values.business_type.trim() || null,
      currency: values.currency,
      phone: values.phone.trim() || null,
      email: values.email.trim() || null,
      address: values.address.trim() || null,
    })
    .eq('id', organizationId)
    .select()
    .single();

  return { organization: data as Organization | null, error };
}

export async function fetchSettings(organizationId: string) {
  const { data, error } = await supabase
    .from('settings')
    .select('*')
    .eq('organization_id', organizationId)
    .maybeSingle();

  return { settings: data as Settings | null, error };
}

export async function updateWhatsAppSettings(organizationId: string, values: WhatsAppSettingsFormValues) {
  const reminderDays = Number(values.reminder_days_before);

  const { data, error } = await supabase
    .from('settings')
    .update({
      reminder_days_before: Number.isFinite(reminderDays) ? reminderDays : 1,
      whatsapp_template: values.whatsapp_template.trim(),
    })
    .eq('organization_id', organizationId)
    .select()
    .single();

  return { settings: data as Settings | null, error };
}

export async function fetchMembers(organizationId: string) {
  const { data, error } = await supabase.rpc('list_organization_members', {
    p_organization_id: organizationId,
  });

  return { members: (data ?? []) as OrganizationMemberWithProfile[], error };
}

export async function addMemberByEmail(organizationId: string, email: string, role: OrgMemberRole) {
  const { data, error } = await supabase.rpc('add_organization_member_by_email', {
    p_organization_id: organizationId,
    p_email: email,
    p_role: role,
  });

  return { member: data, error };
}

export async function updateMemberRole(memberId: string, role: OrgMemberRole) {
  const { data, error } = await supabase.rpc('update_member_role', {
    p_member_id: memberId,
    p_role: role,
  });

  return { member: data, error };
}

export async function removeMember(memberId: string) {
  const { error } = await supabase.rpc('remove_organization_member', {
    p_member_id: memberId,
  });

  return { error };
}
