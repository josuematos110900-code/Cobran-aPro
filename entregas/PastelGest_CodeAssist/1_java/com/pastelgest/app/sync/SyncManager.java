package com.pastelgest.app.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.pastelgest.app.data.AppDatabase;
import com.pastelgest.app.data.entity.SyncLog;
import com.pastelgest.app.util.Prefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sincronização offline-first entre o Room e o Supabase.
 *
 * Regras:
 *  - Identificadores UUID gerados no dispositivo: o mesmo registo nunca é duplicado (upsert por id).
 *  - Envio (push): registos com syncState = 1, por ordem de dependências.
 *    Tabelas-razão (movimentos, pagamentos, auditoria) são só de inserção
 *    (ignore-duplicates); as restantes usam "last write wins" por updated_at,
 *    garantido também no servidor por trigger (dados antigos nunca substituem novos).
 *  - Recepção (pull): por cursor server_seq crescente; um registo remoto só
 *    substitui o local se tiver updated_at mais recente.
 *  - Após a recepção, as caches de stock e saldos são recalculadas a partir das
 *    tabelas-razão, mantendo consistência entre dispositivos.
 */
public class SyncManager {

    /** Ordem respeita as chaves estrangeiras (pais antes de filhos). */
    static final String[] TABLES = {
            "app_settings", "suppliers", "clients", "ingredients", "products", "recipes",
            "recipe_ingredients", "recipe_cost_snapshots",
            "purchases", "purchase_items", "purchase_payments",
            "productions", "production_items",
            "stock_movements", "product_movements",
            "sales", "sale_items", "debts", "payments",
            "expenses", "audit_log"
    };

    static final Set<String> INSERT_ONLY = new HashSet<>(Arrays.asList(
            "stock_movements", "product_movements", "purchase_payments", "payments", "audit_log",
            "recipe_cost_snapshots", "production_items", "sale_items", "purchase_items"));

    private static final int PUSH_BATCH = 200;
    private static final int PULL_PAGE = 500;
    private static final long PULL_OVERLAP = 500;
    private static final Object LOCK = new Object();

    private final Context ctx;
    private final AppDatabase db;
    private final SupabaseClient client;
    private final AuthManager auth;
    private final Map<String, Map<String, String>> columnCache = new HashMap<>();

    public static class Result {
        public int pushed;
        public int pulled;
        public String message;
    }

    public SyncManager(Context c) {
        this(c, new SupabaseClient(c.getApplicationContext()), new AuthManager(c.getApplicationContext()));
    }

    /** Construtor com dependências injectáveis (permite substituir o cliente HTTP ou a autenticação). */
    public SyncManager(Context c, SupabaseClient client, AuthManager auth) {
        this.ctx = c.getApplicationContext();
        this.db = AppDatabase.get(ctx);
        this.client = client;
        this.auth = auth;
    }

    public int pendingCount() {
        SupportSQLiteDatabase sql = db.getOpenHelper().getReadableDatabase();
        int total = 0;
        for (String t : TABLES) {
            try (Cursor c = sql.query("SELECT COUNT(*) FROM " + t + " WHERE syncState = 1")) {
                if (c.moveToFirst()) total += c.getInt(0);
            }
        }
        return total;
    }

    public Result sync() throws SyncException {
        synchronized (LOCK) {
            SyncLog log = new SyncLog();
            log.startedAt = System.currentTimeMillis();
            Result res = new Result();
            try {
                if (!SupabaseConfig.isConfigured(ctx)) throw new SyncException("Supabase não configurado.", 0);
                if (!auth.isLoggedIn()) throw new SyncException("Sessão não iniciada.", 401);
                for (int round = 0; round < 3; round++) {
                    int n = pushAll();
                    res.pushed += n;
                    if (n == 0) break;
                }
                res.pulled = pullAll();
                if (res.pulled > 0) recomputeCaches();
                res.message = "Enviados " + res.pushed + ", recebidos " + res.pulled + ".";
                log.status = "OK";
                log.message = res.message;
                Prefs.putLong(ctx, Prefs.LAST_SYNC, System.currentTimeMillis());
                Prefs.put(ctx, Prefs.LAST_SYNC_STATUS, "OK: " + res.message);
                return res;
            } catch (SyncException e) {
                log.status = "ERRO";
                log.message = e.getMessage();
                Prefs.put(ctx, Prefs.LAST_SYNC_STATUS, "Erro: " + e.getMessage());
                throw e;
            } finally {
                log.finishedAt = System.currentTimeMillis();
                log.pushed = res.pushed;
                log.pulled = res.pulled;
                db.syncLogDao().insert(log);
                db.syncLogDao().trim();
            }
        }
    }

    // ------------------------------------------------------------------ PUSH

    private int pushAll() throws SyncException {
        int total = 0;
        for (String table : TABLES) total += pushTable(table);
        return total;
    }

    private int pushTable(String table) throws SyncException {
        SupportSQLiteDatabase sql = db.getOpenHelper().getWritableDatabase();
        int sent = 0;
        Set<String> attempted = new HashSet<>();
        while (true) {
            List<String[]> marks = new ArrayList<>();
            JSONArray batch = new JSONArray();
            try (Cursor c = sql.query("SELECT * FROM " + table + " WHERE syncState = 1 LIMIT " + PUSH_BATCH)) {
                int idIdx = c.getColumnIndexOrThrow("id");
                int upIdx = c.getColumnIndexOrThrow("updatedAt");
                while (c.moveToNext()) {
                    String id = c.getString(idIdx);
                    if (!attempted.add(id)) continue;
                    batch.put(rowToJson(c));
                    marks.add(new String[]{id, String.valueOf(c.getLong(upIdx))});
                }
            } catch (JSONException e) {
                throw new SyncException("Erro a preparar dados de " + table, e);
            }
            if (batch.length() == 0) return sent;
            Map<String, String> h = new HashMap<>();
            h.put("Prefer", (INSERT_ONLY.contains(table) ? "resolution=ignore-duplicates" : "resolution=merge-duplicates")
                    + ",return=minimal");
            SupabaseClient.Response r = authed("POST", "/rest/v1/" + table + "?on_conflict=owner_id,id", batch.toString(), h);
            if (!r.ok()) throw new SyncException("Falha ao enviar " + table + " (HTTP " + r.code + "): " + shorten(r.body), r.code);
            sql.beginTransaction();
            try {
                for (String[] m : marks) {
                    // Só marca como sincronizado se não foi alterado entretanto.
                    sql.execSQL("UPDATE " + table + " SET syncState = 0 WHERE id = ? AND updatedAt = ?",
                            new Object[]{m[0], Long.parseLong(m[1])});
                }
                sql.setTransactionSuccessful();
            } finally {
                sql.endTransaction();
            }
            sent += marks.size();
        }
    }

    private JSONObject rowToJson(Cursor c) throws JSONException {
        JSONObject o = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++) {
            String col = c.getColumnName(i);
            if ("syncState".equals(col)) continue;
            String key = camelToSnake(col);
            switch (c.getType(i)) {
                case Cursor.FIELD_TYPE_NULL: o.put(key, JSONObject.NULL); break;
                case Cursor.FIELD_TYPE_INTEGER: o.put(key, c.getLong(i)); break;
                case Cursor.FIELD_TYPE_FLOAT: o.put(key, c.getDouble(i)); break;
                default: o.put(key, c.getString(i));
            }
        }
        return o;
    }

    // ------------------------------------------------------------------ PULL

    private int pullAll() throws SyncException {
        int total = 0;
        for (String table : TABLES) total += pullTable(table);
        return total;
    }

    /**
     * Recebe as linhas novas de uma tabela. A primeira página relê uma janela de
     * PULL_OVERLAP posições antes do cursor: no PostgreSQL duas transacções podem
     * obter server_seq por uma ordem e fazer commit pela ordem inversa, e esta
     * sobreposição garante que uma linha "atrasada" não é perdida. Linhas já
     * conhecidas são ignoradas pela regra de updated_at (sem efeitos).
     */
    private int pullTable(String table) throws SyncException {
        String cursorKey = Prefs.PULL_CURSOR_PREFIX + table;
        long cursor = Prefs.lng(ctx, cursorKey, 0);
        long from = Math.max(0, cursor - PULL_OVERLAP);
        int applied = 0;
        while (true) {
            String path = "/rest/v1/" + table + "?select=*&server_seq=gt." + from
                    + "&order=server_seq.asc&limit=" + PULL_PAGE;
            SupabaseClient.Response r = authed("GET", path, null, null);
            if (!r.ok()) throw new SyncException("Falha ao receber " + table + " (HTTP " + r.code + "): " + shorten(r.body), r.code);
            JSONArray rows;
            try {
                rows = new JSONArray(r.body);
            } catch (JSONException e) {
                throw new SyncException("Resposta inválida para " + table, e);
            }
            if (rows.length() == 0) break;
            long maxSeq = from;
            Map<String, String> cols = localColumns(table);
            SupportSQLiteDatabase sql = db.getOpenHelper().getWritableDatabase();
            sql.beginTransaction();
            try {
                sql.execSQL("PRAGMA defer_foreign_keys = ON");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    maxSeq = Math.max(maxSeq, row.optLong("server_seq", maxSeq));
                    if (applyRow(sql, table, cols, row)) applied++;
                }
                sql.setTransactionSuccessful();
            } catch (JSONException e) {
                throw new SyncException("Dados inválidos em " + table, e);
            } finally {
                sql.endTransaction();
            }
            if (maxSeq <= from) break;
            from = maxSeq;
            cursor = Math.max(cursor, maxSeq);
            Prefs.putLong(ctx, cursorKey, cursor);
            if (rows.length() < PULL_PAGE) break;
        }
        return applied;
    }

    /** Aplica uma linha remota se for mais recente que a local. */
    private boolean applyRow(SupportSQLiteDatabase sql, String table, Map<String, String> cols, JSONObject row)
            throws JSONException {
        String id = row.getString("id");
        long remoteUpdated = row.optLong("updated_at", 0);
        Long localUpdated = null;
        try (Cursor c = sql.query("SELECT updatedAt FROM " + table + " WHERE id = ?", new Object[]{id})) {
            if (c.moveToFirst()) localUpdated = c.getLong(0);
        }
        if (localUpdated != null && remoteUpdated <= localUpdated) return false;
        ContentValues cv = new ContentValues();
        for (Map.Entry<String, String> e : cols.entrySet()) {
            String snake = e.getKey();
            String local = e.getValue();
            if ("syncState".equals(local)) continue;
            if (!row.has(snake)) continue;
            Object v = row.get(snake);
            if (v == JSONObject.NULL) cv.putNull(local);
            else if (v instanceof Boolean) cv.put(local, ((Boolean) v) ? 1 : 0);
            else if (v instanceof Integer) cv.put(local, ((Integer) v).longValue());
            else if (v instanceof Long) cv.put(local, (Long) v);
            else if (v instanceof Number) cv.put(local, ((Number) v).longValue());
            else cv.put(local, v.toString());
        }
        cv.put("syncState", 0);
        if (localUpdated == null) {
            sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv);
        } else {
            sql.update(table, SQLiteDatabase.CONFLICT_ABORT, cv, "id = ?", new Object[]{id});
        }
        return true;
    }

    /** Mapa snake_case -> nome da coluna local, lido do próprio esquema SQLite. */
    private Map<String, String> localColumns(String table) {
        Map<String, String> m = columnCache.get(table);
        if (m != null) return m;
        m = new LinkedHashMap<>();
        try (Cursor c = db.getOpenHelper().getReadableDatabase().query("PRAGMA table_info(" + table + ")")) {
            int nameIdx = c.getColumnIndexOrThrow("name");
            while (c.moveToNext()) {
                String col = c.getString(nameIdx);
                m.put(camelToSnake(col), col);
            }
        }
        columnCache.put(table, m);
        return m;
    }

    private void recomputeCaches() {
        db.runInTransaction(() -> {
            db.ingredientDao().recomputeAllStock();
            db.productDao().recomputeAllStock();
            db.debtDao().recomputePaid();
            db.debtDao().recomputeBalances();
            db.saleDao().recomputePaid();
            db.purchaseDao().recomputePaid();
        });
    }

    private SupabaseClient.Response authed(String method, String path, String body, Map<String, String> headers)
            throws SyncException {
        SupabaseClient.Response r = client.request(method, path, body, auth.accessToken(), headers);
        if (r.code == 401) {
            r = client.request(method, path, body, auth.refresh(), headers);
        }
        return r;
    }

    public static String camelToSnake(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isUpperCase(ch)) sb.append('_').append(Character.toLowerCase(ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
