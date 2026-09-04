// Configuração central dos planos comerciais do CobrançaPro, para a UI
// (nomes, preços, benefícios). Os LIMITES reais são impostos no servidor
// (ver supabase/migrations/021_plans_trial_and_limits.sql, função
// plan_limits()) — este ficheiro nunca é a fonte da verdade da aplicação
// de limites, só de apresentação. Mantenha os números de limite aqui
// sincronizados manualmente com a migration sempre que um for alterado.

import type { PlanTier } from './types/database';

export interface PlanDefinition {
  id: PlanTier;
  name: string;
  priceMonthlyAoa: number | null;
  tagline: string;
  highlights: string[];
  limits: {
    maxClients: number | null;
    maxServices: number | null;
    maxMembers: number | null;
    maxInvoicesPerMonth: number | null;
    recurring: boolean;
    reports: boolean;
    advanced: boolean;
  };
}

export const PLANS: PlanDefinition[] = [
  {
    id: 'free',
    name: 'Free',
    priceMonthlyAoa: 0,
    tagline: 'Para começar a organizar as suas cobranças.',
    highlights: ['Até 5 clientes', 'Até 10 cobranças por mês', 'Lembretes via WhatsApp'],
    limits: {
      maxClients: 5,
      maxServices: 3,
      maxMembers: 1,
      maxInvoicesPerMonth: 10,
      recurring: false,
      reports: false,
      advanced: false,
    },
  },
  {
    id: 'basico',
    name: 'Básico',
    priceMonthlyAoa: 5000,
    tagline: 'Para pequenos negócios com uma carteira de clientes fixa.',
    highlights: [
      'Até 30 clientes',
      'Até 60 cobranças por mês',
      'Cobranças recorrentes',
      'Relatórios',
      'Até 3 membros da equipa',
    ],
    limits: {
      maxClients: 30,
      maxServices: 15,
      maxMembers: 3,
      maxInvoicesPerMonth: 60,
      recurring: true,
      reports: true,
      advanced: false,
    },
  },
  {
    id: 'profissional',
    name: 'Profissional',
    priceMonthlyAoa: 10000,
    tagline: 'Para negócios em crescimento com equipa e recorrências.',
    highlights: [
      'Até 150 clientes',
      'Até 300 cobranças por mês',
      'Cobranças recorrentes',
      'Relatórios avançados',
      'Até 8 membros da equipa',
    ],
    limits: {
      maxClients: 150,
      maxServices: 50,
      maxMembers: 8,
      maxInvoicesPerMonth: 300,
      recurring: true,
      reports: true,
      advanced: true,
    },
  },
  {
    id: 'empresa',
    name: 'Empresa',
    priceMonthlyAoa: 25000,
    tagline: 'Para operações de maior volume, com suporte prioritário.',
    highlights: [
      'Clientes ilimitados',
      'Cobranças ilimitadas',
      'Membros da equipa ilimitados',
      'Relatórios avançados',
      'Suporte prioritário',
    ],
    limits: {
      maxClients: null,
      maxServices: null,
      maxMembers: null,
      maxInvoicesPerMonth: null,
      recurring: true,
      reports: true,
      advanced: true,
    },
  },
];

export function getPlanDefinition(planId: PlanTier): PlanDefinition {
  return PLANS.find((p) => p.id === planId) ?? PLANS[0];
}

export function formatAoa(value: number) {
  try {
    return new Intl.NumberFormat('pt-AO', {
      style: 'currency',
      currency: 'AOA',
      maximumFractionDigits: 0,
    }).format(value);
  } catch {
    return `${value.toLocaleString('pt-AO')} Kz`;
  }
}

// Número de WhatsApp para pedidos de upgrade manual (não existe gateway
// de pagamento com API/webhook disponível para Angola nesta versão — ver
// README, secção "Planos e assinatura"). Configurável por variável de
// ambiente para não ficar hardcoded por instalação do produto.
export const SALES_WHATSAPP_NUMBER = import.meta.env.VITE_SALES_WHATSAPP_NUMBER as string | undefined;
