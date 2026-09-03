-- 001_extensions_and_helpers.sql
-- Extensões necessárias e funções utilitárias reutilizadas pelas
-- migrations seguintes.

create extension if not exists "pgcrypto";

-- Função genérica para manter a coluna updated_at sempre actualizada.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
