package com.pastelgest.app.util;

import com.pastelgest.app.core.Money;
import com.pastelgest.app.core.Units;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Formatação para apresentação em português de Portugal. */
public final class Fmt {
    public static final Locale PT = new Locale("pt", "PT");

    private Fmt() { }

    public static String money(long cents) {
        return Money.format(cents);
    }

    public static String date(long millis) {
        if (millis <= 0) return "—";
        return new SimpleDateFormat("dd/MM/yyyy", PT).format(new Date(millis));
    }

    public static String dateTime(long millis) {
        if (millis <= 0) return "—";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", PT).format(new Date(millis));
    }

    public static String dayShort(long millis) {
        return new SimpleDateFormat("EEE", PT).format(new Date(millis));
    }

    public static String stamp(long millis) {
        return new SimpleDateFormat("yyMMdd", PT).format(new Date(millis));
    }

    public static String fileStamp(long millis) {
        return new SimpleDateFormat("yyyyMMdd_HHmm", PT).format(new Date(millis));
    }

    public static String qty(long milli, String unit) {
        return Units.format(milli, unit == null ? Units.UN : unit);
    }

    public static String units(long n) {
        return n + (n == 1 ? " un" : " un");
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }

    public static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
