-- 004_clients.sql

create table if not exists public.clients (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  name text not null,
  phone text,
  email text,
  address text,
  notes text,
  status text not null default 'active' check (status in ('active', 'inactive', 'archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.clients enable row level security;

create index if not exists idx_clients_org_id on public.clients (organization_id);
create index if not exists idx_clients_org_status on public.clients (organization_id, status);
create index if not exists idx_clients_name_trgm on public.clients using btree (organization_id, name);

create trigger trg_clients_updated_at
  before update on public.clients
  for each row execute function public.set_updated_at();

create policy "clients_select_member"
  on public.clients for select
  using (organization_id in (select public.user_organization_ids()));

create policy "clients_insert_member"
  on public.clients for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "clients_update_member"
  on public.clients for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "clients_delete_member"
  on public.clients for delete
  using (organization_id in (select public.user_organization_ids()));
