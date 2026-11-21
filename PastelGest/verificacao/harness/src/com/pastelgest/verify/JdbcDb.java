package com.pastelgest.verify;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;

import androidx.room.Dao;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import com.pastelgest.app.data.AppDatabase;
import com.pastelgest.app.data.dao.*;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementação de verificação do AppDatabase sobre SQLite (JDBC): executa as
 * mesmas @Query, @Insert, @Update e @Upsert declaradas nos DAOs, com o mesmo
 * esquema que o Room gera. Serve para testar os repositórios reais.
 */
public class JdbcDb extends AppDatabase {
    final Connection con;
    public final List<String> warnings = new ArrayList<>();
    private final Map<Class<?>, Object> daos = new HashMap<>();

    public JdbcDb(String schemaFile) throws Exception {
        con = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = con.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            for (String s : new String(Files.readAllBytes(Paths.get(schemaFile)), "UTF-8").split(";\n")) {
                if (!s.trim().isEmpty()) st.execute(s);
            }
        }
    }

    /** Instala esta BD como a instância global do AppDatabase. */
    public void install() throws Exception {
        Field f = AppDatabase.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, this);
    }

    @Override protected void txBegin() { try { con.setAutoCommit(false); } catch (SQLException e) { throw new RuntimeException(e); } }
    @Override protected void txCommit() { try { con.commit(); con.setAutoCommit(true); } catch (SQLException e) { throw new RuntimeException(e); } }
    @Override protected void txRollback() { try { con.rollback(); con.setAutoCommit(true); } catch (SQLException e) { throw new RuntimeException(e); } }

    @SuppressWarnings("unchecked")
    private <T> T dao(Class<T> k) {
        return (T) daos.computeIfAbsent(k, c -> Proxy.newProxyInstance(k.getClassLoader(), new Class<?>[]{k}, this::invoke));
    }

    @Override public SupplierDao supplierDao() { return dao(SupplierDao.class); }
    @Override public IngredientDao ingredientDao() { return dao(IngredientDao.class); }
    @Override public PurchaseDao purchaseDao() { return dao(PurchaseDao.class); }
    @Override public RecipeDao recipeDao() { return dao(RecipeDao.class); }
    @Override public ProductDao productDao() { return dao(ProductDao.class); }
    @Override public ProductionDao productionDao() { return dao(ProductionDao.class); }
    @Override public SaleDao saleDao() { return dao(SaleDao.class); }
    @Override public ClientDao clientDao() { return dao(ClientDao.class); }
    @Override public DebtDao debtDao() { return dao(DebtDao.class); }
    @Override public ExpenseDao expenseDao() { return dao(ExpenseDao.class); }
    @Override public AuditDao auditDao() { return dao(AuditDao.class); }
    @Override public SettingsDao settingsDao() { return dao(SettingsDao.class); }
    @Override public SyncLogDao syncLogDao() { return dao(SyncLogDao.class); }

    // ------------------------------------------------------------ DAO dinâmico
    private Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        if (m.getDeclaringClass() == Object.class) return m.invoke(this, args);
        if (!m.getDeclaringClass().isAnnotationPresent(Dao.class)) throw new IllegalStateException("não é DAO");
        try {
            if (m.isAnnotationPresent(Query.class)) return query(m, m.getAnnotation(Query.class).value(), args);
            if (m.isAnnotationPresent(Insert.class)) return insert(args[0], m.getAnnotation(Insert.class).onConflict(), m.getReturnType());
            if (m.isAnnotationPresent(Update.class)) return update(args[0]);
            if (m.isAnnotationPresent(Upsert.class)) { upsert(args[0]); return null; }
        } catch (SQLException e) {
            // O Room lança SQLiteConstraintException em violações de restrições.
            if (e.getMessage() != null && e.getMessage().contains("CONSTRAINT")) {
                throw new android.database.sqlite.SQLiteConstraintException(e.getMessage());
            }
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("método sem anotação Room: " + m);
    }

    static String table(Class<?> k) {
        return k.getAnnotation(Entity.class).tableName();
    }

    static List<Field> fields(Class<?> k) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                out.add(f);
            }
        }
        return out;
    }

    static Field pk(Class<?> k) {
        for (Field f : fields(k)) if (f.isAnnotationPresent(PrimaryKey.class)) return f;
        throw new IllegalStateException("sem PK: " + k);
    }

    private Object insert(Object e, int conflict, Class<?> ret) throws Exception {
        Class<?> k = e.getClass();
        Field pk = pk(k);
        boolean auto = pk.getAnnotation(PrimaryKey.class).autoGenerate();
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Field f : fields(k)) {
            if (auto && f.equals(pk)) continue;
            cols.add(f.getName());
            vals.add(f.get(e));
        }
        String verb = conflict == OnConflictStrategy.REPLACE ? "INSERT OR REPLACE" : conflict == OnConflictStrategy.IGNORE ? "INSERT OR IGNORE" : "INSERT";
        String sql = verb + " INTO `" + table(k) + "` (" + String.join(",", cols) + ") VALUES (" + String.join(",", Collections.nCopies(cols.size(), "?")) + ")";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, vals);
            ps.executeUpdate();
            if (ret == long.class) {
                try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getLong(1) : -1L; }
            }
        }
        return null;
    }

    private Object update(Object e) throws Exception {
        Class<?> k = e.getClass();
        Field pk = pk(k);
        List<String> sets = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Field f : fields(k)) { sets.add(f.getName() + "=?"); vals.add(f.get(e)); }
        vals.add(pk.get(e));
        try (PreparedStatement ps = con.prepareStatement("UPDATE `" + table(k) + "` SET " + String.join(",", sets) + " WHERE " + pk.getName() + "=?")) {
            bind(ps, vals);
            return ps.executeUpdate();
        }
    }

    private void upsert(Object e) throws Exception {
        Class<?> k = e.getClass();
        Field pk = pk(k);
        List<String> cols = new ArrayList<>();
        List<String> sets = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Field f : fields(k)) { cols.add(f.getName()); vals.add(f.get(e)); if (!f.equals(pk)) sets.add(f.getName() + "=excluded." + f.getName()); }
        String sql = "INSERT INTO `" + table(k) + "` (" + String.join(",", cols) + ") VALUES (" + String.join(",", Collections.nCopies(cols.size(), "?"))
                + ") ON CONFLICT(" + pk.getName() + ") DO UPDATE SET " + String.join(",", sets);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, vals);
            ps.executeUpdate();
        }
    }

    private static final Pattern PARAM = Pattern.compile(":(\\w+)");

    private Object query(Method m, String q, Object[] args) throws Exception {
        Map<String, Object> byName = new HashMap<>();
        Parameter[] ps = m.getParameters();
        for (int i = 0; i < ps.length; i++) {
            if (!ps[i].isNamePresent()) throw new IllegalStateException("compile com -parameters");
            byName.put(ps[i].getName(), args[i]);
        }
        List<Object> vals = new ArrayList<>();
        Matcher mt = PARAM.matcher(q);
        StringBuffer sb = new StringBuffer();
        while (mt.find()) {
            if (!byName.containsKey(mt.group(1))) throw new IllegalStateException("parâmetro inexistente :" + mt.group(1));
            vals.add(byName.get(mt.group(1)));
            mt.appendReplacement(sb, "?");
        }
        mt.appendTail(sb);
        String sql = sb.toString();
        Class<?> ret = m.getReturnType();
        try (PreparedStatement st = con.prepareStatement(sql)) {
            bind(st, vals);
            if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                int n = st.executeUpdate();
                return ret == int.class ? n : null;
            }
            try (ResultSet rs = st.executeQuery()) {
                if (ret == List.class) {
                    Class<?> el = (Class<?>) ((ParameterizedType) m.getGenericReturnType()).getActualTypeArguments()[0];
                    List<Object> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs, el, m.getName()));
                    return out;
                }
                if (!rs.next()) {
                    if (ret == long.class) return 0L;
                    if (ret == int.class) return 0;
                    return null;
                }
                return map(rs, ret, m.getName());
            }
        }
    }

    private Object map(ResultSet rs, Class<?> k, String where) throws Exception {
        if (k == long.class || k == Long.class) return rs.getLong(1);
        if (k == int.class || k == Integer.class) return rs.getInt(1);
        if (k == String.class) return rs.getString(1);
        ResultSetMetaData md = rs.getMetaData();
        Set<String> cols = new HashSet<>();
        for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnLabel(i));
        return fill(rs, k, cols, where);
    }

    private Object fill(ResultSet rs, Class<?> k, Set<String> cols, String where) throws Exception {
        Object o = k.getDeclaredConstructor().newInstance();
        for (Field f : fields(k)) {
            f.setAccessible(true);
            if (f.isAnnotationPresent(Embedded.class)) { f.set(o, fill(rs, f.getType(), cols, where)); continue; }
            if (!cols.contains(f.getName())) {
                warnings.add(where + ": coluna em falta " + f.getName() + " para " + k.getSimpleName());
                continue;
            }
            Class<?> t = f.getType();
            if (t == long.class) f.setLong(o, rs.getLong(f.getName()));
            else if (t == int.class) f.setInt(o, rs.getInt(f.getName()));
            else if (t == String.class) f.set(o, rs.getString(f.getName()));
            else throw new IllegalStateException("tipo não suportado " + t);
        }
        return o;
    }

    private static void bind(PreparedStatement ps, List<Object> vals) throws SQLException {
        for (int i = 0; i < vals.size(); i++) {
            Object v = vals.get(i);
            if (v == null) ps.setNull(i + 1, Types.NULL);
            else if (v instanceof Long) ps.setLong(i + 1, (Long) v);
            else if (v instanceof Integer) ps.setInt(i + 1, (Integer) v);
            else ps.setString(i + 1, v.toString());
        }
    }

    // --------------------------------------------- SupportSQLite sobre JDBC (sincronização)
    @Override
    public SupportSQLiteOpenHelper getOpenHelper() {
        SupportSQLiteDatabase sdb = new SupportSQLiteDatabase() {
            @Override public void execSQL(String sql) { execSQL(sql, new Object[0]); }
            @Override public void execSQL(String sql, Object[] a) {
                try (PreparedStatement ps = con.prepareStatement(sql)) { bind(ps, Arrays.asList(a)); ps.execute(); }
                catch (SQLException e) { throw new RuntimeException(e); }
            }
            @Override public Cursor query(String sql) { return query(sql, new Object[0]); }
            @Override public Cursor query(String sql, Object[] a) {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    bind(ps, Arrays.asList(a));
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData md = rs.getMetaData();
                        String[] names = new String[md.getColumnCount()];
                        for (int i = 0; i < names.length; i++) names[i] = md.getColumnLabel(i + 1);
                        MatrixCursor mc = new MatrixCursor(names);
                        while (rs.next()) {
                            Object[] row = new Object[names.length];
                            for (int i = 0; i < names.length; i++) row[i] = rs.getObject(i + 1);
                            mc.addRow(row);
                        }
                        return mc;
                    }
                } catch (SQLException e) { throw new RuntimeException(e); }
            }
            @Override public long insert(String table, int conflict, ContentValues v) {
                List<String> cols = new ArrayList<>(v.keySet());
                List<Object> vals = new ArrayList<>();
                for (String c : cols) vals.add(v.get(c));
                String sql = "INSERT INTO `" + table + "` (" + String.join(",", cols) + ") VALUES (" + String.join(",", Collections.nCopies(cols.size(), "?")) + ")";
                try (PreparedStatement ps = con.prepareStatement(sql)) { bind(ps, vals); return ps.executeUpdate(); }
                catch (SQLException e) { throw new RuntimeException(e); }
            }
            @Override public int update(String table, int conflict, ContentValues v, String where, Object[] a) {
                List<String> cols = new ArrayList<>(v.keySet());
                List<Object> vals = new ArrayList<>();
                List<String> sets = new ArrayList<>();
                for (String c : cols) { sets.add(c + "=?"); vals.add(v.get(c)); }
                vals.addAll(Arrays.asList(a));
                try (PreparedStatement ps = con.prepareStatement("UPDATE `" + table + "` SET " + String.join(",", sets) + " WHERE " + where)) {
                    bind(ps, vals); return ps.executeUpdate();
                } catch (SQLException e) { throw new RuntimeException(e); }
            }
            @Override public void beginTransaction() { txBegin(); }
            @Override public void setTransactionSuccessful() { ok = true; }
            @Override public void endTransaction() { if (ok) txCommit(); else txRollback(); ok = false; }
            boolean ok;
        };
        return new SupportSQLiteOpenHelper() {
            @Override public SupportSQLiteDatabase getWritableDatabase() { return sdb; }
            @Override public SupportSQLiteDatabase getReadableDatabase() { return sdb; }
        };
    }

    /** Executa uma query de controlo directamente. */
    public long scalar(String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }
}
