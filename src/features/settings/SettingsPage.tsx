import { useEffect, useState } from 'react';
import { AppShell } from '@/components/layout/AppShell';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { useAuth } from '@/contexts/AuthContext';
import type { Settings } from '@/lib/types/database';
import { fetchSettings } from './api';
import { CompanySection } from './CompanySection';
import { WhatsAppSection } from './WhatsAppSection';
import { MembersSection } from './MembersSection';

export function SettingsPage() {
  const { organization, refreshOrganization } = useAuth();
  const [settings, setSettings] = useState<Settings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!organization) return;
    let isMounted = true;

    async function load() {
      setLoading(true);
      const { settings: fetched, error: fetchError } = await fetchSettings(organization!.id);
      if (!isMounted) return;
      setLoading(false);
      if (fetchError) {
        setError('Não foi possível carregar as definições.');
        return;
      }
      setSettings(fetched);
    }

    load();
    return () => {
      isMounted = false;
    };
  }, [organization]);

  if (!organization) {
    return (
      <AppShell>
        <div className="mx-auto max-w-3xl p-4 md:p-8">
          <Skeleton className="h-40" />
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-3xl space-y-6 p-4 pb-24 md:p-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Definições</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Dados da empresa, WhatsApp e membros da sua organização.
          </p>
        </div>

        {error && <ErrorMessage message={error} />}

        <CompanySection
          organization={organization}
          onUpdated={() => {
            refreshOrganization();
          }}
        />

        {loading ? (
          <Skeleton className="h-56" />
        ) : settings ? (
          <WhatsAppSection organizationId={organization.id} settings={settings} onUpdated={setSettings} />
        ) : null}

        <MembersSection organizationId={organization.id} />
      </div>
    </AppShell>
  );
}
