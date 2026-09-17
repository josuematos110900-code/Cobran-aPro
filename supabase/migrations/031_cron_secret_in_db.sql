-- 031_cron_secret_in_db.sql
--
-- Corrige a autenticação do cron diário que chama a Edge Function
-- "generate-invoices" (facturação recorrente, cobranças em atraso e fim
-- de subscrições expiradas).
--
-- Problema encontrado em produção: a função lia o segredo esperado de
-- Deno.env.get('CRON_SECRET'), configurado manualmente no Supabase
-- Dashboard (Project Settings > Edge Functions > Secrets). Apesar de
-- configurado, a função reportava sempre CRON_SECRET vazio
-- (cron_secret_is_set: false) — a execução diária às 03:00 falhava
-- sempre com 401 e nenhuma cobrança recorrente era gerada
-- automaticamente. Não existe forma de diagnosticar/corrigir a
-- propagação desse secret específico apenas com acesso à base de dados.
--
-- Solução: deixar de depender de um secret configurado manualmente no
-- Dashboard. O segredo passa a viver numa tabela normal do Postgres,
-- só acessível a service_role — tanto o job do pg_cron (que corre com
-- privilégios de superutilizador e ignora RLS) como a própria Edge
-- Function (que já usa SUPABASE_SERVICE_ROLE_KEY, esse sim sempre
-- injectado automaticamente pela plataforma, sem configuração manual)
-- o conseguem ler. Isto elimina o único ponto de falha que dependia de
-- uma configuração externa ao código/migrations.

create table if not exists public.app_secrets (
  key text primary key,
  value text not null,
  updated_at timestamptz not null default now()
);

comment on table public.app_secrets is
  'Segredos internos da aplicação (ex: cron_secret). Nunca exposto via API — sem policies de RLS e sem grants a anon/authenticated, só acessível por service_role ou pelo role que corre as migrations.';

alter table public.app_secrets enable row level security;
-- Sem policies: por omissão, ninguém (anon/authenticated) consegue
-- ler ou escrever. service_role ignora RLS, como sempre no Supabase.

revoke all on public.app_secrets from public, anon, authenticated;

-- Gera um novo segredo aleatório de 32 bytes (64 caracteres hex) e
-- substitui o anterior, que nunca chegou a funcionar.
insert into public.app_secrets (key, value, updated_at)
values ('cron_secret', encode(gen_random_bytes(32), 'hex'), now())
on conflict (key) do update set value = excluded.value, updated_at = now();

-- Reagenda o job diário para ler o segredo directamente da tabela em
-- cada execução, em vez de o ter gravado como literal fixo no comando
-- (o que tornava fácil ficar dessincronizado do lado da função).
select cron.unschedule(jobid) from cron.job where jobname = 'generate-invoices-daily';

select cron.schedule(
  'generate-invoices-daily',
  '0 3 * * *',
  $$
  select net.http_post(
    url := 'https://woucfdhotryzfyvzmzdd.supabase.co/functions/v1/generate-invoices',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', (select value from public.app_secrets where key = 'cron_secret')
    ),
    body := '{}'::jsonb
  );
  $$
);
