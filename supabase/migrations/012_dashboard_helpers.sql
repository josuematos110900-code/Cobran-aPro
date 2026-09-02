-- 012_dashboard_helpers.sql

-- Função a correr periodicamente (via cron/scheduled function) que marca
-- como "overdue" as invoices pendentes cuja due_date já passou. Não deve
-- ser chamada a partir do browser do utilizador final.
create or replace function public.mark_overdue_invoices()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated integer;
begin
  update public.invoices
  set status = 'overdue'
  where status = 'pending' and due_date < current_date;

  get diagnostics v_updated = row_count;
  return v_updated;
end;
$$;

-- View com os totais usados no dashboard (receita recebida, pendente,
-- atrasada) por organização. Herda a RLS das tabelas subjacentes porque é
-- uma view "security invoker" (comportamento por omissão em Postgres).
create or replace view public.dashboard_totals as
select
  o.id as organization_id,
  coalesce(sum(i.amount) filter (where i.status = 'paid'), 0) as total_received,
  coalesce(sum(i.amount) filter (where i.status = 'pending'), 0) as total_pending,
  coalesce(sum(i.amount) filter (where i.status = 'overdue'), 0) as total_overdue,
  count(distinct c.id) filter (where c.status = 'active') as active_clients
from public.organizations o
left join public.invoices i on i.organization_id = o.id
left join public.clients c on c.organization_id = o.id
group by o.id;
