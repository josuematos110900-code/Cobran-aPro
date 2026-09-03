import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Pencil,
  Archive,
  Phone,
  Mail,
  MapPin,
  MessageCircle,
  Wallet,
  Clock,
  AlertTriangle,
  Repeat,
  Pause,
  Play,
  Ban,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Badge } from '@/components/ui/Badge';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import type { Client, Invoice, InvoiceStatus, Payment } from '@/lib/types/database';
import {
  archiveClient,
  fetchClientBalance,
  fetchClientById,
  fetchClientInvoices,
  fetchClientPayments,
  fetchClientSubscriptions,
} from './api';
import { ClientFormModal } from './ClientFormModal';
import { ClientStatusBadge } from './StatusBadge';
import type { ClientBalance } from './types';
import {
  setSubscriptionStatus,
  type SubscriptionWithRelations,
} from '@/features/subscriptions/api';
import { SubscriptionFormModal } from '@/features/subscriptions/SubscriptionFormModal';
import { SubscriptionStatusBadge } from '@/features/subscriptions/SubscriptionStatusBadge';
import { BILLING_PERIOD_LABELS } from '@/features/subscriptions/types';
import { ReceiptModal } from '@/features/payments/ReceiptModal';
import { SendWhatsAppModal } from '@/features/reminders/SendWhatsAppModal';
import { ReminderTypeBadge } from '@/features/reminders/ReminderTypeBadge';
import { fetchClientReminders, type ReminderWithRelations } from '@/features/reminders/api';

function formatCurrency(value: number, currency: string) {
  try {
    return new Intl.NumberFormat('pt-AO', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(value);
  } catch {
    return `${value.toLocaleString('pt-AO')} ${currency}`;
  }
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

const INVOICE_STATUS_COLORS: Record<InvoiceStatus, 'green' | 'amber' | 'red' | 'slate'> = {
  paid: 'green',
  pending: 'amber',
  overdue: 'red',
  cancelled: 'slate',
};

const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  paid: 'Paga',
  pending: 'Pendente',
  overdue: 'Atrasada',
  cancelled: 'Cancelada',
};

type ClientPayment = Payment & { invoices: { invoice_number: string; description: string | null } | null };

type ClientSubscription = SubscriptionWithRelations;

export function ClientProfilePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { organization } = useAuth();
  const { showToast } = useToast();

  const [client, setClient] = useState<Client | null>(null);
  const [balance, setBalance] = useState<ClientBalance | null>(null);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [payments, setPayments] = useState<ClientPayment[]>([]);
  const [subscriptions, setSubscriptions] = useState<ClientSubscription[]>([]);
  const [reminders, setReminders] = useState<ReminderWithRelations[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [receiptPayment, setReceiptPayment] = useState<ClientPayment | null>(null);
  const [whatsAppOpen, setWhatsAppOpen] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [archiving, setArchiving] = useState(false);

  const [subscriptionFormOpen, setSubscriptionFormOpen] = useState(false);
  const [editingSubscription, setEditingSubscription] = useState<ClientSubscription | null>(null);
  const [cancelSubscriptionTarget, setCancelSubscriptionTarget] = useState<ClientSubscription | null>(null);
  const [subscriptionActionId, setSubscriptionActionId] = useState<string | null>(null);

  const currency = organization?.currency ?? 'AOA';

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);

    const { client: fetchedClient, error: clientError } = await fetchClientById(id);

    if (clientError || !fetchedClient) {
      setError('Cliente não encontrado ou sem acesso.');
      setLoading(false);
      return;
    }

    setClient(fetchedClient);

    const [
      { balance: fetchedBalance },
      { invoices: fetchedInvoices },
      { payments: fetchedPayments },
      { subscriptions: fetchedSubscriptions },
      { reminders: fetchedReminders },
    ] = await Promise.all([
      fetchClientBalance(id),
      fetchClientInvoices(id),
      fetchClientPayments(id),
      fetchClientSubscriptions(id),
      fetchClientReminders(id),
    ]);

    setBalance(fetchedBalance);
    setInvoices(fetchedInvoices as Invoice[]);
    setPayments(fetchedPayments as ClientPayment[]);
    setSubscriptions(fetchedSubscriptions as ClientSubscription[]);
    setReminders(fetchedReminders);
    setLoading(false);
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleConfirmArchive() {
    if (!client) return;
    setArchiving(true);
    const { error: archiveError } = await archiveClient(client.id);
    setArchiving(false);

    if (archiveError) {
      showToast('Não foi possível arquivar o cliente.', 'error');
      return;
    }

    showToast('Cliente arquivado.');
    setArchiveOpen(false);
    load();
  }

  function handleSaved(updated: Client) {
    setClient(updated);
    setFormOpen(false);
    showToast('Cliente actualizado.');
  }

  function handleSubscriptionSaved() {
    setSubscriptionFormOpen(false);
    setEditingSubscription(null);
    showToast('Cobrança recorrente guardada.');
    load();
  }

  async function handleToggleSubscriptionPause(subscription: ClientSubscription) {
    setSubscriptionActionId(subscription.id);
    const nextStatus = subscription.status === 'active' ? 'paused' : 'active';
    const { error: statusError } = await setSubscriptionStatus(subscription.id, nextStatus);
    setSubscriptionActionId(null);

    if (statusError) {
      showToast('Não foi possível actualizar o estado da subscrição.', 'error');
      return;
    }

    showToast(nextStatus === 'paused' ? 'Subscrição pausada.' : 'Subscrição retomada.');
    load();
  }

  async function handleConfirmCancelSubscription() {
    if (!cancelSubscriptionTarget) return;
    setSubscriptionActionId(cancelSubscriptionTarget.id);
    const { error: statusError } = await setSubscriptionStatus(cancelSubscriptionTarget.id, 'cancelled');
    setSubscriptionActionId(null);

    if (statusError) {
      showToast('Não foi possível cancelar a subscrição.', 'error');
      return;
    }

    showToast('Subscrição cancelada.');
    setCancelSubscriptionTarget(null);
    load();
  }

  if (loading) {
    return (
      <AppShell>
        <div className="mx-auto max-w-4xl space-y-4 p-4 md:p-8">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
      </AppShell>
    );
  }

  if (error || !client) {
    return (
      <AppShell>
        <div className="mx-auto max-w-4xl p-4 md:p-8">
          <ErrorMessage message={error ?? 'Cliente não encontrado.'} />
          <Button variant="secondary" className="mt-4" onClick={() => navigate('/clients')}>
            <ArrowLeft className="h-4 w-4" /> Voltar aos clientes
          </Button>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl p-4 md:p-8">
        <button
          onClick={() => navigate('/clients')}
          className="mb-4 flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
        >
          <ArrowLeft className="h-4 w-4" /> Voltar aos clientes
        </button>

        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-bold text-slate-900 dark:text-white">{client.name}</h1>
              <ClientStatusBadge status={client.status} />
            </div>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              Cliente desde {formatDate(client.created_at)}
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setFormOpen(true)}>
              <Pencil className="h-4 w-4" /> Editar
            </Button>
            {client.status !== 'archived' && (
              <Button variant="danger" onClick={() => setArchiveOpen(true)}>
                <Archive className="h-4 w-4" /> Arquivar
              </Button>
            )}
          </div>
        </div>

        {/* Informações de contacto */}
        <Card className="mt-6">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Informações
          </h2>
          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
              <Phone className="h-4 w-4 text-slate-400" /> {client.phone || 'Sem telefone'}
            </div>
            <div className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
              <Mail className="h-4 w-4 text-slate-400" /> {client.email || 'Sem email'}
            </div>
            <div className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300 sm:col-span-2">
              <MapPin className="h-4 w-4 text-slate-400" /> {client.address || 'Sem endereço'}
            </div>
          </div>
          {client.notes && (
            <div className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-800/50 dark:text-slate-400">
              {client.notes}
            </div>
          )}

          {/* Mensagem manual, registada como lembrete do tipo "manual". */}
          <Button
            variant="secondary"
            className="mt-4"
            disabled={!client.phone}
            title={
              client.phone
                ? 'Enviar mensagem via WhatsApp'
                : 'Adicione um telefone para activar esta acção'
            }
            onClick={() => setWhatsAppOpen(true)}
          >
            <MessageCircle className="h-4 w-4" /> Enviar WhatsApp
          </Button>
        </Card>

        {/* Cobranças recorrentes */}
        <Card className="mt-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Cobranças recorrentes
            </h2>
            <Button
              variant="secondary"
              onClick={() => {
                setEditingSubscription(null);
                setSubscriptionFormOpen(true);
              }}
            >
              <Repeat className="h-4 w-4" /> Nova
            </Button>
          </div>

          {subscriptions.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Este cliente ainda não tem nenhuma cobrança recorrente associada.
            </p>
          ) : (
            <div className="mt-3 divide-y divide-slate-200 dark:divide-slate-800">
              {subscriptions.map((subscription) => (
                <div
                  key={subscription.id}
                  className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-900 dark:text-white">
                        {subscription.services?.name ?? 'Serviço removido'}
                      </span>
                      <SubscriptionStatusBadge status={subscription.status} />
                    </div>
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      {formatCurrency(subscription.amount, subscription.currency)} /{' '}
                      {BILLING_PERIOD_LABELS[subscription.billing_period].toLowerCase()}
                      {subscription.status === 'active' && subscription.next_billing_date && (
                        <> · Próxima cobrança: {formatDate(subscription.next_billing_date)}</>
                      )}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setEditingSubscription(subscription);
                        setSubscriptionFormOpen(true);
                      }}
                      disabled={subscription.status === 'cancelled' || subscription.status === 'ended'}
                    >
                      <Pencil className="h-4 w-4" /> Editar
                    </Button>
                    {(subscription.status === 'active' || subscription.status === 'paused') && (
                      <Button
                        variant="secondary"
                        loading={subscriptionActionId === subscription.id}
                        onClick={() => handleToggleSubscriptionPause(subscription)}
                      >
                        {subscription.status === 'active' ? (
                          <>
                            <Pause className="h-4 w-4" /> Pausar
                          </>
                        ) : (
                          <>
                            <Play className="h-4 w-4" /> Retomar
                          </>
                        )}
                      </Button>
                    )}
                    {(subscription.status === 'active' || subscription.status === 'paused') && (
                      <Button variant="danger" onClick={() => setCancelSubscriptionTarget(subscription)}>
                        <Ban className="h-4 w-4" /> Cancelar
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Totais financeiros */}
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <Wallet className="h-4 w-4 text-emerald-500" /> Total pago
            </div>
            <div className="mt-2 text-xl font-bold text-emerald-600 dark:text-emerald-400">
              {formatCurrency(balance?.total_paid ?? 0, currency)}
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <Clock className="h-4 w-4 text-amber-500" /> Pendente
            </div>
            <div className="mt-2 text-xl font-bold text-amber-600 dark:text-amber-400">
              {formatCurrency(balance?.total_pending ?? 0, currency)}
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <AlertTriangle className="h-4 w-4 text-red-500" /> Atrasado
            </div>
            <div className="mt-2 text-xl font-bold text-red-600 dark:text-red-400">
              {formatCurrency(balance?.total_overdue ?? 0, currency)}
            </div>
          </Card>
        </div>

        {/* Histórico de cobranças */}
        <Card className="mt-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Cobranças
          </h2>
          {invoices.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Ainda não existem cobranças para este cliente.
            </p>
          ) : (
            <div className="mt-3 divide-y divide-slate-200 dark:divide-slate-800">
              {invoices.map((invoice) => (
                <div key={invoice.id} className="flex items-center justify-between py-2.5 text-sm">
                  <div>
                    <div className="font-medium text-slate-900 dark:text-white">
                      {invoice.description || invoice.invoice_number}
                    </div>
                    <div className="text-xs text-slate-500 dark:text-slate-400">
                      Vence em {formatDate(invoice.due_date)}
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-slate-700 dark:text-slate-300">
                      {formatCurrency(invoice.amount, invoice.currency)}
                    </span>
                    <Badge color={INVOICE_STATUS_COLORS[invoice.status]}>
                      {INVOICE_STATUS_LABELS[invoice.status]}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Histórico de pagamentos */}
        <Card className="mt-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Pagamentos
          </h2>
          {payments.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Ainda não existem pagamentos registados para este cliente.
            </p>
          ) : (
            <div className="mt-3 divide-y divide-slate-200 dark:divide-slate-800">
              {payments.map((payment) => (
                <div key={payment.id} className="flex items-center justify-between py-2.5 text-sm">
                  <div>
                    <div className="text-slate-700 dark:text-slate-300">{formatDate(payment.paid_at)}</div>
                    <div className="text-xs text-slate-400">{payment.invoices?.invoice_number ?? '—'}</div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-emerald-600 dark:text-emerald-400">
                      {formatCurrency(payment.amount, payment.currency)}
                    </span>
                    <button
                      onClick={() => setReceiptPayment(payment)}
                      className="text-xs font-medium text-brand-600 hover:underline dark:text-brand-400"
                    >
                      Ver recibo
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Histórico de lembretes */}
        <Card className="mt-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Histórico de lembretes
          </h2>
          {reminders.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Ainda não foi iniciado nenhum lembrete para este cliente.
            </p>
          ) : (
            <div className="mt-3 divide-y divide-slate-200 dark:divide-slate-800">
              {reminders.map((reminder) => (
                <div key={reminder.id} className="py-2.5 text-sm">
                  <div className="flex items-center justify-between gap-2">
                    <ReminderTypeBadge type={reminder.reminder_type} />
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                      {reminder.sent_at ? formatDate(reminder.sent_at) : '—'}
                      {reminder.invoices?.invoice_number ? ` · ${reminder.invoices.invoice_number}` : ''}
                    </span>
                  </div>
                  <p className="mt-1 line-clamp-2 text-xs text-slate-500 dark:text-slate-400">
                    {reminder.message}
                  </p>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {organization && (
        <ClientFormModal
          open={formOpen}
          organizationId={organization.id}
          client={client}
          onClose={() => setFormOpen(false)}
          onSaved={handleSaved}
        />
      )}

      <ConfirmDialog
        open={archiveOpen}
        title="Arquivar cliente"
        description={`Tem a certeza que quer arquivar "${client.name}"? Pode reverter isto mais tarde ao editar o cliente.`}
        confirmLabel="Arquivar"
        danger
        loading={archiving}
        onConfirm={handleConfirmArchive}
        onCancel={() => setArchiveOpen(false)}
      />

      {organization && (
        <SubscriptionFormModal
          open={subscriptionFormOpen}
          organizationId={organization.id}
          defaultCurrency={organization.currency}
          subscription={editingSubscription}
          preselectedClientId={client.id}
          onClose={() => {
            setSubscriptionFormOpen(false);
            setEditingSubscription(null);
          }}
          onSaved={handleSubscriptionSaved}
        />
      )}

      <ConfirmDialog
        open={Boolean(cancelSubscriptionTarget)}
        title="Cancelar cobrança recorrente"
        description="Tem a certeza que quer cancelar esta subscrição? Não serão geradas mais facturas a partir dela."
        confirmLabel="Cancelar subscrição"
        danger
        loading={subscriptionActionId === cancelSubscriptionTarget?.id}
        onConfirm={handleConfirmCancelSubscription}
        onCancel={() => setCancelSubscriptionTarget(null)}
      />

      {receiptPayment && (
        <ReceiptModal
          open={Boolean(receiptPayment)}
          onClose={() => setReceiptPayment(null)}
          organizationName={organization?.name ?? ''}
          clientName={client.name}
          invoiceNumber={receiptPayment.invoices?.invoice_number ?? '—'}
          description={receiptPayment.invoices?.description ?? null}
          amount={receiptPayment.amount}
          currency={receiptPayment.currency}
          paymentMethod={receiptPayment.payment_method}
          transactionReference={receiptPayment.transaction_reference}
          paidAt={receiptPayment.paid_at}
        />
      )}

      {organization && client.phone && (
        <SendWhatsAppModal
          open={whatsAppOpen}
          onClose={() => setWhatsAppOpen(false)}
          organizationId={organization.id}
          client={{ id: client.id, name: client.name, phone: client.phone }}
          mode={{ kind: 'manual' }}
          onSent={() => {
            setWhatsAppOpen(false);
            load();
          }}
        />
      )}
    </AppShell>
  );
}
