-- 003_organizations.sql
-- Núcleo do sistema multi-tenant: organizations + organization_members.
--
-- Todas as restantes tabelas de negócio (clients, services, invoices, ...)
-- pertencem a uma organization_id e usam a função
-- public.user_organization_ids() definida aqui para decidir o acesso.
--
-- IMPORTANTE sobre RLS recursiva: uma política em organization_members
-- nunca deve fazer "select ... from organization_members" directamente,
-- porque isso reaplica a própria RLS da tabela recursivamente. Por isso
-- usamos funções "security definer" (que correm com privilégios elevados
-- e ignoram RLS internamente) para calcular pertença e papel do utilizador.

create table if not exists public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  business_type text,
  currency text not null default 'AOA',
  plan text not null default 'free' check (plan in ('free', 'basico', 'profissional', 'empresa')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.organizations enable row level security;

create trigger trg_organizations_updated_at
  before update on public.organizations
  for each row execute function public.set_updated_at();

create table if not exists public.organization_members (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null default 'owner' check (role in ('owner', 'admin', 'staff')),
  created_at timestamptz not null default now(),
  unique (organization_id, user_id)
);

alter table public.organization_members enable row level security;

create index if not exists idx_org_members_user_id on public.organization_members (user_id);
create index if not exists idx_org_members_org_id on public.organization_members (organization_id);

-- Devolve os IDs de todas as organizações a que o utilizador autenticado
-- pertence (qualquer papel). Usada pelas políticas RLS de TODAS as
-- tabelas de negócio.
create or replace function public.user_organization_ids()
returns setof uuid
language sql
security definer
stable
set search_path = public
as $$
  select organization_id
  from public.organization_members
  where user_id = auth.uid();
$$;

-- Devolve os IDs das organizações onde o utilizador autenticado é
-- "owner" ou "admin". Usada para acções de gestão (editar organização,
-- gerir membros).
create or replace function public.user_admin_organization_ids()
returns setof uuid
language sql
security definer
stable
set search_path = public
as $$
  select organization_id
  from public.organization_members
  where user_id = auth.uid() and role in ('owner', 'admin');
$$;

-- ---------------------------------------------------------------------
-- Políticas: organizations
-- ---------------------------------------------------------------------

create policy "organizations_select_member"
  on public.organizations for select
  using (id in (select public.user_organization_ids()));

-- Qualquer utilizador autenticado pode criar uma organização nova
-- (fluxo de onboarding). Torna-se "owner" através de um insert
-- subsequente em organization_members feito pela aplicação.
create policy "organizations_insert_authenticated"
  on public.organizations for insert
  with check (auth.uid() is not null);

create policy "organizations_update_owner_admin"
  on public.organizations for update
  using (id in (select public.user_admin_organization_ids()));

-- ---------------------------------------------------------------------
-- Políticas: organization_members
-- ---------------------------------------------------------------------

create policy "org_members_select_same_org"
  on public.organization_members for select
  using (organization_id in (select public.user_organization_ids()));

-- Permite ao criador de uma organização nova juntar-se como "owner",
-- apenas se ainda não existir nenhum membro nessa organização (evita que
-- um utilizador se auto-promova a owner de uma organização já existente).
create policy "org_members_insert_first_owner"
  on public.organization_members for insert
  with check (
    user_id = auth.uid()
    and role = 'owner'
    and not exists (
      select 1 from public.organization_members existing
      where existing.organization_id = organization_members.organization_id
    )
  );

create policy "org_members_insert_by_admin"
  on public.organization_members for insert
  with check (organization_id in (select public.user_admin_organization_ids()));

create policy "org_members_update_by_admin"
  on public.organization_members for update
  using (organization_id in (select public.user_admin_organization_ids()));

create policy "org_members_delete_by_admin"
  on public.organization_members for delete
  using (organization_id in (select public.user_admin_organization_ids()));
