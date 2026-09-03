// Tipos que espelham o schema criado nas migrations SQL
// (supabase/migrations). Mantenha isto sincronizado manualmente por agora;
// mais tarde pode gerar automaticamente com `supabase gen types typescript`.

export type ClientStatus = 'active' | 'inactive' | 'archived';
export type ServiceStatus = 'active' | 'inactive';
export type BillingPeriod = 'weekly' | 'monthly' | 'quarterly' | 'yearly';
export type SubscriptionStatus = 'active' | 'paused' | 'cancelled' | 'ended';
export type InvoiceStatus = 'pending' | 'paid' | 'overdue' | 'cancelled';
export type PaymentMethod =
  | 'cash'
  | 'bank_transfer'
  | 'mobile_money'
  | 'card'
  | 'multicaixa'
  | 'multicaixa_express'
  | 'other';
export type PaymentStatus = 'completed' | 'refunded' | 'failed';
export type ReminderChannel = 'whatsapp' | 'sms' | 'email' | 'internal';
export type ReminderStatus = 'scheduled' | 'sent' | 'failed' | 'cancelled';
export type OrgMemberRole = 'owner' | 'admin' | 'staff';
export type PlanTier = 'free' | 'basico' | 'profissional' | 'empresa';
export type SubscriptionStatusTier = 'trialing' | 'active' | 'past_due' | 'cancelled' | 'free';
export type BillingCycle = 'monthly' | 'yearly';

export interface Profile {
  id: string;
  full_name: string | null;
  phone: string | null;
  avatar_url: string | null;
  created_at: string;
  updated_at: string;
}

export interface Organization {
  id: string;
  name: string;
  business_type: string | null;
  currency: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  plan: PlanTier;
  subscription_status: SubscriptionStatusTier;
  trial_start: string | null;
  trial_end: string | null;
  billing_period: BillingCycle;
  started_at: string | null;
  expires_at: string | null;
  cancelled_at: string | null;
  provider: string | null;
  external_reference: string | null;
  created_at: string;
  updated_at: string;
}

export interface PlanLimits {
  max_clients: number | null;
  max_services: number | null;
  max_members: number | null;
  max_invoices_per_month: number | null;
  recurring_enabled: boolean;
  reports_enabled: boolean;
  advanced_features: boolean;
}

export interface PlanUsage {
  clients_count: number;
  services_count: number;
  members_count: number;
  invoices_this_month: number;
}

export interface PlanStatus {
  plan: PlanTier;
  effective_plan: PlanTier;
  subscription_status: SubscriptionStatusTier;
  billing_period: BillingCycle;
  trial_start: string | null;
  trial_end: string | null;
  trial_days_remaining: number;
  is_trial_active: boolean;
  is_trial_expired: boolean;
  started_at: string | null;
  expires_at: string | null;
  cancelled_at: string | null;
  provider: string | null;
  external_reference: string | null;
  limits: PlanLimits;
  usage: PlanUsage;
}

export type PurchaseIntentStatus = 'pending' | 'confirmed' | 'rejected';

export interface PurchaseIntent {
  id: string;
  organization_id: string;
  requested_plan: PlanTier;
  billing_period: BillingCycle;
  provider: string | null;
  external_reference: string | null;
  note: string | null;
  status: PurchaseIntentStatus;
  requested_by: string;
  created_at: string;
  confirmed_at: string | null;
}

export interface OrganizationMemberWithProfile {
  member_id: string;
  user_id: string;
  role: OrgMemberRole;
  full_name: string | null;
  email: string | null;
  is_you: boolean;
  created_at: string;
}

export interface OrganizationMember {
  id: string;
  organization_id: string;
  user_id: string;
  role: OrgMemberRole;
  created_at: string;
}

export interface Client {
  id: string;
  organization_id: string;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  notes: string | null;
  status: ClientStatus;
  created_at: string;
  updated_at: string;
}

export interface Service {
  id: string;
  organization_id: string;
  name: string;
  description: string | null;
  price: number;
  currency: string;
  billing_period: BillingPeriod;
  status: ServiceStatus;
  created_at: string;
  updated_at: string;
}

export interface Subscription {
  id: string;
  organization_id: string;
  client_id: string;
  service_id: string;
  amount: number;
  currency: string;
  billing_period: BillingPeriod;
  due_day: number;
  start_date: string;
  end_date: string | null;
  status: SubscriptionStatus;
  next_billing_date: string | null;
  last_billing_date: string | null;
  created_at: string;
  updated_at: string;
}

export interface Invoice {
  id: string;
  organization_id: string;
  client_id: string;
  subscription_id: string | null;
  invoice_number: string;
  description: string | null;
  amount: number;
  currency: string;
  due_date: string;
  status: InvoiceStatus;
  paid_at: string | null;
  reference: string | null;
  created_at: string;
  updated_at: string;
}

export interface Payment {
  id: string;
  organization_id: string;
  invoice_id: string;
  client_id: string;
  amount: number;
  currency: string;
  payment_method: PaymentMethod;
  transaction_reference: string | null;
  paid_at: string;
  status: PaymentStatus;
  note: string | null;
  created_at: string;
}

export interface Reminder {
  id: string;
  organization_id: string;
  client_id: string;
  invoice_id: string | null;
  channel: ReminderChannel;
  message: string;
  scheduled_at: string;
  sent_at: string | null;
  status: ReminderStatus;
  created_at: string;
}

export interface Notification {
  id: string;
  organization_id: string;
  title: string;
  body: string;
  read_at: string | null;
  created_at: string;
}

export interface Settings {
  organization_id: string;
  default_currency: string;
  reminder_days_before: number;
  whatsapp_template: string;
  updated_at: string;
}

// Placeholder mínimo para o generic do supabase-js. Uma vez geradas as
// tabelas reais via CLI (`supabase gen types typescript --local`), este
// tipo pode ser substituído pelo tipo `Database` completo e fortemente
// tipado linha a linha.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Database = any;
