package com.pastelgest.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.math.BigDecimal;

public class MoneyTest {
    @Test
    public void parseAcceptsPortugueseAndInternationalFormats() {
        assertEquals(125050, Money.parse("1 250,50"));
        assertEquals(125050, Money.parse("1.250,50"));
        assertEquals(125050, Money.parse("1250.50"));
        assertEquals(125050, Money.parse("1250,5"));
        assertEquals(100000000, Money.parse("1.000.000"));
        assertEquals(1500, Money.parse("15 Kz"));
        assertEquals(1, Money.parse("0,005"));
    }

    @Test
    public void parseRejectsInvalid() {
        assertThrows(BusinessException.class, () -> Money.parse("abc"));
        assertThrows(BusinessException.class, () -> Money.parse(""));
        assertThrows(BusinessException.class, () -> Money.parseNonNegative("-5"));
    }

    @Test
    public void formatUsesSpaceGroupingAndComma() {
        assertEquals("1 250,50 Kz", Money.format(125050).replace(' ', ' '));
        assertEquals("0,00 Kz", Money.format(0));
        assertEquals("-3,07 Kz", Money.format(-307));
        assertEquals("1250,50", Money.formatInput(125050));
    }

    @Test
    public void percentAndDivideRoundHalfUp() {
        assertEquals(1000, Money.percentOf(10000, BigDecimal.TEN));
        assertEquals(33, Money.divide(100, 3));
        assertEquals(67, Money.divide(200, 3));
        assertEquals(0, Money.divide(100, 0));
    }

    @Test
    public void noFloatingPointDrift() {
        long total = 0;
        for (int i = 0; i < 1000; i++) total += Money.parse("0,10");
        assertEquals(10000, total);
    }
}
