package com.pastelgest.app.sync;

import android.content.Context;

import com.pastelgest.app.R;
import com.pastelgest.app.util.Prefs;

/**
 * URL do projecto e chave pública (anon / publishable) do Supabase.
 * Valores definidos nas Definições da aplicação ou, por omissão, em
 * res/values/supabase_config.xml. NUNCA colocar aqui a chave service_role.
 */
public final class SupabaseConfig {
    private SupabaseConfig() { }

    public static String url(Context c) {
        String v = Prefs.str(c, Prefs.SUPABASE_URL, null);
        if (v == null || v.trim().isEmpty()) v = c.getString(R.string.supabase_url);
        v = v.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    public static String anonKey(Context c) {
        String v = Prefs.str(c, Prefs.SUPABASE_KEY, null);
        if (v == null || v.trim().isEmpty()) v = c.getString(R.string.supabase_anon_key);
        return v.trim();
    }

    public static boolean isConfigured(Context c) {
        String u = url(c);
        return u.startsWith("https://") && !anonKey(c).isEmpty();
    }

    /** Rejeita chaves de administração por engano (o JWT service_role contém "service_role"). */
    public static boolean looksLikeServiceKey(String key) {
        if (key == null) return false;
        if (key.startsWith("sb_secret_")) return true;
        String[] parts = key.split("\\.");
        if (parts.length != 3) return false;
        try {
            String payload = new String(android.util.Base64.decode(parts[1],
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING), "UTF-8");
            return payload.contains("service_role");
        } catch (Exception e) {
            return false;
        }
    }
}
