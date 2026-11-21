package com.pastelgest.app.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.data.AppDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Cópia de segurança e restauro da base de dados completa. */
public final class BackupHelper {
    private BackupHelper() { }

    public static String suggestedName() {
        return "PastelGest_backup_" + Fmt.fileStamp(System.currentTimeMillis()) + ".db";
    }

    /** Exporta a base de dados (após checkpoint do WAL) para o destino escolhido. */
    public static void export(Context c, Uri target) throws IOException {
        AppDatabase db = AppDatabase.get(c);
        try (Cursor cur = db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)")) {
            cur.moveToFirst();
        }
        File src = c.getDatabasePath(AppDatabase.NAME);
        try (InputStream in = new FileInputStream(src);
             OutputStream out = c.getContentResolver().openOutputStream(target)) {
            if (out == null) throw new IOException("Não foi possível abrir o destino.");
            copy(in, out);
        }
    }

    /**
     * Restaura a partir de um ficheiro. Valida que é uma base de dados PastelGest
     * da mesma versão antes de substituir. Os cursores de sincronização são
     * reiniciados para voltar a reconciliar com o servidor sem perder dados.
     */
    public static void restore(Context c, Uri source) throws IOException {
        File tmp = new File(c.getCacheDir(), "restore_tmp.db");
        try (InputStream in = c.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("Não foi possível ler o ficheiro.");
            copy(in, out);
        }
        validate(tmp);
        AppDatabase.closeInstance();
        File db = c.getDatabasePath(AppDatabase.NAME);
        new File(db.getPath() + "-wal").delete();
        new File(db.getPath() + "-shm").delete();
        try (InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(db)) {
            copy(in, out);
        }
        tmp.delete();
        android.content.SharedPreferences.Editor ed = Prefs.get(c).edit();
        for (Map.Entry<String, ?> e : Prefs.get(c).getAll().entrySet()) {
            if (e.getKey().startsWith(Prefs.PULL_CURSOR_PREFIX)) ed.remove(e.getKey());
        }
        ed.apply();
        AppDatabase.get(c);
    }

    private static void validate(File f) {
        SQLiteDatabase sql = null;
        try {
            sql = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            int version = sql.getVersion();
            if (version != AppDatabase.VERSION) {
                throw new BusinessException("Cópia de versão incompatível (" + version + "). Esperada: " + AppDatabase.VERSION + ".");
            }
            try (Cursor c = sql.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('ingredients','sales','payments','audit_log')", null)) {
                if (!c.moveToFirst() || c.getInt(0) != 4) throw new BusinessException("O ficheiro não é uma cópia de segurança do PastelGest.");
            }
            try (Cursor c = sql.rawQuery("PRAGMA integrity_check", null)) {
                if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) throw new BusinessException("A cópia de segurança está danificada.");
            }
        } catch (android.database.sqlite.SQLiteException e) {
            throw new BusinessException("O ficheiro não é uma base de dados válida.");
        } finally {
            if (sql != null) sql.close();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }
}
