-- 019_reminders_types.sql
-- Fase 6: Central de Lembretes. Reutiliza a tabela "reminders" já
-- existente (migration 009) — não cria tabela nova.

-- 1. Tipo de lembrete, para distinguir cobrança criada / vencimento
--    próximo / atrasada / confirmação de pagamento / mensagem manual.
alter table public.reminders add column if not exists reminder_type text
  not null default 'manual'
  check (reminder_type in ('invoice_created', 'invoice_due', 'invoice_overdue', 'payment_confirmation', 'manual'));

-- 2. Estado honesto: nunca fingimos saber que uma mensagem foi entregue
--    ou lida. "initiated" representa exactamente o que sabemos — que o
--    utilizador clicou para abrir o WhatsApp com a mensagem preparada.
--    Mantemos os valores antigos ('scheduled', 'sent', 'failed',
--    'cancelled') no schema por compatibilidade com registos anteriores
--    (Fases 3 e 5 usavam 'sent'), mas o código a partir desta fase só
--    grava 'initiated'.
alter table public.reminders drop constraint if exists reminders_status_check;
alter table public.reminders add constraint reminders_status_check
  check (status in ('scheduled', 'initiated', 'sent', 'failed', 'cancelled'));

-- 3. Índice para os filtros de período da página /reminders.
create index if not exists idx_reminders_org_created_at on public.reminders (organization_id, created_at);

-- 4. Totais para os cartões de /reminders e do Dashboard. Security
--    invoker (por omissão) — herda a RLS de "reminders", tal como as
--    outras views já criadas (dashboard_totals, client_balances,
--    payment_totals).
create or replace view public.reminder_totals as
select
  r.organization_id,
  count(*) filter (where r.status in ('initiated', 'sent')) as total_sent,
  count(*) filter (
    where r.status in ('initiated', 'sent') and r.created_at::date = current_date
  ) as sent_today,
  count(*) filter (
    where r.status in ('initiated', 'sent')
      and date_trunc('month', r.created_at) = date_trunc('month', now())
  ) as sent_this_month
from public.reminders r
group by r.organization_id;
