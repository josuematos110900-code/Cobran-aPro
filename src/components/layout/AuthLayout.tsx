import type { ReactNode } from 'react';
import { Logo } from '@/components/ui/Logo';

interface AuthLayoutProps {
  title?: string;
  subtitle?: string;
  maxWidthClassName?: string;
  children: ReactNode;
}

// Moldura partilhada pelas páginas de autenticação (login, registo,
// recuperação/redefinição de senha, onboarding) — logótipo, título e um
// fundo com um leve gradiente da cor da marca, em vez de uma cor sólida
// plana.
export function AuthLayout({
  title = 'CobrançaPro',
  subtitle,
  maxWidthClassName = 'max-w-sm',
  children,
}: AuthLayoutProps) {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-50 px-4 py-12 dark:bg-surface-dark">
      <div
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-96 bg-gradient-to-b from-brand-100/70 via-brand-50/30 to-transparent dark:from-brand-900/20 dark:via-brand-950/10"
        aria-hidden="true"
      />
      <div className={`w-full ${maxWidthClassName}`}>
        <div className="mb-8 text-center">
          <Logo className="mx-auto h-12 w-12" />
          <h1 className="mt-3 text-2xl font-bold text-slate-900 dark:text-white">{title}</h1>
          {subtitle && (
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>
          )}
        </div>
        {children}
      </div>
    </div>
  );
}
