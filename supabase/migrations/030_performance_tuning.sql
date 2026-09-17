-- 030_performance_tuning.sql
--
-- Afinações de performance identificadas pelo advisor do Supabase depois
-- da migration 029 (já aplicadas directamente no projecto real via MCP;
-- este ficheiro só documenta/reproduz essas alterações no histórico de
-- migrations do repositório para manter o schema_full.sql e uma instalação
-- nova a partir de zero sincronizados com o que está em produção):
--
--   1. Índices em falta nas foreign keys mais consultadas (evita "seq scan"
--      em tabelas que crescem com o uso: pedidos de upgrade, lembretes,
--      histórico de facturação recorrente e subscrições).
--   2. Reescreve 5 políticas RLS que chamavam auth.uid() directamente em
--      cada linha avaliada, para usarem "(select auth.uid())" — o Postgres
--      cacheia o resultado do sub-select uma vez por query em vez de o
--      reavaliar por linha, o que importa em tabelas grandes.

-- 1. Índices em falta nas foreign keys.
create index if not exists idx_purchase_intents_requested_by on public.purchase_intents (requested_by);
create index if not exists idx_reminders_invoice_id on public.reminders (invoice_id);
create index if not exists idx_billing_log_invoice_id on public.subscription_billing_log (invoice_id);
create index if not exists idx_subscriptions_service_id on public.subscriptions (service_id);

-- 2. Políticas RLS reescritas para "(select auth.uid())".
drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own on public.profiles
  for select
  using (id = (select auth.uid()));

drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles
  for insert
  with check (id = (select auth.uid()));

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles
  for update
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

drop policy if exists organizations_insert_authenticated on public.organizations;
create policy organizations_insert_authenticated on public.organizations
  for insert
  with check ((select auth.uid()) is not null);

drop policy if exists org_members_insert_first_owner on public.organization_members;
create policy org_members_insert_first_owner on public.organization_members
  for insert
  with check (
    user_id = (select auth.uid())
    and role = 'owner'
    and not exists (
      select 1 from public.organization_members existing
      where existing.organization_id = organization_members.organization_id
    )
  );
