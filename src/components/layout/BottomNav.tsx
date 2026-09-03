import { NavLink } from 'react-router-dom';
import { NAV_ITEMS } from './navItems';

export function BottomNav() {
  const items = NAV_ITEMS.filter((item) => !item.desktopOnly);

  return (
    <nav className="fixed inset-x-0 bottom-0 z-20 flex border-t border-slate-200 bg-white/95 backdrop-blur dark:border-slate-800 dark:bg-slate-950/95 md:hidden print:hidden">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) =>
            `flex flex-1 flex-col items-center gap-1 py-2.5 text-xs font-medium ${
              isActive
                ? 'text-brand-600 dark:text-brand-400'
                : 'text-slate-500 dark:text-slate-400'
            }`
          }
        >
          <item.icon className="h-5 w-5" aria-hidden="true" />
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
