import { useEffect, useState } from 'react';
import { CheckCircle2, MessageCircle, Sparkles } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { Modal } from '@/components/ui/Modal';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import { PLANS, formatAoa, getPlanDefinition, SALES_WHATSAPP_NUMBER } from '@/lib/plans';
import type { PlanStatus, PlanTier } from '@/lib/types/database';
import { fetchPlanStatus, requestPlanUpgrade } from './api';
import { UsageBar } from './UsageBar';

const STATUS_LABELS: Record<string, { label: string; color: 'green' | 'amber' | 'red' | 'blue' | 'slate' }> = {
  trialing: { label: 'Em teste gratuito', color: 'blue' },
  active: { label: 'Activo', color: 'green' },
  past_due: { label: 'Pagamento em falta', color: 'amber' },
  cancelled: { label: 'Cancelado', color: 'red' },
  free: { label: 'Plano gratuito', color: 'slate' },
};

export function BillingPage() {
  const { organization, isAdmin } = useAuth();
  const { showToast } = useToast();

  const [status, setStatus] = useState<PlanStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [requesting, setRequesting] = useState<PlanTier | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<PlanTier | null>(null);
  const [requestSent, setRequestSent] = useState(false);

  async function load() {
    if (!organization) return;
    setLoading(true);
    setError(null);
    const { status: fetched, error: fetchError } = await fetchPlanStatus(organization.id);
    setLoading(false);
    if (fetchError || !fetched) {
      setError(fetchError?.message ?? 'Não foi possível carregar o estado do seu plano.');
      return;
    }
    setStatus(fetched);
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [organization?.id]);

  async function handleRequestUpgrade() {
    if (!organization || !selectedPlan) return;
    setRequesting(selectedPlan);
    const { error: reqError } = await requestPlanUpgrade(organization.id, selectedPlan, 'monthly', null);
    setRequesting(null);

    if (reqError) {
      showToast(reqError.message || 'Não foi possível registar o pedido.', 'error');
      return;
    }

    setRequestSent(true);
    showToast('Pedido de upgrade registado com sucesso.');
  }

  const statusMeta = status ? STATUS_LABELS[status.subscription_status] ?? STATUS_LABELS.free : null;
  const currentPlanDef = status ? getPlanDefinition(status.plan) : null;
  const selectedPlanDef = selectedPlan ? getPlanDefinition(selectedPlan) : null;

  const whatsappHref =
    SALES_WHATSAPP_NUMBER && selectedPlanDef
      ? `https://wa.me/${SALES_WHATSAPP_NUMBER.replace(/\D/g, '')}?text=${encodeURIComponent(
          `Olá! Gostaria de actualizar a minha organização "${organization?.name ?? ''}" para o plano ${selectedPlanDef.name} do CobrançaPro.`
        )}`
      : null;

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Plano e facturação</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Consulte o seu plano actual, a utilização e actualize quando precisar.
        </p>

        {error && (
          <div className="mt-4">
            <ErrorMessage message={error} />
          </div>
        )}

        {loading ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            <Skeleton className="h-40" />
            <Skeleton className="h-40" />
          </div>
        ) : status ? (
          <>
            <Card className="mt-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
                      Plano {currentPlanDef?.name}
                    </h2>
                    {statusMeta && <Badge color={statusMeta.color}>{statusMeta.label}</Badge>}
                  </div>
                  {status.is_trial_active && (
                    <p className="mt-1 text-sm text-brand-700 dark:text-brand-400">
                      Está em período de teste gratuito com acesso ao plano Profissional — restam{' '}
                      <strong>{status.trial_days_remaining}</strong>{' '}
                      {status.trial_days_remaining === 1 ? 'dia' : 'dias'}.
                    </p>
                  )}
                  {status.is_trial_expired && (
                    <p className="mt-1 text-sm text-amber-700 dark:text-amber-400">
                      O seu teste gratuito terminou. Está agora no plano {currentPlanDef?.name} — actualize para
                      recuperar as funcionalidades do Profissional.
                    </p>
                  )}
                </div>
                {currentPlanDef?.priceMonthlyAoa !== null && currentPlanDef?.priceMonthlyAoa !== undefined && (
                  <div className="text-right">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      {currentPlanDef.priceMonthlyAoa === 0 ? 'Grátis' : formatAoa(currentPlanDef.priceMonthlyAoa)}
                    </div>
                    {currentPlanDef.priceMonthlyAoa > 0 && (
                      <div className="text-xs text-slate-500 dark:text-slate-400">por mês</div>
                    )}
                  </div>
                )}
              </div>

              <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <UsageBar label="Clientes" current={status.usage.clients_count} max={status.limits.max_clients} />
                <UsageBar label="Serviços" current={status.usage.services_count} max={status.limits.max_services} />
                <UsageBar label="Membros da equipa" current={status.usage.members_count} max={status.limits.max_members} />
                <UsageBar
                  label="Cobranças este mês"
                  current={status.usage.invoices_this_month}
                  max={status.limits.max_invoices_per_month}
                />
              </div>
            </Card>

            <h2 className="mt-8 text-lg font-semibold text-slate-900 dark:text-white">Planos disponíveis</h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {PLANS.map((plan) => {
                const isCurrent = plan.id === status.plan;
                return (
                  <Card key={plan.id} className={isCurrent ? 'ring-2 ring-brand-500' : ''}>
                    <div className="flex items-center gap-1.5">
                      <h3 className="text-base font-semibold text-slate-900 dark:text-white">{plan.name}</h3>
                      {plan.id === 'profissional' && <Sparkles className="h-4 w-4 text-brand-500" aria-hidden="true" />}
                    </div>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{plan.tagline}</p>
                    <div className="mt-3 text-xl font-bold text-slate-900 dark:text-white">
                      {plan.priceMonthlyAoa === null
                        ? 'Sob consulta'
                        : plan.priceMonthlyAoa === 0
                          ? 'Grátis'
                          : formatAoa(plan.priceMonthlyAoa)}
                      {plan.priceMonthlyAoa !== null && plan.priceMonthlyAoa > 0 && (
                        <span className="text-xs font-normal text-slate-500 dark:text-slate-400"> /mês</span>
                      )}
                    </div>
                    <ul className="mt-3 space-y-1.5">
                      {plan.highlights.map((h) => (
                        <li key={h} className="flex items-start gap-1.5 text-xs text-slate-600 dark:text-slate-400">
                          <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-500" aria-hidden="true" />
                          {h}
                        </li>
                      ))}
                    </ul>
                    <div className="mt-4">
                      {isCurrent ? (
                        <Badge color="blue">Plano actual</Badge>
                      ) : plan.id === 'free' ? (
                        <span className="text-xs text-slate-400">Plano de entrada</span>
                      ) : !isAdmin ? (
                        <span className="text-xs text-slate-400">Só o dono/admin pode pedir upgrade</span>
                      ) : (
                        <Button
                          variant="secondary"
                          className="w-full"
                          onClick={() => {
                            setSelectedPlan(plan.id);
                            setRequestSent(false);
                          }}
                        >
                          Pedir este plano
                        </Button>
                      )}
                    </div>
                  </Card>
                );
              })}
            </div>
          </>
        ) : null}

        <Modal
          open={selectedPlan !== null}
          onClose={() => setSelectedPlan(null)}
          title={`Pedir plano ${selectedPlanDef?.name ?? ''}`}
          footer={
            requestSent ? (
              <Button onClick={() => setSelectedPlan(null)}>Fechar</Button>
            ) : (
              <>
                <Button variant="secondary" onClick={() => setSelectedPlan(null)} disabled={requesting !== null}>
                  Cancelar
                </Button>
                <Button onClick={handleRequestUpgrade} loading={requesting !== null}>
                  Confirmar pedido
                </Button>
              </>
            )
          }
        >
          {requestSent ? (
            <div className="space-y-3 text-sm text-slate-600 dark:text-slate-400">
              <p>
                O seu pedido para o plano <strong>{selectedPlanDef?.name}</strong> foi registado. Ainda não existe
                cobrança automática para Angola nesta versão — a nossa equipa confirma o pagamento manualmente e
                activa o plano na sua organização.
              </p>
              {whatsappHref && (
                <a
                  href={whatsappHref}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-emerald-700"
                >
                  <MessageCircle className="h-4 w-4" aria-hidden="true" />
                  Falar connosco no WhatsApp
                </a>
              )}
            </div>
          ) : (
            <p className="text-sm text-slate-600 dark:text-slate-400">
              Isto regista um pedido de actualização para o plano <strong>{selectedPlanDef?.name}</strong>. Como
              ainda não existe um processador de pagamentos com activação automática para Angola, a nossa equipa
              entra em contacto para combinar o pagamento e activa o plano manualmente assim que confirmado.
            </p>
          )}
        </Modal>
      </div>
    </AppShell>
  );
}
