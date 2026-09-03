-- 024_dashboard_extra_stats.sql
-- Fase 7 (Dashboard): mais métricas úteis no ecrã inicial, sem duplicar
-- lógica — tudo continua a vir de agregações sobre as tabelas já
-- existentes, com a mesma RLS "security invoker" da view original.

create or replace view public.dashboard_totals as
select
  o.id as organization_id,
  coalesce(sum(i.amount) filter (where i.status = 'paid'), 0) as total_received,
  coalesce(sum(i.amount) filter (where i.status = 'pending'), 0) as total_pending,
  coalesce(sum(i.amount) filter (where i.status = 'overdue'), 0) as total_overdue,
  count(distinct c.id) filter (where c.status = 'active') as active_clients,
  count(distinct i.id) filter (
    where date_trunc('month', i.created_at) = date_trunc('month', now())
  ) as invoices_this_month,
  count(distinct s.id) filter (where s.status = 'active') as active_subscriptions
from public.organizations o
left join public.invoices i on i.organization_id = o.id
left join public.clients c on c.organization_id = o.id
left join public.subscriptions s on s.organization_id = o.id
group by o.id;

-- Cobranças vencidas mais recentes, para o widget "Vencidas recentemente"
-- do dashboard. security invoker (por omissão) — herda a RLS de invoices.
create or replace function public.get_recent_overdue_invoices(p_organization_id uuid, p_limit integer default 5)
returns table (
  id uuid,
  invoice_number text,
  client_name text,
  amount numeric,
  currency text,
  due_date date
)
language sql
stable
as $$
  select i.id, i.invoice_number, c.name, i.amount, i.currency, i.due_date
  from public.invoices i
  join public.clients c on c.id = i.client_id
  where i.organization_id = p_organization_id and i.status = 'overdue'
  order by i.due_date desc
  limit greatest(1, least(p_limit, 50));
$$;
