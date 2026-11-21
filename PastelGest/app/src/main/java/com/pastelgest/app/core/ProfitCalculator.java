package com.pastelgest.app.core;

/**
 * Demonstração de resultados de um período.
 * Lucro bruto = vendas - custo dos produtos vendidos (CPV).
 * Lucro líquido = lucro bruto - despesas operacionais - perdas/desperdícios - ajustes negativos.
 * Dinheiro recebido e compras NÃO entram no cálculo do lucro.
 */
public final class ProfitCalculator {

    public static final class Input {
        public long revenueCents;
        public long cogsCents;
        public long operatingExpensesCents;
        public long ingredientWasteCents;
        public long productWasteCents;
        /** Ajustes de inventário: positivo = perda, negativo = ganho. */
        public long adjustmentLossCents;
        public long receivedCents;
        public long receivableCents;
        public long purchasesCents;
        public long productionCostCents;
        public long stockValueCents;
        public long discountsCents;
        public long salesCount;
    }

    public static final class Report {
        public Input in;
        public long grossProfitCents;
        public long totalLossesCents;
        public long netProfitCents;
        public double grossMarginPct;
        public double netMarginPct;
    }

    private ProfitCalculator() { }

    public static Report compute(Input in) {
        Report r = new Report();
        r.in = in;
        r.grossProfitCents = in.revenueCents - in.cogsCents;
        r.totalLossesCents = in.ingredientWasteCents + in.productWasteCents + in.adjustmentLossCents;
        r.netProfitCents = r.grossProfitCents - in.operatingExpensesCents - r.totalLossesCents;
        if (in.revenueCents > 0) {
            r.grossMarginPct = Math.round(r.grossProfitCents * 1000.0 / in.revenueCents) / 10.0;
            r.netMarginPct = Math.round(r.netProfitCents * 1000.0 / in.revenueCents) / 10.0;
        }
        return r;
    }
}
