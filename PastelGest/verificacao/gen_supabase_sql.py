#!/usr/bin/env python3
"""Gera supabase/pastelgest_schema.sql a partir do esquema Room (paridade de colunas garantida)."""
import re, sys
room = open(sys.argv[1]).read()
out = sys.argv[2]
LEDGER = {"stock_movements","product_movements","purchase_payments","payments","audit_log",
          "recipe_cost_snapshots","production_items","sale_items","purchase_items"}
ORDER = ["app_settings","suppliers","clients","ingredients","products","recipes","recipe_ingredients",
         "recipe_cost_snapshots","purchases","purchase_items","purchase_payments","productions",
         "production_items","stock_movements","product_movements","sales","sale_items","debts",
         "payments","expenses","audit_log"]
def snake(s): return re.sub(r'([A-Z])', lambda m: '_' + m.group(1).lower(), s)
tables = {}
for m in re.finditer(r'CREATE TABLE IF NOT EXISTS `(\w+)` \((.*)\);', room):
    t, body = m.group(1), m.group(2)
    cols, fks = [], []
    for part in re.split(r', (?=`|PRIMARY|FOREIGN)', body):
        cm = re.match(r'`(\w+)` (\w+)( NOT NULL)?', part)
        if cm:
            cols.append((cm.group(1), cm.group(2), bool(cm.group(3))))
        fm = re.match(r'FOREIGN KEY\(`(\w+)`\) REFERENCES `(\w+)`', part)
        if fm: fks.append((fm.group(1), fm.group(2)))
    tables[t] = (cols, fks)
idx = re.findall(r'CREATE (UNIQUE )?INDEX IF NOT EXISTS `(\w+)` ON `(\w+)` \(([^)]*)\);', room)

L = []
w = L.append
w("""-- =============================================================================
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
""")
for t in ORDER:
    cols, fks = tables[t]
    w(f"-- ---------------------------------------------------------------- {t}")
    w(f"create table if not exists public.{t} (")
    w("  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,")
    for name, typ, nn in cols:
        if name == "syncState": continue
        pg = "text" if typ == "TEXT" else "bigint"
        null = " not null" if nn else ""
        default = " default 0" if pg == "bigint" and nn else ""
        w(f"  {snake(name)} {pg}{null}{default},")
    w("  server_seq bigint not null default 0,")
    w("  server_updated_at timestamptz not null default now(),")
    w("  primary key (owner_id, id)")
    w(");")
    for col, parent in fks:
        cname = f"{t}_{snake(col)}_fkey"
        w(f"do $$ begin\n  alter table public.{t} add constraint {cname}\n    foreign key (owner_id, {snake(col)}) references public.{parent}(owner_id, id);\nexception when duplicate_object then null; end $$;")
    w(f"create index if not exists {t}_owner_seq_idx on public.{t} (owner_id, server_seq);")
    for uniq, iname, it, icols in idx:
        if it != t: continue
        cl = ", ".join(snake(c.strip('` ')) for c in icols.split(","))
        u = "unique " if uniq else ""
        w(f"create {u}index if not exists {iname} on public.{t} (owner_id, {cl});")
    w(f"drop trigger if exists {t}_sync_stamp on public.{t};")
    w(f"create trigger {t}_sync_stamp before insert or update on public.{t}\n  for each row execute function public.pg_sync_stamp();")
    w(f"alter table public.{t} enable row level security;")
    w(f"drop policy if exists {t}_select on public.{t};")
    w(f"create policy {t}_select on public.{t} for select to authenticated using (owner_id = (select auth.uid()));")
    w(f"drop policy if exists {t}_insert on public.{t};")
    w(f"create policy {t}_insert on public.{t} for insert to authenticated with check (owner_id = (select auth.uid()));")
    w(f"drop policy if exists {t}_update on public.{t};")
    if t not in LEDGER:
        w(f"create policy {t}_update on public.{t} for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));")
    else:
        w(f"-- {t}: tabela-razão imutável (sem UPDATE nem DELETE)")
    w(f"revoke delete on public.{t} from anon, authenticated;")
    w(f"revoke all on public.{t} from anon;")
    w(f"grant select, insert{'' if t in LEDGER else ', update'} on public.{t} to authenticated;")
    w("")
w("grant usage, select on sequence public.pg_sync_seq to authenticated;")
open(out, "w").write("\n".join(L) + "\n")
print("tabelas:", len(ORDER), "| tabelas-razão:", len(LEDGER))
