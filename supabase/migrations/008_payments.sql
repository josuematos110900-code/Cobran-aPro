-- 008_payments.sql

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  invoice_id uuid not null references public.invoices (id) on delete cascade,
  client_id uuid not null references public.clients (id) on delete cascade,
  amount numeric(14, 2) not null check (amount >= 0),
  currency text not null default 'AOA',
  payment_method text not null default 'other' check (payment_method in ('cash', 'bank_transfer', 'mobile_money', 'card', 'other')),
  transaction_reference text,
  paid_at timestamptz not null default now(),
  status text not null default 'completed' check (status in ('completed', 'refunded', 'failed')),
  created_at timestamptz not null default now()
);

alter table public.payments enable row level security;

create index if not exists idx_payments_org_id on public.payments (organization_id);
create index if not exists idx_payments_invoice_id on public.payments (invoice_id);
create index if not exists idx_payments_client_id on public.payments (client_id);
create index if not exists idx_payments_org_paid_at on public.payments (organization_id, paid_at);

create policy "payments_select_member"
  on public.payments for select
  using (organization_id in (select public.user_organization_ids()));

create policy "payments_insert_member"
  on public.payments for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "payments_update_member"
  on public.payments for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "payments_delete_member"
  on public.payments for delete
  using (organization_id in (select public.user_organization_ids()));

-- Ao registar um pagamento "completed" que cobre o valor total da
-- invoice, marca automaticamente a invoice como paga.
create or replace function public.handle_payment_marks_invoice_paid()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_invoice_amount numeric(14, 2);
  v_total_paid numeric(14, 2);
begin
  if new.status = 'completed' then
    select amount into v_invoice_amount from public.invoices where id = new.invoice_id;

    select coalesce(sum(amount), 0) into v_total_paid
    from public.payments
    where invoice_id = new.invoice_id and status = 'completed';

    if v_total_paid >= v_invoice_amount then
      update public.invoices
      set status = 'paid', paid_at = coalesce(paid_at, now())
      where id = new.invoice_id;
    end if;
  end if;
  return new;
end;
$$;

create trigger trg_payments_mark_invoice_paid
  after insert on public.payments
  for each row execute function public.handle_payment_marks_invoice_paid();
