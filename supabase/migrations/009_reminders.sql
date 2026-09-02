-- 009_reminders.sql

create table if not exists public.reminders (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  client_id uuid not null references public.clients (id) on delete cascade,
  invoice_id uuid references public.invoices (id) on delete cascade,
  channel text not null default 'whatsapp' check (channel in ('whatsapp', 'sms', 'email', 'internal')),
  message text not null,
  scheduled_at timestamptz not null default now(),
  sent_at timestamptz,
  status text not null default 'scheduled' check (status in ('scheduled', 'sent', 'failed', 'cancelled')),
  created_at timestamptz not null default now()
);

alter table public.reminders enable row level security;

create index if not exists idx_reminders_org_id on public.reminders (organization_id);
create index if not exists idx_reminders_client_id on public.reminders (client_id);
create index if not exists idx_reminders_org_status on public.reminders (organization_id, status);

create policy "reminders_select_member"
  on public.reminders for select
  using (organization_id in (select public.user_organization_ids()));

create policy "reminders_insert_member"
  on public.reminders for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "reminders_update_member"
  on public.reminders for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "reminders_delete_member"
  on public.reminders for delete
  using (organization_id in (select public.user_organization_ids()));
