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
execução (`001_...` a `029_...`). Existem três formas de as aplicar:

**Opção A — Um único ficheiro (mais rápido, projecto novo/vazio):**

1. Abra **SQL Editor** no painel do seu projecto Supabase.
2. Copie todo o conteúdo de [`supabase/schema_full.sql`](./supabase/schema_full.sql)
   (as 29 migrations já concatenadas pela ordem certa) e cole numa
   query nova.
3. Clique **Run**.

Só usar num projecto Supabase que ainda não tenha nenhuma destas
tabelas — não é pensado para aplicar por cima de um schema parcial.

**Opção B — Editor SQL, ficheiro a ficheiro (mais controlo):**

1. Abra **SQL Editor** no painel do seu projecto.
2. Copie e execute o conteúdo de cada ficheiro em `supabase/migrations/`,
   na ordem numérica (001 → 029).

**Opção C — Supabase CLI (recomendado para equipas):**

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

## 6. Verificação de tipos, testes e build

```bash
npm run typecheck   # verifica TypeScript sem gerar ficheiros
npm run lint         # ESLint (flat config)
npm run test         # testes unitários (Vitest) — validações, WhatsApp, planos
npm run build        # build de produção (inclui verificação de tipos)
npm run preview       # pré-visualiza o build de produção localmente
```

Ver também `PRODUCTION_CHECKLIST.md` para a lista completa de verificação
antes do primeiro lançamento comercial, e
`supabase/tests/manual_security_checks.sql` para os testes de segurança e
regras de negócio que dependem de uma instância Supabase real (isolamento
multi-tenant, permissões, pagamento, recorrência, limites de plano).

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

### 7.1. Deploy no Netlify

O ficheiro `netlify.toml` incluído já define o build e o redirect
necessário para o SPA routing — não precisa de configurar nada disso
manualmente no painel.

1. Faça push do repositório para o GitHub/GitLab/Bitbucket.
2. Em [app.netlify.com](https://app.netlify.com), **Add new site → Import
   an existing project**, escolha o repositório e a branch
   (`claude/cobrancapro-v1-production` ou a que tiver feito merge para
   `main`).
3. O Netlify lê `netlify.toml` automaticamente: **Build command**
   `npm run build`, **Publish directory** `dist`. Não altere isto — se o
   assistente do Netlify sugerir outro valor, confirme que fica exactamente
   `npm run build` / `dist`, senão o site fica a servir os ficheiros fonte
   (`.tsx`) em vez do build, e a app não arranca (erro de "MIME type" no
   browser, exactamente como abrir o `index.html` directamente).
4. Em **Site configuration → Environment variables**, adicione
   `VITE_SUPABASE_URL` e `VITE_SUPABASE_ANON_KEY` com os valores do seu
   projecto Supabase.
5. **Deploy site.** Depois de qualquer alteração às variáveis de
   ambiente, é preciso disparar um novo deploy (**Deploys → Trigger
   deploy**) — o Netlify não aplica variáveis novas a um build já feito.

Se preferir testar rapidamente sem ligar o Git: corra `npm run build`
localmente e arraste a pasta `dist/` gerada para
[app.netlify.com/drop](https://app.netlify.com/drop) — nunca arraste o
repositório inteiro nem o `index.html` sozinho, só o conteúdo de `dist/`
depois do build.

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
  PROFISSIONAL/EMPRESA) são impostos no servidor — ver secção 12.

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
    payments/       Registo de pagamentos (RPC atómica), recibo, histórico
    reminders/      Envio de lembretes via WhatsApp (wa.me), histórico
    reports/        Relatórios por período, ranking de clientes/serviços,
                     exportação CSV
    settings/       Dados da empresa, modelo de WhatsApp, gestão de membros
    billing/        Plano actual, utilização face aos limites, upgrade
  contexts/      AuthContext (sessão + organização activa + papel do
                 utilizador), ToastContext
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

Testes unitários automatizados (Vitest, `npm run test`) cobrem hoje a
lógica pura: validação de formulários (clientes/serviços/cobranças),
normalização de telefone para o link `wa.me`, e consistência dos limites
de plano apresentados na UI face aos aplicados no servidor. O que
depende de uma sessão autenticada e de RLS real (permissões,
isolamento multi-tenant, pagamento atómico, idempotência da recorrência)
está documentado como checklist executável em
`supabase/tests/manual_security_checks.sql`, a correr num projecto
Supabase de teste — não é possível automatizar isto sem uma instância
Supabase viva.

Prioridades de teste, conforme o plano do produto:

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

### 11.3. Segredo do cron (`app_secrets`)

A função só aceita pedidos que incluam o cabeçalho `x-cron-secret` com
um valor exacto. Isto existe para que ninguém que descubra o URL da
função a consiga invocar e disparar geração de facturas à vontade.

Numa versão anterior este valor era lido de `Deno.env.get('CRON_SECRET')`,
configurado manualmente no Dashboard (Project Settings > Edge Functions
> Secrets). **Na prática esse secret nunca chegava ao ambiente de
execução da função** (o Dashboard reportava-o como definido, mas a
função via-o sempre vazio), pelo que a execução diária falhava sempre
com `401` e nenhuma factura recorrente era gerada automaticamente.

A correcção (migration `031_cron_secret_in_db.sql`) deixa de depender de
qualquer configuração manual fora do código: o segredo passou a viver
numa tabela normal do Postgres, `public.app_secrets` (chave
`cron_secret`), sem RLS nem grants a `anon`/`authenticated` — só
`service_role` (e o `postgres` que corre o `pg_cron`) lhe consegue
aceder. A própria migration já gera um valor aleatório de 64 caracteres
e reagenda o job do `pg_cron` para o ler directamente da tabela em cada
execução — não há nada a configurar manualmente no Dashboard.

Para gerar um novo segredo (ex: se suspeitar que foi exposto), corra no
SQL Editor do seu projecto:

```sql
update public.app_secrets
set value = encode(gen_random_bytes(32), 'hex'), updated_at = now()
where key = 'cron_secret';
```

O `cron.schedule` já lê o valor da tabela em cada execução
(`(select value from public.app_secrets where key = 'cron_secret')`),
por isso um novo valor entra em vigor na execução seguinte, sem
precisar de reagendar o job nem de reimplantar a função.

**Não precisa de configurar `SUPABASE_URL` nem
`SUPABASE_SERVICE_ROLE_KEY`** — a plataforma Supabase injecta estas
automaticamente em todas as Edge Functions, tanto em produção como em
`supabase functions serve` local. Nunca as defina manualmente nem as
exponha no frontend.

### 11.4. Configurar o Cron

O agendamento usa `pg_cron` + `pg_net` directamente (migration
`031_cron_secret_in_db.sql`), já aplicado no projecto de produção — o
job `generate-invoices-daily` corre todos os dias às `03:00 UTC`
(`0 3 * * *`), cedo o suficiente para que as facturas do dia já estejam
geradas quando os utilizadores abrirem o dashboard de manhã. A função é
idempotente, pelo que correr com mais frequência nunca duplica facturas
(ver 11.5).

Para consultar ou alterar o agendamento:

```sql
select jobid, jobname, schedule, active from cron.job
where jobname = 'generate-invoices-daily';

-- para alterar a frequência, por exemplo:
select cron.alter_job(job_id := <jobid>, schedule := '0 */6 * * *');
```

Se precisar de recriar o job do zero num projecto novo, a definição
completa está na migration `031_cron_secret_in_db.sql` deste
repositório — corre `cron.schedule(...)` lendo o segredo directamente
de `public.app_secrets`, sem qualquer valor hardcoded.

### 11.5. Como testar manualmente

**Chamar a função directamente por HTTP** (fora do horário do cron, para
testar):

```bash
curl -i -X POST \
  'https://SEU_PROJECT_REF.supabase.co/functions/v1/generate-invoices' \
  -H 'Content-Type: application/json' \
  -H "x-cron-secret: $(psql "$DATABASE_URL" -tAc "select value from public.app_secrets where key = 'cron_secret'")"
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

(em produção o Supabase já injecta `SUPABASE_URL`/
`SUPABASE_SERVICE_ROLE_KEY` automaticamente; o segredo do cron é lido
de `public.app_secrets`, como referido em 11.3, por isso não precisa de
nenhum `.env` local para o testar).

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

- Não existem segredos escritos no código: `SUPABASE_URL` e
  `SUPABASE_SERVICE_ROLE_KEY` são lidos de variáveis de ambiente
  (`Deno.env.get(...)`), injectadas automaticamente pela plataforma em
  todas as Edge Functions, nunca hardcoded. O segredo do cron
  (`x-cron-secret`) é lido de `public.app_secrets`, uma tabela sem RLS
  nem grants a `anon`/`authenticated` (secção 11.3) — não depende de
  nenhuma configuração manual fora do código/migrations.
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


---

## 12. Planos, trial e assinatura

Implementado nas migrations `021_plans_trial_and_limits.sql`,
`022_member_management_and_settings.sql`, `023_organization_contact_info.sql`
e `024_dashboard_extra_stats.sql`.

### 12.1. Planos

Quatro planos comerciais — **Free, Básico, Profissional, Empresa** — com
limites definidos numa única fonte de verdade no servidor: a função SQL
`plan_limits(plan)`. O frontend (`src/lib/plans.ts`) só espelha estes
valores para apresentação (preços, nomes, benefícios) — **nunca é ele que
decide se uma ação é permitida.**

| Limite | Free | Básico | Profissional | Empresa |
|---|---|---|---|---|
| Clientes | 5 | 30 | 150 | ilimitado |
| Serviços | 3 | 15 | 50 | ilimitado |
| Membros da equipa | 1 | 3 | 8 | ilimitado |
| Cobranças/mês | 10 | 60 | 300 | ilimitado |
| Cobranças recorrentes | ❌ | ✅ | ✅ | ✅ |
| Relatórios | ❌ | ✅ | ✅ | ✅ |

A aplicação destes limites é feita por **triggers `before insert`** nas
tabelas `clients`, `services`, `invoices`, `organization_members` e
`subscriptions` — mesmo que alguém contorne a interface e escreva
directamente via `supabase-js`, a base de dados rejeita a operação com uma
mensagem de erro amigável (ex: *"Limite do seu plano atingido: 5 de 5
clientes permitidos."*). O frontend só usa estes limites para desenhar
barras de utilização e desactivar botões preventivamente — a garantia real
está sempre no Postgres.

A RPC `get_plan_status(organization_id)` devolve, numa só chamada, o plano
actual, o plano "efectivo" (ver 12.2), o estado do trial, os limites e a
utilização actual — é o que alimenta o Dashboard e a página `/billing`.

### 12.2. Trial gratuito

Toda a organização nova arranca com `subscription_status = 'trialing'` e
usufrui, durante o trial, dos limites do plano **Profissional** mesmo que
`organizations.plan` continue `'free'` (é o "plano efectivo" — ver função
`get_effective_plan()`). A duração do trial está centralizada numa única
função, `trial_duration_days()` (14 dias por omissão) — para a alterar,
basta editar essa função.

Findo o trial, a organização volta automaticamente ao plano realmente
contratado (`organizations.plan`, `'free'` por omissão) — nenhum dado é
apagado, só deixam de se aplicar os limites alargados do Profissional.

### 12.3. Assinatura e activação

`organizations` tem os campos `subscription_status`, `billing_period`,
`started_at`, `expires_at`, `cancelled_at`, `provider` e
`external_reference`, prontos para qualquer gateway de pagamento futuro.

**Não existe hoje, para Angola, um gateway de pagamento com API/webhook
público e fiável integrado neste projecto** — por isso o fluxo de upgrade
é: o utilizador (owner/admin) pede um plano em `/billing`
(`request_plan_upgrade`, que regista uma linha em `purchase_intents` com
`status = 'pending'`), a página mostra um link de WhatsApp opcional
(`VITE_SALES_WHATSAPP_NUMBER`) para combinar o pagamento, e **a activação
é manual**: quem gere o SaaS confirma o pagamento e corre, no SQL Editor
do Supabase (com a `service_role`, nunca pelo frontend):

```sql
update public.purchase_intents set status = 'confirmed', confirmed_at = now() where id = '...';
update public.organizations
set plan = 'profissional', subscription_status = 'active', started_at = now(), expires_at = now() + interval '30 days'
where id = '...';
```

A arquitectura fica pronta para automatizar isto mais tarde — bastaria um
webhook do gateway escolhido a fazer o mesmo `update`, sem alterar mais
nada no resto da aplicação.

---

## 13. Papéis e permissões (owner / admin / staff)

| | Owner | Admin | Staff |
|---|---|---|---|
| Clientes, serviços, cobranças, pagamentos, lembretes | ✅ | ✅ | ✅ |
| Ver relatórios | ✅ | ✅ | ✅ |
| Editar dados da empresa e modelo de WhatsApp (`/settings`) | ✅ | ✅ | ❌ (só leitura) |
| Adicionar/remover membros, alterar papéis | ✅ | ✅ | ❌ |
| Pedir upgrade de plano (`/billing`) | ✅ | ✅ | ❌ |
| Ser removido da organização | nunca | por outro admin/owner | por admin/owner ou a si próprio |
| Alterar o seu próprio papel | ❌ (ninguém pode) | ❌ | ❌ |

Regras impostas no **Postgres**, não só na interface (migration
`022_member_management_and_settings.sql`):

- Só pode existir **um owner** por organização — é sempre quem criou a
  organização no onboarding; não existe (ainda) transferência de
  titularidade.
- Um trigger em `organization_members` (`enforce_member_role_change`)
  bloqueia, para qualquer via de escrita (RPC ou tabela directa):
  promover alguém a `owner`, alterar o papel de quem já é `owner`, e um
  utilizador alterar o seu **próprio** papel — fecha uma escalada de
  privilégios que a política de RLS da migration `003` sozinha não
  impedia.
- A gestão de membros em `/settings` usa sempre as RPCs
  `list_organization_members`, `add_organization_member_by_email`,
  `update_member_role` e `remove_organization_member` — nunca escreve
  directamente na tabela — e cada uma valida de novo, no servidor, que o
  chamador é owner/admin da organização em causa.
- Adicionar um membro exige que a pessoa **já tenha conta** CobrançaPro
  (procurada por email); não existe convite por email nesta versão.

---

## 14. Auditoria de segurança pós-deploy (migrations 025-028)

Depois de aplicar o schema pela primeira vez num projecto Supabase real
(Postgres 17), corri `mcp__Supabase__get_advisors` (linter de segurança
nativo do Supabase) e encontrei — e corrigi de imediato — dois problemas
reais que não apareciam ao rever o SQL isoladamente:

- **Views "security definer" por omissão** (`dashboard_totals`,
  `client_balances`, `payment_totals`, `reminder_totals`): no Postgres 15+,
  uma view sem `security_invoker = true` corre com os privilégios do
  DONO da view, não do utilizador que a consulta — o oposto do que os
  comentários originais destas views assumiam ("comportamento por
  omissão"). Um utilizador autenticado conseguia, por esta via, ver
  totais de **qualquer** organização, não só da sua. Corrigido em
  `025_fix_security_definer_views.sql`.
- **Funções acessíveis por `anon`** (sem sessão nenhuma): o projecto
  Supabase concede `EXECUTE` a `anon`/`authenticated` por omissão em
  toda a função nova do schema `public`; as migrations originais só
  fechavam isto explicitamente nalgumas RPCs. Corrigido função a função
  em `026`-`028` — hoje só `plan_limits()` é mesmo pública; tudo o resto
  exige sessão autenticada (e, mesmo assim, valida sempre a pertença à
  organização dentro da própria função, nunca confiando só no grant).

Sempre que alterar RPCs ou views neste projecto, corra
`mcp__Supabase__get_advisors` (tipo `security`) depois de aplicar a
migration — é a única forma fiável de apanhar este tipo de problema,
que não aparece a rever o SQL a olho.

**Pendente, fora do alcance de uma migration SQL:** activar "Leaked
password protection" em **Authentication → Providers → Email** no
painel do Supabase (verifica a palavra-passe contra fugas conhecidas via
HaveIBeenPwned) — é uma definição do serviço de Auth, não da base de
dados.

## 15. Bug real encontrado ao testar o onboarding (migration 029)

Ao testar a criação da primeira organização com um utilizador real,
o passo falhava sempre com `new row violates row-level security policy
for table "organizations"` (código Postgres `42501`), mesmo com uma
sessão válida e correctamente autenticada.

**Causa:** `OnboardingPage.tsx` fazia dois `insert` separados —
primeiro em `organizations` (pedindo a linha de volta com `.select()`),
depois em `organization_members`. A política de leitura de
`organizations` só permite ver organizações onde o utilizador **já é
membro** — e nesse instante ainda não é (o segundo insert só acontece a
seguir). O Postgres recusa a operação inteira quando o `RETURNING` de
um `INSERT` não passa na política de leitura, não é um problema de
sessão/token.

**Correcção:** `create_organization_with_owner()` (migration `029`) faz
os dois inserts numa única transacção atómica, como `security definer`
— nunca fica uma organização "órfã" sem responsável, e evita por
completo o conflito entre `INSERT ... RETURNING` e a política de
leitura. `OnboardingPage.tsx` passou a chamar esta RPC em vez de dois
inserts directos.
