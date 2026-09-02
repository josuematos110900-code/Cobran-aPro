import type { LucideIcon } from 'lucide-react';
import {
  LayoutDashboard,
  Users,
  Package,
  Repeat,
  FileText,
  Wallet,
  Bell,
  BarChart3,
  Settings,
} from 'lucide-react';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  // Mostrado apenas na sidebar desktop (evita sobrecarregar a bottom nav).
  desktopOnly?: boolean;
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/clients', label: 'Clientes', icon: Users },
  { to: '/invoices', label: 'Cobranças', icon: FileText },
  { to: '/subscriptions', label: 'Recorrentes', icon: Repeat, desktopOnly: true },
  { to: '/services', label: 'Serviços', icon: Package, desktopOnly: true },
  { to: '/payments', label: 'Pagamentos', icon: Wallet, desktopOnly: true },
  { to: '/reminders', label: 'Lembretes', icon: Bell, desktopOnly: true },
  { to: '/reports', label: 'Relatórios', icon: BarChart3 },
  { to: '/settings', label: 'Definições', icon: Settings, desktopOnly: true },
];
