import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/contexts/AuthContext';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { ErrorMessage } from '@/components/ui/ErrorMessage';

const BUSINESS_TYPES = [
  'Escola / Explicador',
  'Ginásio',
  'Barbearia / Salão',
  'Prestador de serviços',
  'Pequena empresa',
  'Clube / Associação',
  'Outro',
];

const CURRENCIES = [
  { value: 'AOA', label: 'Kwanza (AOA)' },
  { value: 'BRL', label: 'Real (BRL)' },
  { value: 'EUR', label: 'Euro (EUR)' },
  { value: 'USD', label: 'Dólar (USD)' },
];

export function OnboardingPage() {
  const navigate = useNavigate();
  const { organization, refreshOrganization } = useAuth();

  // Quem já tem organização (ex: voltou atrás no browser, ou abriu
  // /onboarding por engano) não deve poder criar uma segunda — segue
  // logo para o dashboard.
  useEffect(() => {
    if (organization) {
      navigate('/dashboard', { replace: true });
    }
  }, [organization, navigate]);

  const [name, setName] = useState('');
  const [businessType, setBusinessType] = useState(BUSINESS_TYPES[0]);
  const [currency, setCurrency] = useState('AOA');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);

    // create_organization_with_owner() cria a empresa e associa o
    // utilizador como "owner" numa única transacção atómica no servidor
    // — nunca fica uma empresa "órfã" sem responsável a meio caminho, e
    // evita o problema de RLS de tentar ler de volta uma organização
    // antes de o utilizador ainda ser membro dela.
    const { error: rpcError } = await supabase.rpc('create_organization_with_owner', {
      p_name: name,
      p_business_type: businessType,
      p_currency: currency,
    });

    setLoading(false);

    if (rpcError) {
      setError(rpcError.message || 'Não foi possível criar a sua empresa. Tente novamente.');
      return;
    }

    await refreshOrganization();
    navigate('/dashboard', { replace: true });
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-surface-dark">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
            Vamos configurar a sua empresa
          </h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Só demora um minuto.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          {error && <ErrorMessage message={error} />}

          <Input
            label="Nome da empresa"
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Ex: Academia Vida Fit"
          />

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
              Tipo de negócio
            </label>
            <select
              value={businessType}
              onChange={(e) => setBusinessType(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
            >
              {BUSINESS_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
              Moeda principal
            </label>
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
            >
              {CURRENCIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <Button type="submit" className="w-full" loading={loading}>
            Continuar
          </Button>
        </form>
      </div>
    </div>
  );
}
