-- 017_recurring_billing.sql
-- Suporte completo para geração automática de cobranças a partir de
-- subscriptions, com idempotência garantida ao nível do Postgres.

-- 1. Colunas de controlo em subscriptions.
alter table public.subscriptions add column if not exists next_billing_date date;
alter table public.subscriptions add column if not exists last_billing_date date;

create index if not exists idx_subscriptions_next_billing
  on public.subscriptions (next_billing_date)
  where status = 'active';

create index if not exists idx_invoices_subscription_id
  on public.invoices (subscription_id);

-- 2. Log de facturação: a chave da idempotência. Cada linha representa
--    "esta subscrição já gerou a invoice da competência X" — a restrição
--    unique garante que, mesmo que a função de geração corra duas vezes
--    (ou em paralelo), nunca é criada uma segunda invoice para a mesma
--    competência da mesma subscrição.
create table if not exists public.subscription_billing_log (
  id uuid primary key default gen_random_uuid(),
  subscription_id uuid not null references public.subscriptions (id) on delete cascade,
  organization_id uuid not null references public.organizations (id) on delete cascade,
  billing_period_start date not null,
  invoice_id uuid references public.invoices (id) on delete set null,
  created_at timestamptz not null default now(),
  unique (subscription_id, billing_period_start)
);

alter table public.subscription_billing_log enable row level security;

create index if not exists idx_billing_log_org_id on public.subscription_billing_log (organization_id);
create index if not exists idx_billing_log_subscription_id on public.subscription_billing_log (subscription_id);

-- Apenas leitura para membros da organização (auditoria/histórico). A
-- escrita é feita inteiramente pela função generate_recurring_invoices(),
-- que corre como service_role e por isso ignora RLS.
create policy "billing_log_select_member"
  on public.subscription_billing_log for select
  using (organization_id in (select public.user_organization_ids()));

-- 3. Calcula a próxima data de vencimento de uma subscrição, respeitando
--    periodicidade e due_day, a partir de uma data de referência
--    ("p_after" — normalmente "hoje" ou "última cobrança + 1 dia").
--    Nunca devolve uma data anterior a p_start_date.
create or replace function public.compute_next_billing_date(
  p_start_date date,
  p_due_day smallint,
  p_billing_period text,
  p_after date
)
returns date
language plpgsql
immutable
as $$
declare
  v_candidate date;
  v_period interval;
  v_year int;
  v_month int;
  v_days_in_month int;
  v_iterations int := 0;
  v_after date := greatest(p_after, p_start_date);
begin
  if p_billing_period = 'weekly' then
    -- p_due_day interpretado como dia da semana ISO (1=Segunda..7=Domingo).
    v_candidate := p_start_date + (((p_due_day - extract(isodow from p_start_date)::int) % 7 + 7) % 7);
    while v_candidate < v_after and v_iterations < 2000 loop
      v_candidate := v_candidate + 7;
      v_iterations := v_iterations + 1;
    end loop;
    return v_candidate;
  end if;

  v_period := case p_billing_period
    when 'quarterly' then interval '3 months'
    when 'yearly' then interval '1 year'
    else interval '1 month'
  end;

  v_year := extract(year from p_start_date)::int;
  v_month := extract(month from p_start_date)::int;
  v_days_in_month := extract(day from (make_date(v_year, v_month, 1) + interval '1 month - 1 day'))::int;
  v_candidate := make_date(v_year, v_month, least(p_due_day, v_days_in_month)::int);

  if v_candidate < p_start_date then
    v_year := extract(year from (v_candidate + v_period))::int;
    v_month := extract(month from (v_candidate + v_period))::int;
    v_days_in_month := extract(day from (make_date(v_year, v_month, 1) + interval '1 month - 1 day'))::int;
    v_candidate := make_date(v_year, v_month, least(p_due_day, v_days_in_month)::int);
  end if;

  while v_candidate < v_after and v_iterations < 2000 loop
    v_year := extract(year from (v_candidate + v_period))::int;
    v_month := extract(month from (v_candidate + v_period))::int;
    v_days_in_month := extract(day from (make_date(v_year, v_month, 1) + interval '1 month - 1 day'))::int;
    v_candidate := make_date(v_year, v_month, least(p_due_day, v_days_in_month)::int);
    v_iterations := v_iterations + 1;
  end loop;

  return v_candidate;
end;
$$;

-- 4. next_invoice_number(): relaxa a verificação de pertença à organização
--    quando chamada sem utilizador autenticado (auth.uid() is null), que é
--    o caso da função de geração automática a correr como service_role.
--    Chamadas feitas a partir do frontend (com um utilizador autenticado)
--    continuam totalmente validadas como antes.
create or replace function public.next_invoice_number(p_organization_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_next integer;
begin
  if auth.uid() is not null and p_organization_id not in (select public.user_organization_ids()) then
    raise exception 'Não pertence a esta organização.' using errcode = '42501';
  end if;

  insert into public.invoice_number_counters (organization_id, last_number)
  values (p_organization_id, 1)
  on conflict (organization_id)
  do update set last_number = public.invoice_number_counters.last_number + 1
  returning last_number into v_next;

  return 'FAT-' || lpad(v_next::text, 6, '0');
end;
$$;

-- 5. Recalcula next_billing_date automaticamente:
--    - ao criar uma subscrição;
--    - ao editar start_date, due_day ou billing_period;
--    - ao retomar uma subscrição pausada (recomeça a partir de hoje, sem
--      cobrar retroactivamente o período em que esteve pausada).
create or replace function public.set_subscription_next_billing_date()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' then
    new.next_billing_date := public.compute_next_billing_date(
      new.start_date, new.due_day, new.billing_period, current_date
    );
    return new;
  end if;

  if (new.start_date, new.due_day, new.billing_period) is distinct from
     (old.start_date, old.due_day, old.billing_period) then
    new.next_billing_date := public.compute_next_billing_date(
      new.start_date, new.due_day, new.billing_period,
      greatest(coalesce(new.last_billing_date, new.start_date), current_date)
    );
  elsif old.status = 'paused' and new.status = 'active' then
    new.next_billing_date := public.compute_next_billing_date(
      new.start_date, new.due_day, new.billing_period, current_date
    );
  end if;

  return new;
end;
$$;

drop trigger if exists trg_subscriptions_next_billing_insert on public.subscriptions;
create trigger trg_subscriptions_next_billing_insert
  before insert on public.subscriptions
  for each row execute function public.set_subscription_next_billing_date();

drop trigger if exists trg_subscriptions_next_billing_update on public.subscriptions;
create trigger trg_subscriptions_next_billing_update
  before update on public.subscriptions
  for each row execute function public.set_subscription_next_billing_date();

-- 6. Marca como "ended" subscrições activas cuja end_date já passou.
--    Tal como mark_overdue_invoices(), é uma tarefa de manutenção global
--    e só deve ser invocada pela service_role.
create or replace function public.end_expired_subscriptions()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated integer;
begin
  update public.subscriptions
  set status = 'ended'
  where status = 'active' and end_date is not null and end_date < current_date;

  get diagnostics v_updated = row_count;
  return v_updated;
end;
$$;

revoke execute on function public.end_expired_subscriptions() from public, authenticated, anon;
grant execute on function public.end_expired_subscriptions() to service_role;

-- 7. Função principal: gera as invoices em falta para todas as
--    subscrições activas, com idempotência via subscription_billing_log.
--    Recupera competências em atraso (até 24 por subscrição e por
--    execução, como limite de segurança) caso o job não tenha corrido
--    durante algum tempo.
create or replace function public.generate_recurring_invoices()
returns table (
  out_subscription_id uuid,
  out_invoice_id uuid,
  out_invoice_number text,
  out_billing_period_start date
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_sub record;
  v_period_start date;
  v_invoice_id uuid;
  v_invoice_number text;
  v_inserted boolean;
  v_iterations int;
begin
  for v_sub in
    select s.*, sv.name as service_name
    from public.subscriptions s
    join public.services sv on sv.id = s.service_id
    where s.status = 'active'
      and s.start_date <= current_date
      and (s.end_date is null or s.end_date >= current_date)
  loop
    v_iterations := 0;
    v_period_start := coalesce(
      v_sub.next_billing_date,
      public.compute_next_billing_date(v_sub.start_date, v_sub.due_day, v_sub.billing_period, current_date)
    );

    while v_period_start <= current_date
      and (v_sub.end_date is null or v_period_start <= v_sub.end_date)
      and v_iterations < 24
    loop
      v_inserted := false;

      begin
        insert into public.subscription_billing_log (subscription_id, organization_id, billing_period_start)
        values (v_sub.id, v_sub.organization_id, v_period_start);
        v_inserted := true;
      exception when unique_violation then
        v_inserted := false;
      end;

      if v_inserted then
        v_invoice_number := public.next_invoice_number(v_sub.organization_id);

        insert into public.invoices (
          organization_id, client_id, subscription_id, invoice_number,
          description, amount, currency, due_date, status
        )
        values (
          v_sub.organization_id, v_sub.client_id, v_sub.id, v_invoice_number,
          v_sub.service_name, v_sub.amount, v_sub.currency, v_period_start, 'pending'
        )
        returning id into v_invoice_id;

        update public.subscription_billing_log
        set invoice_id = v_invoice_id
        where subscription_id = v_sub.id and billing_period_start = v_period_start;

        update public.subscriptions
        set last_billing_date = v_period_start,
            next_billing_date = public.compute_next_billing_date(
              v_sub.start_date, v_sub.due_day, v_sub.billing_period, v_period_start + 1
            )
        where id = v_sub.id;

        out_subscription_id := v_sub.id;
        out_invoice_id := v_invoice_id;
        out_invoice_number := v_invoice_number;
        out_billing_period_start := v_period_start;
        return next;
      end if;

      v_period_start := public.compute_next_billing_date(
        v_sub.start_date, v_sub.due_day, v_sub.billing_period, v_period_start + 1
      );
      v_iterations := v_iterations + 1;
    end loop;
  end loop;

  return;
end;
$$;

revoke execute on function public.generate_recurring_invoices() from public, authenticated, anon;
grant execute on function public.generate_recurring_invoices() to service_role;
