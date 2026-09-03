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
