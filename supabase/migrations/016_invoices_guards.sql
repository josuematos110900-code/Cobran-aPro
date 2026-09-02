-- 016_invoices_guards.sql

-- 1. Campo opcional de referência (ex: nº de recibo externo, referência de
--    depósito bancário) a mostrar/editar na cobrança.
alter table public.invoices add column if not exists reference text;

-- 2. Protecção de máquina de estados: uma vez "paid" ou "cancelled", uma
--    invoice não pode ser devolvida a outro estado por uma actualização
--    directa da tabela. Isto protege contra bugs de UI ou chamadas
--    manuais que tentassem "reabrir" uma cobrança já fechada. A função
--    mark_invoice_paid() já valida isto antes de chegar aqui, mas o
--    trigger garante a mesma regra mesmo para outros caminhos de escrita.
create or replace function public.enforce_invoice_status_transition()
returns trigger
language plpgsql
as $$
begin
  if old.status in ('paid', 'cancelled') and new.status is distinct from old.status then
    raise exception 'Não é possível alterar o estado de uma cobrança já %.', old.status
      using errcode = 'P0001';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_invoices_status_guard on public.invoices;
create trigger trg_invoices_status_guard
  before update on public.invoices
  for each row execute function public.enforce_invoice_status_transition();

-- 3. mark_overdue_invoices() actualiza facturas de TODAS as organizações
--    (é uma tarefa de manutenção global, não uma operação por-organização),
--    por isso nunca deve ser chamável directamente pelo frontend. Deve ser
--    invocada apenas por um job agendado (Supabase Edge Function / cron)
--    autenticado com a service_role, que ignora RLS e privilégios de
--    "authenticated" por definição.
revoke execute on function public.mark_overdue_invoices() from public, authenticated, anon;
grant execute on function public.mark_overdue_invoices() to service_role;
