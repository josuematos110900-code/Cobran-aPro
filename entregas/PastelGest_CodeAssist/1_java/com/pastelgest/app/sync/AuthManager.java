package com.pastelgest.app.sync;

import android.content.Context;

import com.pastelgest.app.util.Prefs;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Autenticação Supabase (email/palavra-passe) via API REST /auth/v1.
 * Os tokens ficam nas SharedPreferences privadas da aplicação.
 */
public class AuthManager {
    private final Context ctx;
    private final SupabaseClient client;

    public AuthManager(Context c) {
        ctx = c.getApplicationContext();
        client = new SupabaseClient(ctx);
    }

    public boolean isLoggedIn() {
        return Prefs.str(ctx, Prefs.REFRESH_TOKEN, null) != null;
    }

    public String email() {
        return Prefs.str(ctx, Prefs.USER_EMAIL, null);
    }

    public String userId() {
        return Prefs.str(ctx, Prefs.USER_ID, null);
    }

    /** Cria conta. Devolve true se a sessão ficou activa; false se exigir confirmação por email. */
    public boolean signUp(String email, String password) throws SyncException {
        validate(email, password);
        SupabaseClient.Response r = client.request("POST", "/auth/v1/signup", credentials(email, password), null, null);
        if (!r.ok()) throw new SyncException(authError(r), r.code);
        try {
            JSONObject o = new JSONObject(r.body);
            if (o.has("access_token")) {
                storeSession(o);
                return true;
            }
            return false;
        } catch (JSONException e) {
            throw new SyncException("Resposta inválida do servidor.", e);
        }
    }

    public void signIn(String email, String password) throws SyncException {
        validate(email, password);
        SupabaseClient.Response r = client.request("POST", "/auth/v1/token?grant_type=password",
                credentials(email, password), null, null);
        if (!r.ok()) throw new SyncException(authError(r), r.code);
        try {
            storeSession(new JSONObject(r.body));
        } catch (JSONException e) {
            throw new SyncException("Resposta inválida do servidor.", e);
        }
    }

    public void signOut() {
        String token = Prefs.str(ctx, Prefs.ACCESS_TOKEN, null);
        if (token != null) {
            try {
                client.request("POST", "/auth/v1/logout", "{}", token, null);
            } catch (SyncException ignored) {
                // Termina a sessão local mesmo sem Internet.
            }
        }
        Prefs.get(ctx).edit()
                .remove(Prefs.ACCESS_TOKEN).remove(Prefs.REFRESH_TOKEN).remove(Prefs.TOKEN_EXPIRES)
                .remove(Prefs.USER_ID).remove(Prefs.USER_EMAIL).apply();
    }

    /** Token de acesso válido, renovado automaticamente quando expira. */
    public synchronized String accessToken() throws SyncException {
        String refresh = Prefs.str(ctx, Prefs.REFRESH_TOKEN, null);
        if (refresh == null) throw new SyncException("Sessão não iniciada. Entre na conta em Definições > Sincronização.", 401);
        long expires = Prefs.lng(ctx, Prefs.TOKEN_EXPIRES, 0);
        String access = Prefs.str(ctx, Prefs.ACCESS_TOKEN, null);
        if (access != null && System.currentTimeMillis() < expires - 60_000) return access;
        return refresh();
    }

    public synchronized String refresh() throws SyncException {
        String refresh = Prefs.str(ctx, Prefs.REFRESH_TOKEN, null);
        if (refresh == null) throw new SyncException("Sessão não iniciada.", 401);
        JSONObject body = new JSONObject();
        try {
            body.put("refresh_token", refresh);
        } catch (JSONException e) {
            throw new SyncException("Erro interno.", e);
        }
        SupabaseClient.Response r = client.request("POST", "/auth/v1/token?grant_type=refresh_token", body.toString(), null, null);
        if (!r.ok()) {
            if (r.code == 400 || r.code == 401) {
                Prefs.get(ctx).edit().remove(Prefs.ACCESS_TOKEN).remove(Prefs.REFRESH_TOKEN).apply();
                throw new SyncException("A sessão expirou. Entre novamente na conta.", 401);
            }
            throw new SyncException(authError(r), r.code);
        }
        try {
            JSONObject o = new JSONObject(r.body);
            storeSession(o);
            return o.getString("access_token");
        } catch (JSONException e) {
            throw new SyncException("Resposta inválida do servidor.", e);
        }
    }

    private void storeSession(JSONObject o) throws JSONException {
        long expiresIn = o.optLong("expires_in", 3600);
        JSONObject user = o.optJSONObject("user");
        android.content.SharedPreferences.Editor ed = Prefs.get(ctx).edit()
                .putString(Prefs.ACCESS_TOKEN, o.getString("access_token"))
                .putString(Prefs.REFRESH_TOKEN, o.getString("refresh_token"))
                .putLong(Prefs.TOKEN_EXPIRES, System.currentTimeMillis() + expiresIn * 1000L);
        if (user != null) {
            ed.putString(Prefs.USER_ID, user.optString("id"));
            ed.putString(Prefs.USER_EMAIL, user.optString("email"));
        }
        ed.commit();
    }

    private static void validate(String email, String password) throws SyncException {
        if (email == null || !email.contains("@")) throw new SyncException("Email inválido.", 0);
        if (password == null || password.length() < 6) throw new SyncException("A palavra-passe deve ter pelo menos 6 caracteres.", 0);
    }

    private static String credentials(String email, String password) throws SyncException {
        try {
            JSONObject o = new JSONObject();
            o.put("email", email.trim());
            o.put("password", password);
            return o.toString();
        } catch (JSONException e) {
            throw new SyncException("Erro interno.", e);
        }
    }

    private static String authError(SupabaseClient.Response r) {
        try {
            JSONObject o = new JSONObject(r.body);
            String msg = o.optString("error_description", o.optString("msg", o.optString("message", "")));
            if (msg.toLowerCase().contains("invalid login")) return "Email ou palavra-passe incorrectos.";
            if (msg.toLowerCase().contains("already registered")) return "Já existe uma conta com este email.";
            if (msg.toLowerCase().contains("email not confirmed")) return "Confirme o email antes de entrar.";
            if (!msg.isEmpty()) return msg;
        } catch (JSONException ignored) {
            // corpo não-JSON
        }
        return "Erro de autenticação (HTTP " + r.code + ").";
    }
}
