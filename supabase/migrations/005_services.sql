-- 005_services.sql

create table if not exists public.services (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  name text not null,
  description text,
  price numeric(14, 2) not null check (price >= 0),
  currency text not null default 'AOA',
  billing_period text not null default 'monthly' check (billing_period in ('weekly', 'monthly', 'quarterly', 'yearly')),
  status text not null default 'active' check (status in ('active', 'inactive')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.services enable row level security;

create index if not exists idx_services_org_id on public.services (organization_id);
create index if not exists idx_services_org_status on public.services (organization_id, status);

create trigger trg_services_updated_at
  before update on public.services
  for each row execute function public.set_updated_at();

create policy "services_select_member"
  on public.services for select
  using (organization_id in (select public.user_organization_ids()));

create policy "services_insert_member"
  on public.services for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "services_update_member"
  on public.services for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "services_delete_member"
  on public.services for delete
  using (organization_id in (select public.user_organization_ids()));
