package com.pastelgest.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class PeriodTest {
    private static final TimeZone LUANDA = TimeZone.getTimeZone("Africa/Luanda");

    @Test
    public void weekStartsOnMonday() {
        Calendar c = Calendar.getInstance(LUANDA);
        c.set(2026, Calendar.NOVEMBER, 21, 15, 30); // sábado
        Period w = Period.of(Period.WEEK, c.getTimeInMillis(), LUANDA);
        Calendar s = Calendar.getInstance(LUANDA);
        s.setTimeInMillis(w.start);
        assertEquals(Calendar.MONDAY, s.get(Calendar.DAY_OF_WEEK));
        assertEquals(16, s.get(Calendar.DAY_OF_MONTH));
        assertEquals(7L * 86_400_000L, w.end - w.start);
    }

    @Test
    public void monthAndDay() {
        Calendar c = Calendar.getInstance(LUANDA);
        c.set(2026, Calendar.FEBRUARY, 10, 9, 0);
        Period m = Period.of(Period.MONTH, c.getTimeInMillis(), LUANDA);
        assertEquals(28L * 86_400_000L, m.end - m.start);
        Period d = Period.of(Period.DAY, c.getTimeInMillis(), LUANDA);
        assertTrue(d.start <= c.getTimeInMillis() && c.getTimeInMillis() < d.end);
    }
}
