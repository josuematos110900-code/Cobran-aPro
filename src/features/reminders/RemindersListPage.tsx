import { useCallback, useEffect, useMemo, useState } from 'react';
import { Search, Bell, CalendarCheck, CalendarDays } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Pagination } from '@/components/ui/Pagination';
import { useAuth } from '@/contexts/AuthContext';
import { fetchReminderTotals, fetchReminders, type ReminderTotals, type ReminderWithRelations } from './api';
import { ReminderTypeBadge } from './ReminderTypeBadge';
import { REMINDER_PERIOD_LABELS, REMINDER_TYPE_LABELS, type ReminderPeriod, type ReminderType } from './types';

const PAGE_SIZE = 10;

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function RemindersListPage() {
  const { organization } = useAuth();

  const [reminders, setReminders] = useState<ReminderWithRelations[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [totals, setTotals] = useState<ReminderTotals | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [clientSearchInput, setClientSearchInput] = useState('');
  const [clientSearch, setClientSearch] = useState('');
  const [type, setType] = useState<ReminderType | 'all'>('all');
  const [period, setPeriod] = useState<ReminderPeriod>('all');
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timeout = setTimeout(() => {
      setClientSearch(clientSearchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(timeout);
  }, [clientSearchInput]);

  const load = useCallback(async () => {
    if (!organization) return;
    setLoading(true);
    setError(null);

    const { reminders: fetched, totalCount: count, error: fetchError } = await fetchReminders(organization.id, {
      clientSearch,
      type,
      period,
      page,
      pageSize: PAGE_SIZE,
    });

    if (fetchError) {
      setError('Não foi possível carregar os lembretes.');
      setLoading(false);
      return;
    }

    setReminders(fetched);
    setTotalCount(count);
    setLoading(false);

    const { totals: fetchedTotals } = await fetchReminderTotals(organization.id);
    setTotals(fetchedTotals);
  }, [organization, clientSearch, type, period, page]);

  useEffect(() => {
    load();
  }, [load]);

  const hasActiveFilters = useMemo(
    () => clientSearch.trim() !== '' || type !== 'all' || period !== 'all',
    [clientSearch, type, period]
  );

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl p-4 md:p-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Lembretes</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {loading ? 'A carregar…' : `${totalCount} lembrete${totalCount === 1 ? '' : 's'}`}
          </p>
        </div>

        {/* Totais */}
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <Bell className="h-4 w-4 text-brand-500" /> Total iniciados
            </div>
            <div className="mt-2 text-xl font-bold text-brand-600 dark:text-brand-400">
              {totals ? totals.total_sent : <Skeleton className="h-6 w-16" />}
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <CalendarCheck className="h-4 w-4 text-amber-500" /> Hoje
            </div>
            <div className="mt-2 text-xl font-bold text-amber-600 dark:text-amber-400">
              {totals ? totals.sent_today : <Skeleton className="h-6 w-16" />}
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <CalendarDays className="h-4 w-4 text-emerald-500" /> Este mês
            </div>
            <div className="mt-2 text-xl font-bold text-emerald-600 dark:text-emerald-400">
              {totals ? totals.sent_this_month : <Skeleton className="h-6 w-16" />}
            </div>
          </Card>
        </div>
        <p className="mt-2 text-xs text-slate-400">
          "Iniciado" significa apenas que a mensagem foi preparada e o WhatsApp foi aberto — nunca
          confirmamos entrega ou leitura.
        </p>

        {/* Filtros */}
        <Card className="mt-4 p-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                type="text"
                value={clientSearchInput}
                onChange={(e) => setClientSearchInput(e.target.value)}
                placeholder="Pesquisar por cliente…"
                className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              />
            </div>
            <Select
              aria-label="Filtrar por tipo"
              value={type}
              onChange={(e) => {
                setType(e.target.value as ReminderType | 'all');
                setPage(0);
              }}
            >
              <option value="all">Todos os tipos</option>
              {Object.entries(REMINDER_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Filtrar por período"
              value={period}
              onChange={(e) => {
                setPeriod(e.target.value as ReminderPeriod);
                setPage(0);
              }}
            >
              {Object.entries(REMINDER_PERIOD_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        </Card>

        <Card className="mt-4 p-0">
          {error && (
            <div className="p-4">
              <ErrorMessage message={error} />
            </div>
          )}

          {loading ? (
            <div className="divide-y divide-slate-200 dark:divide-slate-800">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-4 p-4">
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-1/3" />
                    <Skeleton className="h-3 w-1/2" />
                  </div>
                  <Skeleton className="h-6 w-24 rounded-full" />
                </div>
              ))}
            </div>
          ) : reminders.length === 0 ? (
            <div className="p-4">
              <EmptyState
                icon={Bell}
                title={hasActiveFilters ? 'Nenhum lembrete encontrado' : 'Ainda não tem lembretes'}
                description={
                  hasActiveFilters
                    ? 'Tente ajustar a pesquisa ou os filtros.'
                    : 'Os lembretes enviados a partir das cobranças, pagamentos e perfis de cliente aparecem aqui.'
                }
              />
            </div>
          ) : (
            <>
              <div className="divide-y divide-slate-200 dark:divide-slate-800">
                {reminders.map((reminder) => (
                  <div key={reminder.id} className="flex flex-col gap-1.5 p-4">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-medium text-slate-900 dark:text-white">
                        {reminder.clients?.name ?? 'Cliente removido'}
                      </span>
                      <ReminderTypeBadge type={reminder.reminder_type} />
                    </div>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {reminder.sent_at ? formatDateTime(reminder.sent_at) : '—'}
                      {reminder.invoices?.invoice_number ? ` · ${reminder.invoices.invoice_number}` : ''}
                    </p>
                    <p className="line-clamp-2 text-sm text-slate-600 dark:text-slate-400">{reminder.message}</p>
                  </div>
                ))}
              </div>

              <div className="px-4">
                <Pagination page={page} pageSize={PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} />
              </div>
            </>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
