-- 032_service_role_bypass_plan_checks.sql
--
-- Segundo bug encontrado ao testar a correcção do cron (migration 031):
-- depois de resolver a autenticação, a chamada a generate_recurring_invoices()
-- continuava a falhar, agora com "Não tem permissão para consultar esta
-- organização." — vindo de get_effective_plan() e get_organization_usage(),
-- chamadas por enforce_plan_limit() sempre que uma invoice é inserida.
--
-- Estas duas funções verificam sempre
-- "p_organization_id not in (select user_organization_ids())", que depende
-- de auth.uid() — no contexto do cron (chamado pela Edge Function com a
-- service_role key, sem utilizador autenticado) isto é sempre null, logo
-- user_organization_ids() devolve vazio e a verificação falha sempre.
--
-- next_invoice_number() já tinha sido corrigida com uma excepção para
-- auth.role() = 'service_role' (migration 026). Aplica-se aqui o mesmo
-- padrão às outras duas funções que faltavam.

create or replace function public.get_effective_plan(p_organization_id uuid)
returns text
language plpgsql
stable security definer
set search_path to 'public'
as $function$
declare
  v_org public.organizations%rowtype;
begin
  if auth.role() <> 'service_role' and p_organization_id not in (select public.user_organization_ids()) then
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
$function$;

create or replace function public.get_organization_usage(p_organization_id uuid)
returns table(clients_count integer, services_count integer, members_count integer, invoices_this_month integer)
language plpgsql
stable security definer
set search_path to 'public'
as $function$
begin
  if auth.role() <> 'service_role' and p_organization_id not in (select public.user_organization_ids()) then
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
$function$;
