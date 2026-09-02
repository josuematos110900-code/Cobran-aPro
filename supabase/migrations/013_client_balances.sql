-- 013_client_balances.sql
-- View com os totais de facturação por cliente (pago, pendente, atrasado),
-- usada na lista de clientes ("total em dívida") e no perfil do cliente.
--
-- Tal como dashboard_totals, esta view é "security invoker" (comportamento
-- por omissão em Postgres): quando consultada por um utilizador
-- autenticado, a RLS da tabela subjacente "invoices" continua a aplicar-se
-- normalmente, pelo que um utilizador nunca vê saldos de clientes fora das
-- suas próprias organizações. Não é necessária nenhuma política RLS nova.

create or replace view public.client_balances as
select
  c.id as client_id,
  c.organization_id,
  coalesce(sum(i.amount) filter (where i.status = 'paid'), 0) as total_paid,
  coalesce(sum(i.amount) filter (where i.status = 'pending'), 0) as total_pending,
  coalesce(sum(i.amount) filter (where i.status = 'overdue'), 0) as total_overdue
from public.clients c
left join public.invoices i on i.client_id = c.id
group by c.id, c.organization_id;
