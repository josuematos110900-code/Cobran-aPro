import type { ReactNode } from 'react';

type BadgeColor = 'green' | 'amber' | 'red' | 'slate' | 'blue';

const colorClasses: Record<BadgeColor, string> = {
  green: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300',
  amber: 'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  red: 'bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  slate: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
  blue: 'bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-300',
};

export function Badge({ color = 'slate', children }: { color?: BadgeColor; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${colorClasses[color]}`}
    >
      {children}
    </span>
  );
}
