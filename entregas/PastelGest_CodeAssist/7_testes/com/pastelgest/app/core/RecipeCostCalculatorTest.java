package com.pastelgest.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

public class RecipeCostCalculatorTest {
    private static BigDecimal perMilli(String priceKz, String qty, String unit) {
        return CostMath.costPerMilli(Money.parse(priceKz), Units.parseToBaseMilli(qty, unit));
    }

    @Test
    public void realisticRecipe() {
        // Farinha: 1 kg = 1 200 Kz; receita usa 500 g -> 600 Kz
        // Leite: 1 l = 900 Kz; receita usa 250 ml -> 225 Kz
        // Ovos: 30 un = 3 000 Kz; receita usa 4 un -> 400 Kz
        RecipeCostCalculator.Result r = RecipeCostCalculator.calculate(Arrays.asList(
                new RecipeCostCalculator.Line("f", "Farinha", Units.parseToBaseMilli("500", Units.G), perMilli("1200", "1", Units.KG)),
                new RecipeCostCalculator.Line("l", "Leite", Units.parseToBaseMilli("250", Units.ML), perMilli("900", "1", Units.L)),
                new RecipeCostCalculator.Line("o", "Ovos", Units.parseToBaseMilli("4", Units.UN), perMilli("3000", "30", Units.UN))
        ), 20, Money.parse("25"), Money.parse("300"));
        assertEquals(Money.parse("1225"), r.ingredientsCostCents);
        assertEquals(Money.parse("500"), r.packagingCostCents);   // 25 x 20
        assertEquals(Money.parse("2025"), r.totalCostCents);
        assertEquals(Money.parse("101,25"), r.unitCostCents);     // 2025 / 20
        assertFalse(r.missingPrices);
    }

    @Test
    public void missingPriceFlagged() {
        RecipeCostCalculator.Result r = RecipeCostCalculator.calculate(Collections.singletonList(
                new RecipeCostCalculator.Line("x", "Canela", 1000, BigDecimal.ZERO)), 10, 0, 0);
        assertTrue(r.missingPrices);
        assertEquals(0, r.unitCostCents);
    }

    @Test
    public void invalidYield() {
        assertThrows(BusinessException.class, () -> RecipeCostCalculator.calculate(Collections.emptyList(), 0, 0, 0));
    }

    @Test
    public void margin() {
        assertEquals(new BigDecimal("50.0"), RecipeCostCalculator.marginPercent(20000, 10000));
    }
}
