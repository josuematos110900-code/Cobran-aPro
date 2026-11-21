package com.pastelgest.app.sync;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Cliente HTTP mínimo para a API REST (PostgREST) e Auth do Supabase. */
public class SupabaseClient {
    private static final int TIMEOUT_MS = 20_000;

    public static class Response {
        public final int code;
        public final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    private final Context ctx;

    public SupabaseClient(Context c) {
        this.ctx = c.getApplicationContext();
    }

    public Response request(String method, String path, String body, String bearer, Map<String, String> headers)
            throws SyncException {
        if (!SupabaseConfig.isConfigured(ctx)) {
            throw new SyncException("Supabase não configurado (URL ou chave pública em falta).", 0);
        }
        HttpURLConnection con = null;
        try {
            URL url = new URL(SupabaseConfig.url(ctx) + path);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);
            con.setRequestMethod(method);
            con.setRequestProperty("apikey", SupabaseConfig.anonKey(ctx));
            con.setRequestProperty("Authorization", "Bearer " + (bearer != null ? bearer : SupabaseConfig.anonKey(ctx)));
            con.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) con.setRequestProperty(e.getKey(), e.getValue());
            }
            if (body != null) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = con.getResponseCode();
            InputStream is = code >= 400 ? con.getErrorStream() : con.getInputStream();
            return new Response(code, read(is));
        } catch (IOException e) {
            throw new SyncException("Sem ligação ao servidor: " + e.getMessage(), e);
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) return "";
        try (InputStream in = is) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
