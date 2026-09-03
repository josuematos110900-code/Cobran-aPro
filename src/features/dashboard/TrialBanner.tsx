import { Link } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import type { PlanStatus } from '@/lib/types/database';

export function TrialBanner({ status }: { status: PlanStatus }) {
  if (status.is_trial_active) {
    return (
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand-200 bg-brand-50 p-4 dark:border-brand-900/40 dark:bg-brand-950/30">
        <div className="flex items-center gap-2 text-sm text-brand-800 dark:text-brand-300">
          <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span>
            <strong>Teste gratuito</strong> — restam{' '}
            <strong>{status.trial_days_remaining}</strong>{' '}
            {status.trial_days_remaining === 1 ? 'dia' : 'dias'} com acesso ao plano Profissional.
          </span>
        </div>
        <Link
          to="/billing"
          className="rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
        >
          Ver planos
        </Link>
      </div>
    );
  }

  if (status.is_trial_expired) {
    return (
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 dark:border-amber-900/40 dark:bg-amber-950/30">
        <span className="text-sm text-amber-800 dark:text-amber-300">
          O seu teste gratuito terminou. Está agora no plano {status.plan} — algumas funcionalidades podem estar
          limitadas.
        </span>
        <Link
          to="/billing"
          className="rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-amber-700"
        >
          Actualizar plano
        </Link>
      </div>
    );
  }

  return null;
}
