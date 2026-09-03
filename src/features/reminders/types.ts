export type ReminderType =
  | 'invoice_created'
  | 'invoice_due'
  | 'invoice_overdue'
  | 'payment_confirmation'
  | 'manual';

export type ReminderPeriod = 'all' | 'today' | 'this_month' | 'last_month';

export interface ReminderFilters {
  clientSearch: string;
  type: ReminderType | 'all';
  period: ReminderPeriod;
  page: number;
  pageSize: number;
}

export const REMINDER_TYPE_LABELS: Record<ReminderType, string> = {
  invoice_created: 'Cobrança criada',
  invoice_due: 'Cobrança próxima do vencimento',
  invoice_overdue: 'Cobrança vencida',
  payment_confirmation: 'Pagamento confirmado',
  manual: 'Lembrete manual',
};

export const REMINDER_PERIOD_LABELS: Record<ReminderPeriod, string> = {
  all: 'Todo o período',
  today: 'Hoje',
  this_month: 'Este mês',
  last_month: 'Mês passado',
};

// O único estado que registamos a partir desta fase. Representa apenas o
// facto de que o utilizador clicou para abrir o WhatsApp com a mensagem
// preparada — nunca que a mensagem foi entregue ou lida.
export const REMINDER_STATUS = 'initiated' as const;

function formatCurrency(value: number, currency: string): string {
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

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-AO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

export interface InvoiceMessageData {
  invoiceNumber: string;
  amount: number;
  currency: string;
  dueDate: string; // ISO
  description: string | null;
}

export interface PaymentMessageData {
  amount: number;
  currency: string;
  paidAt: string; // ISO
  transactionReference: string | null;
  description: string | null;
}

// Constrói a mensagem por defeito (já com os placeholders substituídos
// por valores reais) para cada tipo de lembrete. O utilizador pode depois
// editar livremente o resultado antes de abrir o WhatsApp.
export function buildDefaultMessage(
  type: ReminderType,
  data: { clientName: string; invoice?: InvoiceMessageData; payment?: PaymentMessageData }
): string {
  const { clientName } = data;

  if (type === 'invoice_created' && data.invoice) {
    const inv = data.invoice;
    return (
      `Olá, ${clientName}!\n\n` +
      `Foi criada uma nova cobrança para si.\n\n` +
      `Cobrança: ${inv.invoiceNumber}\n` +
      `Valor: ${formatCurrency(inv.amount, inv.currency)}\n` +
      `Vencimento: ${formatDate(inv.dueDate)}` +
      `${inv.description ? `\nDescrição: ${inv.description}` : ''}\n\n` +
      `Obrigado!`
    );
  }

  if (type === 'invoice_due' && data.invoice) {
    const inv = data.invoice;
    return (
      `Olá, ${clientName}!\n\n` +
      `Este é um lembrete sobre a cobrança ${inv.invoiceNumber} no valor de ${formatCurrency(
        inv.amount,
        inv.currency
      )}.\n\n` +
      `Data de vencimento: ${formatDate(inv.dueDate)}\n\n` +
      `Caso já tenha efectuado o pagamento, por favor desconsidere esta mensagem.\n\n` +
      `Obrigado!`
    );
  }

  if (type === 'invoice_overdue' && data.invoice) {
    const inv = data.invoice;
    return (
      `Olá, ${clientName}!\n\n` +
      `Verificámos que a cobrança ${inv.invoiceNumber}, no valor de ${formatCurrency(
        inv.amount,
        inv.currency
      )}, encontra-se em atraso.\n\n` +
      `Vencimento: ${formatDate(inv.dueDate)}\n\n` +
      `Por favor, entre em contacto connosco para regularizar o pagamento.\n\n` +
      `Obrigado!`
    );
  }

  if (type === 'payment_confirmation' && data.payment) {
    const pay = data.payment;
    return (
      `Olá, ${clientName}!\n\n` +
      `Confirmamos o recebimento de ${formatCurrency(pay.amount, pay.currency)}` +
      `${pay.description ? ` referente a ${pay.description}` : ''}.\n\n` +
      `Pagamento: ${formatDate(pay.paidAt)}\n` +
      `${pay.transactionReference ? `Referência: ${pay.transactionReference}\n\n` : '\n'}` +
      `Obrigado!`
    );
  }

  // manual — apenas uma saudação; o utilizador escreve o resto.
  return `Olá, ${clientName}!\n\n`;
}
