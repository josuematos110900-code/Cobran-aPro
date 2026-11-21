package com.pastelgest.app.core;

import java.util.Calendar;
import java.util.TimeZone;

/** Intervalo de datas [start, end) em milissegundos. */
public final class Period {
    public static final int DAY = 0;
    public static final int WEEK = 1;
    public static final int MONTH = 2;
    public static final int YEAR = 3;
    public static final int CUSTOM = 4;

    public final long start;
    public final long end;
    public final int kind;

    public Period(long start, long end, int kind) {
        if (end <= start) throw new BusinessException("A data final tem de ser posterior à inicial.");
        this.start = start;
        this.end = end;
        this.kind = kind;
    }

    public static Period of(int kind, long now, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.setTimeInMillis(now);
        startOfDay(c);
        switch (kind) {
            case WEEK:
                while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_MONTH, -1);
                long ws = c.getTimeInMillis();
                c.add(Calendar.DAY_OF_MONTH, 7);
                return new Period(ws, c.getTimeInMillis(), WEEK);
            case MONTH:
                c.set(Calendar.DAY_OF_MONTH, 1);
                long ms = c.getTimeInMillis();
                c.add(Calendar.MONTH, 1);
                return new Period(ms, c.getTimeInMillis(), MONTH);
            case YEAR:
                c.set(Calendar.DAY_OF_YEAR, 1);
                long ys = c.getTimeInMillis();
                c.add(Calendar.YEAR, 1);
                return new Period(ys, c.getTimeInMillis(), YEAR);
            default:
                long ds = c.getTimeInMillis();
                c.add(Calendar.DAY_OF_MONTH, 1);
                return new Period(ds, c.getTimeInMillis(), DAY);
        }
    }

    public static Period of(int kind) {
        return of(kind, System.currentTimeMillis(), TimeZone.getDefault());
    }

    /** Período personalizado: do início do dia 'from' ao fim do dia 'to'. */
    public static Period custom(long from, long to) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(from);
        startOfDay(a);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(to);
        startOfDay(b);
        b.add(Calendar.DAY_OF_MONTH, 1);
        return new Period(a.getTimeInMillis(), b.getTimeInMillis(), CUSTOM);
    }

    /** Lista dos inícios de cada um dos últimos n dias (inclui hoje). */
    public static long[] lastDays(int n, long now) {
        long[] out = new long[n + 1];
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        startOfDay(c);
        c.add(Calendar.DAY_OF_MONTH, -(n - 1));
        for (int i = 0; i <= n; i++) {
            out[i] = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    private static void startOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
