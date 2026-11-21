package com.pastelgest.app.ui.adapter;

import com.pastelgest.app.R;

/** Modelo de apresentação de uma linha de lista. */
public class Row {
    public static final int TONE_NONE = 0;
    public static final int TONE_ACCENT = 1;
    public static final int TONE_GREEN = 2;
    public static final int TONE_RED = 3;
    public static final int TONE_ORANGE = 4;

    public String id;
    public String title;
    public String subtitle;
    public String value;
    public String badge;
    public int icon = R.drawable.ic_cake;
    public int iconTone = TONE_ACCENT;
    public int valueTone = TONE_NONE;
    public int badgeTone = TONE_ACCENT;
    public boolean chevron = true;
    public Object tag;

    public Row() { }

    public Row(String id, String title, String subtitle, String value) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.value = value;
    }

    public Row icon(int res, int tone) {
        icon = res;
        iconTone = tone;
        return this;
    }

    public Row value(String v, int tone) {
        value = v;
        valueTone = tone;
        return this;
    }

    public Row badge(String b, int tone) {
        badge = b;
        badgeTone = tone;
        return this;
    }

    public Row tag(Object t) {
        tag = t;
        return this;
    }

    public Row noChevron() {
        chevron = false;
        return this;
    }
}
