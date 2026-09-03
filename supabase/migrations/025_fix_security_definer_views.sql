-- 025_fix_security_definer_views.sql
-- CRÍTICO: no Postgres 15+, uma view criada sem "security_invoker = true"
-- corre com as permissões do DONO da view (normalmente "postgres", que
-- ignora RLS), não do utilizador que a consulta — o oposto do que os
-- comentários das migrations 012/013/018/019 assumiam ("security invoker
-- por omissão"). Isto permitia, por exemplo, que um utilizador
-- autenticado consultasse dashboard_totals de QUALQUER organização, não
-- só da sua — descoberto via `mcp__Supabase__get_advisors` (lint
-- security_definer_view) depois de aplicar o schema num projecto real
-- em Postgres 17. Corrigido para as 4 views existentes.

alter view public.dashboard_totals set (security_invoker = true);
alter view public.client_balances set (security_invoker = true);
alter view public.payment_totals set (security_invoker = true);
alter view public.reminder_totals set (security_invoker = true);
