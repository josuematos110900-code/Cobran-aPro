package com.pastelgest.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.data.AppDatabase;
import com.pastelgest.app.data.entity.SyncLog;
import com.pastelgest.app.sync.AuthManager;
import com.pastelgest.app.sync.SupabaseConfig;
import com.pastelgest.app.sync.SyncManager;
import com.pastelgest.app.sync.SyncScheduler;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.Prefs;

import java.util.List;

/** Configuração do Supabase, conta, estado e registo de sincronização. */
public class SyncActivity extends BaseActivity {
    private EditText url, key, email, password;
    private TextView account, status;
    private LinearLayout logBox;
    private MaterialSwitch auto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);
        setupToolbar("Sincronização", true);
        url = findViewById(R.id.sy_url);
        key = findViewById(R.id.sy_key);
        email = findViewById(R.id.sy_email);
        password = findViewById(R.id.sy_password);
        account = findViewById(R.id.sy_account);
        status = findViewById(R.id.sy_status);
        logBox = findViewById(R.id.sy_log);
        auto = findViewById(R.id.sy_auto);
        url.setText(Prefs.str(this, Prefs.SUPABASE_URL, getString(R.string.supabase_url)));
        key.setText(Prefs.str(this, Prefs.SUPABASE_KEY, getString(R.string.supabase_anon_key)));
        auto.setChecked(Prefs.bool(this, Prefs.AUTO_SYNC, true));
        auto.setOnCheckedChangeListener((b, c) -> {
            Prefs.putBool(this, Prefs.AUTO_SYNC, c);
            SyncScheduler.schedulePeriodic(this);
        });
        findViewById(R.id.sy_save_config).setOnClickListener(v -> saveConfig());
        findViewById(R.id.sy_sign_in).setOnClickListener(v -> auth(false));
        findViewById(R.id.sy_sign_up).setOnClickListener(v -> auth(true));
        findViewById(R.id.sy_sign_out).setOnClickListener(v -> Ui.confirm(this, "Terminar sessão",
                "Os dados locais mantêm-se no dispositivo. A sincronização fica suspensa até entrar novamente.", "Terminar", () ->
                        work(() -> {
                            new AuthManager(this).signOut();
                            return true;
                        }, r -> refresh())));
        findViewById(R.id.sy_sync_now).setOnClickListener(v -> syncNow());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void saveConfig() {
        String u = Ui.text(url);
        String k = Ui.text(key);
        try {
            if (!u.isEmpty() && !u.startsWith("https://")) throw new BusinessException("O URL deve começar por https://");
            if (SupabaseConfig.looksLikeServiceKey(k)) {
                throw new BusinessException("Esta parece ser a chave service_role/secret. Por segurança, use apenas a chave pública (anon/publishable).");
            }
            Prefs.put(this, Prefs.SUPABASE_URL, u);
            Prefs.put(this, Prefs.SUPABASE_KEY, k);
            Ui.success(this, "Configuração guardada.");
            refresh();
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }

    private void auth(boolean signUp) {
        String e = Ui.text(email), p = password.getText() == null ? "" : password.getText().toString();
        work(() -> {
            AuthManager a = new AuthManager(this);
            if (signUp) return a.signUp(e, p);
            a.signIn(e, p);
            return true;
        }, ok -> {
            password.setText("");
            if (ok) {
                Ui.success(this, "Sessão iniciada. A sincronizar…");
                SyncScheduler.schedulePeriodic(this);
                syncNow();
            } else {
                Ui.info(this, "Confirme o email", "Conta criada. Abra o email de confirmação enviado pelo Supabase e depois toque em Entrar.");
            }
            refresh();
        });
    }

    private void syncNow() {
        work(() -> new SyncManager(this).sync(), r -> {
            Ui.success(this, "Sincronização concluída. " + r.message);
            refresh();
        });
    }

    private void refresh() {
        AuthManager a = new AuthManager(this);
        boolean configured = SupabaseConfig.isConfigured(this);
        boolean logged = a.isLoggedIn();
        account.setText(!configured ? "Supabase não configurado." : logged ? "Ligado como " + a.email() : "Sessão não iniciada.");
        findViewById(R.id.sy_sign_out).setVisibility(logged ? View.VISIBLE : View.GONE);
        findViewById(R.id.sy_sign_in).setVisibility(logged ? View.GONE : View.VISIBLE);
        findViewById(R.id.sy_sign_up).setVisibility(logged ? View.GONE : View.VISIBLE);
        findViewById(R.id.sy_email_layout).setVisibility(logged ? View.GONE : View.VISIBLE);
        findViewById(R.id.sy_password_layout).setVisibility(logged ? View.GONE : View.VISIBLE);
        findViewById(R.id.sy_sync_now).setEnabled(configured && logged);
        work(() -> {
            Object[] out = new Object[2];
            out[0] = new SyncManager(this).pendingCount();
            out[1] = AppDatabase.get(this).syncLogDao().recent(15);
            return out;
        }, data -> {
            int pending = (Integer) data[0];
            @SuppressWarnings("unchecked") List<SyncLog> logs = (List<SyncLog>) data[1];
            status.setText("Registos por enviar: " + pending + "\nÚltima sincronização: "
                    + Fmt.dateTime(Prefs.lng(this, Prefs.LAST_SYNC, 0)) + "\n" + Prefs.str(this, Prefs.LAST_SYNC_STATUS, ""));
            logBox.removeAllViews();
            for (SyncLog l : logs) {
                Ui.addKv(logBox, Fmt.dateTime(l.startedAt) + " • " + l.status, Fmt.nz(l.message),
                        "OK".equals(l.status) ? Row.TONE_GREEN : Row.TONE_RED);
            }
        });
    }
}
