-- 011_settings.sql
-- Uma única linha de configuração por organização.

create table if not exists public.settings (
  organization_id uuid primary key references public.organizations (id) on delete cascade,
  default_currency text not null default 'AOA',
  reminder_days_before smallint not null default 1 check (reminder_days_before >= 0),
  whatsapp_template text not null default
    E'Olá, {{nome}}!\n\nA sua cobrança de {{valor}} referente a {{descricao}} vence em {{data}}.\n\nPara mais informações, entre em contacto connosco.\n\nObrigado!',
  updated_at timestamptz not null default now()
);

alter table public.settings enable row level security;

create trigger trg_settings_updated_at
  before update on public.settings
  for each row execute function public.set_updated_at();

create policy "settings_select_member"
  on public.settings for select
  using (organization_id in (select public.user_organization_ids()));

create policy "settings_insert_member"
  on public.settings for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "settings_update_member"
  on public.settings for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

-- Cria automaticamente uma linha de settings quando uma organização é criada.
create or replace function public.handle_new_organization_settings()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.settings (organization_id, default_currency)
  values (new.id, new.currency);
  return new;
end;
$$;

create trigger trg_on_organization_created_settings
  after insert on public.organizations
  for each row execute function public.handle_new_organization_settings();
