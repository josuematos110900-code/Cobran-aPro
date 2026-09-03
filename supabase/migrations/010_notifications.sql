-- 010_notifications.sql
-- Notificações internas da aplicação (sino no topo do dashboard), distintas
-- dos "reminders" que são mensagens enviadas a clientes.

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  title text not null,
  body text not null,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

alter table public.notifications enable row level security;

create index if not exists idx_notifications_org_id on public.notifications (organization_id);
create index if not exists idx_notifications_org_unread on public.notifications (organization_id, read_at);

create policy "notifications_select_member"
  on public.notifications for select
  using (organization_id in (select public.user_organization_ids()));

create policy "notifications_insert_member"
  on public.notifications for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "notifications_update_member"
  on public.notifications for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "notifications_delete_member"
  on public.notifications for delete
  using (organization_id in (select public.user_organization_ids()));
