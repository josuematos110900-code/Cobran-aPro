-- reset_public_schema.sql
--
-- APAGA POR COMPLETO tudo o que estiver no schema "public" (tabelas,
-- views, funções, policies) e recria-o vazio, com as permissões que o
-- Supabase espera. Não toca em auth.*, storage.* nem em nenhum outro
-- schema interno do Supabase — só o "public", onde vive o schema da
-- aplicação.
--
-- Use isto SÓ se o projecto ainda não tiver dados reais que precise de
-- manter (ex: uma tentativa anterior mal sucedida de aplicar o schema).
-- Depois de correr isto, corra supabase/schema_full.sql (ou as
-- migrations 001 a 024 uma a uma) num schema já limpo.

drop schema public cascade;
create schema public;

grant usage on schema public to postgres, anon, authenticated, service_role;
grant all on schema public to postgres;
grant all on schema public to anon;
grant all on schema public to authenticated;
grant all on schema public to service_role;

alter default privileges in schema public grant all on tables to postgres, anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to postgres, anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to postgres, anon, authenticated, service_role;
