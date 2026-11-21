package com.pastelgest.app.ui;

import android.content.Intent;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.pastelgest.app.PastelGestApp;
import com.pastelgest.app.R;
import com.pastelgest.app.util.Async;
import com.pastelgest.app.util.PinSecurity;

import java.util.concurrent.Callable;

/** Base de todas as actividades: barra superior, trabalho assíncrono e bloqueio por PIN. */
public abstract class BaseActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_MODE = "mode";

    private int busy;

    protected void setupToolbar(String title, boolean back) {
        MaterialToolbar tb = findViewById(R.id.toolbar);
        if (tb == null) return;
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(back);
        }
    }

    protected void setTitleText(String title) {
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);
    }

    protected String extraId() {
        return getIntent().getStringExtra(EXTRA_ID);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (requiresUnlock() && PinSecurity.hasPin(this) && !PastelGestApp.isUnlocked()) {
            startActivity(new Intent(this, LockActivity.class));
        }
    }

    protected boolean requiresUnlock() {
        return true;
    }

    /** Executa em segundo plano; erros são apresentados ao utilizador. */
    protected <T> void work(Callable<T> task, Async.Success<T> onDone) {
        setBusy(true);
        Async.run(task, r -> {
            setBusy(false);
            if (!isFinishing() && !isDestroyed()) onDone.accept(r);
        }, e -> {
            setBusy(false);
            if (!isFinishing() && !isDestroyed()) Ui.showError(this, e);
        });
    }

    private void setBusy(boolean on) {
        busy += on ? 1 : -1;
        View p = findViewById(R.id.progress);
        if (p != null) p.setVisibility(busy > 0 ? View.VISIBLE : View.GONE);
        View save = findViewById(R.id.btn_save);
        if (save != null) save.setEnabled(busy <= 0);
    }

    protected boolean isBusy() {
        return busy > 0;
    }
}
