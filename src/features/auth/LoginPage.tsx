import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { supabase } from '@/lib/supabase';
import { translateAuthError } from '@/lib/authErrors';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { ErrorMessage } from '@/components/ui/ErrorMessage';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const { error: signInError } = await supabase.auth.signInWithPassword({
      email,
      password,
    });

    setLoading(false);

    if (signInError) {
      setError(translateAuthError(signInError.message));
      return;
    }

    const redirectTo = (location.state as { from?: string } | null)?.from ?? '/dashboard';
    navigate(redirectTo, { replace: true });
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-surface-dark">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">CobrançaPro</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Nunca mais perca uma cobrança.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-white">Iniciar sessão</h2>

          {error && <ErrorMessage message={error} />}

          <Input
            label="Email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Input
            label="Palavra-passe"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          <div className="flex items-center justify-end">
            <Link to="/forgot-password" className="text-sm font-medium text-brand-600 hover:underline">
              Esqueceu a palavra-passe?
            </Link>
          </div>

          <Button type="submit" className="w-full" loading={loading}>
            Entrar
          </Button>

          <p className="text-center text-sm text-slate-500 dark:text-slate-400">
            Ainda não tem conta?{' '}
            <Link to="/register" className="font-medium text-brand-600 hover:underline">
              Criar conta
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
