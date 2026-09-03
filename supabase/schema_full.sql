-- schema_full.sql — gerado automaticamente por concatenação de
-- 001_extensions_and_helpers.sql até 024_dashboard_extra_stats.sql,
-- pela ordem numérica. NÃO editar directamente — para alterar o
-- schema, edite a migration individual correspondente em
-- supabase/migrations/ e regenere este ficheiro.
--
-- Uso: copie todo o conteúdo e cole de uma vez no SQL Editor de um
-- projecto Supabase NOVO (ainda sem nenhuma destas tabelas), depois
-- clique Run. Não corra isto num projecto que já tenha algumas
-- destas migrations aplicadas parcialmente sem rever primeiro.


-- =======================================================================
-- 001_extensions_and_helpers.sql
-- =======================================================================
-- 001_extensions_and_helpers.sql
-- Extensões necessárias e funções utilitárias reutilizadas pelas
-- migrations seguintes.

create extension if not exists "pgcrypto";

-- Função genérica para manter a coluna updated_at sempre actualizada.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- =======================================================================
-- 002_profiles.sql
-- =======================================================================
-- 002_profiles.sql
-- Um profile por utilizador autenticado (1:1 com auth.users).

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  full_name text,
  phone text,
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

create trigger trg_profiles_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

-- Um utilizador só pode ver e editar o seu próprio perfil.
create policy "profiles_select_own"
  on public.profiles for select
  using (id = auth.uid());

create policy "profiles_update_own"
  on public.profiles for update
  using (id = auth.uid())
  with check (id = auth.uid());

create policy "profiles_insert_own"
  on public.profiles for insert
  with check (id = auth.uid());

-- Cria automaticamente um profile quando um novo utilizador se regista.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, full_name)
  values (new.id, new.raw_user_meta_data ->> 'full_name');
  return new;
end;
$$;

create trigger trg_on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- =======================================================================
-- 003_organizations.sql
-- =======================================================================
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

-- =======================================================================
-- 004_clients.sql
-- =======================================================================
-- 004_clients.sql

create table if not exists public.clients (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  name text not null,
  phone text,
  email text,
  address text,
  notes text,
  status text not null default 'active' check (status in ('active', 'inactive', 'archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.clients enable row level security;

create index if not exists idx_clients_org_id on public.clients (organization_id);
create index if not exists idx_clients_org_status on public.clients (organization_id, status);
create index if not exists idx_clients_name_trgm on public.clients using btree (organization_id, name);

create trigger trg_clients_updated_at
  before update on public.clients
  for each row execute function public.set_updated_at();

create policy "clients_select_member"
  on public.clients for select
  using (organization_id in (select public.user_organization_ids()));

create policy "clients_insert_member"
  on public.clients for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "clients_update_member"
  on public.clients for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "clients_delete_member"
  on public.clients for delete
  using (organization_id in (select public.user_organization_ids()));

-- =======================================================================
-- 005_services.sql
-- =======================================================================
-- 005_services.sql

create table if not exists public.services (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  name text not null,
  description text,
  price numeric(14, 2) not null check (price >= 0),
  currency text not null default 'AOA',
  billing_period text not null default 'monthly' check (billing_period in ('weekly', 'monthly', 'quarterly', 'yearly')),
  status text not null default 'active' check (status in ('active', 'inactive')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.services enable row level security;

create index if not exists idx_services_org_id on public.services (organization_id);
create index if not exists idx_services_org_status on public.services (organization_id, status);

create trigger trg_services_updated_at
  before update on public.services
  for each row execute function public.set_updated_at();

create policy "services_select_member"
  on public.services for select
  using (organization_id in (select public.user_organization_ids()));

create policy "services_insert_member"
  on public.services for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "services_update_member"
  on public.services for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "services_delete_member"
  on public.services for delete
  using (organization_id in (select public.user_organization_ids()));

-- =======================================================================
-- 006_subscriptions.sql
-- =======================================================================
-- 006_subscriptions.sql
-- Representa uma cobrança recorrente configurada para um cliente. A
-- geração das invoices futuras a partir daqui é feita por um job
-- backend/cron (ver README, secção "Cobranças recorrentes"), nunca pelo
-- browser do utilizador.

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  client_id uuid not null references public.clients (id) on delete cascade,
  service_id uuid not null references public.services (id) on delete restrict,
  amount numeric(14, 2) not null check (amount >= 0),
  currency text not null default 'AOA',
  billing_period text not null default 'monthly' check (billing_period in ('weekly', 'monthly', 'quarterly', 'yearly')),
  due_day smallint not null check (due_day between 1 and 31),
  start_date date not null default current_date,
  end_date date,
  status text not null default 'active' check (status in ('active', 'paused', 'cancelled', 'ended')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint chk_subscription_dates check (end_date is null or end_date >= start_date)
);

alter table public.subscriptions enable row level security;

create index if not exists idx_subscriptions_org_id on public.subscriptions (organization_id);
create index if not exists idx_subscriptions_client_id on public.subscriptions (client_id);
create index if not exists idx_subscriptions_org_status on public.subscriptions (organization_id, status);

create trigger trg_subscriptions_updated_at
  before update on public.subscriptions
  for each row execute function public.set_updated_at();

create policy "subscriptions_select_member"
  on public.subscriptions for select
  using (organization_id in (select public.user_organization_ids()));

create policy "subscriptions_insert_member"
  on public.subscriptions for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "subscriptions_update_member"
  on public.subscriptions for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "subscriptions_delete_member"
  on public.subscriptions for delete
  using (organization_id in (select public.user_organization_ids()));

-- =======================================================================
-- 007_invoices.sql
-- =======================================================================
-- 007_invoices.sql

create table if not exists public.invoices (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  client_id uuid not null references public.clients (id) on delete cascade,
  subscription_id uuid references public.subscriptions (id) on delete set null,
  invoice_number text not null,
  description text,
  amount numeric(14, 2) not null check (amount >= 0),
  currency text not null default 'AOA',
  due_date date not null,
  status text not null default 'pending' check (status in ('pending', 'paid', 'overdue', 'cancelled')),
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, invoice_number)
);

alter table public.invoices enable row level security;

create index if not exists idx_invoices_org_id on public.invoices (organization_id);
create index if not exists idx_invoices_client_id on public.invoices (client_id);
create index if not exists idx_invoices_org_status on public.invoices (organization_id, status);
create index if not exists idx_invoices_org_due_date on public.invoices (organization_id, due_date);

create trigger trg_invoices_updated_at
  before update on public.invoices
  for each row execute function public.set_updated_at();

create policy "invoices_select_member"
  on public.invoices for select
  using (organization_id in (select public.user_organization_ids()));

create policy "invoices_insert_member"
  on public.invoices for insert
  with check (organization_id in (select public.user_organization_ids()));

create policy "invoices_update_member"
  on public.invoices for update
  using (organization_id in (select public.user_organization_ids()))
  with check (organization_id in (select public.user_organization_ids()));

create policy "invoices_delete_member"
  on public.invoices for delete
  using (organization_id in (select public.user_organization_ids()));

-- Função auxiliar para gerar o próximo número de factura sequencial por
-- organização, no formato FAT-000001. Chamada pela aplicação (ou por um
-- trigger, se preferido) ao criar uma invoice sem invoice_number definido.
create or replace function public.next_invoice_number(p_organization_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer;
begin
  select count(*) into v_count
  from public.invoices
  where organization_id = p_organization_id;

  return 'FAT-' || lpad((v_count + 1)::text, 6, '0');
end;
$$;

-- =======================================================================
-- 008_payments.sql
-- =======================================================================
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

-- =======================================================================
-- 009_reminders.sql
-- =======================================================================
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

-- =======================================================================
-- 010_notifications.sql
-- =======================================================================
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

-- =======================================================================
-- 011_settings.sql
-- =======================================================================
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

-- =======================================================================
-- 012_dashboard_helpers.sql
-- =======================================================================
-- 012_dashboard_helpers.sql

-- Função a correr periodicamente (via cron/scheduled function) que marca
-- como "overdue" as invoices pendentes cuja due_date já passou. Não deve
-- ser chamada a partir do browser do utilizador final.
create or replace function public.mark_overdue_invoices()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated integer;
begin
  update public.invoices
  set status = 'overdue'
  where status = 'pending' and due_date < current_date;

  get diagnostics v_updated = row_count;
  return v_updated;
end;
$$;

-- View com os totais usados no dashboard (receita recebida, pendente,
-- atrasada) por organização. Herda a RLS das tabelas subjacentes porque é
-- uma view "security invoker" (comportamento por omissão em Postgres).
create or replace view public.dashboard_totals as
select
  o.id as organization_id,
  coalesce(sum(i.amount) filter (where i.status = 'paid'), 0) as total_received,
  coalesce(sum(i.amount) filter (where i.status = 'pending'), 0) as total_pending,
  coalesce(sum(i.amount) filter (where i.status = 'overdue'), 0) as total_overdue,
  count(distinct c.id) filter (where c.status = 'active') as active_clients
from public.organizations o
left join public.invoices i on i.organization_id = o.id
left join public.clients c on c.organization_id = o.id
group by o.id;

-- =======================================================================
-- 013_client_balances.sql
-- =======================================================================
-- 013_client_balances.sql
-- View com os totais de facturação por cliente (pago, pendente, atrasado),
-- usada na lista de clientes ("total em dívida") e no perfil do cliente.
--
-- Tal como dashboard_totals, esta view é "security invoker" (comportamento
-- por omissão em Postgres): quando consultada por um utilizador
-- autenticado, a RLS da tabela subjacente "invoices" continua a aplicar-se
-- normalmente, pelo que um utilizador nunca vê saldos de clientes fora das
-- suas próprias organizações. Não é necessária nenhuma política RLS nova.

create or replace view public.client_balances as
select
  c.id as client_id,
  c.organization_id,
  coalesce(sum(i.amount) filter (where i.status = 'paid'), 0) as total_paid,
  coalesce(sum(i.amount) filter (where i.status = 'pending'), 0) as total_pending,
  coalesce(sum(i.amount) filter (where i.status = 'overdue'), 0) as total_overdue
from public.clients c
left join public.invoices i on i.client_id = c.id
group by c.id, c.organization_id;

-- =======================================================================
-- 014_invoice_number_counters.sql
-- =======================================================================
-- 014_invoice_number_counters.sql
-- Resolve a limitação de concorrência identificada em next_invoice_number():
-- contar linhas em "invoices" não é seguro sob concorrência alta (duas
-- transacções podem ler a mesma contagem antes de qualquer uma confirmar).
--
-- Em vez disso, mantemos um contador dedicado por organização e usamos
-- "insert ... on conflict ... do update" para o incrementar atomicamente:
-- este padrão adquire um lock de linha durante o UPDATE, pelo que chamadas
-- concorrentes são serializadas pelo Postgres e nunca devolvem o mesmo
-- número duas vezes.

create table if not exists public.invoice_number_counters (
  organization_id uuid primary key references public.organizations (id) on delete cascade,
  last_number integer not null default 0
);

-- Esta tabela é gerida inteiramente através da função abaixo (security
-- definer). Não expomos políticas de insert/update/delete a utilizadores
-- normais — só a leitura é permitida, para eventual auditoria/debug.
alter table public.invoice_number_counters enable row level security;

create policy "invoice_number_counters_select_member"
  on public.invoice_number_counters for select
  using (organization_id in (select public.user_organization_ids()));

create or replace function public.next_invoice_number(p_organization_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_next integer;
begin
  if p_organization_id not in (select public.user_organization_ids()) then
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

-- Apenas utilizadores autenticados (membros de alguma organização, filtrado
-- dentro da própria função de negócio que a chama) podem invocar isto.
revoke execute on function public.next_invoice_number(uuid) from public;
grant execute on function public.next_invoice_number(uuid) to authenticated;

-- =======================================================================
-- 015_mark_invoice_paid.sql
-- =======================================================================
-- 015_mark_invoice_paid.sql
-- RPC que executa, numa única transacção atómica:
--   1. bloqueia a linha da invoice (FOR UPDATE) para serializar chamadas
--      concorrentes sobre a mesma invoice;
--   2. verifica que o utilizador pertence à organização da invoice;
--   3. impede pagar uma invoice já paga ou cancelada (evita pagamentos
--      duplicados mesmo em caso de duplo-clique ou pedidos em paralelo);
--   4. cria o registo em "payments";
--   5. actualiza o estado da invoice para "paid" e define paid_at.
--
-- Chamar a partir do frontend via:
--   supabase.rpc('mark_invoice_paid', {
--     p_invoice_id, p_payment_method, p_transaction_reference
--   })

create or replace function public.mark_invoice_paid(
  p_invoice_id uuid,
  p_payment_method text default 'other',
  p_transaction_reference text default null
)
returns public.payments
language plpgsql
security definer
set search_path = public
as $$
declare
  v_invoice public.invoices%rowtype;
  v_payment public.payments%rowtype;
begin
  select * into v_invoice
  from public.invoices
  where id = p_invoice_id
  for update;

  if not found then
    raise exception 'Cobrança não encontrada.' using errcode = 'P0002';
  end if;

  if v_invoice.organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não tem permissão para esta cobrança.' using errcode = '42501';
  end if;

  if v_invoice.status = 'paid' then
    raise exception 'Esta cobrança já foi paga.' using errcode = 'P0001';
  end if;

  if v_invoice.status = 'cancelled' then
    raise exception 'Não é possível pagar uma cobrança cancelada.' using errcode = 'P0001';
  end if;

  insert into public.payments (
    organization_id, invoice_id, client_id, amount, currency,
    payment_method, transaction_reference, paid_at, status
  )
  values (
    v_invoice.organization_id, v_invoice.id, v_invoice.client_id, v_invoice.amount,
    v_invoice.currency, coalesce(p_payment_method, 'other'), p_transaction_reference,
    now(), 'completed'
  )
  returning * into v_payment;

  update public.invoices
  set status = 'paid', paid_at = now()
  where id = p_invoice_id;

  return v_payment;
end;
$$;

revoke execute on function public.mark_invoice_paid(uuid, text, text) from public;
grant execute on function public.mark_invoice_paid(uuid, text, text) to authenticated;

-- =======================================================================
-- 016_invoices_guards.sql
-- =======================================================================
-- 016_invoices_guards.sql

-- 1. Campo opcional de referência (ex: nº de recibo externo, referência de
--    depósito bancário) a mostrar/editar na cobrança.
alter table public.invoices add column if not exists reference text;

-- 2. Protecção de máquina de estados: uma vez "paid" ou "cancelled", uma
--    invoice não pode ser devolvida a outro estado por uma actualização
--    directa da tabela. Isto protege contra bugs de UI ou chamadas
--    manuais que tentassem "reabrir" uma cobrança já fechada. A função
--    mark_invoice_paid() já valida isto antes de chegar aqui, mas o
--    trigger garante a mesma regra mesmo para outros caminhos de escrita.
create or replace function public.enforce_invoice_status_transition()
returns trigger
language plpgsql
as $$
begin
  if old.status in ('paid', 'cancelled') and new.status is distinct from old.status then
    raise exception 'Não é possível alterar o estado de uma cobrança já %.', old.status
      using errcode = 'P0001';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_invoices_status_guard on public.invoices;
create trigger trg_invoices_status_guard
  before update on public.invoices
  for each row execute function public.enforce_invoice_status_transition();

-- 3. mark_overdue_invoices() actualiza facturas de TODAS as organizações
--    (é uma tarefa de manutenção global, não uma operação por-organização),
--    por isso nunca deve ser chamável directamente pelo frontend. Deve ser
--    invocada apenas por um job agendado (Supabase Edge Function / cron)
--    autenticado com a service_role, que ignora RLS e privilégios de
--    "authenticated" por definição.
revoke execute on function public.mark_overdue_invoices() from public, authenticated, anon;
grant execute on function public.mark_overdue_invoices() to service_role;

-- =======================================================================
-- 017_recurring_billing.sql
-- =======================================================================
-- 017_recurring_billing.sql
-- Suporte completo para geração automática de cobranças a partir de
-- subscriptions, com idempotência garantida ao nível do Postgres.

-- 1. Colunas de controlo em subscriptions.
alter table public.subscriptions add column if not exists next_billing_date date;
alter table public.subscriptions add column if not exists last_billing_date date;

create index if not exists idx_subscriptions_next_billing
  on public.subscriptions (next_billing_date)
  where status = 'active';

create index if not exists idx_invoices_subscription_id
  on public.invoices (subscription_id);

-- 2. Log de facturação: a chave da idempotência. Cada linha representa
--    "esta subscrição já gerou a invoice da competência X" — a restrição
--    unique garante que, mesmo que a função de geração corra duas vezes
--    (ou em paralelo), nunca é criada uma segunda invoice para a mesma
--    competência da mesma subscrição.
create table if not exists public.subscription_billing_log (
  id uuid primary key default gen_random_uuid(),
  subscription_id uuid not null references public.subscriptions (id) on delete cascade,
  organization_id uuid not null references public.organizations (id) on delete cascade,
  billing_period_start date not null,
  invoice_id uuid references public.invoices (id) on delete set null,
  created_at timestamptz not null default now(),
  unique (subscription_id, billing_period_start)
);

alter table public.subscription_billing_log enable row level security;

create index if not exists idx_billing_log_org_id on public.subscription_billing_log (organization_id);
create index if not exists idx_billing_log_subscription_id on public.subscription_billing_log (subscription_id);

-- Apenas leitura para membros da organização (auditoria/histórico). A
-- escrita é feita inteiramente pela função generate_recurring_invoices(),
-- que corre como service_role e por isso ignora RLS.
create policy "billing_log_select_member"
  on public.subscription_billing_log for select
  using (organization_id in (select public.user_organization_ids()));

-- 3. Calcula a próxima data de vencimento de uma subscrição, respeitando
--    periodicidade e due_day, a partir de uma data de referência
--    ("p_after" — normalmente "hoje" ou "última cobrança + 1 dia").
--    Nunca devolve uma data anterior a p_start_date.
create or replace function public.compute_next_billing_date(
  p_start_date date,
  p_due_day smallint,
  p_billing_period text,
  p_after date
)
returns date
language plpgsql
immutable
as $$
declare
  v_candidate date;
  v_period interval;
  v_year int;
  v_month int;
  v_days_in_month int;
  v_iterations int := 0;
  v_after date := greatest(p_after, p_start_date);
begin
  if p_billing_period = 'weekly' then
    -- p_due_day interpretado como dia da semana ISO (1=Segunda..7=Domingo).
    v_candidate := p_start_date + (((p_due_day - extract(isodow from p_start_date)::int) % 7 + 7) % 7);
    while v_candidate < v_after and v_iterations < 2000 loop
      v_candidate := v_candidate + 7;
      v_iterations := v_iterations + 1;
    end loop;
    return v_candidate;
  end if;

  v_period := case p_billing_period
    when 'quarterly' then interval '3 months'
    when 'yearly' then interval '1 year'
    else interval '1 month'
  end;

  v_year := extract(year from p_start_date)::int;
  v_month := extract(month from p_start_date)::int;
  v_days_in_month := extract(day from (make_date(v_year, v_month, 1) + interval '1 month - 1 day'))::int;
  v_candidate := make_date(v_year, v_month, least(p_due_day, v_days_in_month)::int);

  if v_candidate < p_start_date then
    v_year := extract(year from (v_candidate + v_period))::int;
    v_month := extract(month from (v_candidate + v_period))::int;
    v_days_in_month := extract(day from (make_date(v_year, v_month, 1) + interval '1 month - 1 day'))::int;
    v_candidate := make_date(v_year, v_month, least(p_due_day, v_days_in_month)::int);
  end if;

  while v_candidate < v_after and v_iterations < 2000 loop
    v_year := extract(year from (v_candidate + v_period))::int;
    v_month := extract(month from (v_candidate + v_period))::int;
    v_days_in_month := extract(day from (make_date(v_year, v_month, 1) + interval '1 month - 1 day'))::int;
    v_candidate := make_date(v_year, v_month, least(p_due_day, v_days_in_month)::int);
    v_iterations := v_iterations + 1;
  end loop;

  return v_candidate;
end;
$$;

-- 4. next_invoice_number(): relaxa a verificação de pertença à organização
--    quando chamada sem utilizador autenticado (auth.uid() is null), que é
--    o caso da função de geração automática a correr como service_role.
--    Chamadas feitas a partir do frontend (com um utilizador autenticado)
--    continuam totalmente validadas como antes.
create or replace function public.next_invoice_number(p_organization_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_next integer;
begin
  if auth.uid() is not null and p_organization_id not in (select public.user_organization_ids()) then
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

-- 5. Recalcula next_billing_date automaticamente:
--    - ao criar uma subscrição;
--    - ao editar start_date, due_day ou billing_period;
--    - ao retomar uma subscrição pausada (recomeça a partir de hoje, sem
--      cobrar retroactivamente o período em que esteve pausada).
create or replace function public.set_subscription_next_billing_date()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' then
    new.next_billing_date := public.compute_next_billing_date(
      new.start_date, new.due_day, new.billing_period, current_date
    );
    return new;
  end if;

  if (new.start_date, new.due_day, new.billing_period) is distinct from
     (old.start_date, old.due_day, old.billing_period) then
    new.next_billing_date := public.compute_next_billing_date(
      new.start_date, new.due_day, new.billing_period,
      greatest(coalesce(new.last_billing_date, new.start_date), current_date)
    );
  elsif old.status = 'paused' and new.status = 'active' then
    new.next_billing_date := public.compute_next_billing_date(
      new.start_date, new.due_day, new.billing_period, current_date
    );
  end if;

  return new;
end;
$$;

drop trigger if exists trg_subscriptions_next_billing_insert on public.subscriptions;
create trigger trg_subscriptions_next_billing_insert
  before insert on public.subscriptions
  for each row execute function public.set_subscription_next_billing_date();

drop trigger if exists trg_subscriptions_next_billing_update on public.subscriptions;
create trigger trg_subscriptions_next_billing_update
  before update on public.subscriptions
  for each row execute function public.set_subscription_next_billing_date();

-- 6. Marca como "ended" subscrições activas cuja end_date já passou.
--    Tal como mark_overdue_invoices(), é uma tarefa de manutenção global
--    e só deve ser invocada pela service_role.
create or replace function public.end_expired_subscriptions()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated integer;
begin
  update public.subscriptions
  set status = 'ended'
  where status = 'active' and end_date is not null and end_date < current_date;

  get diagnostics v_updated = row_count;
  return v_updated;
end;
$$;

revoke execute on function public.end_expired_subscriptions() from public, authenticated, anon;
grant execute on function public.end_expired_subscriptions() to service_role;

-- 7. Função principal: gera as invoices em falta para todas as
--    subscrições activas, com idempotência via subscription_billing_log.
--    Recupera competências em atraso (até 24 por subscrição e por
--    execução, como limite de segurança) caso o job não tenha corrido
--    durante algum tempo.
create or replace function public.generate_recurring_invoices()
returns table (
  out_subscription_id uuid,
  out_invoice_id uuid,
  out_invoice_number text,
  out_billing_period_start date
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_sub record;
  v_period_start date;
  v_invoice_id uuid;
  v_invoice_number text;
  v_inserted boolean;
  v_iterations int;
begin
  for v_sub in
    select s.*, sv.name as service_name
    from public.subscriptions s
    join public.services sv on sv.id = s.service_id
    where s.status = 'active'
      and s.start_date <= current_date
      and (s.end_date is null or s.end_date >= current_date)
  loop
    v_iterations := 0;
    v_period_start := coalesce(
      v_sub.next_billing_date,
      public.compute_next_billing_date(v_sub.start_date, v_sub.due_day, v_sub.billing_period, current_date)
    );

    while v_period_start <= current_date
      and (v_sub.end_date is null or v_period_start <= v_sub.end_date)
      and v_iterations < 24
    loop
      v_inserted := false;

      begin
        insert into public.subscription_billing_log (subscription_id, organization_id, billing_period_start)
        values (v_sub.id, v_sub.organization_id, v_period_start);
        v_inserted := true;
      exception when unique_violation then
        v_inserted := false;
      end;

      if v_inserted then
        v_invoice_number := public.next_invoice_number(v_sub.organization_id);

        insert into public.invoices (
          organization_id, client_id, subscription_id, invoice_number,
          description, amount, currency, due_date, status
        )
        values (
          v_sub.organization_id, v_sub.client_id, v_sub.id, v_invoice_number,
          v_sub.service_name, v_sub.amount, v_sub.currency, v_period_start, 'pending'
        )
        returning id into v_invoice_id;

        update public.subscription_billing_log
        set invoice_id = v_invoice_id
        where subscription_id = v_sub.id and billing_period_start = v_period_start;

        update public.subscriptions
        set last_billing_date = v_period_start,
            next_billing_date = public.compute_next_billing_date(
              v_sub.start_date, v_sub.due_day, v_sub.billing_period, v_period_start + 1
            )
        where id = v_sub.id;

        out_subscription_id := v_sub.id;
        out_invoice_id := v_invoice_id;
        out_invoice_number := v_invoice_number;
        out_billing_period_start := v_period_start;
        return next;
      end if;

      v_period_start := public.compute_next_billing_date(
        v_sub.start_date, v_sub.due_day, v_sub.billing_period, v_period_start + 1
      );
      v_iterations := v_iterations + 1;
    end loop;
  end loop;

  return;
end;
$$;

revoke execute on function public.generate_recurring_invoices() from public, authenticated, anon;
grant execute on function public.generate_recurring_invoices() to service_role;

-- =======================================================================
-- 018_payments_hardening.sql
-- =======================================================================
-- 018_payments_hardening.sql
-- Fase 5: módulo de Pagamentos. Reutiliza a tabela "payments" já existente
-- (migration 008) — não cria tabela nova. Esta migration:
--   1. adiciona a coluna "note" (observação);
--   2. amplia os métodos de pagamento aceites;
--   3. FECHA uma falha de integridade: a partir daqui, nenhum utilizador
--      pode criar/editar/apagar um pagamento por escrita directa — só a
--      função mark_invoice_paid() (que corre com os privilégios do dono
--      da tabela) pode gerar pagamentos;
--   4. cria a view payment_totals para os cartões do dashboard de
--      pagamentos;
--   5. estende mark_invoice_paid() com data/hora e observação
--      opcionais, mantendo total compatibilidade com as chamadas já
--      existentes desde a Fase 3.

-- 1. Observação livre sobre o pagamento.
alter table public.payments add column if not exists note text;

-- 2. Métodos de pagamento: adiciona multicaixa/multicaixa_express.
--    Mantemos "mobile_money" e "card" na lista permitida por
--    compatibilidade com eventuais registos antigos — deixam de ser
--    oferecidos como opção na interface, mas não invalidamos dados
--    já existentes.
alter table public.payments drop constraint if exists payments_payment_method_check;
alter table public.payments add constraint payments_payment_method_check
  check (payment_method in ('cash', 'bank_transfer', 'mobile_money', 'card', 'multicaixa', 'multicaixa_express', 'other'));

-- 3. Fecha a escrita directa na tabela. As políticas de INSERT/UPDATE/
--    DELETE criadas na migration 008 permitiam a qualquer membro da
--    organização escrever pagamentos directamente, o que contradiz a
--    exigência de que toda a criação/alteração financeira passe pela
--    função transaccional. A partir daqui só existe política de SELECT:
--    a única forma de um pagamento passar a existir é via
--    mark_invoice_paid() (security definer, dono da tabela, ignora RLS).
drop policy if exists "payments_insert_member" on public.payments;
drop policy if exists "payments_update_member" on public.payments;
drop policy if exists "payments_delete_member" on public.payments;
-- "payments_select_member" (leitura) mantém-se inalterada.

-- 4. Totais para os cartões da página /payments. Security invoker (por
--    omissão) — herda a RLS de "payments", tal como dashboard_totals e
--    client_balances.
create or replace view public.payment_totals as
select
  p.organization_id,
  coalesce(sum(p.amount) filter (where p.status = 'completed'), 0) as total_received,
  coalesce(sum(p.amount) filter (
    where p.status = 'completed'
      and date_trunc('month', p.paid_at) = date_trunc('month', now())
  ), 0) as total_this_month,
  coalesce(sum(p.amount) filter (
    where p.status = 'completed' and p.paid_at::date = current_date
  ), 0) as total_today
from public.payments p
group by p.organization_id;

-- 5. mark_invoice_paid(): acrescenta p_paid_at (data/hora do pagamento,
--    por omissão "agora") e p_note (observação opcional). O valor do
--    pagamento continua a ser sempre v_invoice.amount — nunca um valor
--    vindo do chamador — o que é a garantia real contra pagamentos
--    incorrectos ou duplicados: o cliente nunca decide "quanto" se paga.
--
--    IMPORTANTE: a assinatura antiga (uuid, text, text) tem de ser
--    eliminada explicitamente antes de criar a nova (uuid, text, text,
--    timestamptz, text). Um "create or replace" com uma lista de
--    parâmetros diferente NÃO substitui a função anterior — cria uma
--    segunda função sobrecarregada em paralelo, o que tornaria uma
--    chamada com apenas 3 argumentos nomeados ambígua (dois candidatos
--    possíveis) e faria a aplicação falhar em produção.
drop function if exists public.mark_invoice_paid(uuid, text, text);

create function public.mark_invoice_paid(
  p_invoice_id uuid,
  p_payment_method text default 'other',
  p_transaction_reference text default null,
  p_paid_at timestamptz default null,
  p_note text default null
)
returns public.payments
language plpgsql
security definer
set search_path = public
as $$
declare
  v_invoice public.invoices%rowtype;
  v_payment public.payments%rowtype;
  v_paid_at timestamptz := coalesce(p_paid_at, now());
begin
  if v_paid_at > now() then
    raise exception 'A data do pagamento não pode ser no futuro.' using errcode = 'P0001';
  end if;

  select * into v_invoice
  from public.invoices
  where id = p_invoice_id
  for update;

  if not found then
    raise exception 'Cobrança não encontrada.' using errcode = 'P0002';
  end if;

  if v_invoice.organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não tem permissão para esta cobrança.' using errcode = '42501';
  end if;

  if v_invoice.status = 'paid' then
    raise exception 'Esta cobrança já foi paga.' using errcode = 'P0001';
  end if;

  if v_invoice.status = 'cancelled' then
    raise exception 'Não é possível pagar uma cobrança cancelada.' using errcode = 'P0001';
  end if;

  insert into public.payments (
    organization_id, invoice_id, client_id, amount, currency,
    payment_method, transaction_reference, paid_at, status, note
  )
  values (
    v_invoice.organization_id, v_invoice.id, v_invoice.client_id, v_invoice.amount,
    v_invoice.currency, coalesce(p_payment_method, 'other'), p_transaction_reference,
    v_paid_at, 'completed', p_note
  )
  returning * into v_payment;

  update public.invoices
  set status = 'paid', paid_at = v_paid_at
  where id = p_invoice_id;

  return v_payment;
end;
$$;

-- Nova assinatura -> precisa dos seus próprios grants (os da assinatura
-- antiga desaparecem automaticamente com o "drop function" acima).
revoke execute on function public.mark_invoice_paid(uuid, text, text, timestamptz, text) from public;
grant execute on function public.mark_invoice_paid(uuid, text, text, timestamptz, text) to authenticated;

-- =======================================================================
-- 019_reminders_types.sql
-- =======================================================================
-- 019_reminders_types.sql
-- Fase 6: Central de Lembretes. Reutiliza a tabela "reminders" já
-- existente (migration 009) — não cria tabela nova.

-- 1. Tipo de lembrete, para distinguir cobrança criada / vencimento
--    próximo / atrasada / confirmação de pagamento / mensagem manual.
alter table public.reminders add column if not exists reminder_type text
  not null default 'manual'
  check (reminder_type in ('invoice_created', 'invoice_due', 'invoice_overdue', 'payment_confirmation', 'manual'));

-- 2. Estado honesto: nunca fingimos saber que uma mensagem foi entregue
--    ou lida. "initiated" representa exactamente o que sabemos — que o
--    utilizador clicou para abrir o WhatsApp com a mensagem preparada.
--    Mantemos os valores antigos ('scheduled', 'sent', 'failed',
--    'cancelled') no schema por compatibilidade com registos anteriores
--    (Fases 3 e 5 usavam 'sent'), mas o código a partir desta fase só
--    grava 'initiated'.
alter table public.reminders drop constraint if exists reminders_status_check;
alter table public.reminders add constraint reminders_status_check
  check (status in ('scheduled', 'initiated', 'sent', 'failed', 'cancelled'));

-- 3. Índice para os filtros de período da página /reminders.
create index if not exists idx_reminders_org_created_at on public.reminders (organization_id, created_at);

-- 4. Totais para os cartões de /reminders e do Dashboard. Security
--    invoker (por omissão) — herda a RLS de "reminders", tal como as
--    outras views já criadas (dashboard_totals, client_balances,
--    payment_totals).
create or replace view public.reminder_totals as
select
  r.organization_id,
  count(*) filter (where r.status in ('initiated', 'sent')) as total_sent,
  count(*) filter (
    where r.status in ('initiated', 'sent') and r.created_at::date = current_date
  ) as sent_today,
  count(*) filter (
    where r.status in ('initiated', 'sent')
      and date_trunc('month', r.created_at) = date_trunc('month', now())
  ) as sent_this_month
from public.reminders r
group by r.organization_id;

-- =======================================================================
-- 020_reports.sql
-- =======================================================================
-- 020_reports.sql
-- Fase 7: Relatórios & Analytics. Não altera nenhuma tabela existente —
-- só acrescenta funções de leitura agregada e um índice de apoio.
--
-- IMPORTANTE: todas as funções abaixo são "security invoker" (o padrão em
-- Postgres — nenhuma usa "security definer"). Correm com os privilégios
-- de quem as chama (o utilizador autenticado), pelo que a Row Level
-- Security das tabelas subjacentes (invoices, payments, clients,
-- subscriptions, services) continua a aplicar-se integralmente. Mesmo
-- que alguém passe o organization_id de outra organização, a RLS impede
-- que as queries internas devolvam qualquer linha dessa organização —
-- o parâmetro nunca é uma forma de contornar o isolamento multi-tenant.
--
-- Como reforço adicional (defesa em profundidade, não apenas RLS),
-- todas as funções validam explicitamente que o utilizador pertence à
-- organização indicada antes de devolver qualquer resultado, e que
-- p_from <= p_to, falhando de forma clara em vez de devolver dados
-- vazios silenciosamente por um parâmetro incorrecto.

create index if not exists idx_invoices_org_created_at on public.invoices (organization_id, created_at);

-- Função auxiliar de validação, reutilizada por todas as funções desta
-- migration para evitar duplicar a mesma verificação sete vezes.
create or replace function public.assert_report_access(p_organization_id uuid, p_from date, p_to date)
returns void
language plpgsql
stable
as $$
begin
  if p_organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não pertence a esta organização.' using errcode = '42501';
  end if;

  if p_from is not null and p_to is not null and p_from > p_to then
    raise exception 'A data inicial não pode ser posterior à data final.' using errcode = '22023';
  end if;
end;
$$;

-- ---------------------------------------------------------------------
-- 1. Indicadores principais, todos numa só chamada (evita uma query por
--    cartão no frontend).
--
--    Definições (documentadas e estáveis — não alterar sem rever o
--    frontend que depende delas):
--      revenue_received  -> payments.paid_at no período
--      total_billed      -> invoices.created_at no período (emitidas,
--                           inclui as entretanto canceladas)
--      total_pending/overdue -> invoices.due_date no período + estado actual
--      payment_rate      -> pagas ÷ emitidas, excluindo canceladas,
--                           coorte pela data de emissão
--      active_clients/active_subscriptions -> estado actual (não
--                           filtrado por período)
-- ---------------------------------------------------------------------
create or replace function public.report_summary(
  p_organization_id uuid,
  p_from date,
  p_to date
)
returns table (
  revenue_received numeric,
  total_billed numeric,
  total_pending numeric,
  total_overdue numeric,
  invoices_emitted_count integer,
  invoices_paid_count integer,
  payment_rate numeric,
  active_clients integer,
  active_subscriptions integer
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  select
    coalesce((
      select sum(p.amount) from public.payments p
      where p.organization_id = p_organization_id
        and p.status = 'completed'
        and p.paid_at::date between p_from and p_to
    ), 0) as revenue_received,

    coalesce((
      select sum(i.amount) from public.invoices i
      where i.organization_id = p_organization_id
        and i.created_at::date between p_from and p_to
    ), 0) as total_billed,

    coalesce((
      select sum(i.amount) from public.invoices i
      where i.organization_id = p_organization_id
        and i.status = 'pending'
        and i.due_date between p_from and p_to
    ), 0) as total_pending,

    coalesce((
      select sum(i.amount) from public.invoices i
      where i.organization_id = p_organization_id
        and i.status = 'overdue'
        and i.due_date between p_from and p_to
    ), 0) as total_overdue,

    (
      select count(*)::integer from public.invoices i
      where i.organization_id = p_organization_id
        and i.created_at::date between p_from and p_to
        and i.status <> 'cancelled'
    ) as invoices_emitted_count,

    (
      select count(*)::integer from public.invoices i
      where i.organization_id = p_organization_id
        and i.created_at::date between p_from and p_to
        and i.status = 'paid'
    ) as invoices_paid_count,

    case
      when (
        select count(*) from public.invoices i
        where i.organization_id = p_organization_id
          and i.created_at::date between p_from and p_to
          and i.status <> 'cancelled'
      ) = 0 then 0
      else round(
        100.0 * (
          select count(*) from public.invoices i
          where i.organization_id = p_organization_id
            and i.created_at::date between p_from and p_to
            and i.status = 'paid'
        ) / (
          select count(*) from public.invoices i
          where i.organization_id = p_organization_id
            and i.created_at::date between p_from and p_to
            and i.status <> 'cancelled'
        ), 1
      )
    end as payment_rate,

    (
      select count(*)::integer from public.clients c
      where c.organization_id = p_organization_id and c.status = 'active'
    ) as active_clients,

    (
      select count(*)::integer from public.subscriptions s
      where s.organization_id = p_organization_id and s.status = 'active'
    ) as active_subscriptions;
end;
$$;

-- ---------------------------------------------------------------------
-- 2. Série temporal de receita recebida, agrupada por dia/semana/mês,
--    com "buracos" preenchidos a zero (generate_series) para o gráfico
--    não ficar com falhas visuais em dias sem pagamentos. Os dois
--    limites (p_from e p_to truncados à mesma unidade) garantem que o
--    último período é sempre incluído, mesmo quando não é múltiplo
--    exacto do passo.
-- ---------------------------------------------------------------------
create or replace function public.report_revenue_series(
  p_organization_id uuid,
  p_from date,
  p_to date,
  p_granularity text default 'day'
)
returns table (bucket date, total numeric)
language plpgsql
stable
as $$
declare
  v_step interval;
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  if p_granularity not in ('day', 'week', 'month') then
    raise exception 'Granularidade inválida: %', p_granularity using errcode = '22023';
  end if;

  v_step := case p_granularity when 'week' then interval '7 days' when 'month' then interval '1 month' else interval '1 day' end;

  return query
  with buckets as (
    select generate_series(
      date_trunc(p_granularity, p_from::timestamp),
      date_trunc(p_granularity, p_to::timestamp),
      v_step
    )::date as bucket
  ),
  paid as (
    select date_trunc(p_granularity, p.paid_at)::date as bucket, sum(p.amount) as total
    from public.payments p
    where p.organization_id = p_organization_id
      and p.status = 'completed'
      and p.paid_at::date between p_from and p_to
    group by 1
  )
  select b.bucket, coalesce(paid.total, 0) as total
  from buckets b
  left join paid on paid.bucket = b.bucket
  order by b.bucket;
end;
$$;

-- ---------------------------------------------------------------------
-- 3. Cobranças por estado (para o gráfico de barras), com base na data
--    de vencimento dentro do período.
-- ---------------------------------------------------------------------
create or replace function public.report_invoice_status_breakdown(
  p_organization_id uuid,
  p_from date,
  p_to date
)
returns table (status text, invoice_count integer, total_amount numeric)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  select i.status, count(*)::integer as invoice_count, coalesce(sum(i.amount), 0) as total_amount
  from public.invoices i
  where i.organization_id = p_organization_id
    and i.due_date between p_from and p_to
  group by i.status;
end;
$$;

-- ---------------------------------------------------------------------
-- 4. Ranking de clientes. total_paid usa payments.paid_at no período;
--    total_pending/total_overdue/invoice_count usam invoices.due_date no
--    período — os dois grupos são combinados por client_id.
-- ---------------------------------------------------------------------
create or replace function public.report_top_clients(
  p_organization_id uuid,
  p_from date,
  p_to date,
  p_limit integer default 10
)
returns table (
  client_id uuid,
  client_name text,
  total_paid numeric,
  total_pending numeric,
  total_overdue numeric,
  invoice_count integer
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  with pays as (
    select p.client_id, sum(p.amount) as total_paid
    from public.payments p
    where p.organization_id = p_organization_id
      and p.status = 'completed'
      and p.paid_at::date between p_from and p_to
    group by p.client_id
  ),
  invs as (
    select
      i.client_id,
      sum(i.amount) filter (where i.status = 'pending') as total_pending,
      sum(i.amount) filter (where i.status = 'overdue') as total_overdue,
      count(*) as invoice_count
    from public.invoices i
    where i.organization_id = p_organization_id
      and i.due_date between p_from and p_to
    group by i.client_id
  )
  select
    c.id as client_id,
    c.name as client_name,
    coalesce(pays.total_paid, 0) as total_paid,
    coalesce(invs.total_pending, 0) as total_pending,
    coalesce(invs.total_overdue, 0) as total_overdue,
    coalesce(invs.invoice_count, 0)::integer as invoice_count
  from public.clients c
  left join pays on pays.client_id = c.id
  left join invs on invs.client_id = c.id
  where c.organization_id = p_organization_id
    and (pays.total_paid is not null or invs.invoice_count is not null)
  order by (coalesce(pays.total_paid, 0) + coalesce(invs.total_pending, 0) + coalesce(invs.total_overdue, 0)) desc
  limit p_limit;
end;
$$;

-- ---------------------------------------------------------------------
-- 5. Receita por serviço. LIMITAÇÃO REAL, documentada e reflectida no
--    resultado: só cobranças nascidas de uma subscrição (invoices com
--    subscription_id preenchido) podem ser associadas a um serviço.
--    Facturas manuais (sem subscription_id) nunca entram nesta soma,
--    porque a base de dados não guarda essa ligação — não inventamos
--    uma correspondência que não existe. O frontend mostra este aviso
--    explicitamente junto à tabela.
-- ---------------------------------------------------------------------
create or replace function public.report_service_breakdown(
  p_organization_id uuid,
  p_from date,
  p_to date
)
returns table (
  service_id uuid,
  service_name text,
  subscriptions_total integer,
  subscriptions_active integer,
  estimated_mrr numeric,
  revenue_in_period numeric
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  with sub_counts as (
    select
      s.service_id,
      count(*) as subscriptions_total,
      count(*) filter (where s.status = 'active') as subscriptions_active,
      coalesce(sum(
        case s.billing_period
          when 'weekly' then s.amount * 4.33
          when 'monthly' then s.amount
          when 'quarterly' then s.amount / 3.0
          when 'yearly' then s.amount / 12.0
          else 0
        end
      ) filter (where s.status = 'active'), 0) as estimated_mrr
    from public.subscriptions s
    where s.organization_id = p_organization_id
    group by s.service_id
  ),
  revenue as (
    select sub.service_id, sum(p.amount) as revenue_in_period
    from public.payments p
    join public.invoices i on i.id = p.invoice_id
    join public.subscriptions sub on sub.id = i.subscription_id
    where p.organization_id = p_organization_id
      and p.status = 'completed'
      and p.paid_at::date between p_from and p_to
    group by sub.service_id
  )
  select
    sv.id as service_id,
    sv.name as service_name,
    coalesce(sc.subscriptions_total, 0)::integer as subscriptions_total,
    coalesce(sc.subscriptions_active, 0)::integer as subscriptions_active,
    coalesce(sc.estimated_mrr, 0) as estimated_mrr,
    coalesce(r.revenue_in_period, 0) as revenue_in_period
  from public.services sv
  left join sub_counts sc on sc.service_id = sv.id
  left join revenue r on r.service_id = sv.id
  where sv.organization_id = p_organization_id
    and (sc.subscriptions_total is not null or r.revenue_in_period is not null)
  order by coalesce(r.revenue_in_period, 0) desc, coalesce(sc.subscriptions_active, 0) desc;
end;
$$;

-- ---------------------------------------------------------------------
-- 6. Maiores valores em atraso — estado actual, não filtrado por
--    período (é uma lista accionável "quem me deve mais agora").
--    days_overdue = current_date - due_date (subtracção de duas datas
--    em Postgres devolve directamente um inteiro em dias).
-- ---------------------------------------------------------------------
create or replace function public.report_overdue_top(
  p_organization_id uuid,
  p_limit integer default 10
)
returns table (
  invoice_id uuid,
  invoice_number text,
  client_id uuid,
  client_name text,
  client_phone text,
  amount numeric,
  currency text,
  due_date date,
  days_overdue integer
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, null, null);

  return query
  select
    i.id as invoice_id,
    i.invoice_number,
    c.id as client_id,
    c.name as client_name,
    c.phone as client_phone,
    i.amount,
    i.currency,
    i.due_date,
    (current_date - i.due_date) as days_overdue
  from public.invoices i
  join public.clients c on c.id = i.client_id
  where i.organization_id = p_organization_id
    and i.status = 'overdue'
  order by i.amount desc
  limit p_limit;
end;
$$;

-- =======================================================================
-- 021_plans_trial_and_limits.sql
-- =======================================================================
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

-- =======================================================================
-- 022_member_management_and_settings.sql
-- =======================================================================
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

-- =======================================================================
-- 023_organization_contact_info.sql
-- =======================================================================
-- 023_organization_contact_info.sql
-- Fase 2 (Definições): dados de contacto da empresa, usados na página
-- /settings e futuramente em documentos/recibos.

alter table public.organizations
  add column if not exists phone text,
  add column if not exists email text,
  add column if not exists address text;

-- =======================================================================
-- 024_dashboard_extra_stats.sql
-- =======================================================================
-- 024_dashboard_extra_stats.sql
-- Fase 7 (Dashboard): mais métricas úteis no ecrã inicial, sem duplicar
-- lógica — tudo continua a vir de agregações sobre as tabelas já
-- existentes, com a mesma RLS "security invoker" da view original.

create or replace view public.dashboard_totals as
select
  o.id as organization_id,
  coalesce(sum(i.amount) filter (where i.status = 'paid'), 0) as total_received,
  coalesce(sum(i.amount) filter (where i.status = 'pending'), 0) as total_pending,
  coalesce(sum(i.amount) filter (where i.status = 'overdue'), 0) as total_overdue,
  count(distinct c.id) filter (where c.status = 'active') as active_clients,
  count(distinct i.id) filter (
    where date_trunc('month', i.created_at) = date_trunc('month', now())
  ) as invoices_this_month,
  count(distinct s.id) filter (where s.status = 'active') as active_subscriptions
from public.organizations o
left join public.invoices i on i.organization_id = o.id
left join public.clients c on c.organization_id = o.id
left join public.subscriptions s on s.organization_id = o.id
group by o.id;

-- Cobranças vencidas mais recentes, para o widget "Vencidas recentemente"
-- do dashboard. security invoker (por omissão) — herda a RLS de invoices.
create or replace function public.get_recent_overdue_invoices(p_organization_id uuid, p_limit integer default 5)
returns table (
  id uuid,
  invoice_number text,
  client_name text,
  amount numeric,
  currency text,
  due_date date
)
language sql
stable
as $$
  select i.id, i.invoice_number, c.name, i.amount, i.currency, i.due_date
  from public.invoices i
  join public.clients c on c.id = i.client_id
  where i.organization_id = p_organization_id and i.status = 'overdue'
  order by i.due_date desc
  limit greatest(1, least(p_limit, 50));
$$;
