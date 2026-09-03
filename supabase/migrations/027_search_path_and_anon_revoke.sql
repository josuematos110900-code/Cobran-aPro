-- 027_search_path_and_anon_revoke.sql
-- Fixa search_path em todas as funções que ainda não o tinham (protecção
-- contra hijacking de schema).
alter function public.assert_report_access(uuid, date, date) set search_path = public;
alter function public.compute_next_billing_date(date, smallint, text, date) set search_path = public;
alter function public.enforce_invoice_status_transition() set search_path = public;
alter function public.enforce_member_role_change() set search_path = public;
alter function public.get_recent_overdue_invoices(uuid, integer) set search_path = public;
alter function public.handle_new_organization_trial() set search_path = public;
alter function public.plan_limits(text) set search_path = public;
alter function public.report_invoice_status_breakdown(uuid, date, date) set search_path = public;
alter function public.report_overdue_top(uuid, integer) set search_path = public;
alter function public.report_revenue_series(uuid, date, date, text) set search_path = public;
alter function public.report_service_breakdown(uuid, date, date) set search_path = public;
alter function public.report_summary(uuid, date, date) set search_path = public;
alter function public.report_top_clients(uuid, date, date, integer) set search_path = public;
alter function public.set_subscription_next_billing_date() set search_path = public;
alter function public.set_updated_at() set search_path = public;
alter function public.trg_enforce_clients_limit() set search_path = public;
alter function public.trg_enforce_invoices_limit() set search_path = public;
alter function public.trg_enforce_members_limit() set search_path = public;
alter function public.trg_enforce_services_limit() set search_path = public;
alter function public.trial_duration_days() set search_path = public;

-- Revoga EXECUTE de "anon" explicitamente, função a função (ver
-- 028_revoke_public_grant_authenticated.sql: revogar só de "anon" não
-- chega para estas funções — nunca tinham "revoke ... from public" e
-- por isso continuavam acessíveis via o pseudo-papel PUBLIC).
revoke execute on function public.add_organization_member_by_email(uuid, text, text) from anon;
revoke execute on function public.assert_report_access(uuid, date, date) from anon;
revoke execute on function public.compute_next_billing_date(date, smallint, text, date) from anon;
revoke execute on function public.enforce_invoice_status_transition() from anon;
revoke execute on function public.enforce_member_role_change() from anon;
revoke execute on function public.enforce_plan_limit(uuid, text) from anon;
revoke execute on function public.get_effective_plan(uuid) from anon;
revoke execute on function public.get_organization_usage(uuid) from anon;
revoke execute on function public.get_plan_status(uuid) from anon;
revoke execute on function public.get_recent_overdue_invoices(uuid, integer) from anon;
revoke execute on function public.handle_new_organization_settings() from anon;
revoke execute on function public.handle_new_organization_trial() from anon;
revoke execute on function public.handle_new_user() from anon;
revoke execute on function public.handle_payment_marks_invoice_paid() from anon;
revoke execute on function public.list_organization_members(uuid) from anon;
revoke execute on function public.mark_invoice_paid(uuid, text, text, timestamptz, text) from anon;
revoke execute on function public.next_invoice_number(uuid) from anon;
revoke execute on function public.remove_organization_member(uuid) from anon;
revoke execute on function public.report_invoice_status_breakdown(uuid, date, date) from anon;
revoke execute on function public.report_overdue_top(uuid, integer) from anon;
revoke execute on function public.report_revenue_series(uuid, date, date, text) from anon;
revoke execute on function public.report_service_breakdown(uuid, date, date) from anon;
revoke execute on function public.report_summary(uuid, date, date) from anon;
revoke execute on function public.report_top_clients(uuid, date, date, integer) from anon;
revoke execute on function public.request_plan_upgrade(uuid, text, text, text) from anon;
revoke execute on function public.set_subscription_next_billing_date() from anon;
revoke execute on function public.set_updated_at() from anon;
revoke execute on function public.trg_enforce_clients_limit() from anon;
revoke execute on function public.trg_enforce_invoices_limit() from anon;
revoke execute on function public.trg_enforce_members_limit() from anon;
revoke execute on function public.trg_enforce_recurring_enabled() from anon;
revoke execute on function public.trg_enforce_services_limit() from anon;
revoke execute on function public.trial_duration_days() from anon;
revoke execute on function public.update_member_role(uuid, text) from anon;
revoke execute on function public.user_admin_organization_ids() from anon;
revoke execute on function public.user_organization_ids() from anon;
