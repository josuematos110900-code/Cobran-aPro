import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';

interface ProtectedRouteProps {
  children: ReactNode;
  // A generalidade das páginas precisa de uma organização activa para
  // fazer sentido (dashboard, clientes, cobranças, ...). Só a própria
  // página de onboarding passa isto a false — é lá que a organização é
  // criada. Sem este redirect, um utilizador autenticado mas ainda sem
  // organização (registo feito, onboarding nunca concluído ou
  // interrompido) ficava preso numa página que nunca teria dados para
  // mostrar, sem nenhuma indicação do que fazer a seguir.
  requireOrganization?: boolean;
}

export function ProtectedRoute({ children, requireOrganization = true }: ProtectedRouteProps) {
  const { session, organization, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-white dark:bg-surface-dark">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
      </div>
    );
  }

  if (!session) {
    return <Navigate to="/login" replace />;
  }

  if (requireOrganization && !organization) {
    return <Navigate to="/onboarding" replace />;
  }

  return <>{children}</>;
}
