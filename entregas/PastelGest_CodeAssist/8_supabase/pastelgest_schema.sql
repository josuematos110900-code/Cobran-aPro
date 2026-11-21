-- =============================================================================
-- PastelGest — Esquema Supabase (PostgreSQL)
-- Gerado a partir do esquema Room da aplicação (mesmas colunas, em snake_case).
-- Executar no Supabase: SQL Editor > New query > colar este ficheiro > Run.
-- Seguro para reexecução (IF NOT EXISTS / CREATE OR REPLACE).
--
-- Modelo de segurança:
--  * Cada linha pertence a um utilizador autenticado (owner_id = auth.uid()).
--  * RLS activo em todas as tabelas: cada conta só vê e altera os seus dados.
--  * Tabelas-razão (movimentos, pagamentos, auditoria...) só aceitam inserção:
--    não existe política de UPDATE nem de DELETE (histórico imutável).
--  * Nenhuma tabela tem política de DELETE: a aplicação usa eliminação lógica.
--  * A aplicação Android usa apenas a chave pública (anon/publishable).
--
-- Sincronização:
--  * Chave primária (owner_id, id); o id é o UUID gerado no dispositivo.
--  * Trigger pg_sync_stamp: atribui server_seq (cursor de recepção) e impede que
--    um registo com updated_at mais antigo substitua um mais recente.
-- =============================================================================

create sequence if not exists public.pg_sync_seq;

create or replace function public.pg_sync_stamp()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  if tg_op = 'UPDATE' then
    -- Nunca substituir dados mais recentes por dados antigos (last-write-wins).
    if new.updated_at < old.updated_at then
      return null;
    end if;
    new.owner_id := old.owner_id;
  end if;
  new.server_seq := nextval('public.pg_sync_seq');
  new.server_updated_at := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------- app_settings
create table if not exists public.app_settings (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  value text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
create index if not exists app_settings_owner_seq_idx on public.app_settings (owner_id, server_seq);
drop trigger if exists app_settings_sync_stamp on public.app_settings;
create trigger app_settings_sync_stamp before insert or update on public.app_settings
  for each row execute function public.pg_sync_stamp();
alter table public.app_settings enable row level security;
drop policy if exists app_settings_select on public.app_settings;
create policy app_settings_select on public.app_settings for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists app_settings_insert on public.app_settings;
create policy app_settings_insert on public.app_settings for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists app_settings_update on public.app_settings;
create policy app_settings_update on public.app_settings for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.app_settings from anon, authenticated;
revoke all on public.app_settings from anon;
grant select, insert, update on public.app_settings to authenticated;

-- ---------------------------------------------------------------- suppliers
create table if not exists public.suppliers (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text,
  phone text,
  email text,
  address text,
  nif text,
  notes text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
create index if not exists suppliers_owner_seq_idx on public.suppliers (owner_id, server_seq);
create index if not exists index_suppliers_name on public.suppliers (owner_id, name);
drop trigger if exists suppliers_sync_stamp on public.suppliers;
create trigger suppliers_sync_stamp before insert or update on public.suppliers
  for each row execute function public.pg_sync_stamp();
alter table public.suppliers enable row level security;
drop policy if exists suppliers_select on public.suppliers;
create policy suppliers_select on public.suppliers for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists suppliers_insert on public.suppliers;
create policy suppliers_insert on public.suppliers for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists suppliers_update on public.suppliers;
create policy suppliers_update on public.suppliers for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.suppliers from anon, authenticated;
revoke all on public.suppliers from anon;
grant select, insert, update on public.suppliers to authenticated;

-- ---------------------------------------------------------------- clients
create table if not exists public.clients (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text,
  phone text,
  address text,
  notes text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
create index if not exists clients_owner_seq_idx on public.clients (owner_id, server_seq);
create index if not exists index_clients_name on public.clients (owner_id, name);
create index if not exists index_clients_phone on public.clients (owner_id, phone);
drop trigger if exists clients_sync_stamp on public.clients;
create trigger clients_sync_stamp before insert or update on public.clients
  for each row execute function public.pg_sync_stamp();
alter table public.clients enable row level security;
drop policy if exists clients_select on public.clients;
create policy clients_select on public.clients for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists clients_insert on public.clients;
create policy clients_insert on public.clients for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists clients_update on public.clients;
create policy clients_update on public.clients for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.clients from anon, authenticated;
revoke all on public.clients from anon;
grant select, insert, update on public.clients to authenticated;

-- ---------------------------------------------------------------- ingredients
create table if not exists public.ingredients (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text,
  category text,
  unit_type bigint not null default 0,
  display_unit text,
  stock_qty_milli bigint not null default 0,
  stock_value_cents bigint not null default 0,
  min_stock_milli bigint not null default 0,
  last_purchase_cost_cents bigint not null default 0,
  last_purchase_qty_milli bigint not null default 0,
  last_purchase_date bigint not null default 0,
  supplier_id text,
  notes text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.ingredients add constraint ingredients_supplier_id_fkey
    foreign key (owner_id, supplier_id) references public.suppliers(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists ingredients_owner_seq_idx on public.ingredients (owner_id, server_seq);
create index if not exists index_ingredients_name on public.ingredients (owner_id, name);
create index if not exists index_ingredients_supplierId on public.ingredients (owner_id, supplier_id);
drop trigger if exists ingredients_sync_stamp on public.ingredients;
create trigger ingredients_sync_stamp before insert or update on public.ingredients
  for each row execute function public.pg_sync_stamp();
alter table public.ingredients enable row level security;
drop policy if exists ingredients_select on public.ingredients;
create policy ingredients_select on public.ingredients for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists ingredients_insert on public.ingredients;
create policy ingredients_insert on public.ingredients for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists ingredients_update on public.ingredients;
create policy ingredients_update on public.ingredients for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.ingredients from anon, authenticated;
revoke all on public.ingredients from anon;
grant select, insert, update on public.ingredients to authenticated;

-- ---------------------------------------------------------------- products
create table if not exists public.products (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text,
  recipe_id text,
  sale_price_cents bigint not null default 0,
  stock_qty bigint not null default 0,
  stock_value_cents bigint not null default 0,
  min_stock bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
create index if not exists products_owner_seq_idx on public.products (owner_id, server_seq);
create index if not exists index_products_name on public.products (owner_id, name);
create index if not exists index_products_recipeId on public.products (owner_id, recipe_id);
drop trigger if exists products_sync_stamp on public.products;
create trigger products_sync_stamp before insert or update on public.products
  for each row execute function public.pg_sync_stamp();
alter table public.products enable row level security;
drop policy if exists products_select on public.products;
create policy products_select on public.products for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists products_insert on public.products;
create policy products_insert on public.products for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists products_update on public.products;
create policy products_update on public.products for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.products from anon, authenticated;
revoke all on public.products from anon;
grant select, insert, update on public.products to authenticated;

-- ---------------------------------------------------------------- recipes
create table if not exists public.recipes (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text,
  description text,
  photo_path text,
  yield_qty bigint not null default 0,
  packaging_cost_cents bigint not null default 0,
  other_cost_cents bigint not null default 0,
  suggested_price_cents bigint not null default 0,
  product_id text,
  last_unit_cost_cents bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
create index if not exists recipes_owner_seq_idx on public.recipes (owner_id, server_seq);
create index if not exists index_recipes_name on public.recipes (owner_id, name);
create index if not exists index_recipes_productId on public.recipes (owner_id, product_id);
drop trigger if exists recipes_sync_stamp on public.recipes;
create trigger recipes_sync_stamp before insert or update on public.recipes
  for each row execute function public.pg_sync_stamp();
alter table public.recipes enable row level security;
drop policy if exists recipes_select on public.recipes;
create policy recipes_select on public.recipes for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists recipes_insert on public.recipes;
create policy recipes_insert on public.recipes for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists recipes_update on public.recipes;
create policy recipes_update on public.recipes for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.recipes from anon, authenticated;
revoke all on public.recipes from anon;
grant select, insert, update on public.recipes to authenticated;

-- ---------------------------------------------------------------- recipe_ingredients
create table if not exists public.recipe_ingredients (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  recipe_id text,
  ingredient_id text,
  qty_milli bigint not null default 0,
  entered_unit text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.recipe_ingredients add constraint recipe_ingredients_recipe_id_fkey
    foreign key (owner_id, recipe_id) references public.recipes(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.recipe_ingredients add constraint recipe_ingredients_ingredient_id_fkey
    foreign key (owner_id, ingredient_id) references public.ingredients(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists recipe_ingredients_owner_seq_idx on public.recipe_ingredients (owner_id, server_seq);
create index if not exists index_recipe_ingredients_recipeId on public.recipe_ingredients (owner_id, recipe_id);
create index if not exists index_recipe_ingredients_ingredientId on public.recipe_ingredients (owner_id, ingredient_id);
drop trigger if exists recipe_ingredients_sync_stamp on public.recipe_ingredients;
create trigger recipe_ingredients_sync_stamp before insert or update on public.recipe_ingredients
  for each row execute function public.pg_sync_stamp();
alter table public.recipe_ingredients enable row level security;
drop policy if exists recipe_ingredients_select on public.recipe_ingredients;
create policy recipe_ingredients_select on public.recipe_ingredients for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists recipe_ingredients_insert on public.recipe_ingredients;
create policy recipe_ingredients_insert on public.recipe_ingredients for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists recipe_ingredients_update on public.recipe_ingredients;
create policy recipe_ingredients_update on public.recipe_ingredients for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.recipe_ingredients from anon, authenticated;
revoke all on public.recipe_ingredients from anon;
grant select, insert, update on public.recipe_ingredients to authenticated;

-- ---------------------------------------------------------------- recipe_cost_snapshots
create table if not exists public.recipe_cost_snapshots (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  recipe_id text,
  date bigint not null default 0,
  yield_qty bigint not null default 0,
  ingredients_cost_cents bigint not null default 0,
  packaging_cost_cents bigint not null default 0,
  other_cost_cents bigint not null default 0,
  total_cost_cents bigint not null default 0,
  unit_cost_cents bigint not null default 0,
  details_json text,
  reason text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.recipe_cost_snapshots add constraint recipe_cost_snapshots_recipe_id_fkey
    foreign key (owner_id, recipe_id) references public.recipes(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists recipe_cost_snapshots_owner_seq_idx on public.recipe_cost_snapshots (owner_id, server_seq);
create index if not exists index_recipe_cost_snapshots_recipeId on public.recipe_cost_snapshots (owner_id, recipe_id);
create index if not exists index_recipe_cost_snapshots_date on public.recipe_cost_snapshots (owner_id, date);
drop trigger if exists recipe_cost_snapshots_sync_stamp on public.recipe_cost_snapshots;
create trigger recipe_cost_snapshots_sync_stamp before insert or update on public.recipe_cost_snapshots
  for each row execute function public.pg_sync_stamp();
alter table public.recipe_cost_snapshots enable row level security;
drop policy if exists recipe_cost_snapshots_select on public.recipe_cost_snapshots;
create policy recipe_cost_snapshots_select on public.recipe_cost_snapshots for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists recipe_cost_snapshots_insert on public.recipe_cost_snapshots;
create policy recipe_cost_snapshots_insert on public.recipe_cost_snapshots for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists recipe_cost_snapshots_update on public.recipe_cost_snapshots;
-- recipe_cost_snapshots: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.recipe_cost_snapshots from anon, authenticated;
revoke all on public.recipe_cost_snapshots from anon;
grant select, insert on public.recipe_cost_snapshots to authenticated;

-- ---------------------------------------------------------------- purchases
create table if not exists public.purchases (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  number text,
  supplier_id text,
  date bigint not null default 0,
  subtotal_cents bigint not null default 0,
  discount_cents bigint not null default 0,
  total_cents bigint not null default 0,
  paid_cents bigint not null default 0,
  payment_method text,
  payment_status text,
  status text,
  corrects_purchase_id text,
  cancel_reason text,
  notes text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.purchases add constraint purchases_supplier_id_fkey
    foreign key (owner_id, supplier_id) references public.suppliers(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists purchases_owner_seq_idx on public.purchases (owner_id, server_seq);
create index if not exists index_purchases_supplierId on public.purchases (owner_id, supplier_id);
create index if not exists index_purchases_date on public.purchases (owner_id, date);
create index if not exists index_purchases_correctsPurchaseId on public.purchases (owner_id, corrects_purchase_id);
drop trigger if exists purchases_sync_stamp on public.purchases;
create trigger purchases_sync_stamp before insert or update on public.purchases
  for each row execute function public.pg_sync_stamp();
alter table public.purchases enable row level security;
drop policy if exists purchases_select on public.purchases;
create policy purchases_select on public.purchases for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists purchases_insert on public.purchases;
create policy purchases_insert on public.purchases for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists purchases_update on public.purchases;
create policy purchases_update on public.purchases for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.purchases from anon, authenticated;
revoke all on public.purchases from anon;
grant select, insert, update on public.purchases to authenticated;

-- ---------------------------------------------------------------- purchase_items
create table if not exists public.purchase_items (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  purchase_id text,
  ingredient_id text,
  entered_unit text,
  qty_milli bigint not null default 0,
  unit_price_cents bigint not null default 0,
  line_discount_cents bigint not null default 0,
  line_total_cents bigint not null default 0,
  allocated_total_cents bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.purchase_items add constraint purchase_items_purchase_id_fkey
    foreign key (owner_id, purchase_id) references public.purchases(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.purchase_items add constraint purchase_items_ingredient_id_fkey
    foreign key (owner_id, ingredient_id) references public.ingredients(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists purchase_items_owner_seq_idx on public.purchase_items (owner_id, server_seq);
create index if not exists index_purchase_items_purchaseId on public.purchase_items (owner_id, purchase_id);
create index if not exists index_purchase_items_ingredientId on public.purchase_items (owner_id, ingredient_id);
drop trigger if exists purchase_items_sync_stamp on public.purchase_items;
create trigger purchase_items_sync_stamp before insert or update on public.purchase_items
  for each row execute function public.pg_sync_stamp();
alter table public.purchase_items enable row level security;
drop policy if exists purchase_items_select on public.purchase_items;
create policy purchase_items_select on public.purchase_items for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists purchase_items_insert on public.purchase_items;
create policy purchase_items_insert on public.purchase_items for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists purchase_items_update on public.purchase_items;
-- purchase_items: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.purchase_items from anon, authenticated;
revoke all on public.purchase_items from anon;
grant select, insert on public.purchase_items to authenticated;

-- ---------------------------------------------------------------- purchase_payments
create table if not exists public.purchase_payments (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  purchase_id text,
  amount_cents bigint not null default 0,
  method text,
  date bigint not null default 0,
  note text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.purchase_payments add constraint purchase_payments_purchase_id_fkey
    foreign key (owner_id, purchase_id) references public.purchases(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists purchase_payments_owner_seq_idx on public.purchase_payments (owner_id, server_seq);
create index if not exists index_purchase_payments_purchaseId on public.purchase_payments (owner_id, purchase_id);
create index if not exists index_purchase_payments_date on public.purchase_payments (owner_id, date);
drop trigger if exists purchase_payments_sync_stamp on public.purchase_payments;
create trigger purchase_payments_sync_stamp before insert or update on public.purchase_payments
  for each row execute function public.pg_sync_stamp();
alter table public.purchase_payments enable row level security;
drop policy if exists purchase_payments_select on public.purchase_payments;
create policy purchase_payments_select on public.purchase_payments for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists purchase_payments_insert on public.purchase_payments;
create policy purchase_payments_insert on public.purchase_payments for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists purchase_payments_update on public.purchase_payments;
-- purchase_payments: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.purchase_payments from anon, authenticated;
revoke all on public.purchase_payments from anon;
grant select, insert on public.purchase_payments to authenticated;

-- ---------------------------------------------------------------- productions
create table if not exists public.productions (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  number text,
  recipe_id text,
  product_id text,
  date bigint not null default 0,
  planned_qty bigint not null default 0,
  good_qty bigint not null default 0,
  lost_qty bigint not null default 0,
  ingredients_cost_cents bigint not null default 0,
  packaging_cost_cents bigint not null default 0,
  other_cost_cents bigint not null default 0,
  total_cost_cents bigint not null default 0,
  unit_cost_cents bigint not null default 0,
  cost_snapshot_id text,
  notes text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.productions add constraint productions_recipe_id_fkey
    foreign key (owner_id, recipe_id) references public.recipes(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.productions add constraint productions_product_id_fkey
    foreign key (owner_id, product_id) references public.products(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists productions_owner_seq_idx on public.productions (owner_id, server_seq);
create index if not exists index_productions_recipeId on public.productions (owner_id, recipe_id);
create index if not exists index_productions_productId on public.productions (owner_id, product_id);
create index if not exists index_productions_date on public.productions (owner_id, date);
drop trigger if exists productions_sync_stamp on public.productions;
create trigger productions_sync_stamp before insert or update on public.productions
  for each row execute function public.pg_sync_stamp();
alter table public.productions enable row level security;
drop policy if exists productions_select on public.productions;
create policy productions_select on public.productions for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists productions_insert on public.productions;
create policy productions_insert on public.productions for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists productions_update on public.productions;
create policy productions_update on public.productions for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.productions from anon, authenticated;
revoke all on public.productions from anon;
grant select, insert, update on public.productions to authenticated;

-- ---------------------------------------------------------------- production_items
create table if not exists public.production_items (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  production_id text,
  ingredient_id text,
  qty_milli bigint not null default 0,
  value_cents bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.production_items add constraint production_items_production_id_fkey
    foreign key (owner_id, production_id) references public.productions(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.production_items add constraint production_items_ingredient_id_fkey
    foreign key (owner_id, ingredient_id) references public.ingredients(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists production_items_owner_seq_idx on public.production_items (owner_id, server_seq);
create index if not exists index_production_items_productionId on public.production_items (owner_id, production_id);
create index if not exists index_production_items_ingredientId on public.production_items (owner_id, ingredient_id);
drop trigger if exists production_items_sync_stamp on public.production_items;
create trigger production_items_sync_stamp before insert or update on public.production_items
  for each row execute function public.pg_sync_stamp();
alter table public.production_items enable row level security;
drop policy if exists production_items_select on public.production_items;
create policy production_items_select on public.production_items for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists production_items_insert on public.production_items;
create policy production_items_insert on public.production_items for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists production_items_update on public.production_items;
-- production_items: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.production_items from anon, authenticated;
revoke all on public.production_items from anon;
grant select, insert on public.production_items to authenticated;

-- ---------------------------------------------------------------- stock_movements
create table if not exists public.stock_movements (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  ingredient_id text,
  type text,
  qty_milli bigint not null default 0,
  value_cents bigint not null default 0,
  ref_type text,
  ref_id text,
  note text,
  date bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.stock_movements add constraint stock_movements_ingredient_id_fkey
    foreign key (owner_id, ingredient_id) references public.ingredients(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists stock_movements_owner_seq_idx on public.stock_movements (owner_id, server_seq);
create index if not exists index_stock_movements_ingredientId on public.stock_movements (owner_id, ingredient_id);
create index if not exists index_stock_movements_date on public.stock_movements (owner_id, date);
create index if not exists index_stock_movements_type on public.stock_movements (owner_id, type);
create index if not exists index_stock_movements_refType_refId on public.stock_movements (owner_id, ref_type, ref_id);
drop trigger if exists stock_movements_sync_stamp on public.stock_movements;
create trigger stock_movements_sync_stamp before insert or update on public.stock_movements
  for each row execute function public.pg_sync_stamp();
alter table public.stock_movements enable row level security;
drop policy if exists stock_movements_select on public.stock_movements;
create policy stock_movements_select on public.stock_movements for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists stock_movements_insert on public.stock_movements;
create policy stock_movements_insert on public.stock_movements for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists stock_movements_update on public.stock_movements;
-- stock_movements: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.stock_movements from anon, authenticated;
revoke all on public.stock_movements from anon;
grant select, insert on public.stock_movements to authenticated;

-- ---------------------------------------------------------------- product_movements
create table if not exists public.product_movements (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  product_id text,
  type text,
  qty bigint not null default 0,
  value_cents bigint not null default 0,
  ref_type text,
  ref_id text,
  note text,
  date bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.product_movements add constraint product_movements_product_id_fkey
    foreign key (owner_id, product_id) references public.products(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists product_movements_owner_seq_idx on public.product_movements (owner_id, server_seq);
create index if not exists index_product_movements_productId on public.product_movements (owner_id, product_id);
create index if not exists index_product_movements_date on public.product_movements (owner_id, date);
create index if not exists index_product_movements_type on public.product_movements (owner_id, type);
create index if not exists index_product_movements_refType_refId on public.product_movements (owner_id, ref_type, ref_id);
drop trigger if exists product_movements_sync_stamp on public.product_movements;
create trigger product_movements_sync_stamp before insert or update on public.product_movements
  for each row execute function public.pg_sync_stamp();
alter table public.product_movements enable row level security;
drop policy if exists product_movements_select on public.product_movements;
create policy product_movements_select on public.product_movements for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists product_movements_insert on public.product_movements;
create policy product_movements_insert on public.product_movements for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists product_movements_update on public.product_movements;
-- product_movements: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.product_movements from anon, authenticated;
revoke all on public.product_movements from anon;
grant select, insert on public.product_movements to authenticated;

-- ---------------------------------------------------------------- sales
create table if not exists public.sales (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  number text,
  client_id text,
  date bigint not null default 0,
  subtotal_cents bigint not null default 0,
  discount_cents bigint not null default 0,
  total_cents bigint not null default 0,
  cogs_cents bigint not null default 0,
  paid_cents bigint not null default 0,
  payment_method text,
  payment_type text,
  status text,
  cancel_reason text,
  cancelled_at bigint not null default 0,
  notes text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.sales add constraint sales_client_id_fkey
    foreign key (owner_id, client_id) references public.clients(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists sales_owner_seq_idx on public.sales (owner_id, server_seq);
create index if not exists index_sales_clientId on public.sales (owner_id, client_id);
create index if not exists index_sales_date on public.sales (owner_id, date);
create index if not exists index_sales_status on public.sales (owner_id, status);
drop trigger if exists sales_sync_stamp on public.sales;
create trigger sales_sync_stamp before insert or update on public.sales
  for each row execute function public.pg_sync_stamp();
alter table public.sales enable row level security;
drop policy if exists sales_select on public.sales;
create policy sales_select on public.sales for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists sales_insert on public.sales;
create policy sales_insert on public.sales for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists sales_update on public.sales;
create policy sales_update on public.sales for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.sales from anon, authenticated;
revoke all on public.sales from anon;
grant select, insert, update on public.sales to authenticated;

-- ---------------------------------------------------------------- sale_items
create table if not exists public.sale_items (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  sale_id text,
  product_id text,
  product_name text,
  qty bigint not null default 0,
  unit_price_cents bigint not null default 0,
  discount_cents bigint not null default 0,
  line_total_cents bigint not null default 0,
  cogs_cents bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.sale_items add constraint sale_items_sale_id_fkey
    foreign key (owner_id, sale_id) references public.sales(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.sale_items add constraint sale_items_product_id_fkey
    foreign key (owner_id, product_id) references public.products(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists sale_items_owner_seq_idx on public.sale_items (owner_id, server_seq);
create index if not exists index_sale_items_saleId on public.sale_items (owner_id, sale_id);
create index if not exists index_sale_items_productId on public.sale_items (owner_id, product_id);
drop trigger if exists sale_items_sync_stamp on public.sale_items;
create trigger sale_items_sync_stamp before insert or update on public.sale_items
  for each row execute function public.pg_sync_stamp();
alter table public.sale_items enable row level security;
drop policy if exists sale_items_select on public.sale_items;
create policy sale_items_select on public.sale_items for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists sale_items_insert on public.sale_items;
create policy sale_items_insert on public.sale_items for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists sale_items_update on public.sale_items;
-- sale_items: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.sale_items from anon, authenticated;
revoke all on public.sale_items from anon;
grant select, insert on public.sale_items to authenticated;

-- ---------------------------------------------------------------- debts
create table if not exists public.debts (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  client_id text,
  sale_id text,
  original_cents bigint not null default 0,
  paid_cents bigint not null default 0,
  balance_cents bigint not null default 0,
  due_date bigint not null default 0,
  status text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.debts add constraint debts_client_id_fkey
    foreign key (owner_id, client_id) references public.clients(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.debts add constraint debts_sale_id_fkey
    foreign key (owner_id, sale_id) references public.sales(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists debts_owner_seq_idx on public.debts (owner_id, server_seq);
create index if not exists index_debts_clientId on public.debts (owner_id, client_id);
create unique index if not exists index_debts_saleId on public.debts (owner_id, sale_id);
create index if not exists index_debts_status on public.debts (owner_id, status);
drop trigger if exists debts_sync_stamp on public.debts;
create trigger debts_sync_stamp before insert or update on public.debts
  for each row execute function public.pg_sync_stamp();
alter table public.debts enable row level security;
drop policy if exists debts_select on public.debts;
create policy debts_select on public.debts for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists debts_insert on public.debts;
create policy debts_insert on public.debts for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists debts_update on public.debts;
create policy debts_update on public.debts for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.debts from anon, authenticated;
revoke all on public.debts from anon;
grant select, insert, update on public.debts to authenticated;

-- ---------------------------------------------------------------- payments
create table if not exists public.payments (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  client_id text,
  sale_id text,
  debt_id text,
  amount_cents bigint not null default 0,
  method text,
  kind text,
  receipt_number text,
  date bigint not null default 0,
  note text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
do $$ begin
  alter table public.payments add constraint payments_client_id_fkey
    foreign key (owner_id, client_id) references public.clients(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.payments add constraint payments_sale_id_fkey
    foreign key (owner_id, sale_id) references public.sales(owner_id, id);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.payments add constraint payments_debt_id_fkey
    foreign key (owner_id, debt_id) references public.debts(owner_id, id);
exception when duplicate_object then null; end $$;
create index if not exists payments_owner_seq_idx on public.payments (owner_id, server_seq);
create index if not exists index_payments_clientId on public.payments (owner_id, client_id);
create index if not exists index_payments_saleId on public.payments (owner_id, sale_id);
create index if not exists index_payments_debtId on public.payments (owner_id, debt_id);
create index if not exists index_payments_date on public.payments (owner_id, date);
drop trigger if exists payments_sync_stamp on public.payments;
create trigger payments_sync_stamp before insert or update on public.payments
  for each row execute function public.pg_sync_stamp();
alter table public.payments enable row level security;
drop policy if exists payments_select on public.payments;
create policy payments_select on public.payments for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists payments_insert on public.payments;
create policy payments_insert on public.payments for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists payments_update on public.payments;
-- payments: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.payments from anon, authenticated;
revoke all on public.payments from anon;
grant select, insert on public.payments to authenticated;

-- ---------------------------------------------------------------- expenses
create table if not exists public.expenses (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  category text,
  description text,
  amount_cents bigint not null default 0,
  date bigint not null default 0,
  payment_method text,
  fixed bigint not null default 0,
  receipt_path text,
  status text,
  cancel_reason text,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
create index if not exists expenses_owner_seq_idx on public.expenses (owner_id, server_seq);
create index if not exists index_expenses_date on public.expenses (owner_id, date);
create index if not exists index_expenses_category on public.expenses (owner_id, category);
drop trigger if exists expenses_sync_stamp on public.expenses;
create trigger expenses_sync_stamp before insert or update on public.expenses
  for each row execute function public.pg_sync_stamp();
alter table public.expenses enable row level security;
drop policy if exists expenses_select on public.expenses;
create policy expenses_select on public.expenses for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists expenses_insert on public.expenses;
create policy expenses_insert on public.expenses for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists expenses_update on public.expenses;
create policy expenses_update on public.expenses for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke delete on public.expenses from anon, authenticated;
revoke all on public.expenses from anon;
grant select, insert, update on public.expenses to authenticated;

-- ---------------------------------------------------------------- audit_log
create table if not exists public.audit_log (
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  entity text,
  entity_id text,
  action text,
  details text,
  device text,
  date bigint not null default 0,
  id text not null,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted bigint not null default 0,
  server_seq bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, id)
);
create index if not exists audit_log_owner_seq_idx on public.audit_log (owner_id, server_seq);
create index if not exists index_audit_log_entity on public.audit_log (owner_id, entity);
create index if not exists index_audit_log_entityId on public.audit_log (owner_id, entity_id);
create index if not exists index_audit_log_date on public.audit_log (owner_id, date);
drop trigger if exists audit_log_sync_stamp on public.audit_log;
create trigger audit_log_sync_stamp before insert or update on public.audit_log
  for each row execute function public.pg_sync_stamp();
alter table public.audit_log enable row level security;
drop policy if exists audit_log_select on public.audit_log;
create policy audit_log_select on public.audit_log for select to authenticated using (owner_id = (select auth.uid()));
drop policy if exists audit_log_insert on public.audit_log;
create policy audit_log_insert on public.audit_log for insert to authenticated with check (owner_id = (select auth.uid()));
drop policy if exists audit_log_update on public.audit_log;
-- audit_log: tabela-razão imutável (sem UPDATE nem DELETE)
revoke delete on public.audit_log from anon, authenticated;
revoke all on public.audit_log from anon;
grant select, insert on public.audit_log to authenticated;

grant usage, select on sequence public.pg_sync_seq to authenticated;
