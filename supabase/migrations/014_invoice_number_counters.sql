-- 014_invoice_number_counters.sql
-- Resolve a limitação de concorrência identificada em next_invoice_number():
-- contar linhas em "invoices" não é seguro sob concorrência alta (duas
-- transacções podem ler a mesma contagem antes de qualquer uma confirmar).
--
-- Em vez disso, mantemos um contador dedicado por organização e usamos
-- "insert ... on conflict ... do update" para o incrementar atomicamente:
-- este padrão adquire um lock de linha durante o UPDATE, pelo que chamadas
-- concorrentes são serializadas pelo Postgres e nunca devolvem o mesmo
-- número duas vezes.

create table if not exists public.invoice_number_counters (
  organization_id uuid primary key references public.organizations (id) on delete cascade,
  last_number integer not null default 0
);

-- Esta tabela é gerida inteiramente através da função abaixo (security
-- definer). Não expomos políticas de insert/update/delete a utilizadores
-- normais — só a leitura é permitida, para eventual auditoria/debug.
alter table public.invoice_number_counters enable row level security;

create policy "invoice_number_counters_select_member"
  on public.invoice_number_counters for select
  using (organization_id in (select public.user_organization_ids()));

create or replace function public.next_invoice_number(p_organization_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_next integer;
begin
  if p_organization_id not in (select public.user_organization_ids()) then
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

-- Apenas utilizadores autenticados (membros de alguma organização, filtrado
-- dentro da própria função de negócio que a chama) podem invocar isto.
revoke execute on function public.next_invoice_number(uuid) from public;
grant execute on function public.next_invoice_number(uuid) to authenticated;
