import { Badge } from '@/components/ui/Badge';
import type { InvoiceStatus } from '@/lib/types/database';
import { INVOICE_STATUS_LABELS } from './types';

const STATUS_COLORS: Record<InvoiceStatus, 'green' | 'amber' | 'red' | 'slate'> = {
  paid: 'green',
  pending: 'amber',
  overdue: 'red',
  cancelled: 'slate',
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  return <Badge color={STATUS_COLORS[status]}>{INVOICE_STATUS_LABELS[status]}</Badge>;
}
