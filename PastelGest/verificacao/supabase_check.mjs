// Validação do esquema Supabase do PastelGest num PostgreSQL real (PGlite).
// Uso: node supabase_check.mjs <pastelgest_schema.sql>   (requer: npm i @electric-sql/pglite)
import { PGlite } from '@electric-sql/pglite';
import { readFileSync } from 'fs';

const sql = readFileSync(process.argv[2], 'utf8');
const db = new PGlite();
const results = []; let failures = 0;
const ok = (name, cond) => { results.push(`${cond ? '[OK]   ' : '[FALHA]'} ${name}`); if (!cond) failures++; };
async function fails(name, fn, contains) {
  try { await fn(); ok(name + ' (deveria falhar)', false); }
  catch (e) { ok(`${name} → ${e.message.split('\n')[0]}`, !contains || e.message.includes(contains)); }
}

// Ambiente equivalente ao Supabase: schema auth, auth.uid(), papéis anon/authenticated.
await db.exec(`
  create schema auth;
  create table auth.users (id uuid primary key);
  create or replace function auth.uid() returns uuid language sql stable as
    $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  create role anon nologin; create role authenticated nologin;
  grant usage on schema public to anon, authenticated;
  grant usage on schema auth to anon, authenticated;
  grant execute on function auth.uid() to anon, authenticated;
  insert into auth.users values ('11111111-1111-1111-1111-111111111111'), ('22222222-2222-2222-2222-222222222222');
`);
await db.exec(sql);
ok('Esquema aplicado sem erros', true);
await db.exec(sql);
ok('Esquema reexecutável (idempotente)', true);

const tables = (await db.query(`select count(*)::int n from information_schema.tables where table_schema='public'`)).rows[0].n;
ok(`21 tabelas criadas (${tables})`, tables === 21);
const rls = (await db.query(`select count(*)::int n from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind='r' and c.relrowsecurity`)).rows[0].n;
ok(`RLS activo em todas as tabelas (${rls})`, rls === 21);

const A = '11111111-1111-1111-1111-111111111111', B = '22222222-2222-2222-2222-222222222222';
async function as(user, q, params) {
  await db.exec(`reset role; select set_config('request.jwt.claim.sub', '${user}', false); set role authenticated;`);
  try { return await db.query(q, params); } finally { await db.exec('reset role'); }
}

// Upsert como o PostgREST faz (merge-duplicates)
const upsertClient = `insert into public.clients (id, name, phone, created_at, updated_at, deleted)
  values ($1, $2, '923', 1, $3, 0)
  on conflict (owner_id, id) do update set name = excluded.name, updated_at = excluded.updated_at`;
await as(A, upsertClient, ['c1', 'Maria', 100]);
const r1 = await as(A, `select name, server_seq from public.clients where id='c1'`);
ok('Inserção pelo dono com owner_id = auth.uid() por omissão', r1.rows.length === 1 && r1.rows[0].server_seq > 0);
await as(A, upsertClient, ['c1', 'Maria Lopes', 200]);
const r2 = await as(A, `select name, server_seq from public.clients where id='c1'`);
ok('Upsert com updated_at mais recente actualiza e avança server_seq', r2.rows[0].name === 'Maria Lopes' && r2.rows[0].server_seq > r1.rows[0].server_seq);
await as(A, upsertClient, ['c1', 'Versao antiga', 150]);
const r3 = await as(A, `select name from public.clients where id='c1'`);
ok('Dados antigos NÃO substituem dados mais recentes (trigger LWW)', r3.rows[0].name === 'Maria Lopes');
await as(A, upsertClient, ['c1', 'Maria Lopes', 200]);
const dup = await as(A, `select count(*)::int n from public.clients`);
ok('Reenvio do mesmo registo não duplica', dup.rows[0].n === 1);

// Isolamento entre contas
const rb = await as(B, `select count(*)::int n from public.clients`);
ok('Outra conta não vê os dados (RLS select)', rb.rows[0].n === 0);
await as(B, upsertClient, ['c1', 'Cliente da conta B', 100]);
const ra = await as(A, `select name from public.clients where id='c1'`);
const rbb = await as(B, `select name from public.clients where id='c1'`);
ok('Mesmo id em contas diferentes não colide (PK owner_id,id)', ra.rows[0].name === 'Maria Lopes' && rbb.rows[0].name === 'Cliente da conta B');
await fails('Inserir com owner_id de outra conta (RLS with check)',
  () => as(B, `insert into public.clients (owner_id, id, name, created_at, updated_at, deleted) values ('${A}', 'x', 'x', 1, 1, 0)`), 'row-level security');
const upd = await as(B, `update public.clients set name='hack' where owner_id='${A}' returning id`);
ok('Outra conta não consegue alterar dados alheios (RLS update)', upd.rows.length === 0);
await fails('DELETE é proibido (eliminação apenas lógica)', () => as(A, `delete from public.clients where id='c1'`), 'permission denied');
await fails('Papel anon não acede aos dados', async () => {
  await db.exec(`reset role; set role anon;`);
  try { await db.query(`select * from public.clients`); } finally { await db.exec('reset role'); }
}, 'permission denied');

// Tabelas-razão: só inserção; ignore-duplicates
const insPay = `insert into public.payments (id, client_id, amount_cents, kind, date, created_at, updated_at, deleted)
  values ('p1', 'c1', 5000, 'DIVIDA', 1, 1, 1, 0) on conflict (owner_id, id) do nothing`;
await as(A, insPay);
await as(A, insPay);
const np = await as(A, `select count(*)::int n from public.payments`);
ok('Pagamento reenviado não é duplicado (ignore-duplicates)', np.rows[0].n === 1);
await fails('Pagamentos são imutáveis (sem UPDATE)', () => as(A, `update public.payments set amount_cents = 1`), 'permission denied');
await fails('Chave estrangeira (pagamento de cliente inexistente)',
  () => as(A, `insert into public.payments (id, client_id, amount_cents, created_at, updated_at, deleted, date) values ('p2', 'nao-existe', 1, 1, 1, 0, 1)`), 'foreign key');

// Cursor de recepção
const seq = await as(A, `select id from public.clients where server_seq > 0 order by server_seq asc limit 500`);
ok('Consulta incremental por server_seq', seq.rows.length === 1);

console.log('\n==== Validação do esquema Supabase (PostgreSQL ' + (await db.query('show server_version')).rows[0].server_version + ') ====');
for (const r of results) console.log('  ' + r);
console.log(`  Total: ${results.length - failures} OK, ${failures} falhas`);
process.exit(failures ? 1 : 0);
