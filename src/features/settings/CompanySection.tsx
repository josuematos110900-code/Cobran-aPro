import { useState, type FormEvent } from 'react';
import { Building2 } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import type { Organization } from '@/lib/types/database';
import { updateCompany } from './api';
import { BUSINESS_TYPES, CURRENCIES, type CompanyFormValues } from './types';

export function CompanySection({
  organization,
  onUpdated,
}: {
  organization: Organization;
  onUpdated: (org: Organization) => void;
}) {
  const { isAdmin } = useAuth();
  const { showToast } = useToast();

  const [values, setValues] = useState<CompanyFormValues>({
    name: organization.name,
    business_type: organization.business_type ?? BUSINESS_TYPES[0],
    currency: organization.currency,
    phone: organization.phone ?? '',
    email: organization.email ?? '',
    address: organization.address ?? '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!values.name.trim()) {
      setError('O nome da empresa é obrigatório.');
      return;
    }
    setError(null);
    setSaving(true);

    const { organization: updated, error: updateError } = await updateCompany(organization.id, values);

    setSaving(false);

    if (updateError || !updated) {
      setError(updateError?.message || 'Não foi possível guardar as alterações.');
      return;
    }

    onUpdated(updated);
    showToast('Dados da empresa actualizados.');
  }

  return (
    <Card>
      <div className="flex items-center gap-2">
        <Building2 className="h-5 w-5 text-brand-600 dark:text-brand-400" aria-hidden="true" />
        <h2 className="text-base font-semibold text-slate-900 dark:text-white">Dados da empresa</h2>
      </div>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
        Estas informações aparecem nas cobranças e comunicações com os seus clientes.
      </p>

      <form onSubmit={handleSubmit} className="mt-4 space-y-4">
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        <fieldset disabled={!isAdmin || saving} className="space-y-4">
          <Input
            label="Nome da empresa"
            required
            value={values.name}
            onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Tipo de negócio"
              value={values.business_type}
              onChange={(e) => setValues((v) => ({ ...v, business_type: e.target.value }))}
            >
              {BUSINESS_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </Select>

            <Select
              label="Moeda"
              value={values.currency}
              onChange={(e) => setValues((v) => ({ ...v, currency: e.target.value }))}
            >
              {CURRENCIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label="Telefone"
              type="tel"
              value={values.phone}
              onChange={(e) => setValues((v) => ({ ...v, phone: e.target.value }))}
              placeholder="Ex: 923 000 000"
            />
            <Input
              label="Email"
              type="email"
              value={values.email}
              onChange={(e) => setValues((v) => ({ ...v, email: e.target.value }))}
              placeholder="empresa@exemplo.com"
            />
          </div>

          <Input
            label="Endereço"
            value={values.address}
            onChange={(e) => setValues((v) => ({ ...v, address: e.target.value }))}
            placeholder="Ex: Rua da Missão, Luanda"
          />
        </fieldset>

        {isAdmin ? (
          <Button type="submit" loading={saving}>
            Guardar alterações
          </Button>
        ) : (
          <p className="text-xs text-slate-400">Só o responsável ou administrador pode editar os dados da empresa.</p>
        )}
      </form>
    </Card>
  );
}
