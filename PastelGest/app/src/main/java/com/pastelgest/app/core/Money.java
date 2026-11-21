package com.pastelgest.app.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Valores monetários em Kz guardados como inteiros de cêntimos (long), evitando
 * erros de arredondamento de vírgula flutuante.
 */
public final class Money {
    public static final String CURRENCY = "Kz";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() { }

    /** Converte texto introduzido pelo utilizador ("1 250,50", "1250.5", "1.250,50") em cêntimos. */
    public static long parse(String text) {
        if (text == null) throw new BusinessException("Valor em falta.");
        String s = text.trim().replace(CURRENCY, "").replace(" ", "").replace(" ", "");
        if (s.isEmpty()) throw new BusinessException("Valor em falta.");
        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                s = s.replace(".", "").replace(',', '.');
            } else {
                s = s.replace(",", "");
            }
        } else if (lastComma >= 0) {
            s = s.replace(',', '.');
        } else if (lastDot >= 0 && s.indexOf('.') != lastDot) {
            s = s.replace(".", "");
        }
        try {
            BigDecimal v = new BigDecimal(s);
            if (v.scale() > 2) {
                v = v.setScale(2, RoundingMode.HALF_UP);
            }
            return v.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new BusinessException("Valor inválido: " + text);
        }
    }

    /** Igual a parse, mas exige valor >= 0. */
    public static long parseNonNegative(String text) {
        long v = parse(text);
        if (v < 0) throw new BusinessException("O valor não pode ser negativo.");
        return v;
    }

    /** Valor opcional: texto vazio = 0. */
    public static long parseOptional(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return parseNonNegative(text);
    }

    public static BigDecimal toDecimal(long cents) {
        return BigDecimal.valueOf(cents).divide(HUNDRED, 2, RoundingMode.UNNECESSARY);
    }

    public static long fromDecimal(BigDecimal value) {
        return value.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Formata 125050 -> "1 250,50 Kz". */
    public static String format(long cents) {
        return formatPlain(cents) + " " + CURRENCY;
    }

    /** Formata sem o símbolo da moeda: "1 250,50". */
    public static String formatPlain(long cents) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols();
        sym.setDecimalSeparator(',');
        sym.setGroupingSeparator(' ');
        sym.setMinusSign('-');
        DecimalFormat df = new DecimalFormat("#,##0.00", sym);
        return df.format(toDecimal(cents));
    }

    /** Valor para campos de edição: "1250,50". */
    public static String formatInput(long cents) {
        return toDecimal(cents).toPlainString().replace('.', ',');
    }

    /** Percentagem de um valor, arredondada ao cêntimo. */
    public static long percentOf(long cents, BigDecimal percent) {
        return BigDecimal.valueOf(cents).multiply(percent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Divisão arredondada (HALF_UP) de cêntimos por uma quantidade. */
    public static long divide(long cents, long divisor) {
        if (divisor == 0) return 0;
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
