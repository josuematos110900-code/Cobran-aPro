package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.data.AppDatabase;
import com.pastelgest.app.data.entity.AuditLog;
import com.pastelgest.app.sync.SyncScheduler;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.Prefs;

import java.util.concurrent.Callable;

/** Base dos repositórios: acesso à BD, transacções, auditoria e pedido de sincronização. */
public abstract class BaseRepository {
    protected final Context ctx;
    protected final AppDatabase db;

    protected BaseRepository(Context context) {
        this.ctx = context.getApplicationContext();
        this.db = AppDatabase.get(ctx);
    }

    /** Executa numa transacção. Qualquer excepção desfaz todas as alterações. */
    protected <T> T tx(Callable<T> work) {
        T result = db.runInTransaction(work);
        SyncScheduler.requestSync(ctx);
        return result;
    }

    protected void audit(String entity, String entityId, String action, String details) {
        AuditLog a = new AuditLog();
        a.initNew();
        a.entity = entity;
        a.entityId = entityId;
        a.action = action;
        a.details = details;
        a.device = Prefs.deviceId(ctx);
        a.date = System.currentTimeMillis();
        db.auditDao().insert(a);
    }

    /** Número legível de documento: PREFIXO-AAMMDD-DISPOSITIVO-NNN. */
    protected String documentNumber(String prefix, long date, int countToday) {
        String dev = Prefs.deviceId(ctx).substring(0, 4);
        return String.format(Fmt.PT, "%s-%s-%s-%03d", prefix, Fmt.stamp(date), dev, countToday + 1);
    }

    protected static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new com.pastelgest.app.core.BusinessException(message);
        }
        return value.trim();
    }

    protected static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
