import type { OrgMemberRole } from '@/lib/types/database';

export interface CompanyFormValues {
  name: string;
  business_type: string;
  currency: string;
  phone: string;
  email: string;
  address: string;
}

export interface WhatsAppSettingsFormValues {
  reminder_days_before: string;
  whatsapp_template: string;
}

export const MEMBER_ROLE_LABELS: Record<OrgMemberRole, string> = {
  owner: 'Responsável',
  admin: 'Administrador',
  staff: 'Colaborador',
};

export const CURRENCIES = [
  { value: 'AOA', label: 'Kwanza (AOA)' },
  { value: 'USD', label: 'Dólar (USD)' },
  { value: 'EUR', label: 'Euro (EUR)' },
];

export const BUSINESS_TYPES = [
  'Escola / Explicador',
  'Ginásio',
  'Barbearia / Salão',
  'Prestador de serviços',
  'Pequena empresa',
  'Clube / Associação',
  'Outro',
];
