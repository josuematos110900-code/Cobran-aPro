package com.pastelgest.app.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unidades de medida. As quantidades são guardadas como long em milésimas da
 * unidade base (g para massa, ml para volume, un para unidades).
 * Ex.: 1,5 kg = 1500 g = 1 500 000 "mili-gramas base".
 */
public final class Units {
    public static final int TYPE_MASS = 1;
    public static final int TYPE_VOLUME = 2;
    public static final int TYPE_UNIT = 3;

    public static final String KG = "kg";
    public static final String G = "g";
    public static final String L = "l";
    public static final String ML = "ml";
    public static final String UN = "un";

    public static final String[] ALL = {KG, G, L, ML, UN};
    public static final String[] LABELS = {"Quilograma (kg)", "Grama (g)", "Litro (l)", "Mililitro (ml)", "Unidade (un)"};

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private Units() { }

    public static int typeOf(String unit) {
        if (KG.equals(unit) || G.equals(unit)) return TYPE_MASS;
        if (L.equals(unit) || ML.equals(unit)) return TYPE_VOLUME;
        if (UN.equals(unit)) return TYPE_UNIT;
        throw new BusinessException("Unidade desconhecida: " + unit);
    }

    /** Factor da unidade para a unidade base (kg->g = 1000). */
    public static long factor(String unit) {
        if (KG.equals(unit) || L.equals(unit)) return 1000;
        typeOf(unit);
        return 1;
    }

    public static String baseUnit(int type) {
        switch (type) {
            case TYPE_MASS: return G;
            case TYPE_VOLUME: return ML;
            default: return UN;
        }
    }

    /** Unidades compatíveis com o tipo do ingrediente. */
    public static String[] compatible(int type) {
        switch (type) {
            case TYPE_MASS: return new String[]{KG, G};
            case TYPE_VOLUME: return new String[]{L, ML};
            default: return new String[]{UN};
        }
    }

    public static boolean isCompatible(int type, String unit) {
        return typeOf(unit) == type;
    }

    /** Converte uma quantidade numa unidade para milésimas da unidade base. */
    public static long toBaseMilli(BigDecimal qty, String unit) {
        return qty.multiply(BigDecimal.valueOf(factor(unit))).multiply(THOUSAND)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Texto do utilizador -> milésimas base. Aceita vírgula ou ponto. */
    public static long parseToBaseMilli(String text, String unit) {
        return toBaseMilli(parseQuantity(text), unit);
    }

    public static BigDecimal parseQuantity(String text) {
        if (text == null || text.trim().isEmpty()) throw new BusinessException("Quantidade em falta.");
        try {
            BigDecimal v = new BigDecimal(text.trim().replace(" ", "").replace(',', '.'));
            if (v.signum() < 0) throw new BusinessException("A quantidade não pode ser negativa.");
            return v;
        } catch (NumberFormatException e) {
            throw new BusinessException("Quantidade inválida: " + text);
        }
    }

    /** Milésimas base -> quantidade na unidade pedida. */
    public static BigDecimal fromBaseMilli(long milli, String unit) {
        return BigDecimal.valueOf(milli)
                .divide(THOUSAND.multiply(BigDecimal.valueOf(factor(unit))), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    /** Formata para apresentação: "1,5 kg". */
    public static String format(long milli, String unit) {
        BigDecimal v = fromBaseMilli(milli, unit).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
        String s = v.toPlainString().replace('.', ',');
        return s + " " + unit;
    }

    /** Valor para campos de edição, sem unidade. */
    public static String formatInput(long milli, String unit) {
        return fromBaseMilli(milli, unit).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }

    /** Unidade de apresentação mais legível para uma quantidade base. */
    public static String bestDisplayUnit(int type, long milli) {
        if (type == TYPE_MASS) return Math.abs(milli) >= 1_000_000 ? KG : G;
        if (type == TYPE_VOLUME) return Math.abs(milli) >= 1_000_000 ? L : ML;
        return UN;
    }
}
