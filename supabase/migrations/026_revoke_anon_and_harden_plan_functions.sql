-- 026_revoke_anon_and_harden_plan_functions.sql
-- get_effective_plan() e get_organization_usage() não validavam que o
-- chamador pertence à organização pedida (só get_plan_status() o fazia).
-- Um utilizador autenticado de qualquer organização podia, por chamada
-- directa à RPC, ver o plano e a utilização (nº de clientes, membros,
-- cobranças do mês) de QUALQUER outra organização. Corrigido aqui.
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
  if p_organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não tem permissão para consultar esta organização.' using errcode = '42501';
  end if;

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

create or replace function public.get_organization_usage(p_organization_id uuid)
returns table (
  clients_count integer,
  services_count integer,
  members_count integer,
  invoices_this_month integer
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não tem permissão para consultar esta organização.' using errcode = '42501';
  end if;

  return query
  select
    (select count(*)::integer from public.clients where organization_id = p_organization_id and status <> 'archived'),
    (select count(*)::integer from public.services where organization_id = p_organization_id),
    (select count(*)::integer from public.organization_members where organization_id = p_organization_id),
    (select count(*)::integer from public.invoices
       where organization_id = p_organization_id
         and date_trunc('month', created_at) = date_trunc('month', now()));
end;
$$;

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

-- next_invoice_number(): a relaxação para service_role ("auth.uid() is
-- not null and ...") também deixava passar qualquer chamada com
-- auth.uid() nulo — incluindo "anon" sem sessão nenhuma. Verifica-se
-- agora explicitamente o papel de service_role.
create or replace function public.next_invoice_number(p_organization_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_next integer;
begin
  if auth.role() <> 'service_role' and p_organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não pertence a esta organização.' using errcode = '42501';
  end if;

  insert into public.invoice_number_counters (organization_id, last_number)
  values (p_organization_id, 1)
  on conflict (organization_id)
  do update set last_number = public.invoice_number_counters.last_number + 1
  returning last_number into v_next;

  return 'FAT-' || lpad(v_next::text, 6, '0');
end;
$$;

revoke execute on function public.next_invoice_number(uuid) from public, anon;
grant execute on function public.next_invoice_number(uuid) to authenticated, service_role;
