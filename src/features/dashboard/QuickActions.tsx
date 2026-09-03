import { Link } from 'react-router-dom';
import { UserPlus, FileText, Wallet, Bell, Package } from 'lucide-react';

const ACTIONS = [
  { to: '/clients', label: 'Novo cliente', icon: UserPlus },
  { to: '/invoices', label: 'Nova cobrança', icon: FileText },
  { to: '/payments', label: 'Registar pagamento', icon: Wallet },
  { to: '/reminders', label: 'Enviar lembrete', icon: Bell },
  { to: '/services', label: 'Novo serviço', icon: Package },
];

export function QuickActions() {
  return (
    <div className="mt-6 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
      {ACTIONS.map(({ to, label, icon: Icon }) => (
        <Link
          key={to}
          to={to}
          className="flex flex-col items-center gap-2 rounded-xl border border-slate-200 bg-white p-3 text-center text-xs font-medium text-slate-700 shadow-sm transition-colors hover:border-brand-300 hover:bg-brand-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:border-brand-800 dark:hover:bg-brand-950/30"
        >
          <Icon className="h-5 w-5 text-brand-600 dark:text-brand-400" aria-hidden="true" />
          {label}
        </Link>
      ))}
    </div>
  );
}
