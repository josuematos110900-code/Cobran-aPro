import { Badge } from '@/components/ui/Badge';
import type { ServiceStatus } from '@/lib/types/database';
import { SERVICE_STATUS_LABELS } from './types';

export function ServiceStatusBadge({ status }: { status: ServiceStatus }) {
  return <Badge color={status === 'active' ? 'green' : 'slate'}>{SERVICE_STATUS_LABELS[status]}</Badge>;
}
