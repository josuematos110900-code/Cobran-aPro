-- 023_organization_contact_info.sql
-- Fase 2 (Definições): dados de contacto da empresa, usados na página
-- /settings e futuramente em documentos/recibos.

alter table public.organizations
  add column if not exists phone text,
  add column if not exists email text,
  add column if not exists address text;
