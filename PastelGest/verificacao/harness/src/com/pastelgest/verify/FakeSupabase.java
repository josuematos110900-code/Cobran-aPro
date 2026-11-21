package com.pastelgest.verify;

import android.content.Context;

import com.pastelgest.app.sync.SupabaseClient;
import com.pastelgest.app.sync.SyncException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emulação do PostgREST do Supabase com as mesmas regras do SQL do projecto:
 * upsert por (owner_id,id), merge-duplicates com LWW por updated_at,
 * ignore-duplicates para tabelas-razão e cursor server_seq.
 * Valida que cada coluna enviada existe no esquema SQL.
 */
public class FakeSupabase {
    final Map<String, Set<String>> columns = new HashMap<>();
    final Map<String, LinkedHashMap<String, JSONObject>> data = new HashMap<>();
    long seq;
    int requests;
    public final List<String> errors = new ArrayList<>();
    boolean offline;

    public FakeSupabase(String sqlFile) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(sqlFile)), "UTF-8");
        Matcher m = Pattern.compile("create table if not exists public\\.(\\w+) \\((.*?)\\n\\);", Pattern.DOTALL).matcher(sql);
        while (m.find()) {
            Set<String> cols = new HashSet<>();
            for (String line : m.group(2).split("\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("primary key")) continue;
                cols.add(t.split(" ")[0]);
            }
            columns.put(m.group(1), cols);
        }
    }

    public int rows(String table) {
        return data.getOrDefault(table, new LinkedHashMap<>()).size();
    }

    /** Cliente para um dispositivo/conta. */
    public SupabaseClient clientFor(Context ctx, String owner) {
        FakeSupabase server = this;
        return new SupabaseClient(ctx) {
            @Override
            public Response request(String method, String path, String body, String bearer, Map<String, String> headers) throws SyncException {
                return server.handle(owner, method, path, body, headers);
            }
        };
    }

    static SupabaseClient.Response response(int code, String body) {
        try {
            Constructor<SupabaseClient.Response> c = SupabaseClient.Response.class.getDeclaredConstructor(int.class, String.class);
            c.setAccessible(true);
            return c.newInstance(code, body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    synchronized SupabaseClient.Response handle(String owner, String method, String path, String body, Map<String, String> headers) throws SyncException {
        requests++;
        if (offline) throw new SyncException("Sem ligação ao servidor: offline (simulado)", new java.io.IOException("offline"));
        try {
            String table = path.substring("/rest/v1/".length()).split("\\?")[0];
            Set<String> cols = columns.get(table);
            if (cols == null) return response(404, "{\"message\":\"relation does not exist\"}");
            LinkedHashMap<String, JSONObject> t = data.computeIfAbsent(table, k -> new LinkedHashMap<>());
            if ("POST".equals(method)) {
                if (!path.contains("on_conflict=owner_id,id")) errors.add("POST sem on_conflict correcto: " + path);
                boolean ignore = headers.get("Prefer").contains("ignore-duplicates");
                JSONArray arr = new JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                        String k = it.next();
                        if (!cols.contains(k)) {
                            errors.add(table + ": coluna inexistente no Supabase: " + k);
                            return response(400, "{\"message\":\"column " + k + " does not exist\"}");
                        }
                    }
                    String key = owner + "|" + o.getString("id");
                    JSONObject existing = t.get(key);
                    if (existing != null) {
                        if (ignore) continue;
                        if (o.getLong("updated_at") < existing.getLong("updated_at")) continue; // trigger LWW
                    }
                    o.put("owner_id", owner);
                    o.put("server_seq", ++seq);
                    t.remove(key);
                    t.put(key, o);
                }
                return response(201, "");
            }
            if ("GET".equals(method)) {
                Matcher m = Pattern.compile("server_seq=gt\\.(\\d+).*limit=(\\d+)").matcher(path);
                if (!m.find()) return response(400, "{}");
                long gt = Long.parseLong(m.group(1));
                int limit = Integer.parseInt(m.group(2));
                List<JSONObject> rows = new ArrayList<>();
                for (Map.Entry<String, JSONObject> e : t.entrySet()) {
                    if (e.getKey().startsWith(owner + "|") && e.getValue().getLong("server_seq") > gt) rows.add(e.getValue());
                }
                rows.sort(Comparator.comparingLong(o -> o.optLong("server_seq")));
                JSONArray out = new JSONArray();
                for (int i = 0; i < rows.size() && i < limit; i++) out.put(rows.get(i));
                return response(200, out.toString());
            }
            return response(405, "");
        } catch (org.json.JSONException e) {
            throw new SyncException("JSON inválido", e);
        }
    }
}
