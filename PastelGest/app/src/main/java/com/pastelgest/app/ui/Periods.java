package com.pastelgest.app.ui;

import com.pastelgest.app.core.Period;

/** Filtros de período usados nas listas. */
public final class Periods {
    public static final String[] LABELS = {"Hoje", "Semana", "Mês", "Ano", "Tudo"};

    private Periods() { }

    public static Period forFilter(int index) {
        switch (index) {
            case 0: return Period.of(Period.DAY);
            case 1: return Period.of(Period.WEEK);
            case 2: return Period.of(Period.MONTH);
            case 3: return Period.of(Period.YEAR);
            default: return new Period(0, Long.MAX_VALUE / 2, Period.CUSTOM);
        }
    }
}
