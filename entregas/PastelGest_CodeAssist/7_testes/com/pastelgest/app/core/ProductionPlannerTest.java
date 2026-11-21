package com.pastelgest.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ProductionPlannerTest {
    @Test
    public void scalesAndRoundsUp() {
        // receita: 500 g para 20 pastéis; produzir 50 -> 1250 g
        assertEquals(1_250_000, ProductionPlanner.required(500_000, 20, 50));
        // 1 ovo para 3 pastéis; produzir 10 -> 3,334 (arredonda para cima ao milésimo)
        assertEquals(3334, ProductionPlanner.required(1000, 3, 10));
    }

    @Test
    public void scaledBatchCost() {
        assertEquals(75000, ProductionPlanner.scaledBatchCost(30000, 20, 50));
    }

    @Test
    public void invalidInputs() {
        assertThrows(BusinessException.class, () -> ProductionPlanner.required(1, 0, 1));
        assertThrows(BusinessException.class, () -> ProductionPlanner.required(1, 1, 0));
    }
}
