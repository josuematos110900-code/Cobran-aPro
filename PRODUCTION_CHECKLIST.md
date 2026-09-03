# Checklist de produção — CobrançaPro

Lista de verificação para o primeiro lançamento comercial em Angola.
Marque cada item no seu próprio projecto Supabase/Vercel antes de dar
acesso a clientes reais — nada aqui é feito automaticamente por mim, são
passos que dependem da sua conta.

## Frontend

- [ ] `npm run typecheck`, `npm run lint` e `npm run build` sem erros
      (ver secção 6 do README).
- [ ] `npm run test` — testes unitários (validação de formulários,
      normalização de telefone/WhatsApp, consistência dos planos).
- [ ] `.env.local` / variáveis de ambiente do Vercel com
      `VITE_SUPABASE_URL` e `VITE_SUPABASE_ANON_KEY` do projecto real
      (nunca a `service_role key`).
- [ ] `VITE_SALES_WHATSAPP_NUMBER` definido, se quiser o botão de
      contacto directo em `/billing`.
- [ ] Routing SPA a funcionar em produção (rewrite do `vercel.json` já
      incluído) — testar refresh directo em `/dashboard`, `/clients/:id`, etc.
- [ ] Testado em ecrã pequeno (mobile) — é o uso principal esperado em Angola.
- [ ] Fluxo de autenticação completo testado em produção: registo, login,
      "esqueci-me da senha", confirmação de email (se activada).

## Supabase

- [ ] Todas as migrations (`001` a `024`) aplicadas, pela ordem numérica.
- [ ] RLS activo em **todas** as tabelas de negócio (confirmar no painel
      **Database → Tables**, coluna "RLS enabled").
- [ ] `next_invoice_number`, `mark_invoice_paid`, `mark_overdue_invoices`,
      `generate_recurring_invoices`, `end_expired_subscriptions` com os
      `grant`/`revoke` correctos (ver migrations 007, 015/018, 016, 017).
- [ ] Edge Function `generate-invoices` publicada
      (`supabase functions deploy generate-invoices`).
- [ ] Secret `CRON_SECRET` definido (`supabase secrets set CRON_SECRET=...`).
- [ ] Cron configurado a chamar `generate-invoices` diariamente (ver
      README secção 11.4) — sem isto, cobranças recorrentes e atrasos não
      avançam sozinhos.
- [ ] `Authentication → URL Configuration` com o domínio real de produção.
- [ ] Correu pelo menos os testes 1-5 e 9 de
      `supabase/tests/manual_security_checks.sql` com duas contas reais.

## Produto

- [ ] Trial de 14 dias a funcionar (criar uma organização de teste e
      confirmar `subscription_status = 'trialing'` e `trial_end` correcto).
- [ ] Os 4 planos (Free/Básico/Profissional/Empresa) com preços revistos
      para o mercado (editar `src/lib/plans.ts` **e** a função
      `plan_limits()` na migration 021 se os limites mudarem — têm de
      ficar sempre sincronizados).
- [ ] Fluxo de upgrade manual testado: pedir plano em `/billing`, confirmar
      a linha em `purchase_intents`, activar manualmente via SQL Editor
      (ver README secção 12.3).
- [ ] Onboarding: uma conta nova consegue, sem ajuda, criar empresa →
      cliente → serviço → cobrança → enviar por WhatsApp → registar
      pagamento, guiado pelo checklist do Dashboard.
- [ ] Modelo de mensagem de WhatsApp revisto para o tom do seu negócio
      (`/settings`).

## Segurança

- [ ] Nenhum segredo (`service_role key`, `CRON_SECRET`, chaves de
      terceiros) em código versionado ou em variáveis `VITE_*`.
- [ ] Confirmado, com duas contas reais, que não há acesso cross-tenant
      (checklist 1-3 de `manual_security_checks.sql`).
- [ ] Confirmado que um `staff` não consegue gerir membros nem editar
      dados da empresa (checklist 4).
- [ ] Confirmado que ninguém consegue auto-promover-se a `owner`
      (checklist 5).
- [ ] Confirmado que os limites de plano bloqueiam no servidor, não só na
      UI (checklist 9).
- [ ] Nenhuma função RPC crítica (`mark_overdue_invoices`,
      `generate_recurring_invoices`, `end_expired_subscriptions`)
      executável por `authenticated`/`anon` — só `service_role`.

## Limitações conhecidas (documentadas, não escondidas)

- Sem gateway de pagamento com API/webhook para Angola nesta versão —
  activação de plano é manual (ver README 12.3).
- Sem convite de membro por email — a pessoa tem de já ter conta.
- Sem transferência de titularidade (owner) de uma organização.
- Relatório de receita por serviço só associa facturas nascidas de uma
  subscrição (ver aviso na própria página `/reports`).
- WhatsApp é só `wa.me` (link com mensagem pré-preenchida) — sem
  confirmação de entrega/leitura, sem WhatsApp Business API oficial.
