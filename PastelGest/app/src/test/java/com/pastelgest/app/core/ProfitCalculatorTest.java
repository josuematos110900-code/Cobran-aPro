package com.pastelgest.app.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProfitCalculatorTest {
    @Test
    public void creditSalesAndPurchasesDoNotDistortProfit() {
        ProfitCalculator.Input in = new ProfitCalculator.Input();
        in.revenueCents = 100_000_00;        // inclui vendas fiadas
        in.cogsCents = 40_000_00;            // custo histórico dos pastéis vendidos
        in.operatingExpensesCents = 20_000_00;
        in.ingredientWasteCents = 1_000_00;
        in.productWasteCents = 500_00;
        in.adjustmentLossCents = 0;
        in.receivedCents = 70_000_00;        // 30 000 ainda por receber
        in.receivableCents = 30_000_00;
        in.purchasesCents = 90_000_00;       // compras ficam em stock: não reduzem o lucro
        ProfitCalculator.Report r = ProfitCalculator.compute(in);
        assertEquals(60_000_00, r.grossProfitCents);
        assertEquals(1_500_00, r.totalLossesCents);
        assertEquals(38_500_00, r.netProfitCents);
        assertEquals(60.0, r.grossMarginPct, 0.001);
        assertEquals(38.5, r.netMarginPct, 0.001);
    }

    @Test
    public void lossIsNegative() {
        ProfitCalculator.Input in = new ProfitCalculator.Input();
        in.revenueCents = 1000;
        in.cogsCents = 800;
        in.operatingExpensesCents = 500;
        assertEquals(-300, ProfitCalculator.compute(in).netProfitCents);
    }
}
