package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.data.entity.AppSetting;

import java.math.BigDecimal;

/** Configurações partilhadas da pastelaria (tabela app_settings, sincronizada). */
public class SettingsRepository extends BaseRepository {
    public static final String BUSINESS_NAME = "business_name";
    public static final String BUSINESS_NIF = "business_nif";
    public static final String BUSINESS_ADDRESS = "business_address";
    public static final String BUSINESS_PHONE = "business_phone";
    public static final String BUSINESS_EMAIL = "business_email";
    public static final String RECEIPT_FOOTER = "receipt_footer";
    public static final String MAX_DISCOUNT_PCT = "max_discount_pct";

    public SettingsRepository(Context c) {
        super(c);
    }

    public String get(String key, String def) {
        AppSetting s = db.settingsDao().get(key);
        return s == null || s.value == null ? def : s.value;
    }

    public void set(String key, String value) {
        db.runInTransaction(() -> {
            AppSetting s = db.settingsDao().get(key);
            String old = s == null ? null : s.value;
            if (s == null) {
                s = new AppSetting();
                s.id = key;
                s.initNew();
            } else {
                s.touch();
            }
            s.value = value;
            db.settingsDao().upsert(s);
            if (old == null || !old.equals(value)) audit("definicao", key, "ALTERAR", (old == null ? "" : old) + " -> " + value);
        });
        com.pastelgest.app.sync.SyncScheduler.requestSync(ctx);
    }

    public String businessName() {
        return get(BUSINESS_NAME, "PastelGest");
    }

    public BigDecimal maxDiscountPercent() {
        try {
            BigDecimal v = new BigDecimal(get(MAX_DISCOUNT_PCT, "10").replace(',', '.'));
            if (v.signum() < 0) return BigDecimal.ZERO;
            return v.min(BigDecimal.valueOf(100));
        } catch (NumberFormatException e) {
            return BigDecimal.TEN;
        }
    }
}
