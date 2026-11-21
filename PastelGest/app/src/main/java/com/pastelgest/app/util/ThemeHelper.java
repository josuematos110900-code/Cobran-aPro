package com.pastelgest.app.util;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/** Tema claro / escuro / sistema. */
public final class ThemeHelper {
    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private ThemeHelper() { }

    public static void apply(Context c) {
        String t = Prefs.str(c, Prefs.THEME, SYSTEM);
        int mode;
        if (LIGHT.equals(t)) mode = AppCompatDelegate.MODE_NIGHT_NO;
        else if (DARK.equals(t)) mode = AppCompatDelegate.MODE_NIGHT_YES;
        else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static int index(Context c) {
        String t = Prefs.str(c, Prefs.THEME, SYSTEM);
        return LIGHT.equals(t) ? 1 : DARK.equals(t) ? 2 : 0;
    }

    public static void set(Context c, int index) {
        Prefs.put(c, Prefs.THEME, index == 1 ? LIGHT : index == 2 ? DARK : SYSTEM);
        apply(c);
    }
}
