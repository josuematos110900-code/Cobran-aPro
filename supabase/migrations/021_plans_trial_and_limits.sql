-- 021_plans_trial_and_limits.sql
-- Fase 3/4/5 do plano de produção: planos comerciais, trial gratuito e
-- assinatura, com limites aplicados no servidor (nunca só no frontend).
--
-- Um utilizador do frontend nunca deve conseguir contornar um limite de
-- plano através de uma chamada directa ao Supabase (insert/update na
-- tabela) — por isso os limites são impostos por triggers "before insert"
-- nas próprias tabelas, não apenas por uma verificação na aplicação.

-- ---------------------------------------------------------------------
-- 1. Campos de trial e assinatura em organizations
-- ---------------------------------------------------------------------

alter table public.organizations
  add column if not exists subscription_status text not null default 'trialing'
    check (subscription_status in ('trialing', 'active', 'past_due', 'cancelled', 'free')),
  add column if not exists trial_start timestamptz,
  add column if not exists trial_end timestamptz,
  add column if not exists billing_period text not null default 'monthly'
    check (billing_period in ('monthly', 'yearly')),
  add column if not exists started_at timestamptz,
  add column if not exists expires_at timestamptz,
  add column if not exists cancelled_at timestamptz,
  add column if not exists provider text,
  add column if not exists external_reference text;

-- Valor único e central da duração do trial gratuito. Qualquer alteração
-- à duração do trial faz-se apenas aqui.
create or replace function public.trial_duration_days()
returns integer
language sql
immutable
as $$
  select 14;
$$;

-- Define o trial automaticamente em toda a organização nova, sem depender
-- do frontend enviar as datas correctas.
create or replace function public.handle_new_organization_trial()
returns trigger
language plpgsql
as $$
begin
  if new.trial_start is null then
    new.trial_start := now();
  end if;
  if new.trial_end is null then
    new.trial_end := new.trial_start + (public.trial_duration_days() || ' days')::interval;
  end if;
  if new.subscription_status is null then
    new.subscription_status := 'trialing';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_organizations_trial_defaults on public.organizations;
create trigger trg_organizations_trial_defaults
  before insert on public.organizations
  for each row execute function public.handle_new_organization_trial();

-- ---------------------------------------------------------------------
-- 2. Limites de cada plano — fonte única da verdade, usada tanto pelas
--    triggers de aplicação como pela RPC que a UI consulta para mostrar
--    "X de Y usados". null = sem limite (plano Empresa).
-- ---------------------------------------------------------------------

create or replace function public.plan_limits(p_plan text)
returns table (
  max_clients integer,
  max_services integer,
  max_members integer,
  max_invoices_per_month integer,
  recurring_enabled boolean,
  reports_enabled boolean,
  advanced_features boolean
)
language sql
immutable
as $$
  select
    case p_plan
      when 'free' then 5
      when 'basico' then 30
      when 'profissional' then 150
      when 'empresa' then null
      else 5
    end as max_clients,
    case p_plan
      when 'free' then 3
      when 'basico' then 15
      when 'profissional' then 50
      when 'empresa' then null
      else 3
    end as max_services,
    case p_plan
      when 'free' then 1
      when 'basico' then 3
      when 'profissional' then 8
      when 'empresa' then null
      else 1
    end as max_members,
    case p_plan
      when 'free' then 10
      when 'basico' then 60
      when 'profissional' then 300
      when 'empresa' then null
      else 10
    end as max_invoices_per_month,
    (p_plan in ('basico', 'profissional', 'empresa')) as recurring_enabled,
    (p_plan in ('basico', 'profissional', 'empresa')) as reports_enabled,
    (p_plan in ('profissional', 'empresa')) as advanced_features;
$$;

-- Plano "efectivo" de uma organização: durante o trial (ainda válido),
-- a organização usufrui dos limites do plano Profissional mesmo que o
-- plano contratado (organizations.plan) continue "free". Findo o trial,
-- volta a valer o plano realmente contratado.
create or replace function public.get_effective_plan(p_organization_id uuid)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_org public.organizations%rowtype;
begin
  select * into v_org from public.organizations where id = p_organization_id;
  if not found then
    raise exception 'Organização não encontrada.' using errcode = 'P0002';
  end if;

  if v_org.subscription_status = 'trialing' and v_org.trial_end is not null and v_org.trial_end > now() then
    return 'profissional';
  end if;

  return v_org.plan;
end;
$$;

-- ---------------------------------------------------------------------
-- 3. Utilização actual da organização — usada pela RPC de estado do
--    plano e pelas triggers de aplicação de limite.
-- ---------------------------------------------------------------------

create or replace function public.get_organization_usage(p_organization_id uuid)
returns table (
  clients_count integer,
  services_count integer,
  members_count integer,
  invoices_this_month integer
)
language sql
stable
security definer
set search_path = public
as $$
  select
    (select count(*)::integer from public.clients where organization_id = p_organization_id and status <> 'archived'),
    (select count(*)::integer from public.services where organization_id = p_organization_id),
    (select count(*)::integer from public.organization_members where organization_id = p_organization_id),
    (select count(*)::integer from public.invoices
       where organization_id = p_organization_id
         and date_trunc('month', created_at) = date_trunc('month', now()));
$$;

-- ---------------------------------------------------------------------
-- 4. Verificação genérica de limite — chamada pelas triggers "before
--    insert" das tabelas relevantes. Lança sempre uma mensagem amigável
--    em português, segura para mostrar directamente ao utilizador.
-- ---------------------------------------------------------------------

create or replace function public.enforce_plan_limit(p_organization_id uuid, p_resource text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_plan text;
  v_limits record;
  v_usage record;
  v_current integer;
  v_max integer;
  v_label text;
begin
  v_plan := public.get_effective_plan(p_organization_id);
  select * into v_limits from public.plan_limits(v_plan);
  select * into v_usage from public.get_organization_usage(p_organization_id);

  case p_resource
    when 'clients' then
      v_current := v_usage.clients_count; v_max := v_limits.max_clients; v_label := 'clientes';
    when 'services' then
      v_current := v_usage.services_count; v_max := v_limits.max_services; v_label := 'serviços';
    when 'members' then
      v_current := v_usage.members_count; v_max := v_limits.max_members; v_label := 'membros da equipa';
    when 'invoices' then
      v_current := v_usage.invoices_this_month; v_max := v_limits.max_invoices_per_month; v_label := 'cobranças este mês';
    else
      return;
  end case;

  if v_max is not null and v_current >= v_max then
    raise exception
      'Limite do seu plano atingido: % de % % permitidos. Actualize o seu plano em /billing para continuar.',
      v_current, v_max, v_label
      using errcode = 'P0001', hint = 'plan_limit_reached';
  end if;
end;
$$;

create or replace function public.trg_enforce_clients_limit()
returns trigger
language plpgsql
as $$
begin
  perform public.enforce_plan_limit(new.organization_id, 'clients');
  return new;
end;
$$;

drop trigger if exists trg_clients_plan_limit on public.clients;
create trigger trg_clients_plan_limit
  before insert on public.clients
  for each row execute function public.trg_enforce_clients_limit();

create or replace function public.trg_enforce_services_limit()
returns trigger
language plpgsql
as $$
begin
  perform public.enforce_plan_limit(new.organization_id, 'services');
  return new;
end;
$$;

drop trigger if exists trg_services_plan_limit on public.services;
create trigger trg_services_plan_limit
  before insert on public.services
  for each row execute function public.trg_enforce_services_limit();

create or replace function public.trg_enforce_invoices_limit()
returns trigger
language plpgsql
as $$
begin
  perform public.enforce_plan_limit(new.organization_id, 'invoices');
  return new;
end;
$$;

drop trigger if exists trg_invoices_plan_limit on public.invoices;
create trigger trg_invoices_plan_limit
  before insert on public.invoices
  for each row execute function public.trg_enforce_invoices_limit();

-- organization_members: o primeiro membro (owner, criado no onboarding)
-- nunca deve ser bloqueado por este limite — só se aplica a partir do
-- segundo membro.
create or replace function public.trg_enforce_members_limit()
returns trigger
language plpgsql
as $$
begin
  if exists (select 1 from public.organization_members where organization_id = new.organization_id) then
    perform public.enforce_plan_limit(new.organization_id, 'members');
  end if;
  return new;
end;
$$;

drop trigger if exists trg_members_plan_limit on public.organization_members;
create trigger trg_members_plan_limit
  before insert on public.organization_members
  for each row execute function public.trg_enforce_members_limit();

-- subscriptions (cobranças recorrentes): o plano Free não inclui
-- recorrências. Este bloqueio é independente do limite numérico acima.
create or replace function public.trg_enforce_recurring_enabled()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_plan text;
  v_limits record;
begin
  v_plan := public.get_effective_plan(new.organization_id);
  select * into v_limits from public.plan_limits(v_plan);

  if not v_limits.recurring_enabled then
    raise exception
      'O seu plano actual não inclui cobranças recorrentes. Actualize o seu plano em /billing.'
      using errcode = 'P0001', hint = 'plan_feature_locked';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_subscriptions_plan_feature on public.subscriptions;
create trigger trg_subscriptions_plan_feature
  before insert on public.subscriptions
  for each row execute function public.trg_enforce_recurring_enabled();

-- ---------------------------------------------------------------------
-- 5. RPC única de estado do plano — usada pelo Dashboard, /billing e
--    /settings para mostrar plano actual, trial e utilização.
-- ---------------------------------------------------------------------

create or replace function public.get_plan_status(p_organization_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_org public.organizations%rowtype;
  v_effective_plan text;
  v_limits record;
  v_usage record;
  v_trial_days_remaining integer;
begin
  select * into v_org from public.organizations where id = p_organization_id;
  if not found or v_org.id not in (select public.user_organization_ids()) then
    raise exception 'Não tem permissão para consultar esta organização.' using errcode = '42501';
  end if;

  v_effective_plan := public.get_effective_plan(p_organization_id);
  select * into v_limits from public.plan_limits(v_effective_plan);
  select * into v_usage from public.get_organization_usage(p_organization_id);

  v_trial_days_remaining := case
    when v_org.subscription_status = 'trialing' and v_org.trial_end is not null
      then greatest(0, ceil(extract(epoch from (v_org.trial_end - now())) / 86400.0)::integer)
    else 0
  end;

  return jsonb_build_object(
    'plan', v_org.plan,
    'effective_plan', v_effective_plan,
    'subscription_status', v_org.subscription_status,
    'billing_period', v_org.billing_period,
    'trial_start', v_org.trial_start,
    'trial_end', v_org.trial_end,
    'trial_days_remaining', v_trial_days_remaining,
    'is_trial_active', v_org.subscription_status = 'trialing' and v_org.trial_end > now(),
    'is_trial_expired', v_org.subscription_status = 'trialing' and v_org.trial_end <= now(),
    'started_at', v_org.started_at,
    'expires_at', v_org.expires_at,
    'cancelled_at', v_org.cancelled_at,
    'provider', v_org.provider,
    'external_reference', v_org.external_reference,
    'limits', jsonb_build_object(
      'max_clients', v_limits.max_clients,
      'max_services', v_limits.max_services,
      'max_members', v_limits.max_members,
      'max_invoices_per_month', v_limits.max_invoices_per_month,
      'recurring_enabled', v_limits.recurring_enabled,
      'reports_enabled', v_limits.reports_enabled,
      'advanced_features', v_limits.advanced_features
    ),
    'usage', jsonb_build_object(
      'clients_count', v_usage.clients_count,
      'services_count', v_usage.services_count,
      'members_count', v_usage.members_count,
      'invoices_this_month', v_usage.invoices_this_month
    )
  );
end;
$$;

revoke execute on function public.get_plan_status(uuid) from public;
grant execute on function public.get_plan_status(uuid) to authenticated;

revoke execute on function public.get_effective_plan(uuid) from public;
grant execute on function public.get_effective_plan(uuid) to authenticated;

revoke execute on function public.get_organization_usage(uuid) from public;
grant execute on function public.get_organization_usage(uuid) to authenticated;

-- plan_limits() não expõe dados de nenhuma organização — pode ficar
-- pública para qualquer utilizador autenticado consultar a grelha de
-- planos (usada na página /billing antes mesmo de ter organização).
revoke execute on function public.plan_limits(text) from public;
grant execute on function public.plan_limits(text) to authenticated, anon;

-- ---------------------------------------------------------------------
-- 6. Intenção de compra / upgrade — Fase 5 (assinatura). Sem gateway de
--    pagamento com API/webhook disponível em Angola neste momento, o
--    fluxo é: o utilizador pede upgrade (regista a intenção aqui) e o
--    pagamento é confirmado manualmente por quem gere o SaaS, que depois
--    actualiza organizations.plan/subscription_status via SQL Editor ou
--    com a service_role. A arquitectura fica pronta para automatizar
--    isto mais tarde (basta um webhook a fazer o mesmo update).
-- ---------------------------------------------------------------------

create table if not exists public.purchase_intents (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  requested_plan text not null check (requested_plan in ('basico', 'profissional', 'empresa')),
  billing_period text not null default 'monthly' check (billing_period in ('monthly', 'yearly')),
  provider text,
  external_reference text,
  note text,
  status text not null default 'pending' check (status in ('pending', 'confirmed', 'rejected')),
  requested_by uuid not null references auth.users (id),
  created_at timestamptz not null default now(),
  confirmed_at timestamptz
);

alter table public.purchase_intents enable row level security;

create index if not exists idx_purchase_intents_org_id on public.purchase_intents (organization_id);

create policy "purchase_intents_select_member"
  on public.purchase_intents for select
  using (organization_id in (select public.user_organization_ids()));

-- A activação em si (status -> 'confirmed' + update de organizations)
-- nunca é feita pelo frontend: não existe política de UPDATE/DELETE
-- para "authenticated" nesta tabela, apenas a de leitura acima e a
-- função abaixo (que só insere linhas "pending").
create or replace function public.request_plan_upgrade(
  p_organization_id uuid,
  p_requested_plan text,
  p_billing_period text default 'monthly',
  p_note text default null
)
returns public.purchase_intents
language plpgsql
security definer
set search_path = public
as $$
declare
  v_intent public.purchase_intents%rowtype;
begin
  if p_organization_id not in (select public.user_admin_organization_ids()) then
    raise exception 'Só o dono ou administrador da organização pode pedir uma alteração de plano.'
      using errcode = '42501';
  end if;

  insert into public.purchase_intents (organization_id, requested_plan, billing_period, note, requested_by)
  values (p_organization_id, p_requested_plan, p_billing_period, p_note, auth.uid())
  returning * into v_intent;

  return v_intent;
end;
$$;

revoke execute on function public.request_plan_upgrade(uuid, text, text, text) from public;
grant execute on function public.request_plan_upgrade(uuid, text, text, text) to authenticated;
