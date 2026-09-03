import { Badge } from '@/components/ui/Badge';
import type { ReminderType } from './types';
import { REMINDER_TYPE_LABELS } from './types';

const TYPE_COLORS: Record<ReminderType, 'blue' | 'amber' | 'red' | 'green' | 'slate'> = {
  invoice_created: 'blue',
  invoice_due: 'amber',
  invoice_overdue: 'red',
  payment_confirmation: 'green',
  manual: 'slate',
};

export function ReminderTypeBadge({ type }: { type: ReminderType }) {
  return <Badge color={TYPE_COLORS[type]}>{REMINDER_TYPE_LABELS[type]}</Badge>;
}
