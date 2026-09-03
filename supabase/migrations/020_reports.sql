-- 020_reports.sql
-- Fase 7: Relatórios & Analytics. Não altera nenhuma tabela existente —
-- só acrescenta funções de leitura agregada e um índice de apoio.
--
-- IMPORTANTE: todas as funções abaixo são "security invoker" (o padrão em
-- Postgres — nenhuma usa "security definer"). Correm com os privilégios
-- de quem as chama (o utilizador autenticado), pelo que a Row Level
-- Security das tabelas subjacentes (invoices, payments, clients,
-- subscriptions, services) continua a aplicar-se integralmente. Mesmo
-- que alguém passe o organization_id de outra organização, a RLS impede
-- que as queries internas devolvam qualquer linha dessa organização —
-- o parâmetro nunca é uma forma de contornar o isolamento multi-tenant.
--
-- Como reforço adicional (defesa em profundidade, não apenas RLS),
-- todas as funções validam explicitamente que o utilizador pertence à
-- organização indicada antes de devolver qualquer resultado, e que
-- p_from <= p_to, falhando de forma clara em vez de devolver dados
-- vazios silenciosamente por um parâmetro incorrecto.

create index if not exists idx_invoices_org_created_at on public.invoices (organization_id, created_at);

-- Função auxiliar de validação, reutilizada por todas as funções desta
-- migration para evitar duplicar a mesma verificação sete vezes.
create or replace function public.assert_report_access(p_organization_id uuid, p_from date, p_to date)
returns void
language plpgsql
stable
as $$
begin
  if p_organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não pertence a esta organização.' using errcode = '42501';
  end if;

  if p_from is not null and p_to is not null and p_from > p_to then
    raise exception 'A data inicial não pode ser posterior à data final.' using errcode = '22023';
  end if;
end;
$$;

-- ---------------------------------------------------------------------
-- 1. Indicadores principais, todos numa só chamada (evita uma query por
--    cartão no frontend).
--
--    Definições (documentadas e estáveis — não alterar sem rever o
--    frontend que depende delas):
--      revenue_received  -> payments.paid_at no período
--      total_billed      -> invoices.created_at no período (emitidas,
--                           inclui as entretanto canceladas)
--      total_pending/overdue -> invoices.due_date no período + estado actual
--      payment_rate      -> pagas ÷ emitidas, excluindo canceladas,
--                           coorte pela data de emissão
--      active_clients/active_subscriptions -> estado actual (não
--                           filtrado por período)
-- ---------------------------------------------------------------------
create or replace function public.report_summary(
  p_organization_id uuid,
  p_from date,
  p_to date
)
returns table (
  revenue_received numeric,
  total_billed numeric,
  total_pending numeric,
  total_overdue numeric,
  invoices_emitted_count integer,
  invoices_paid_count integer,
  payment_rate numeric,
  active_clients integer,
  active_subscriptions integer
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  select
    coalesce((
      select sum(p.amount) from public.payments p
      where p.organization_id = p_organization_id
        and p.status = 'completed'
        and p.paid_at::date between p_from and p_to
    ), 0) as revenue_received,

    coalesce((
      select sum(i.amount) from public.invoices i
      where i.organization_id = p_organization_id
        and i.created_at::date between p_from and p_to
    ), 0) as total_billed,

    coalesce((
      select sum(i.amount) from public.invoices i
      where i.organization_id = p_organization_id
        and i.status = 'pending'
        and i.due_date between p_from and p_to
    ), 0) as total_pending,

    coalesce((
      select sum(i.amount) from public.invoices i
      where i.organization_id = p_organization_id
        and i.status = 'overdue'
        and i.due_date between p_from and p_to
    ), 0) as total_overdue,

    (
      select count(*)::integer from public.invoices i
      where i.organization_id = p_organization_id
        and i.created_at::date between p_from and p_to
        and i.status <> 'cancelled'
    ) as invoices_emitted_count,

    (
      select count(*)::integer from public.invoices i
      where i.organization_id = p_organization_id
        and i.created_at::date between p_from and p_to
        and i.status = 'paid'
    ) as invoices_paid_count,

    case
      when (
        select count(*) from public.invoices i
        where i.organization_id = p_organization_id
          and i.created_at::date between p_from and p_to
          and i.status <> 'cancelled'
      ) = 0 then 0
      else round(
        100.0 * (
          select count(*) from public.invoices i
          where i.organization_id = p_organization_id
            and i.created_at::date between p_from and p_to
            and i.status = 'paid'
        ) / (
          select count(*) from public.invoices i
          where i.organization_id = p_organization_id
            and i.created_at::date between p_from and p_to
            and i.status <> 'cancelled'
        ), 1
      )
    end as payment_rate,

    (
      select count(*)::integer from public.clients c
      where c.organization_id = p_organization_id and c.status = 'active'
    ) as active_clients,

    (
      select count(*)::integer from public.subscriptions s
      where s.organization_id = p_organization_id and s.status = 'active'
    ) as active_subscriptions;
end;
$$;

-- ---------------------------------------------------------------------
-- 2. Série temporal de receita recebida, agrupada por dia/semana/mês,
--    com "buracos" preenchidos a zero (generate_series) para o gráfico
--    não ficar com falhas visuais em dias sem pagamentos. Os dois
--    limites (p_from e p_to truncados à mesma unidade) garantem que o
--    último período é sempre incluído, mesmo quando não é múltiplo
--    exacto do passo.
-- ---------------------------------------------------------------------
create or replace function public.report_revenue_series(
  p_organization_id uuid,
  p_from date,
  p_to date,
  p_granularity text default 'day'
)
returns table (bucket date, total numeric)
language plpgsql
stable
as $$
declare
  v_step interval;
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  if p_granularity not in ('day', 'week', 'month') then
    raise exception 'Granularidade inválida: %', p_granularity using errcode = '22023';
  end if;

  v_step := case p_granularity when 'week' then interval '7 days' when 'month' then interval '1 month' else interval '1 day' end;

  return query
  with buckets as (
    select generate_series(
      date_trunc(p_granularity, p_from::timestamp),
      date_trunc(p_granularity, p_to::timestamp),
      v_step
    )::date as bucket
  ),
  paid as (
    select date_trunc(p_granularity, p.paid_at)::date as bucket, sum(p.amount) as total
    from public.payments p
    where p.organization_id = p_organization_id
      and p.status = 'completed'
      and p.paid_at::date between p_from and p_to
    group by 1
  )
  select b.bucket, coalesce(paid.total, 0) as total
  from buckets b
  left join paid on paid.bucket = b.bucket
  order by b.bucket;
end;
$$;

-- ---------------------------------------------------------------------
-- 3. Cobranças por estado (para o gráfico de barras), com base na data
--    de vencimento dentro do período.
-- ---------------------------------------------------------------------
create or replace function public.report_invoice_status_breakdown(
  p_organization_id uuid,
  p_from date,
  p_to date
)
returns table (status text, invoice_count integer, total_amount numeric)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  select i.status, count(*)::integer as invoice_count, coalesce(sum(i.amount), 0) as total_amount
  from public.invoices i
  where i.organization_id = p_organization_id
    and i.due_date between p_from and p_to
  group by i.status;
end;
$$;

-- ---------------------------------------------------------------------
-- 4. Ranking de clientes. total_paid usa payments.paid_at no período;
--    total_pending/total_overdue/invoice_count usam invoices.due_date no
--    período — os dois grupos são combinados por client_id.
-- ---------------------------------------------------------------------
create or replace function public.report_top_clients(
  p_organization_id uuid,
  p_from date,
  p_to date,
  p_limit integer default 10
)
returns table (
  client_id uuid,
  client_name text,
  total_paid numeric,
  total_pending numeric,
  total_overdue numeric,
  invoice_count integer
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  with pays as (
    select p.client_id, sum(p.amount) as total_paid
    from public.payments p
    where p.organization_id = p_organization_id
      and p.status = 'completed'
      and p.paid_at::date between p_from and p_to
    group by p.client_id
  ),
  invs as (
    select
      i.client_id,
      sum(i.amount) filter (where i.status = 'pending') as total_pending,
      sum(i.amount) filter (where i.status = 'overdue') as total_overdue,
      count(*) as invoice_count
    from public.invoices i
    where i.organization_id = p_organization_id
      and i.due_date between p_from and p_to
    group by i.client_id
  )
  select
    c.id as client_id,
    c.name as client_name,
    coalesce(pays.total_paid, 0) as total_paid,
    coalesce(invs.total_pending, 0) as total_pending,
    coalesce(invs.total_overdue, 0) as total_overdue,
    coalesce(invs.invoice_count, 0)::integer as invoice_count
  from public.clients c
  left join pays on pays.client_id = c.id
  left join invs on invs.client_id = c.id
  where c.organization_id = p_organization_id
    and (pays.total_paid is not null or invs.invoice_count is not null)
  order by (coalesce(pays.total_paid, 0) + coalesce(invs.total_pending, 0) + coalesce(invs.total_overdue, 0)) desc
  limit p_limit;
end;
$$;

-- ---------------------------------------------------------------------
-- 5. Receita por serviço. LIMITAÇÃO REAL, documentada e reflectida no
--    resultado: só cobranças nascidas de uma subscrição (invoices com
--    subscription_id preenchido) podem ser associadas a um serviço.
--    Facturas manuais (sem subscription_id) nunca entram nesta soma,
--    porque a base de dados não guarda essa ligação — não inventamos
--    uma correspondência que não existe. O frontend mostra este aviso
--    explicitamente junto à tabela.
-- ---------------------------------------------------------------------
create or replace function public.report_service_breakdown(
  p_organization_id uuid,
  p_from date,
  p_to date
)
returns table (
  service_id uuid,
  service_name text,
  subscriptions_total integer,
  subscriptions_active integer,
  estimated_mrr numeric,
  revenue_in_period numeric
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, p_from, p_to);

  return query
  with sub_counts as (
    select
      s.service_id,
      count(*) as subscriptions_total,
      count(*) filter (where s.status = 'active') as subscriptions_active,
      coalesce(sum(
        case s.billing_period
          when 'weekly' then s.amount * 4.33
          when 'monthly' then s.amount
          when 'quarterly' then s.amount / 3.0
          when 'yearly' then s.amount / 12.0
          else 0
        end
      ) filter (where s.status = 'active'), 0) as estimated_mrr
    from public.subscriptions s
    where s.organization_id = p_organization_id
    group by s.service_id
  ),
  revenue as (
    select sub.service_id, sum(p.amount) as revenue_in_period
    from public.payments p
    join public.invoices i on i.id = p.invoice_id
    join public.subscriptions sub on sub.id = i.subscription_id
    where p.organization_id = p_organization_id
      and p.status = 'completed'
      and p.paid_at::date between p_from and p_to
    group by sub.service_id
  )
  select
    sv.id as service_id,
    sv.name as service_name,
    coalesce(sc.subscriptions_total, 0)::integer as subscriptions_total,
    coalesce(sc.subscriptions_active, 0)::integer as subscriptions_active,
    coalesce(sc.estimated_mrr, 0) as estimated_mrr,
    coalesce(r.revenue_in_period, 0) as revenue_in_period
  from public.services sv
  left join sub_counts sc on sc.service_id = sv.id
  left join revenue r on r.service_id = sv.id
  where sv.organization_id = p_organization_id
    and (sc.subscriptions_total is not null or r.revenue_in_period is not null)
  order by coalesce(r.revenue_in_period, 0) desc, coalesce(sc.subscriptions_active, 0) desc;
end;
$$;

-- ---------------------------------------------------------------------
-- 6. Maiores valores em atraso — estado actual, não filtrado por
--    período (é uma lista accionável "quem me deve mais agora").
--    days_overdue = current_date - due_date (subtracção de duas datas
--    em Postgres devolve directamente um inteiro em dias).
-- ---------------------------------------------------------------------
create or replace function public.report_overdue_top(
  p_organization_id uuid,
  p_limit integer default 10
)
returns table (
  invoice_id uuid,
  invoice_number text,
  client_id uuid,
  client_name text,
  client_phone text,
  amount numeric,
  currency text,
  due_date date,
  days_overdue integer
)
language plpgsql
stable
as $$
begin
  perform public.assert_report_access(p_organization_id, null, null);

  return query
  select
    i.id as invoice_id,
    i.invoice_number,
    c.id as client_id,
    c.name as client_name,
    c.phone as client_phone,
    i.amount,
    i.currency,
    i.due_date,
    (current_date - i.due_date) as days_overdue
  from public.invoices i
  join public.clients c on c.id = i.client_id
  where i.organization_id = p_organization_id
    and i.status = 'overdue'
  order by i.amount desc
  limit p_limit;
end;
$$;
