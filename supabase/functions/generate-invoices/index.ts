// supabase/functions/generate-invoices/index.ts
//
// Corre periodicamente (via Supabase Cron / pg_cron+pg_net — ver README)
// e executa, por esta ordem, três tarefas de manutenção que só podem ser
// feitas com privilégios de service_role:
//
//   1. generate_recurring_invoices() — gera as invoices em falta a partir
//      das subscriptions activas (idempotente: ver
//      supabase/migrations/017_recurring_billing.sql).
//   2. mark_overdue_invoices()       — marca como "overdue" as invoices
//      pendentes cuja due_date já passou.
//   3. end_expired_subscriptions()   — marca como "ended" as subscriptions
//      cuja end_date já passou.
//
// Segurança:
//   - A SUPABASE_SERVICE_ROLE_KEY nunca é exposta ao browser — só existe
//     aqui, no ambiente de execução da Edge Function, injectada
//     automaticamente pela plataforma Supabase (sem qualquer configuração
//     manual — ao contrário de secrets custom, que se mostraram não
//     fiáveis neste projecto, ver abaixo).
//   - Esta função só aceita pedidos que incluam o cabeçalho
//     "x-cron-secret" com o valor exacto guardado em
//     public.app_secrets (key = 'cron_secret') — ver migration
//     031_cron_secret_in_db.sql. Isto evita que alguém que descubra o
//     URL da função a consiga invocar.
//     NOTA: inicialmente este segredo vinha de Deno.env.get('CRON_SECRET'),
//     configurado manualmente no Dashboard (Project Settings > Edge
//     Functions > Secrets). Na prática esse valor nunca chegava ao
//     ambiente de execução da função (ficava sempre vazio), pelo que a
//     execução diária falhava sempre com 401. Passou a guardar-se numa
//     tabela normal do Postgres, lida aqui com o mesmo client de
//     service_role já usado para tudo o resto — elimina a dependência de
//     uma configuração externa ao código/migrations.
//   - As três funções Postgres chamadas aqui têm o respectivo "execute"
//     revogado de "authenticated"/"anon" nas migrations — só a
//     service_role as pode invocar, pelo que nenhum utilizador do
//     frontend consegue despoletar esta operação global directamente.

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.4';

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Método não permitido.' }), {
      status: 405,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const providedSecret = req.headers.get('x-cron-secret');

  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');

  if (!supabaseUrl || !serviceRoleKey) {
    console.error('SUPABASE_URL ou SUPABASE_SERVICE_ROLE_KEY em falta no ambiente da função.');
    return new Response(JSON.stringify({ error: 'Configuração do servidor em falta.' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false },
  });

  if (!providedSecret) {
    return new Response(JSON.stringify({ error: 'Não autorizado.' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const { data: secretRow, error: secretError } = await supabase
    .from('app_secrets')
    .select('value')
    .eq('key', 'cron_secret')
    .maybeSingle();

  if (secretError) {
    console.error('Erro ao ler cron_secret:', secretError.message);
    return new Response(JSON.stringify({ error: 'Configuração do servidor em falta.' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const expectedSecret = secretRow?.value;

  if (!expectedSecret || providedSecret !== expectedSecret) {
    return new Response(JSON.stringify({ error: 'Não autorizado.' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const { data: generated, error: generateError } = await supabase.rpc('generate_recurring_invoices');

  if (generateError) {
    console.error('Erro ao gerar cobranças recorrentes:', generateError.message);
    return new Response(JSON.stringify({ error: generateError.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const { data: overdueCount, error: overdueError } = await supabase.rpc('mark_overdue_invoices');
  if (overdueError) {
    console.error('Erro ao marcar cobranças atrasadas:', overdueError.message);
  }

  const { data: endedCount, error: endedError } = await supabase.rpc('end_expired_subscriptions');
  if (endedError) {
    console.error('Erro ao terminar subscrições expiradas:', endedError.message);
  }

  const result = {
    invoices_generated: generated?.length ?? 0,
    generated,
    invoices_marked_overdue: overdueCount ?? 0,
    subscriptions_ended: endedCount ?? 0,
    ran_at: new Date().toISOString(),
  };

  console.log('generate-invoices concluído:', JSON.stringify(result));

  return new Response(JSON.stringify(result), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
});
