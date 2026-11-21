package com.pastelgest.app.ui;

import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.PastelGestApp;
import com.pastelgest.app.R;
import com.pastelgest.app.util.PinSecurity;

/** Ecrã de bloqueio por PIN. */
public class LockActivity extends BaseActivity {
    private int attempts;
    private long lockedUntil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
        EditText pin = findViewById(R.id.lk_pin);
        TextInputLayout layout = findViewById(R.id.lk_pin_layout);
        findViewById(R.id.lk_unlock).setOnClickListener(v -> {
            layout.setError(null);
            if (System.currentTimeMillis() < lockedUntil) {
                layout.setError("Demasiadas tentativas. Aguarde 30 segundos.");
                return;
            }
            if (PinSecurity.check(this, Ui.text(pin))) {
                PastelGestApp.setUnlocked(true);
                finish();
            } else {
                attempts++;
                pin.setText("");
                if (attempts >= 5) {
                    attempts = 0;
                    lockedUntil = System.currentTimeMillis() + 30_000;
                    layout.setError("Demasiadas tentativas. Aguarde 30 segundos.");
                } else {
                    layout.setError("PIN incorrecto.");
                }
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }

    @Override
    protected boolean requiresUnlock() {
        return false;
    }
}
