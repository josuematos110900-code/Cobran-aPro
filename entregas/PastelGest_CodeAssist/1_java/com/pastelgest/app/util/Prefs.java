package com.pastelgest.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/** Configurações locais do dispositivo (SharedPreferences privadas). */
public final class Prefs {
    private static final String FILE = "pastelgest_prefs";

    public static final String THEME = "theme";               // system / light / dark
    public static final String PIN_HASH = "pin_hash";
    public static final String PIN_SALT = "pin_salt";
    public static final String LOGO_PATH = "logo_path";
    public static final String DEVICE_ID = "device_id";
    public static final String SUPABASE_URL = "supabase_url";
    public static final String SUPABASE_KEY = "supabase_anon_key";
    public static final String AUTO_SYNC = "auto_sync";
    public static final String ACCESS_TOKEN = "sb_access_token";
    public static final String REFRESH_TOKEN = "sb_refresh_token";
    public static final String TOKEN_EXPIRES = "sb_token_expires";
    public static final String USER_ID = "sb_user_id";
    public static final String USER_EMAIL = "sb_user_email";
    public static final String LAST_SYNC = "last_sync";
    public static final String LAST_SYNC_STATUS = "last_sync_status";
    public static final String PULL_CURSOR_PREFIX = "pull_cursor_";

    private Prefs() { }

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String str(Context c, String key, String def) {
        return get(c).getString(key, def);
    }

    public static void put(Context c, String key, String value) {
        get(c).edit().putString(key, value).apply();
    }

    public static long lng(Context c, String key, long def) {
        return get(c).getLong(key, def);
    }

    public static void putLong(Context c, String key, long value) {
        get(c).edit().putLong(key, value).apply();
    }

    public static boolean bool(Context c, String key, boolean def) {
        return get(c).getBoolean(key, def);
    }

    public static void putBool(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    /** Identificador estável deste dispositivo (para auditoria e numeração). */
    public static synchronized String deviceId(Context c) {
        String id = str(c, DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            get(c).edit().putString(DEVICE_ID, id).commit();
        }
        return id;
    }
}
