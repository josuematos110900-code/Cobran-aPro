package com.pastelgest.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CostMathTest {
    @Test
    public void weightedAverageConsumption() {
        // 10 kg comprados por 15 000 Kz; consumir 2,5 kg = 3 750 Kz
        long qty = Units.parseToBaseMilli("10", Units.KG);
        long value = Money.parse("15000");
        long used = Units.parseToBaseMilli("2,5", Units.KG);
        assertEquals(Money.parse("3750"), CostMath.consumptionValue(used, qty, value));
    }

    @Test
    public void consumingEverythingTakesFullValueNoResidue() {
        assertEquals(1001, CostMath.consumptionValue(3, 3, 1001));
        long a = CostMath.consumptionValue(1, 3, 1000);
        long b = CostMath.consumptionValue(1, 2, 1000 - a);
        long c = CostMath.consumptionValue(1, 1, 1000 - a - b);
        assertEquals(1000, a + b + c);
    }

    @Test
    public void mixedPurchasesAverage() {
        // 1 kg a 1 000 Kz + 1 kg a 2 000 Kz -> custo médio 1 500 Kz/kg
        long qty = Units.parseToBaseMilli("2", Units.KG);
        long value = Money.parse("3000");
        assertEquals(Money.parse("1500"), CostMath.pricePerUnit(value, qty, Units.KG));
        assertEquals(Money.parse("1,5"), CostMath.pricePerUnit(value, qty, Units.G));
    }

    @Test
    public void discountAllocationSumsExactly() {
        long[] lines = {3333, 3333, 3334};
        long[] out = CostMath.allocateDiscount(lines, 1001);
        long sum = 0;
        for (long v : out) sum += v;
        assertEquals(10000 - 1001, sum);
        assertThrows(BusinessException.class, () -> CostMath.allocateDiscount(new long[]{100}, 101));
    }
}
