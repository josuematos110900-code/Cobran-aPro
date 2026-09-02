-- 007_invoices.sql

create table if not exists public.invoices (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  client_id uuid not null references public.clients (id) on delete cascade,
  subscription_id uuid references public.subscriptions (id) on delete set null,
  invoice_number text not null,
  description text,
  amount numeric(14, 2) not null check (amount >= 0),
  currency text not null default 'AOA',
  due_date date not null,
  status text not null default 'pending' check (status in ('pending', 'paid', 'overdue', 'cancelled')),
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, invoice_number)
);

alter table public.invoices enable row level security;

create index if not exists idx_invoices_org_id on public.invoices (organization_id);
create index if not exists idx_invoices_client_id on public.invoices (client_id);
create index if not exists idx_invoices_org_status on public.invoices (organization_id, status);
create index if not exists idx_invoices_org_due_date on public.invoices (organization_id, due_date);

create trigger trg_invoices_updated_at
  before update on public.invoices
  for each row execute function public.set_updated_at();

create policy "invoices_select_member"
  on public.invoices for select
  using (organization_id in (select public.user_organization_ids()));

create policy "invoices_insert_member"
  on public.invoices for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "invoices_update_member"
  on public.invoices for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "invoices_delete_member"
  on public.invoices for delete
  using (organization_id in (select public.user_organization_ids()));

-- Função auxiliar para gerar o próximo número de factura sequencial por
-- organização, no formato FAT-000001. Chamada pela aplicação (ou por um
-- trigger, se preferido) ao criar uma invoice sem invoice_number definido.
create or replace function public.next_invoice_number(p_organization_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer;
begin
  select count(*) into v_count
  from public.invoices
  where organization_id = p_organization_id;

  return 'FAT-' || lpad((v_count + 1)::text, 6, '0');
end;
$$;
