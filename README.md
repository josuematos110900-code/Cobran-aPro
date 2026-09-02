# CobrançaPro

Micro-SaaS de gestão de clientes, cobranças recorrentes e pagamentos —
construído inicialmente para o mercado de Angola.

> "Nunca mais perca uma cobrança."

## Stack

- **Frontend:** React 18 + Vite + TypeScript + Tailwind CSS + React Router + Recharts
- **Backend:** Supabase (PostgreSQL + Auth + Row Level Security)
- **Deploy:** Vercel

---

## 1. Requisitos

- Node.js 18 ou superior
- npm 9 ou superior
- Uma conta gratuita em [supabase.com](https://supabase.com)
- (Opcional) [Supabase CLI](https://supabase.com/docs/guides/cli) para aplicar migrations localmente

---

## 2. Instalação

```bash
npm install
```

---

## 3. Configuração do Supabase

### 3.1. Criar o projecto

1. Aceda a [app.supabase.com](https://app.supabase.com) e crie um novo projecto.
2. Em **Project Settings → API**, copie:
   - **Project URL**
   - **anon public key**

Nunca copie a **service_role key** para esta aplicação — ela nunca deve
existir no frontend.

### 3.2. Aplicar as migrations

As migrations estão em `supabase/migrations/`, numeradas por ordem de
execução (`001_...` a `017_...`). Existem duas formas de as aplicar:

**Opção A — Editor SQL do painel Supabase (mais simples):**

1. Abra **SQL Editor** no painel do seu projecto.
2. Copie e execute o conteúdo de cada ficheiro, na ordem numérica
   (001 → 017).

**Opção B — Supabase CLI (recomendado para equipas):**

```bash
supabase login
supabase link --project-ref SEU_PROJECT_REF
supabase db push
```

### 3.3. Confirmação de email (opcional)

Por omissão, o Supabase Auth exige confirmação de email antes do primeiro
login. Pode ajustar isto em **Authentication → Providers → Email**,
conforme a experiência que preferir para o onboarding.

---

## 4. Variáveis de ambiente

Copie o ficheiro de exemplo:

```bash
cp .env.example .env.local
```

Edite `.env.local` e preencha:

```
VITE_SUPABASE_URL=https://SEU-PROJETO.supabase.co
VITE_SUPABASE_ANON_KEY=SUA_ANON_KEY_PUBLICA
```

Estas são as únicas duas variáveis necessárias no frontend. Qualquer
integração futura que precise de credenciais privadas (WhatsApp Business
API, processadores de pagamento, jobs de cobrança recorrente) deve correr
num ambiente de servidor (Supabase Edge Functions, Vercel Functions, ou
similar) — nunca no browser.

---

## 5. Execução local

```bash
npm run dev
```

A aplicação fica disponível em `http://localhost:5173`.

---

## 6. Verificação de tipos e build

```bash
npm run typecheck   # verifica TypeScript sem gerar ficheiros
npm run build        # build de produção (inclui verificação de tipos)
npm run preview       # pré-visualiza o build de produção localmente
```

---

## 7. Deploy no Vercel

1. Faça push do repositório para o GitHub/GitLab/Bitbucket.
2. Em [vercel.com](https://vercel.com), importe o repositório.
3. Configure o **Framework Preset** como `Vite`.
4. Em **Environment Variables**, adicione:
   - `VITE_SUPABASE_URL`
   - `VITE_SUPABASE_ANON_KEY`
5. Faça deploy.

Não é necessária nenhuma outra configuração de servidor para o MVP actual,
já que toda a lógica de negócio crítica corre no Postgres (via RLS,
triggers e funções) ou no frontend, dentro dos limites de segurança do
Supabase.

---

## 8. Configuração de produção — pontos de atenção

- **RLS:** todas as tabelas de negócio têm Row Level Security activo e
  isolamento por `organization_id`. Antes de lançar em produção, reveja as
  políticas em `supabase/migrations/003_organizations.sql` e confirme que
  correspondem ao modelo de permissões que pretende (owner/admin/staff).
- **Cobranças recorrentes:** a geração automática de `invoices` a partir de
  `subscriptions` **já está implementada** (Edge Function
  `generate-invoices` + funções Postgres idempotentes — ver secção 11).
  Falta apenas configurar o deploy e o agendamento no seu projecto
  Supabase real, também detalhado na secção 11.
- **Cobranças atrasadas:** a função `mark_overdue_invoices()` (que marca
  como `overdue` qualquer invoice `pending` cuja `due_date` já passou) só
  pode ser executada pela `service_role` — o acesso de `authenticated` e
  `anon` foi explicitamente revogado na migration `016_invoices_guards.sql`.
  Isto significa que, em produção, **é obrigatório agendar** esta função
  (ex: Supabase Edge Function invocada por `pg_cron` ou por um Vercel Cron
  Job diário, autenticado com a service role) — sem isso, facturas
  vencidas continuarão a aparecer como "Pendente" até essa tarefa correr.
  A Edge Function `generate-invoices` (secção 11) já chama esta função a
  cada execução — não é preciso agendar isto separadamente.
  Isto foi uma escolha deliberada: nenhum utilizador do frontend deve
  conseguir despoletar uma actualização em massa que afecta todas as
  organizações.
- **Máquina de estados das cobranças:** um trigger
  (`trg_invoices_status_guard`) impede que uma invoice já `paid` ou
  `cancelled` volte a mudar de estado por qualquer via de escrita directa
  na tabela. A única forma segura de pagar uma cobrança é a função RPC
  `mark_invoice_paid()`, que corre numa única transacção (bloqueia a
  linha com `for update`, verifica que não está já paga/cancelada, cria o
  `payment` e actualiza a `invoice`), evitando pagamentos duplicados
  mesmo com duplo-clique ou pedidos em paralelo.
- **Numeração de facturas:** `next_invoice_number()` foi reescrita
  (migration `014_invoice_number_counters.sql`) para usar um contador
  atómico por organização (`insert ... on conflict ... do update`), que é
  seguro sob concorrência alta — a limitação identificada na primeira
  versão (baseada em `count(*)`) já não se aplica.
- **WhatsApp:** o MVP gera apenas um link `wa.me` com a mensagem
  pré-preenchida (ver `settings.whatsapp_template` e o botão "Enviar
  lembrete" em `/invoices/:id`). Cada envio regista uma linha em
  `reminders` para histórico. Não existe integração oficial da WhatsApp
  Business API — isso fica preparado como trabalho futuro, a implementar
  num serviço de backend próprio.
- **Planos e billing:** os limites de cada plano (FREE/BÁSICO/
  PROFISSIONAL/EMPRESA) ainda não são impostos automaticamente pelo
  sistema — a coluna `organizations.plan` existe, mas a validação de
  limites (nº de clientes, cobranças/mês) fica para uma etapa seguinte.

---

## 9. Estrutura do projecto

```text
src/
  components/
    ui/          Botões, inputs, selects, textareas, cards, badges, modal,
                 skeleton, empty state, confirm dialog, paginação, toasts
    layout/      Sidebar, bottom nav, AppShell
  features/
    auth/           Login, registo, recuperação/redefinição de palavra-passe
    onboarding/     Criação da primeira organização
    dashboard/      Página inicial com totais agregados
    clients/        Lista, criar/editar, perfil (/clients/:id) com saldos,
                     histórico e cobranças recorrentes associadas
    services/       Lista, criar/editar, arquivar
    subscriptions/  Cobranças recorrentes: criar/editar, pausar/retomar/
                     cancelar, listagem com próxima data de cobrança
    invoices/       Lista com filtros/totais, criar/editar, marcar como
                     pago (RPC atómica), cancelar, lembrete via WhatsApp
    payments/, reminders/, reports/, settings/   (ainda por implementar)
  contexts/      AuthContext (sessão + organização activa), ToastContext
  routes/        ProtectedRoute
  lib/           Cliente Supabase, tipos, tradução de erros de auth,
                 utilitário de link WhatsApp (wa.me)
supabase/
  migrations/    Schema SQL completo, numerado (001 a 017)
  functions/
    generate-invoices/  Edge Function que gera cobranças recorrentes,
                         marca atrasos e termina subscrições expiradas
                         (ver secção 11)
```

---

## 10. Testes

Ainda não existem testes automatizados neste commit inicial. As
prioridades de teste, conforme o plano do produto, são:

1. Autenticação (login, registo, recuperação de password)
2. Criação de clientes
3. Criação de cobranças
4. Alteração de estado de facturas (pending → paid/overdue → cancelled)
5. Isolamento multi-tenant (um utilizador nunca deve ver dados de outra
   organização)
6. Cálculos financeiros (totais do dashboard, marcação de pagamento)

Recomenda-se Vitest + Testing Library para os componentes React, e testes
de integração SQL (via `pgTAP` ou scripts directos) para validar as
políticas de RLS.

Verificações específicas da geração automática de cobranças (ver secção
11 para os comandos exactos):

- criação de subscrição → `next_billing_date` fica definido correctamente
  logo após o `insert` (trigger `trg_subscriptions_next_billing_insert`);
- cálculo da próxima data para cada periodicidade (semanal, mensal,
  trimestral, anual), incluindo meses mais curtos que o `due_day` (ex:
  dia 31 em Abril) — testável directamente em SQL chamando
  `compute_next_billing_date(...)` com datas de exemplo;
- subscrição com `end_date`: nenhuma invoice é gerada para além dessa
  data, e a subscrição passa a `ended` automaticamente
  (`end_expired_subscriptions()`);
- subscrição `paused`: `generate_recurring_invoices()` ignora-a (o `where`
  da função filtra só `status = 'active'`);
- prevenção de invoice duplicada: chamar `generate_recurring_invoices()`
  duas vezes seguidas e confirmar que a segunda chamada não devolve
  novas linhas para as mesmas subscrições (ver comandos de teste na
  secção 11);
- isolamento entre organizações: `generate_recurring_invoices()` corre
  sobre todas as organizações (é uma tarefa de manutenção global,
  como `mark_overdue_invoices()`), mas cada invoice criada continua
  correctamente associada ao `organization_id` da sua subscrição, e o
  RLS de leitura de `subscription_billing_log`/`invoices` continua
  filtrado por `user_organization_ids()` como em todas as outras
  tabelas.

---

## 11. Geração automática de cobranças recorrentes — Edge Function + Cron

Esta secção documenta o que já está implementado no código e os passos
que **têm de ser feitos manualmente no seu projecto Supabase real**
(não é possível fazê-los a partir deste ambiente de desenvolvimento).

### 11.1. O que a Edge Function faz

O ficheiro `supabase/functions/generate-invoices/index.ts` chama, por
esta ordem, três funções Postgres — todas restritas à `service_role`
(nenhuma é chamável pelo frontend, ver `016_invoices_guards.sql` e
`017_recurring_billing.sql`):

1. `generate_recurring_invoices()` — gera as invoices em falta a partir
   das subscrições activas, de forma idempotente (ver 11.5).
2. `mark_overdue_invoices()` — marca como `overdue` as invoices
   pendentes já vencidas.
3. `end_expired_subscriptions()` — marca como `ended` as subscrições cuja
   `end_date` já passou.

A função devolve um JSON com um resumo (`invoices_generated`,
`invoices_marked_overdue`, `subscriptions_ended`).

### 11.2. Deploy da Edge Function

Com o [Supabase CLI](https://supabase.com/docs/guides/cli) instalado e
autenticado (`supabase login`) e o projecto ligado
(`supabase link --project-ref SEU_PROJECT_REF`):

```bash
supabase functions deploy generate-invoices
```

Isto publica o código tal como está em
`supabase/functions/generate-invoices/index.ts` — não precisa de
alterar nada antes de publicar.

### 11.3. Configurar o `CRON_SECRET`

A função só aceita pedidos que incluam o cabeçalho `x-cron-secret` com
um valor exacto que só você conhece. Isto existe para que ninguém que
descubra o URL da função a consiga invocar e disparar geração de
facturas à vontade.

```bash
supabase secrets set CRON_SECRET=escolha-um-valor-longo-e-aleatorio-aqui
```

**Não precisa de configurar `SUPABASE_URL` nem
`SUPABASE_SERVICE_ROLE_KEY`** — a plataforma Supabase injecta estas
automaticamente em todas as Edge Functions, tanto em produção como em
`supabase functions serve` local. Nunca as defina manualmente nem as
exponha no frontend.

Para confirmar que o secret ficou guardado:

```bash
supabase secrets list
```

### 11.4. Configurar o Cron

O mecanismo actual e recomendado é o **Supabase Cron**, que usa
internamente as extensões `pg_cron` + `pg_net` mas expõe uma interface
simples:

1. No painel do projecto, vá a **Integrations → Cron** (ou **Database →
   Cron**, dependendo da versão do dashboard).
2. Crie um novo Job do tipo **Edge Function**.
3. Seleccione `generate-invoices`.
4. Defina a frequência recomendada: **diariamente**, por exemplo às
   `03:00 UTC` (`0 3 * * *`) — cedo o suficiente para que as facturas do
   dia já estejam geradas quando os utilizadores abrirem o dashboard de
   manhã. Para negócios com subscrições semanais é seguro correr mais
   vezes por dia; a função é idempotente, pelo que correr com mais
   frequência nunca duplica facturas (ver 11.5).
5. Adicione o cabeçalho HTTP `x-cron-secret` com o mesmo valor definido
   em `CRON_SECRET`.
6. Grave o Job.

**Alternativa via SQL** (se preferir gerir o agendamento como código, em
vez da interface do dashboard), a correr uma vez no SQL Editor do seu
projecto (substitua `SEU_PROJECT_REF` e o valor do secret):

```sql
create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.schedule(
  'generate-invoices-daily',
  '0 3 * * *',
  $$
  select net.http_post(
    url := 'https://SEU_PROJECT_REF.supabase.co/functions/v1/generate-invoices',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', 'o-mesmo-valor-do-CRON_SECRET'
    ),
    body := '{}'::jsonb
  );
  $$
);
```

Guardar o segredo directamente numa migration SQL versionada não é
recomendável — prefira criar este `cron.schedule` manualmente no SQL
Editor do dashboard (não faz parte das migrations deste repositório).

**Limitação a ter em conta:** eu não tenho forma de criar, executar ou
verificar este Job a partir deste ambiente — a ligação a `pg_cron`,
`pg_net` e ao URL real da sua função só existe dentro do seu projecto
Supabase. Os passos acima são exactos, mas têm de ser executados por si.

### 11.5. Como testar manualmente

**Chamar a função directamente por HTTP** (fora do horário do cron, para
testar):

```bash
curl -i -X POST \
  'https://SEU_PROJECT_REF.supabase.co/functions/v1/generate-invoices' \
  -H 'Content-Type: application/json' \
  -H 'x-cron-secret: o-mesmo-valor-do-CRON_SECRET'
```

Resposta esperada (exemplo):

```json
{
  "invoices_generated": 3,
  "generated": [
    { "out_subscription_id": "...", "out_invoice_id": "...", "out_invoice_number": "FAT-000042", "out_billing_period_start": "2026-09-01" }
  ],
  "invoices_marked_overdue": 1,
  "subscriptions_ended": 0,
  "ran_at": "2026-08-30T03:00:00.000Z"
}
```

Um pedido **sem** o cabeçalho `x-cron-secret`, ou com um valor errado,
deve devolver `401 Unauthorized` — vale a pena confirmar isto também,
para garantir que a função não está acessível publicamente.

**Testar localmente antes de publicar:**

```bash
supabase functions serve generate-invoices --env-file supabase/functions/.env
```

(crie esse `.env` local, fora do controlo de versões, só com
`CRON_SECRET=...` para os testes locais — em produção o Supabase já
injecta `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY` automaticamente,
como referido em 11.3).

**Chamar a função Postgres directamente**, sem passar pela Edge Function
(útil para depurar a lógica de geração em isolado, no SQL Editor —
requer estar autenticado como `service_role` ou correr como
superutilizador no editor, já que o `execute` está restrito):

```sql
select * from public.generate_recurring_invoices();
```

### 11.6. Confirmar que as invoices recorrentes estão a ser geradas

```sql
-- Ver as últimas invoices criadas a partir de uma subscrição
select i.invoice_number, i.due_date, i.status, i.subscription_id
from public.invoices i
where i.subscription_id is not null
order by i.created_at desc
limit 20;

-- Ver o histórico de competências já facturadas por subscrição
select * from public.subscription_billing_log
order by created_at desc
limit 20;

-- Confirmar que next_billing_date avançou depois da última geração
select id, client_id, service_id, last_billing_date, next_billing_date, status
from public.subscriptions
order by updated_at desc
limit 20;
```

### 11.7. Confirmar que NÃO existem invoices duplicadas

A verificação mais directa é sobre a própria chave de idempotência —
se esta query alguma vez devolver linhas, há um problema sério:

```sql
select subscription_id, billing_period_start, count(*)
from public.subscription_billing_log
group by subscription_id, billing_period_start
having count(*) > 1;
```

Como `(subscription_id, billing_period_start)` tem uma constraint
`unique`, esta query **nunca pode devolver linhas** — se devolver, seria
sinal de a constraint ter sido removida manualmente na base de dados.

Teste prático de idempotência (correr a função duas vezes seguidas e
confirmar que a segunda não gera nada novo):

```sql
select count(*) as gerados_primeira_vez
from public.generate_recurring_invoices();
-- deve devolver, por exemplo, 3

select count(*) as gerados_segunda_vez
from public.generate_recurring_invoices();
-- deve devolver 0, porque as competências da primeira chamada já
-- foram registadas em subscription_billing_log
```

Também pode confirmar por invoice: cada `subscription_id` não deve ter
duas invoices com o mesmo `due_date`:

```sql
select subscription_id, due_date, count(*)
from public.invoices
where subscription_id is not null
group by subscription_id, due_date
having count(*) > 1;
```

### 11.8. Como verificar os logs

- **Logs da Edge Function:** painel Supabase → **Edge Functions →
  generate-invoices → Logs**, ou via CLI:
  ```bash
  supabase functions logs generate-invoices
  ```
  Cada execução regista uma linha `generate-invoices concluído: {...}`
  com o resumo, ou uma mensagem de erro clara se alguma das três RPCs
  falhar.
- **Logs do Cron Job:** se usou o Supabase Cron (interface gráfica), o
  histórico de execuções e o respectivo estado (sucesso/falha) aparece
  na própria página do Job em **Integrations → Cron**. Se usou o método
  SQL com `pg_cron`, pode consultar directamente:
  ```sql
  select * from cron.job_run_details
  order by start_time desc
  limit 20;
  ```

### 11.9. Segurança desta função — resumo

- Não existem segredos escritos no código: `CRON_SECRET`,
  `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` são todos lidos de
  variáveis de ambiente (`Deno.env.get(...)`), nunca hardcoded.
  `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY` são injectadas automaticamente pela
  plataforma; só o `CRON_SECRET` precisa de ser definido manualmente
  (secção 11.3).
- A `SERVICE_ROLE_KEY` existe apenas dentro do ambiente de execução da
  Edge Function (servidor) — nunca é enviada ao browser nem referenciada
  em código do frontend (`src/`).
- Qualquer pedido sem o cabeçalho `x-cron-secret` correcto recebe
  `401 Unauthorized` antes de qualquer ligação à base de dados ser
  estabelecida.
- A função só chama as três RPCs estritamente necessárias
  (`generate_recurring_invoices`, `mark_overdue_invoices`,
  `end_expired_subscriptions`) — não expõe nenhum outro endpoint nem
  aceita parâmetros do chamador que influenciem o que é executado.

