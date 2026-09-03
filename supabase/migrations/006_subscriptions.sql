-- 006_subscriptions.sql
-- Representa uma cobrança recorrente configurada para um cliente. A
-- geração das invoices futuras a partir daqui é feita por um job
-- backend/cron (ver README, secção "Cobranças recorrentes"), nunca pelo
-- browser do utilizador.

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  client_id uuid not null references public.clients (id) on delete cascade,
  service_id uuid not null references public.services (id) on delete restrict,
  amount numeric(14, 2) not null check (amount >= 0),
  currency text not null default 'AOA',
  billing_period text not null default 'monthly' check (billing_period in ('weekly', 'monthly', 'quarterly', 'yearly')),
  due_day smallint not null check (due_day between 1 and 31),
  start_date date not null default current_date,
  end_date date,
  status text not null default 'active' check (status in ('active', 'paused', 'cancelled', 'ended')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint chk_subscription_dates check (end_date is null or end_date >= start_date)
);

alter table public.subscriptions enable row level security;

create index if not exists idx_subscriptions_org_id on public.subscriptions (organization_id);
create index if not exists idx_subscriptions_client_id on public.subscriptions (client_id);
create index if not exists idx_subscriptions_org_status on public.subscriptions (organization_id, status);

create trigger trg_subscriptions_updated_at
  before update on public.subscriptions
  for each row execute function public.set_updated_at();

create policy "subscriptions_select_member"
  on public.subscriptions for select
  using (organization_id in (select public.user_organization_ids()));

create policy "subscriptions_insert_member"
  on public.subscriptions for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "subscriptions_update_member"
  on public.subscriptions for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "subscriptions_delete_member"
  on public.subscriptions for delete
  using (organization_id in (select public.user_organization_ids()));
