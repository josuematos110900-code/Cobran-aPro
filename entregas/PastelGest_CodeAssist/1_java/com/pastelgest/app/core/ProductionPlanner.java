package com.pastelgest.app.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Escala as quantidades da receita para a quantidade de pastéis a produzir. */
public final class ProductionPlanner {
    private ProductionPlanner() { }

    /** Quantidade necessária (milésimas base), arredondada para cima. */
    public static long required(long recipeQtyMilli, long recipeYield, long toProduce) {
        if (recipeYield <= 0) throw new BusinessException("Rendimento inválido na receita.");
        if (toProduce <= 0) throw new BusinessException("A quantidade a produzir tem de ser superior a zero.");
        return BigDecimal.valueOf(recipeQtyMilli).multiply(BigDecimal.valueOf(toProduce))
                .divide(BigDecimal.valueOf(recipeYield), 0, RoundingMode.CEILING).longValueExact();
    }

    /** Outros custos do lote proporcionais à quantidade produzida. */
    public static long scaledBatchCost(long otherPerBatchCents, long recipeYield, long toProduce) {
        return BigDecimal.valueOf(otherPerBatchCents).multiply(BigDecimal.valueOf(toProduce))
                .divide(BigDecimal.valueOf(recipeYield), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
