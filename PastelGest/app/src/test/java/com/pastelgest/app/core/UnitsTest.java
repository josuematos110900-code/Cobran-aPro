package com.pastelgest.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;

public class UnitsTest {
    @Test
    public void kgAndGramsConvertToSameBase() {
        assertEquals(Units.parseToBaseMilli("1,5", Units.KG), Units.parseToBaseMilli("1500", Units.G));
        assertEquals(1_500_000, Units.parseToBaseMilli("1,5", Units.KG));
        assertEquals(250, Units.parseToBaseMilli("0,25", Units.G));
    }

    @Test
    public void litresAndMl() {
        assertEquals(Units.parseToBaseMilli("2", Units.L), Units.parseToBaseMilli("2000", Units.ML));
        assertEquals(new BigDecimal("0.5"), Units.fromBaseMilli(500_000, Units.L));
    }

    @Test
    public void compatibility() {
        assertTrue(Units.isCompatible(Units.TYPE_MASS, Units.G));
        assertFalse(Units.isCompatible(Units.TYPE_MASS, Units.ML));
        assertFalse(Units.isCompatible(Units.TYPE_UNIT, Units.KG));
        assertThrows(BusinessException.class, () -> Units.typeOf("lb"));
    }

    @Test
    public void formatting() {
        assertEquals("1,5 kg", Units.format(1_500_000, Units.KG));
        assertEquals("1500 g", Units.format(1_500_000, Units.G));
        assertEquals("12 un", Units.format(12_000, Units.UN));
        assertEquals("0,333 kg", Units.format(333_333, Units.KG));
    }

    @Test
    public void rejectsNegativeQuantity() {
        assertThrows(BusinessException.class, () -> Units.parseToBaseMilli("-1", Units.KG));
        assertThrows(BusinessException.class, () -> Units.parseToBaseMilli("x", Units.KG));
    }
}
