import { useEffect, useState, type FormEvent } from 'react';
import { Users, UserPlus, Trash2 } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { useAuth } from '@/contexts/AuthContext';
import { useToast } from '@/contexts/ToastContext';
import type { OrganizationMemberWithProfile, OrgMemberRole } from '@/lib/types/database';
import { addMemberByEmail, fetchMembers, removeMember, updateMemberRole } from './api';
import { MEMBER_ROLE_LABELS } from './types';

const ROLE_BADGE: Record<OrgMemberRole, 'blue' | 'green' | 'slate'> = {
  owner: 'blue',
  admin: 'green',
  staff: 'slate',
};

export function MembersSection({ organizationId }: { organizationId: string }) {
  const { isAdmin } = useAuth();
  const { showToast } = useToast();

  const [members, setMembers] = useState<OrganizationMemberWithProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [addOpen, setAddOpen] = useState(false);
  const [addEmail, setAddEmail] = useState('');
  const [addRole, setAddRole] = useState<OrgMemberRole>('staff');
  const [addSaving, setAddSaving] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  const [removeTarget, setRemoveTarget] = useState<OrganizationMemberWithProfile | null>(null);
  const [removing, setRemoving] = useState(false);

  const [roleUpdating, setRoleUpdating] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    const { members: fetched, error: fetchError } = await fetchMembers(organizationId);
    setLoading(false);
    if (fetchError) {
      setError(fetchError.message || 'Não foi possível carregar os membros.');
      return;
    }
    setMembers(fetched);
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [organizationId]);

  async function handleAddMember(e: FormEvent) {
    e.preventDefault();
    if (!addEmail.trim()) {
      setAddError('Indique o email do utilizador.');
      return;
    }
    setAddError(null);
    setAddSaving(true);

    const { error: addErr } = await addMemberByEmail(organizationId, addEmail.trim(), addRole);

    setAddSaving(false);

    if (addErr) {
      setAddError(addErr.message || 'Não foi possível adicionar este membro.');
      return;
    }

    setAddOpen(false);
    setAddEmail('');
    setAddRole('staff');
    showToast('Membro adicionado com sucesso.');
    await load();
  }

  async function handleRoleChange(member: OrganizationMemberWithProfile, role: OrgMemberRole) {
    setRoleUpdating(member.member_id);
    const { error: roleError } = await updateMemberRole(member.member_id, role);
    setRoleUpdating(null);

    if (roleError) {
      showToast(roleError.message || 'Não foi possível alterar o papel deste membro.', 'error');
      return;
    }

    showToast('Papel actualizado.');
    await load();
  }

  async function handleConfirmRemove() {
    if (!removeTarget) return;
    setRemoving(true);
    const { error: removeError } = await removeMember(removeTarget.member_id);
    setRemoving(false);

    if (removeError) {
      showToast(removeError.message || 'Não foi possível remover este membro.', 'error');
      return;
    }

    setRemoveTarget(null);
    showToast('Membro removido.');
    await load();
  }

  return (
    <Card>
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Users className="h-5 w-5 text-brand-600 dark:text-brand-400" aria-hidden="true" />
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">Membros da equipa</h2>
        </div>
        {isAdmin && (
          <Button
            variant="secondary"
            onClick={() => {
              setAddOpen(true);
              setAddError(null);
            }}
          >
            <UserPlus className="h-4 w-4" aria-hidden="true" />
            Adicionar
          </Button>
        )}
      </div>

      {error && (
        <div className="mt-3">
          <ErrorMessage message={error} />
        </div>
      )}

      <div className="mt-4 space-y-2">
        {loading ? (
          <>
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
          </>
        ) : (
          members.map((member) => (
            <div
              key={member.member_id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 p-3 dark:border-slate-800"
            >
              <div>
                <p className="text-sm font-medium text-slate-900 dark:text-white">
                  {member.full_name || member.email || 'Utilizador'}
                  {member.is_you && <span className="ml-1.5 text-xs text-slate-400">(você)</span>}
                </p>
                {member.email && (
                  <p className="text-xs text-slate-500 dark:text-slate-400">{member.email}</p>
                )}
              </div>

              <div className="flex items-center gap-2">
                {isAdmin && member.role !== 'owner' && !member.is_you ? (
                  <Select
                    value={member.role}
                    onChange={(e) => handleRoleChange(member, e.target.value as OrgMemberRole)}
                    disabled={roleUpdating === member.member_id}
                    className="!py-1.5 text-xs"
                  >
                    <option value="admin">Administrador</option>
                    <option value="staff">Colaborador</option>
                  </Select>
                ) : (
                  <Badge color={ROLE_BADGE[member.role]}>{MEMBER_ROLE_LABELS[member.role]}</Badge>
                )}

                {isAdmin && member.role !== 'owner' && !member.is_you && (
                  <button
                    onClick={() => setRemoveTarget(member)}
                    aria-label={`Remover ${member.full_name ?? member.email ?? 'membro'}`}
                    className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/40"
                  >
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      <Modal open={addOpen} onClose={() => setAddOpen(false)} title="Adicionar membro">
        <form onSubmit={handleAddMember} className="space-y-4">
          {addError && <p className="text-sm text-red-600 dark:text-red-400">{addError}</p>}
          <p className="text-sm text-slate-500 dark:text-slate-400">
            O utilizador tem de já ter uma conta CobrançaPro registada com este email.
          </p>
          <Input
            label="Email do utilizador"
            type="email"
            required
            value={addEmail}
            onChange={(e) => setAddEmail(e.target.value)}
            placeholder="colega@exemplo.com"
          />
          <Select label="Papel" value={addRole} onChange={(e) => setAddRole(e.target.value as OrgMemberRole)}>
            <option value="staff">Colaborador — operações do dia-a-dia</option>
            <option value="admin">Administrador — gestão operacional e de membros</option>
          </Select>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setAddOpen(false)} disabled={addSaving}>
              Cancelar
            </Button>
            <Button type="submit" loading={addSaving}>
              Adicionar
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={removeTarget !== null}
        title="Remover membro"
        description={`Tem a certeza que quer remover ${removeTarget?.full_name || removeTarget?.email || 'este membro'} da organização? Esta pessoa perde imediatamente o acesso.`}
        confirmLabel="Remover"
        danger
        loading={removing}
        onConfirm={handleConfirmRemove}
        onCancel={() => setRemoveTarget(null)}
      />
    </Card>
  );
}
