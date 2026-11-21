package com.pastelgest.app.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/** TextWatcher simplificado. */
public final class Watcher implements TextWatcher {
    private final Runnable r;

    private Watcher(Runnable r) {
        this.r = r;
    }

    public static void on(Runnable r, EditText... fields) {
        for (EditText e : fields) e.addTextChangedListener(new Watcher(r));
    }

    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
    @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

    @Override
    public void afterTextChanged(Editable s) {
        r.run();
    }
}
