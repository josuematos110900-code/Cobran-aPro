import { NavLink } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { NAV_ITEMS } from './navItems';
import { useAuth } from '@/contexts/AuthContext';
import { Logo } from '@/components/ui/Logo';

export function Sidebar() {
  const { organization, signOut } = useAuth();
  const initial = organization?.name?.trim()?.charAt(0).toUpperCase() ?? '?';

  return (
    <aside className="hidden w-64 shrink-0 flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950 md:flex print:hidden">
      <div className="flex h-16 items-center gap-2.5 px-6">
        <Logo className="h-8 w-8 shrink-0" />
        <span className="text-lg font-bold tracking-tight text-slate-900 dark:text-white">
          Cobrança<span className="text-brand-600 dark:text-brand-400">Pro</span>
        </span>
      </div>

      {organization && (
        <div className="mx-4 mb-4 flex items-center gap-2.5 rounded-lg bg-slate-100 px-3 py-2 dark:bg-slate-900">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-600 text-xs font-semibold text-white">
            {initial}
          </span>
          <span className="truncate text-sm font-medium text-slate-700 dark:text-slate-300">
            {organization.name}
          </span>
        </div>
      )}

      <nav className="flex-1 space-y-1 px-3">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-300'
                  : 'text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900'
              }`
            }
          >
            <item.icon className="h-5 w-5" aria-hidden="true" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="p-3">
        <button
          onClick={() => void signOut()}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900"
        >
          <LogOut className="h-5 w-5" aria-hidden="true" />
          Terminar sessão
        </button>
      </div>
    </aside>
  );
}
