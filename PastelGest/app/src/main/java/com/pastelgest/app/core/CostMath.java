package com.pastelgest.app.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Custo médio ponderado (inventário permanente). */
public final class CostMath {
    private CostMath() { }

    /**
     * Valor (cêntimos) a retirar do stock ao consumir {@code qty} de um stock com
     * {@code stockQty} e valor total {@code stockValue}. Se consumir tudo, devolve
     * o valor total, evitando resíduos de arredondamento.
     */
    public static long consumptionValue(long qty, long stockQty, long stockValue) {
        if (qty <= 0 || stockQty <= 0 || stockValue <= 0) return 0;
        if (qty >= stockQty) return stockValue;
        return BigDecimal.valueOf(stockValue).multiply(BigDecimal.valueOf(qty))
                .divide(BigDecimal.valueOf(stockQty), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Custo por milésima de unidade base, em cêntimos (alta precisão). */
    public static BigDecimal costPerMilli(long valueCents, long qtyMilli) {
        if (qtyMilli <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(valueCents).divide(BigDecimal.valueOf(qtyMilli), MathContext.DECIMAL64);
    }

    /** Custo de uma quantidade a um custo por milésima, arredondado ao cêntimo. */
    public static long cost(long qtyMilli, BigDecimal costPerMilli) {
        return costPerMilli.multiply(BigDecimal.valueOf(qtyMilli)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Preço por unidade de apresentação (ex.: Kz/kg) a partir de valor e quantidade base. */
    public static long pricePerUnit(long valueCents, long qtyMilli, String unit) {
        if (qtyMilli <= 0) return 0;
        BigDecimal perUnitMilli = BigDecimal.valueOf(Units.factor(unit) * 1000L);
        return BigDecimal.valueOf(valueCents).multiply(perUnitMilli)
                .divide(BigDecimal.valueOf(qtyMilli), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Distribui um desconto global proporcionalmente pelas linhas. A soma dos
     * resultados é exactamente {@code total - discount}.
     */
    public static long[] allocateDiscount(long[] lineTotals, long discount) {
        long sum = 0;
        for (long t : lineTotals) sum += t;
        long[] out = new long[lineTotals.length];
        if (sum <= 0) return out;
        if (discount > sum) throw new BusinessException("O desconto não pode ser superior ao subtotal.");
        long allocated = 0;
        int largest = 0;
        for (int i = 0; i < lineTotals.length; i++) {
            long share = BigDecimal.valueOf(discount).multiply(BigDecimal.valueOf(lineTotals[i]))
                    .divide(BigDecimal.valueOf(sum), 0, RoundingMode.DOWN).longValueExact();
            out[i] = lineTotals[i] - share;
            allocated += share;
            if (lineTotals[i] > lineTotals[largest]) largest = i;
        }
        out[largest] -= (discount - allocated);
        return out;
    }
}
