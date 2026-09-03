-- 028_revoke_public_grant_authenticated.sql
-- A causa raiz do problema em 027: estas funções nunca tiveram
-- "revoke ... from public" nas migrations que as criaram, por isso o
-- EXECUTE concedido por omissão ao pseudo-papel PUBLIC (que todo o papel,
-- incluindo "anon", herda) nunca foi fechado — revogar apenas de "anon"
-- não chega enquanto PUBLIC ainda tiver o grant. Fecha-se aqui em PUBLIC
-- e reabre-se apenas para "authenticated" onde é mesmo necessário:
--   - user_organization_ids()/user_admin_organization_ids() são chamadas
--     dentro das próprias políticas RLS quando um utilizador autenticado
--     consulta qualquer tabela;
--   - os report_* são RPCs chamadas directamente pelo frontend;
--   - compute_next_billing_date()/assert_report_access() são chamadas
--     internamente a partir de funções "security invoker".
-- As funções que só servem de trigger (enforce_invoice_status_transition,
-- enforce_member_role_change, handle_new_organization_settings,
-- handle_new_organization_trial, handle_new_user,
-- handle_payment_marks_invoice_paid, set_subscription_next_billing_date,
-- set_updated_at, trg_enforce_*) ficam sem grant nenhum, de propósito —
-- o mecanismo de triggers do Postgres invoca-as internamente sem
-- verificar o privilégio EXECUTE de quem despoletou o trigger, e chamá-
-- las directamente por RPC falha sempre com "trigger functions can only
-- be called as triggers", independentemente de qualquer grant.
revoke execute on function public.assert_report_access(uuid, date, date) from public;
revoke execute on function public.compute_next_billing_date(date, smallint, text, date) from public;
revoke execute on function public.enforce_invoice_status_transition() from public;
revoke execute on function public.enforce_member_role_change() from public;
revoke execute on function public.enforce_plan_limit(uuid, text) from public;
revoke execute on function public.get_recent_overdue_invoices(uuid, integer) from public;
revoke execute on function public.handle_new_organization_settings() from public;
revoke execute on function public.handle_new_organization_trial() from public;
revoke execute on function public.handle_new_user() from public;
revoke execute on function public.handle_payment_marks_invoice_paid() from public;
revoke execute on function public.report_invoice_status_breakdown(uuid, date, date) from public;
revoke execute on function public.report_overdue_top(uuid, integer) from public;
revoke execute on function public.report_revenue_series(uuid, date, date, text) from public;
revoke execute on function public.report_service_breakdown(uuid, date, date) from public;
revoke execute on function public.report_summary(uuid, date, date) from public;
revoke execute on function public.report_top_clients(uuid, date, date, integer) from public;
revoke execute on function public.set_subscription_next_billing_date() from public;
revoke execute on function public.set_updated_at() from public;
revoke execute on function public.trg_enforce_clients_limit() from public;
revoke execute on function public.trg_enforce_invoices_limit() from public;
revoke execute on function public.trg_enforce_members_limit() from public;
revoke execute on function public.trg_enforce_recurring_enabled() from public;
revoke execute on function public.trg_enforce_services_limit() from public;
revoke execute on function public.trial_duration_days() from public;
revoke execute on function public.user_admin_organization_ids() from public;
revoke execute on function public.user_organization_ids() from public;

grant execute on function public.assert_report_access(uuid, date, date) to authenticated;
grant execute on function public.compute_next_billing_date(date, smallint, text, date) to authenticated;
grant execute on function public.get_recent_overdue_invoices(uuid, integer) to authenticated;
grant execute on function public.report_invoice_status_breakdown(uuid, date, date) to authenticated;
grant execute on function public.report_overdue_top(uuid, integer) to authenticated;
grant execute on function public.report_revenue_series(uuid, date, date, text) to authenticated;
grant execute on function public.report_service_breakdown(uuid, date, date) to authenticated;
grant execute on function public.report_summary(uuid, date, date) to authenticated;
grant execute on function public.report_top_clients(uuid, date, date, integer) to authenticated;
grant execute on function public.trial_duration_days() to authenticated;
grant execute on function public.user_admin_organization_ids() to authenticated;
grant execute on function public.user_organization_ids() to authenticated;
