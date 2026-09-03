import { useState, type FormEvent } from 'react';
import { MessageCircle } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import type { Settings } from '@/lib/types/database';
import { updateWhatsAppSettings } from './api';
import type { WhatsAppSettingsFormValues } from './types';

const TEMPLATE_VARIABLES = ['{{nome}}', '{{valor}}', '{{descricao}}', '{{data}}'];

export function WhatsAppSection({
  organizationId,
  settings,
  onUpdated,
}: {
  organizationId: string;
  settings: Settings;
  onUpdated: (settings: Settings) => void;
}) {
  const { isAdmin } = useAuth();
  const { showToast } = useToast();

  const [values, setValues] = useState<WhatsAppSettingsFormValues>({
    reminder_days_before: String(settings.reminder_days_before),
    whatsapp_template: settings.whatsapp_template,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!values.whatsapp_template.trim()) {
      setError('O modelo de mensagem não pode ficar vazio.');
      return;
    }
    setError(null);
    setSaving(true);

    const { settings: updated, error: updateError } = await updateWhatsAppSettings(organizationId, values);

    setSaving(false);

    if (updateError || !updated) {
      setError(updateError?.message || 'Não foi possível guardar as alterações.');
      return;
    }

    onUpdated(updated);
    showToast('Preferências de WhatsApp actualizadas.');
  }

  return (
    <Card>
      <div className="flex items-center gap-2">
        <MessageCircle className="h-5 w-5 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />
        <h2 className="text-base font-semibold text-slate-900 dark:text-white">WhatsApp e lembretes</h2>
      </div>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
        Modelo de mensagem usado ao enviar lembretes de cobrança pelo WhatsApp (link wa.me).
      </p>

      <form onSubmit={handleSubmit} className="mt-4 space-y-4">
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        <fieldset disabled={!isAdmin || saving} className="space-y-4">
          <Input
            label="Enviar lembrete quantos dias antes do vencimento"
            type="number"
            min={0}
            max={30}
            value={values.reminder_days_before}
            onChange={(e) => setValues((v) => ({ ...v, reminder_days_before: e.target.value }))}
          />

          <Textarea
            label="Modelo da mensagem"
            rows={5}
            value={values.whatsapp_template}
            onChange={(e) => setValues((v) => ({ ...v, whatsapp_template: e.target.value }))}
          />
          <p className="text-xs text-slate-400">
            Variáveis disponíveis: {TEMPLATE_VARIABLES.map((v) => (
              <code key={v} className="mx-0.5 rounded bg-slate-100 px-1 py-0.5 dark:bg-slate-800">
                {v}
              </code>
            ))}
          </p>
        </fieldset>

        {isAdmin ? (
          <Button type="submit" loading={saving}>
            Guardar alterações
          </Button>
        ) : (
          <p className="text-xs text-slate-400">Só o responsável ou administrador pode editar este modelo.</p>
        )}
      </form>
    </Card>
  );
}
