import type { ClientStatus } from '@/lib/types/database';

export interface ClientFilters {
  search: string;
  status: ClientStatus | 'all';
  page: number;
  pageSize: number;
}

export interface ClientBalance {
  client_id: string;
  organization_id: string;
  total_paid: number;
  total_pending: number;
  total_overdue: number;
}

export interface ClientFormValues {
  name: string;
  phone: string;
  email: string;
  address: string;
  notes: string;
  status: ClientStatus;
}

export const EMPTY_CLIENT_FORM: ClientFormValues = {
  name: '',
  phone: '',
  email: '',
  address: '',
  notes: '',
  status: 'active',
};

export const CLIENT_STATUS_LABELS: Record<ClientStatus, string> = {
  active: 'Activo',
  inactive: 'Inactivo',
  archived: 'Arquivado',
};
