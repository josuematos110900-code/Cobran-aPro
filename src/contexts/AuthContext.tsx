import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import type { Session, User } from '@supabase/supabase-js';
import { supabase } from '@/lib/supabase';
import type { Organization, OrgMemberRole } from '@/lib/types/database';

interface AuthContextValue {
  session: Session | null;
  user: User | null;
  organization: Organization | null;
  memberRole: OrgMemberRole | null;
  isAdmin: boolean;
  loading: boolean;
  refreshOrganization: () => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [organization, setOrganization] = useState<Organization | null>(null);
  const [memberRole, setMemberRole] = useState<OrgMemberRole | null>(null);
  const [loading, setLoading] = useState(true);

  async function loadOrganizationForUser() {
    // Carrega a primeira organização a que o utilizador pertence.
    // Numa versão futura com múltiplas organizações por utilizador,
    // isto seria substituído por um selector de organização activa.
    const { data, error } = await supabase
      .from('organizations')
      .select('*')
      .limit(1)
      .maybeSingle();

    if (error) {
      console.error('Erro ao carregar organização:', error.message);
      setOrganization(null);
      setMemberRole(null);
      return;
    }

    const org = data as Organization | null;
    setOrganization(org);

    if (org) {
      const {
        data: { user },
      } = await supabase.auth.getUser();

      if (user) {
        const { data: memberRow } = await supabase
          .from('organization_members')
          .select('role')
          .eq('organization_id', org.id)
          .eq('user_id', user.id)
          .maybeSingle();

        setMemberRole((memberRow?.role as OrgMemberRole | undefined) ?? null);
      }
    } else {
      setMemberRole(null);
    }
  }

  async function refreshOrganization() {
    await loadOrganizationForUser();
  }

  useEffect(() => {
    let isMounted = true;

    async function init() {
      const { data } = await supabase.auth.getSession();
      if (!isMounted) return;

      setSession(data.session);

      if (data.session) {
        await loadOrganizationForUser();
      }
      setLoading(false);
    }

    init();

    const { data: listener } = supabase.auth.onAuthStateChange(
      async (_event, newSession) => {
        setSession(newSession);
        if (newSession) {
          await loadOrganizationForUser();
        } else {
          setOrganization(null);
          setMemberRole(null);
        }
      }
    );

    return () => {
      isMounted = false;
      listener.subscription.unsubscribe();
    };
  }, []);

  async function signOut() {
    await supabase.auth.signOut();
    setOrganization(null);
    setMemberRole(null);
  }

  const value: AuthContextValue = {
    session,
    user: session?.user ?? null,
    organization,
    memberRole,
    isAdmin: memberRole === 'owner' || memberRole === 'admin',
    loading,
    refreshOrganization,
    signOut,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth deve ser usado dentro de um AuthProvider');
  }
  return ctx;
}
