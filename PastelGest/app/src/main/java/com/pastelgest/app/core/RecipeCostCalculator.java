package com.pastelgest.app.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Cálculo do custo real de uma receita e do custo por pastel. */
public final class RecipeCostCalculator {

    /** Linha de cálculo: quantidade do ingrediente e o seu custo unitário actual. */
    public static final class Line {
        public final String ingredientId;
        public final String name;
        public final long qtyMilli;
        public final BigDecimal costPerMilli;
        public final boolean hasCost;
        public long costCents;

        public Line(String ingredientId, String name, long qtyMilli, BigDecimal costPerMilli) {
            this.ingredientId = ingredientId;
            this.name = name;
            this.qtyMilli = qtyMilli;
            this.costPerMilli = costPerMilli == null ? BigDecimal.ZERO : costPerMilli;
            this.hasCost = costPerMilli != null && costPerMilli.signum() > 0;
        }
    }

    public static final class Result {
        public final List<Line> lines = new ArrayList<>();
        public long ingredientsCostCents;
        /** Custo de embalagem total do lote (por pastel x rendimento). */
        public long packagingCostCents;
        /** Outros custos por lote. */
        public long otherCostCents;
        public long totalCostCents;
        public long yieldQty;
        public long unitCostCents;
        public boolean missingPrices;
    }

    private RecipeCostCalculator() { }

    /**
     * @param packagingPerUnitCents custo de embalagem por pastel
     * @param otherPerBatchCents    outros custos por lote (gás, energia, etc.)
     */
    public static Result calculate(List<Line> lines, long yieldQty, long packagingPerUnitCents, long otherPerBatchCents) {
        if (yieldQty <= 0) throw new BusinessException("O rendimento da receita tem de ser superior a zero.");
        Result r = new Result();
        r.yieldQty = yieldQty;
        BigDecimal exact = BigDecimal.ZERO;
        for (Line l : lines) {
            BigDecimal c = l.costPerMilli.multiply(BigDecimal.valueOf(l.qtyMilli));
            l.costCents = c.setScale(0, RoundingMode.HALF_UP).longValueExact();
            exact = exact.add(c);
            if (!l.hasCost) r.missingPrices = true;
            r.lines.add(l);
        }
        r.ingredientsCostCents = exact.setScale(0, RoundingMode.HALF_UP).longValueExact();
        r.packagingCostCents = packagingPerUnitCents * yieldQty;
        r.otherCostCents = otherPerBatchCents;
        r.totalCostCents = r.ingredientsCostCents + r.packagingCostCents + r.otherCostCents;
        r.unitCostCents = Money.divide(r.totalCostCents, yieldQty);
        return r;
    }

    /** Margem de lucro percentual sobre o preço de venda. */
    public static BigDecimal marginPercent(long priceCents, long unitCostCents) {
        if (priceCents <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(priceCents - unitCostCents).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(priceCents), 1, RoundingMode.HALF_UP);
    }
}
