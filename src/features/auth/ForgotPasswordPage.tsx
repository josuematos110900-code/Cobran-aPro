import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { supabase } from '@/lib/supabase';
import { translateAuthError } from '@/lib/authErrors';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { AuthLayout } from '@/components/layout/AuthLayout';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const { error: resetError } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/reset-password`,
    });

    setLoading(false);

    if (resetError) {
      setError(translateAuthError(resetError.message));
      return;
    }

    setSent(true);
  }

  return (
    <AuthLayout>
      <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
          Recuperar palavra-passe
        </h2>

        {sent ? (
          <p className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700 dark:border-emerald-900/40 dark:bg-emerald-950/40 dark:text-emerald-300">
            Se existir uma conta com este email, enviámos um link para
            redefinir a palavra-passe.
          </p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && <ErrorMessage message={error} />}

            <p className="text-sm text-slate-500 dark:text-slate-400">
              Indique o email da sua conta e enviaremos um link para
              redefinir a palavra-passe.
            </p>

            <Input
              label="Email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />

            <Button type="submit" className="w-full" loading={loading}>
              Enviar link de recuperação
            </Button>
          </form>
        )}

        <p className="text-center text-sm text-slate-500 dark:text-slate-400">
          <Link to="/login" className="font-medium text-brand-600 hover:underline">
            Voltar ao login
          </Link>
        </p>
      </div>
    </AuthLayout>
  );
}
