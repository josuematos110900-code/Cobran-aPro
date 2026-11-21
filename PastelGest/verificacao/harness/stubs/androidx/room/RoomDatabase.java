package androidx.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import java.util.concurrent.Callable;

/**
 * STUB DE VERIFICAÇÃO (não faz parte da aplicação). Reproduz a semântica de
 * transacções aninhadas do Room: só a transacção exterior faz commit; qualquer
 * excepção desfaz tudo.
 */
public abstract class RoomDatabase {
    private int depth;

    protected void txBegin() { }
    protected void txCommit() { }
    protected void txRollback() { }

    public SupportSQLiteOpenHelper getOpenHelper() { return null; }

    public void runInTransaction(Runnable r) {
        runInTransaction(() -> { r.run(); return null; });
    }

    public <V> V runInTransaction(Callable<V> c) {
        if (depth++ == 0) txBegin();
        boolean ok = false;
        try {
            V v = c.call();
            ok = true;
            return v;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (--depth == 0) { if (ok) txCommit(); else txRollback(); }
        }
    }

    public void close() { }

    public enum JournalMode { AUTOMATIC, TRUNCATE, WRITE_AHEAD_LOGGING }

    public abstract static class Callback {
        public void onCreate(SupportSQLiteDatabase db) { }
        public void onOpen(SupportSQLiteDatabase db) { }
    }

    public static class Builder<T extends RoomDatabase> {
        public Builder<T> addMigrations(Migration... m) { return this; }
        public Builder<T> setJournalMode(JournalMode m) { return this; }
        public Builder<T> addCallback(Callback c) { return this; }
        public Builder<T> fallbackToDestructiveMigration() { return this; }
        public T build() { return null; }
    }
}
