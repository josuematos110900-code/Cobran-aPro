-- 029_create_organization_with_owner.sql
-- Corrige um bug real do onboarding, descoberto ao testar a aplicação
-- num projecto Supabase real: o frontend fazia dois inserts separados
-- (organizations, depois organization_members) e pedia a linha de volta
-- com .select() logo a seguir ao primeiro insert. Como a política de
-- leitura de organizations exige já ser membro, e o membro só é criado
-- no segundo insert, o RETURNING do primeiro insert violava RLS
-- ("new row violates row-level security policy for table organizations",
-- 42501) — a organização nunca chegava a ser criada. Mesmo sem esse
-- RETURNING, havia risco real de ficar uma organização "órfã" sem
-- nenhum membro se o segundo insert falhasse a meio.
--
-- Esta função faz os dois inserts numa única transacção atómica, como
-- security definer (ignora RLS internamente, mas valida auth.uid()
-- explicitamente) — nunca pode ficar uma organização sem responsável.
create or replace function public.create_organization_with_owner(
  p_name text,
  p_business_type text default null,
  p_currency text default 'AOA'
)
returns public.organizations
language plpgsql
security definer
set search_path = public
as $$
declare
  v_org public.organizations%rowtype;
begin
  if auth.uid() is null then
    raise exception 'Sessão expirada. Inicie sessão novamente.' using errcode = '42501';
  end if;

  if p_name is null or trim(p_name) = '' then
    raise exception 'O nome da empresa é obrigatório.' using errcode = 'P0001';
  end if;

  insert into public.organizations (name, business_type, currency)
  values (trim(p_name), nullif(trim(coalesce(p_business_type, '')), ''), coalesce(nullif(trim(p_currency), ''), 'AOA'))
  returning * into v_org;

  insert into public.organization_members (organization_id, user_id, role)
  values (v_org.id, auth.uid(), 'owner');

  return v_org;
end;
$$;

revoke execute on function public.create_organization_with_owner(text, text, text) from public, anon;
grant execute on function public.create_organization_with_owner(text, text, text) to authenticated;
