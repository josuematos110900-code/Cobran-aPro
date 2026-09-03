import { Link } from 'react-router-dom';
import { CheckCircle2, Circle } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import type { OnboardingProgress } from './api';

export function OnboardingChecklist({ progress }: { progress: OnboardingProgress }) {
  const steps = [
    { done: true, label: 'Empresa configurada', to: '/settings' },
    { done: progress.hasClient, label: 'Primeiro cliente', to: '/clients' },
    { done: progress.hasService, label: 'Primeiro serviço', to: '/services' },
    { done: progress.hasInvoice, label: 'Primeira cobrança', to: '/invoices' },
    { done: progress.hasPayment, label: 'Primeiro pagamento', to: '/payments' },
  ];

  const completed = steps.filter((s) => s.done).length;

  if (completed === steps.length) return null;

  return (
    <Card className="mt-6">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-slate-900 dark:text-white">
          Comece a usar o CobrançaPro
        </h2>
        <span className="text-xs text-slate-500 dark:text-slate-400">
          {completed} de {steps.length}
        </span>
      </div>

      <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          className="h-full rounded-full bg-brand-500 transition-all"
          style={{ width: `${(completed / steps.length) * 100}%` }}
        />
      </div>

      <ul className="mt-4 space-y-2">
        {steps.map((step) => (
          <li key={step.label}>
            <Link
              to={step.to}
              className="flex items-center gap-2 rounded-lg p-1.5 text-sm hover:bg-slate-50 dark:hover:bg-slate-800"
            >
              {step.done ? (
                <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" aria-hidden="true" />
              ) : (
                <Circle className="h-4 w-4 shrink-0 text-slate-300 dark:text-slate-600" aria-hidden="true" />
              )}
              <span
                className={
                  step.done
                    ? 'text-slate-500 line-through dark:text-slate-500'
                    : 'font-medium text-slate-700 dark:text-slate-300'
                }
              >
                {step.label}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  );
}
