import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Pencil,
  Ban,
  CheckCircle2,
  MessageCircle,
  Hash,
  Calendar,
  Wallet,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import { cancelInvoice, fetchInvoiceById } from './api';
import type { InvoiceWithClient } from './api';
import { InvoiceFormModal } from './InvoiceFormModal';
import { InvoiceStatusBadge } from './InvoiceStatusBadge';
import { MarkAsPaidDialog } from './MarkAsPaidDialog';
import { fetchInvoiceReminders, type ReminderWithRelations } from '@/features/reminders/api';
import { SendWhatsAppModal } from '@/features/reminders/SendWhatsAppModal';
import { ReminderTypeBadge } from '@/features/reminders/ReminderTypeBadge';

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

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

type InvoiceDetail = InvoiceWithClient & { clients: { name: string; phone: string | null } | null };

export function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { organization } = useAuth();
  const { showToast } = useToast();

  const [invoice, setInvoice] = useState<InvoiceDetail | null>(null);
  const [reminders, setReminders] = useState<ReminderWithRelations[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editOpen, setEditOpen] = useState(false);
  const [payOpen, setPayOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [whatsAppOpen, setWhatsAppOpen] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);

    const { invoice: fetched, error: fetchError } = await fetchInvoiceById(id);

    if (fetchError || !fetched) {
      setError('Cobrança não encontrada ou sem acesso.');
      setLoading(false);
      return;
    }

    setInvoice(fetched as InvoiceDetail);

    const { reminders: fetchedReminders } = await fetchInvoiceReminders(id);
    setReminders(fetchedReminders);
    setLoading(false);
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleConfirmCancel() {
    if (!invoice) return;
    setCancelling(true);
    const { error: cancelError } = await cancelInvoice(invoice.id);
    setCancelling(false);

    if (cancelError) {
      showToast('Não foi possível cancelar a cobrança.', 'error');
      return;
    }

    showToast('Cobrança cancelada.');
    setCancelOpen(false);
    load();
  }

  function handleSaved(updated: InvoiceWithClient) {
    setInvoice(updated as InvoiceDetail);
    setEditOpen(false);
    showToast('Cobrança actualizada.');
  }

  function handlePaySuccess() {
    setPayOpen(false);
    showToast('Pagamento registado com sucesso.');
    load();
  }

  function handleWhatsAppSent() {
    setWhatsAppOpen(false);
    load();
  }

  const canEdit = invoice !== null && (invoice.status === 'pending' || invoice.status === 'overdue');
  const canPay = invoice !== null && (invoice.status === 'pending' || invoice.status === 'overdue');
  const canCancel = invoice !== null && (invoice.status === 'pending' || invoice.status === 'overdue');

  if (loading) {
    return (
      <AppShell>
        <div className="mx-auto max-w-3xl space-y-4 p-4 md:p-8">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-40 w-full" />
        </div>
      </AppShell>
    );
  }

  if (error || !invoice) {
    return (
      <AppShell>
        <div className="mx-auto max-w-3xl p-4 md:p-8">
          <ErrorMessage message={error ?? 'Cobrança não encontrada.'} />
          <Button variant="secondary" className="mt-4" onClick={() => navigate('/invoices')}>
            <ArrowLeft className="h-4 w-4" /> Voltar às cobranças
          </Button>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-3xl p-4 md:p-8">
        <button
          onClick={() => navigate('/invoices')}
          className="mb-4 flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
        >
          <ArrowLeft className="h-4 w-4" /> Voltar às cobranças
        </button>

        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
                {invoice.invoice_number}
              </h1>
              <InvoiceStatusBadge status={invoice.status} />
            </div>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {invoice.clients?.name ?? 'Cliente removido'}
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {canPay && (
              <Button onClick={() => setPayOpen(true)}>
                <CheckCircle2 className="h-4 w-4" /> Marcar como pago
              </Button>
            )}
            {invoice.clients?.phone && (
              <Button variant="secondary" onClick={() => setWhatsAppOpen(true)}>
                <MessageCircle className="h-4 w-4" /> Enviar lembrete
              </Button>
            )}
            <Button
              variant="secondary"
              onClick={() => setEditOpen(true)}
              disabled={!canEdit}
              title={canEdit ? undefined : 'Só é possível editar cobranças pendentes ou atrasadas'}
            >
              <Pencil className="h-4 w-4" /> Editar
            </Button>
            {canCancel && (
              <Button variant="danger" onClick={() => setCancelOpen(true)}>
                <Ban className="h-4 w-4" /> Cancelar
              </Button>
            )}
          </div>
        </div>

        <Card className="mt-6">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Detalhes
          </h2>
          <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
              <Wallet className="h-4 w-4 text-slate-400" />
              {formatCurrency(invoice.amount, invoice.currency)}
            </div>
            <div className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
              <Calendar className="h-4 w-4 text-slate-400" />
              Vence em {formatDate(invoice.due_date)}
            </div>
            {invoice.paid_at && (
              <div className="flex items-center gap-2 text-sm text-emerald-700 dark:text-emerald-400">
                <CheckCircle2 className="h-4 w-4" />
                Pago em {formatDateTime(invoice.paid_at)}
              </div>
            )}
            {invoice.reference && (
              <div className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
                <Hash className="h-4 w-4 text-slate-400" />
                Referência: {invoice.reference}
              </div>
            )}
          </div>
          {invoice.description && (
            <div className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-800/50 dark:text-slate-400">
              {invoice.description}
            </div>
          )}
        </Card>

        <Card className="mt-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Lembretes enviados
            </h2>
            {invoice.clients?.phone && (
              <button
                onClick={() => setWhatsAppOpen(true)}
                className="text-xs font-medium text-brand-600 hover:underline dark:text-brand-400"
              >
                Enviar novo lembrete
              </button>
            )}
          </div>
          {reminders.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Ainda não foi iniciado nenhum lembrete para esta cobrança.
            </p>
          ) : (
            <div className="mt-3 divide-y divide-slate-200 dark:divide-slate-800">
              {reminders.map((reminder) => (
                <div key={reminder.id} className="py-2.5 text-sm">
                  <div className="flex items-center justify-between">
                    <ReminderTypeBadge type={reminder.reminder_type} />
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                      {reminder.sent_at ? formatDateTime(reminder.sent_at) : '—'}
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

      <InvoiceFormModal
        open={editOpen}
        organizationId={invoice.organization_id}
        defaultCurrency={organization?.currency ?? invoice.currency}
        invoice={invoice}
        onClose={() => setEditOpen(false)}
        onSaved={handleSaved}
      />

      <MarkAsPaidDialog
        open={payOpen}
        invoiceId={invoice.id}
        onClose={() => setPayOpen(false)}
        onSuccess={handlePaySuccess}
      />

      <ConfirmDialog
        open={cancelOpen}
        title="Cancelar cobrança"
        description={`Tem a certeza que quer cancelar a cobrança ${invoice.invoice_number}? Esta acção não pode ser revertida.`}
        confirmLabel="Cancelar cobrança"
        danger
        loading={cancelling}
        onConfirm={handleConfirmCancel}
        onCancel={() => setCancelOpen(false)}
      />

      {invoice.clients?.phone && (
        <SendWhatsAppModal
          open={whatsAppOpen}
          onClose={() => setWhatsAppOpen(false)}
          organizationId={invoice.organization_id}
          client={{ id: invoice.client_id, name: invoice.clients.name, phone: invoice.clients.phone }}
          mode={{
            kind: 'invoice',
            invoice: {
              id: invoice.id,
              status: invoice.status,
              invoiceNumber: invoice.invoice_number,
              amount: invoice.amount,
              currency: invoice.currency,
              dueDate: invoice.due_date,
              description: invoice.description,
            },
          }}
          onSent={handleWhatsAppSent}
        />
      )}
    </AppShell>
  );
}
