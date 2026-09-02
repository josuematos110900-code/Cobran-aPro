import { Badge } from '@/components/ui/Badge';
import type { ClientStatus } from '@/lib/types/database';
import { CLIENT_STATUS_LABELS } from './types';

const STATUS_COLORS: Record<ClientStatus, 'green' | 'slate' | 'amber'> = {
  active: 'green',
  inactive: 'amber',
  archived: 'slate',
};

export function ClientStatusBadge({ status }: { status: ClientStatus }) {
  return <Badge color={STATUS_COLORS[status]}>{CLIENT_STATUS_LABELS[status]}</Badge>;
}
