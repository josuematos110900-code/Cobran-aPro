-- 015_mark_invoice_paid.sql
-- RPC que executa, numa única transacção atómica:
--   1. bloqueia a linha da invoice (FOR UPDATE) para serializar chamadas
--      concorrentes sobre a mesma invoice;
--   2. verifica que o utilizador pertence à organização da invoice;
--   3. impede pagar uma invoice já paga ou cancelada (evita pagamentos
--      duplicados mesmo em caso de duplo-clique ou pedidos em paralelo);
--   4. cria o registo em "payments";
--   5. actualiza o estado da invoice para "paid" e define paid_at.
--
-- Chamar a partir do frontend via:
--   supabase.rpc('mark_invoice_paid', {
--     p_invoice_id, p_payment_method, p_transaction_reference
--   })

create or replace function public.mark_invoice_paid(
  p_invoice_id uuid,
  p_payment_method text default 'other',
  p_transaction_reference text default null
)
returns public.payments
language plpgsql
security definer
set search_path = public
as $$
declare
  v_invoice public.invoices%rowtype;
  v_payment public.payments%rowtype;
begin
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
    payment_method, transaction_reference, paid_at, status
  )
  values (
    v_invoice.organization_id, v_invoice.id, v_invoice.client_id, v_invoice.amount,
    v_invoice.currency, coalesce(p_payment_method, 'other'), p_transaction_reference,
    now(), 'completed'
  )
  returning * into v_payment;

  update public.invoices
  set status = 'paid', paid_at = now()
  where id = p_invoice_id;

  return v_payment;
end;
$$;

revoke execute on function public.mark_invoice_paid(uuid, text, text) from public;
grant execute on function public.mark_invoice_paid(uuid, text, text) to authenticated;
