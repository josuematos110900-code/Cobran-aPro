package com.pastelgest.verify;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

/** Contexto mínimo: o suficiente para os repositórios e a sincronização. */
public class FakeContext extends ContextWrapper {
    final FakePrefs prefs = new FakePrefs();

    public FakeContext() {
        super(null);
    }

    @Override public Context getApplicationContext() { return this; }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
}
