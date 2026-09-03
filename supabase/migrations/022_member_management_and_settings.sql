-- 022_member_management_and_settings.sql
-- Fase 2 (Definições) + Fase 12/13 (RLS, papéis owner/admin/staff).
--
-- Corrige uma falha real de RLS: as políticas de organization_members
-- (migration 003) permitem a qualquer admin fazer UPDATE/INSERT com
-- qualquer valor de "role", incluindo 'owner' — ou seja, um admin podia
-- promover-se a si próprio (ou a outra pessoa) a owner por escrita
-- directa na tabela, e nada impedia alguém de alterar o seu próprio
-- papel. Fecha-se isto com um trigger, que se aplica quer a escrita
-- venha do frontend directamente quer de uma função futura.

create or replace function public.enforce_member_role_change()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' then
    if new.role = 'owner' and exists (
      select 1 from public.organization_members where organization_id = new.organization_id
    ) then
      raise exception 'Já existe um responsável (owner) nesta organização.' using errcode = '42501';
    end if;
    return new;
  end if;

  if tg_op = 'UPDATE' then
    if old.role = 'owner' then
      raise exception 'O papel do responsável (owner) não pode ser alterado.' using errcode = '42501';
    end if;
    if new.role = 'owner' and new.role is distinct from old.role then
      raise exception 'Não é possível promover um membro a responsável (owner) desta forma.' using errcode = '42501';
    end if;
    if new.user_id = auth.uid() and new.role is distinct from old.role then
      raise exception 'Não pode alterar o seu próprio papel.' using errcode = '42501';
    end if;
    return new;
  end if;

  if tg_op = 'DELETE' then
    if old.role = 'owner' then
      raise exception 'O responsável (owner) não pode ser removido da organização.' using errcode = '42501';
    end if;
    if old.user_id = auth.uid() and old.role <> 'owner' then
      -- Um membro (admin/staff) pode remover-se a si próprio (sair da
      -- organização); só o próprio owner nunca pode ser removido.
      return old;
    end if;
    return old;
  end if;

  return coalesce(new, old);
end;
$$;

drop trigger if exists trg_org_members_role_guard on public.organization_members;
create trigger trg_org_members_role_guard
  before insert or update or delete on public.organization_members
  for each row execute function public.enforce_member_role_change();

-- ---------------------------------------------------------------------
-- 1. Definições da organização: só owner/admin podem alterar dados da
--    empresa e o modelo de mensagem — staff continua a poder LER (precisa
--    do template para enviar lembretes) mas não a editar.
-- ---------------------------------------------------------------------

drop policy if exists "settings_update_member" on public.settings;
create policy "settings_update_admin"
  on public.settings for update
  using (organization_id in (select public.user_admin_organization_ids()))
  with check (organization_id in (select public.user_admin_organization_ids()));

drop policy if exists "organizations_update_owner_admin" on public.organizations;
create policy "organizations_update_owner_admin"
  on public.organizations for update
  using (id in (select public.user_admin_organization_ids()))
  with check (id in (select public.user_admin_organization_ids()));

-- ---------------------------------------------------------------------
-- 2. RPCs de gestão de membros — a UI de /settings usa estas funções em
--    vez de escrever directamente em organization_members, para ter
--    sempre mensagens de erro amigáveis; o trigger acima garante a
--    segurança mesmo que alguém tente contornar a função.
-- ---------------------------------------------------------------------

-- Lista os membros de uma organização com nome e email — profiles e
-- auth.users não são directamente legíveis entre colegas de organização
-- (cada profile só é visível ao próprio dono), por isso esta função
-- corre como security definer e valida a pertença à organização antes
-- de devolver qualquer linha.
create or replace function public.list_organization_members(p_organization_id uuid)
returns table (
  member_id uuid,
  user_id uuid,
  role text,
  full_name text,
  email text,
  is_you boolean,
  created_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não pertence a esta organização.' using errcode = '42501';
  end if;

  return query
    select
      om.id,
      om.user_id,
      om.role,
      coalesce(p.full_name, u.email),
      u.email,
      om.user_id = auth.uid(),
      om.created_at
    from public.organization_members om
    left join public.profiles p on p.id = om.user_id
    left join auth.users u on u.id = om.user_id
    where om.organization_id = p_organization_id
    order by (om.role = 'owner') desc, om.created_at asc;
end;
$$;

revoke execute on function public.list_organization_members(uuid) from public;
grant execute on function public.list_organization_members(uuid) to authenticated;

-- Adiciona um utilizador já registado como membro (admin/staff) da
-- organização, por email. Nunca cria uma conta nova nem permite definir
-- role = 'owner' (só o criador da organização é owner).
create or replace function public.add_organization_member_by_email(
  p_organization_id uuid,
  p_email text,
  p_role text default 'staff'
)
returns public.organization_members
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid;
  v_member public.organization_members%rowtype;
begin
  if p_organization_id not in (select public.user_admin_organization_ids()) then
    raise exception 'Só o dono ou administrador pode adicionar membros.' using errcode = '42501';
  end if;

  if p_role not in ('admin', 'staff') then
    raise exception 'Papel inválido. Escolha administrador ou colaborador.' using errcode = 'P0001';
  end if;

  select id into v_user_id from auth.users where lower(email) = lower(trim(p_email));

  if v_user_id is null then
    raise exception 'Não existe nenhuma conta CobrançaPro registada com o email %. Peça para se registar primeiro.', p_email
      using errcode = 'P0001';
  end if;

  if exists (
    select 1 from public.organization_members
    where organization_id = p_organization_id and user_id = v_user_id
  ) then
    raise exception 'Este utilizador já é membro da organização.' using errcode = 'P0001';
  end if;

  insert into public.organization_members (organization_id, user_id, role)
  values (p_organization_id, v_user_id, p_role)
  returning * into v_member;

  return v_member;
end;
$$;

revoke execute on function public.add_organization_member_by_email(uuid, text, text) from public;
grant execute on function public.add_organization_member_by_email(uuid, text, text) to authenticated;

-- Altera o papel de um membro (nunca do próprio, nunca do owner — ver
-- trigger acima, que também se aplica aqui).
create or replace function public.update_member_role(p_member_id uuid, p_role text)
returns public.organization_members
language plpgsql
security definer
set search_path = public
as $$
declare
  v_member public.organization_members%rowtype;
begin
  select * into v_member from public.organization_members where id = p_member_id;
  if not found then
    raise exception 'Membro não encontrado.' using errcode = 'P0002';
  end if;

  if v_member.organization_id not in (select public.user_admin_organization_ids()) then
    raise exception 'Só o dono ou administrador pode alterar papéis.' using errcode = '42501';
  end if;

  if p_role not in ('admin', 'staff') then
    raise exception 'Papel inválido. Escolha administrador ou colaborador.' using errcode = 'P0001';
  end if;

  update public.organization_members set role = p_role where id = p_member_id
  returning * into v_member;

  return v_member;
end;
$$;

revoke execute on function public.update_member_role(uuid, text) from public;
grant execute on function public.update_member_role(uuid, text) to authenticated;

create or replace function public.remove_organization_member(p_member_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_member public.organization_members%rowtype;
begin
  select * into v_member from public.organization_members where id = p_member_id;
  if not found then
    return;
  end if;

  if v_member.organization_id not in (select public.user_admin_organization_ids())
     and v_member.user_id <> auth.uid() then
    raise exception 'Só o dono, administrador, ou o próprio membro pode remover esta associação.' using errcode = '42501';
  end if;

  delete from public.organization_members where id = p_member_id;
end;
$$;

revoke execute on function public.remove_organization_member(uuid) from public;
grant execute on function public.remove_organization_member(uuid) to authenticated;
