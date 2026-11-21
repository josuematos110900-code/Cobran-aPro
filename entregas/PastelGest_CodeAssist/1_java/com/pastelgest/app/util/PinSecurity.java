package com.pastelgest.app.util;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** PIN de acesso à aplicação, guardado apenas como hash SHA-256 com sal. */
public final class PinSecurity {
    private PinSecurity() { }

    public static boolean hasPin(Context c) {
        return Prefs.str(c, Prefs.PIN_HASH, null) != null;
    }

    public static void setPin(Context c, String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new com.pastelgest.app.core.BusinessException("O PIN deve ter entre 4 e 8 algarismos.");
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = hex(salt);
        Prefs.get(c).edit().putString(Prefs.PIN_SALT, saltHex).putString(Prefs.PIN_HASH, hash(saltHex, pin)).apply();
    }

    public static void removePin(Context c) {
        Prefs.get(c).edit().remove(Prefs.PIN_SALT).remove(Prefs.PIN_HASH).apply();
    }

    public static boolean check(Context c, String pin) {
        String salt = Prefs.str(c, Prefs.PIN_SALT, "");
        String expected = Prefs.str(c, Prefs.PIN_HASH, null);
        return expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                hash(salt, pin).getBytes(StandardCharsets.UTF_8));
    }

    static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
