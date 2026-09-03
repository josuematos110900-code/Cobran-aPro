import { describe, expect, it } from 'vitest';
import { PLANS, getPlanDefinition } from './plans';

// Estes valores têm de se manter sincronizados manualmente com
// supabase/migrations/021_plans_trial_and_limits.sql (função
// plan_limits()) — a fonte da verdade real, aplicada no servidor. Este
// teste existe para apanhar um desalinhamento de apresentação, não para
// substituir a validação no Postgres.
const EXPECTED_LIMITS: Record<string, { maxClients: number | null; maxMembers: number | null; recurring: boolean }> = {
  free: { maxClients: 5, maxMembers: 1, recurring: false },
  basico: { maxClients: 30, maxMembers: 3, recurring: true },
  profissional: { maxClients: 150, maxMembers: 8, recurring: true },
  empresa: { maxClients: null, maxMembers: null, recurring: true },
};

describe('PLANS', () => {
  it('has exactly free, basico, profissional and empresa', () => {
    expect(PLANS.map((p) => p.id).sort()).toEqual(['basico', 'empresa', 'free', 'profissional']);
  });

  it('matches the limits enforced in the database migration', () => {
    for (const plan of PLANS) {
      const expected = EXPECTED_LIMITS[plan.id];
      expect(plan.limits.maxClients).toBe(expected.maxClients);
      expect(plan.limits.maxMembers).toBe(expected.maxMembers);
      expect(plan.limits.recurring).toBe(expected.recurring);
    }
  });

  it('Free is the only plan without a price and without recurring billing', () => {
    const free = getPlanDefinition('free');
    expect(free.priceMonthlyAoa).toBe(0);
    expect(free.limits.recurring).toBe(false);
  });

  it('Empresa has no numeric limits (unlimited)', () => {
    const empresa = getPlanDefinition('empresa');
    expect(empresa.limits.maxClients).toBeNull();
    expect(empresa.limits.maxServices).toBeNull();
    expect(empresa.limits.maxMembers).toBeNull();
    expect(empresa.limits.maxInvoicesPerMonth).toBeNull();
  });
});
