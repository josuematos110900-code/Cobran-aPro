import { Badge } from '@/components/ui/Badge';
import type { SubscriptionStatus } from '@/lib/types/database';
import { SUBSCRIPTION_STATUS_LABELS } from './types';

const STATUS_COLORS: Record<SubscriptionStatus, 'green' | 'amber' | 'red' | 'slate'> = {
  active: 'green',
  paused: 'amber',
  cancelled: 'red',
  ended: 'slate',
};

export function SubscriptionStatusBadge({ status }: { status: SubscriptionStatus }) {
  return <Badge color={STATUS_COLORS[status]}>{SUBSCRIPTION_STATUS_LABELS[status]}</Badge>;
}
