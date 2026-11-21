package com.pastelgest.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.data.AppDatabase;
import com.pastelgest.app.repository.SettingsRepository;
import com.pastelgest.app.sync.AuthManager;
import com.pastelgest.app.util.BackupHelper;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.ImageStore;
import com.pastelgest.app.util.PinSecurity;
import com.pastelgest.app.util.Prefs;
import com.pastelgest.app.util.ThemeHelper;

import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Definições: dados comerciais, logótipo, tema, PIN, cópias de segurança e informações. */
public class SettingsActivity extends BaseActivity {
    private EditText name, nif, phone, email, address, footer, maxDiscount;
    private Dropdown<String> theme;
    private ImageView logo;
    private ActivityResultLauncher<String> pickLogo;
    private ActivityResultLauncher<String> createBackup;
    private ActivityResultLauncher<String[]> openBackup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupToolbar("Definições", true);
        name = findViewById(R.id.st_name);
        nif = findViewById(R.id.st_nif);
        phone = findViewById(R.id.st_phone);
        email = findViewById(R.id.st_email);
        address = findViewById(R.id.st_address);
        footer = findViewById(R.id.st_footer);
        maxDiscount = findViewById(R.id.st_max_discount);
        logo = findViewById(R.id.st_logo);
        theme = new Dropdown<>(findViewById(R.id.st_theme));
        theme.setItems(Arrays.asList(getResources().getStringArray(R.array.theme_options)), s -> s);
        theme.select(ThemeHelper.index(this));
        theme.setListener(t -> {
            int idx = theme.selectedIndex();
            if (idx != ThemeHelper.index(this)) ThemeHelper.set(this, idx);
        });

        pickLogo = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            work(() -> ImageStore.copy(this, uri, "logo"), path -> {
                Prefs.put(this, Prefs.LOGO_PATH, path);
                showLogo();
            });
        });
        createBackup = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            if (uri == null) return;
            work(() -> {
                BackupHelper.export(this, uri);
                return true;
            }, r -> Ui.info(this, "Cópia de segurança", "Cópia criada com sucesso. Guarde-a num local seguro fora do telemóvel."));
        });
        openBackup = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            Ui.confirm(this, "Restaurar cópia", "TODOS os dados actuais deste dispositivo serão substituídos pelos da cópia. "
                    + "Recomenda-se criar primeiro uma cópia dos dados actuais. Continuar?", "Restaurar", () -> work(() -> {
                BackupHelper.restore(this, uri);
                return true;
            }, r -> {
                Ui.toast(this, "Cópia restaurada.");
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            }));
        });

        findViewById(R.id.st_logo_pick).setOnClickListener(v -> pickLogo.launch("image/*"));
        findViewById(R.id.st_logo_remove).setOnClickListener(v -> {
            Prefs.get(this).edit().remove(Prefs.LOGO_PATH).apply();
            showLogo();
        });
        findViewById(R.id.st_pin_set).setOnClickListener(v -> setPin());
        findViewById(R.id.st_pin_remove).setOnClickListener(v -> Ui.confirm(this, "Remover PIN",
                "A aplicação deixa de pedir PIN ao abrir.", "Remover", () -> {
                    PinSecurity.removePin(this);
                    refreshStatus();
                }));
        findViewById(R.id.st_open_sync).setOnClickListener(v -> Ui.open(this, SyncActivity.class, null));
        findViewById(R.id.st_backup_export).setOnClickListener(v -> createBackup.launch(BackupHelper.suggestedName()));
        findViewById(R.id.st_backup_restore).setOnClickListener(v -> openBackup.launch(new String[]{"*/*"}));
        findViewById(R.id.st_audit).setOnClickListener(v -> Ui.open(this, AuditActivity.class, null));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        work(() -> {
            SettingsRepository s = new SettingsRepository(this);
            Map<String, String> m = new HashMap<>();
            for (String k : new String[]{SettingsRepository.BUSINESS_NAME, SettingsRepository.BUSINESS_NIF,
                    SettingsRepository.BUSINESS_PHONE, SettingsRepository.BUSINESS_EMAIL, SettingsRepository.BUSINESS_ADDRESS,
                    SettingsRepository.RECEIPT_FOOTER, SettingsRepository.MAX_DISCOUNT_PCT}) {
                m.put(k, s.get(k, null));
            }
            return m;
        }, m -> {
            name.setText(m.get(SettingsRepository.BUSINESS_NAME) == null ? "PastelGest" : m.get(SettingsRepository.BUSINESS_NAME));
            nif.setText(m.get(SettingsRepository.BUSINESS_NIF));
            phone.setText(m.get(SettingsRepository.BUSINESS_PHONE));
            email.setText(m.get(SettingsRepository.BUSINESS_EMAIL));
            address.setText(m.get(SettingsRepository.BUSINESS_ADDRESS));
            footer.setText(m.get(SettingsRepository.RECEIPT_FOOTER) == null ? "Obrigado pela preferência!" : m.get(SettingsRepository.RECEIPT_FOOTER));
            maxDiscount.setText(m.get(SettingsRepository.MAX_DISCOUNT_PCT) == null ? "10" : m.get(SettingsRepository.MAX_DISCOUNT_PCT));
        });
        showLogo();
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "?";
        }
        ((TextView) findViewById(R.id.st_about)).setText("PastelGest " + version + "\nGestão profissional de pastelaria — offline-first, "
                + "com base de dados local (Room, versão " + AppDatabase.VERSION + ") e sincronização Supabase.\n"
                + "Dispositivo: " + Prefs.deviceId(this) + "\nMoeda: Kwanza (Kz) • Idioma: português de Portugal.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        ((TextView) findViewById(R.id.st_pin_status)).setText(PinSecurity.hasPin(this)
                ? "PIN activo: pedido ao abrir a aplicação e após 1 minuto em segundo plano." : "Sem PIN definido.");
        findViewById(R.id.st_pin_remove).setEnabled(PinSecurity.hasPin(this));
        AuthManager auth = new AuthManager(this);
        long last = Prefs.lng(this, Prefs.LAST_SYNC, 0);
        ((TextView) findViewById(R.id.st_sync_status)).setText(auth.isLoggedIn()
                ? "Ligado como " + auth.email() + ". Última sincronização: " + Fmt.dateTime(last)
                : "Não configurada. A aplicação funciona totalmente offline.");
    }

    private void showLogo() {
        String path = Prefs.str(this, Prefs.LOGO_PATH, null);
        if (path != null && new File(path).exists()) {
            logo.setImageTintList(null);
            logo.setPadding(0, 0, 0, 0);
            logo.setImageURI(Uri.fromFile(new File(path)));
        } else {
            logo.setImageResource(R.drawable.ic_cake);
        }
    }

    private void setPin() {
        Ui.askText(this, "Definir PIN", "Introduza um PIN de 4 a 8 algarismos.", "Novo PIN", "Continuar", pin -> {
            if (!pin.matches("\\d{4,8}")) {
                Ui.showError(this, new BusinessException("O PIN deve ter entre 4 e 8 algarismos."));
                return;
            }
            Ui.askText(this, "Confirmar PIN", "Introduza novamente o PIN.", "Confirmar PIN", "Guardar", again -> {
                if (!again.equals(pin)) {
                    Ui.showError(this, new BusinessException("Os PIN não coincidem."));
                    return;
                }
                PinSecurity.setPin(this, pin);
                com.pastelgest.app.PastelGestApp.setUnlocked(true);
                Ui.success(this, "PIN definido.");
                refreshStatus();
            });
        });
    }

    private void save() {
        try {
            String n = Ui.text(name);
            if (n.isEmpty()) throw new BusinessException("Indique o nome da pastelaria.");
            String md = Ui.text(maxDiscount).replace(',', '.');
            BigDecimal pct;
            try {
                pct = new BigDecimal(md.isEmpty() ? "0" : md);
            } catch (NumberFormatException e) {
                throw new BusinessException("Desconto máximo inválido.");
            }
            if (pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException("O desconto máximo deve estar entre 0 e 100%.");
            }
            String em = Ui.text(email);
            if (!em.isEmpty() && !em.contains("@")) throw new BusinessException("Email inválido.");
            Map<String, String> values = new HashMap<>();
            values.put(SettingsRepository.BUSINESS_NAME, n);
            values.put(SettingsRepository.BUSINESS_NIF, Ui.text(nif));
            values.put(SettingsRepository.BUSINESS_PHONE, Ui.text(phone));
            values.put(SettingsRepository.BUSINESS_EMAIL, em);
            values.put(SettingsRepository.BUSINESS_ADDRESS, Ui.text(address));
            values.put(SettingsRepository.RECEIPT_FOOTER, Ui.text(footer));
            values.put(SettingsRepository.MAX_DISCOUNT_PCT, pct.stripTrailingZeros().toPlainString());
            work(() -> {
                SettingsRepository s = new SettingsRepository(this);
                for (Map.Entry<String, String> e : values.entrySet()) s.set(e.getKey(), e.getValue());
                return true;
            }, r -> Ui.success(this, "Definições guardadas."));
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }
}
