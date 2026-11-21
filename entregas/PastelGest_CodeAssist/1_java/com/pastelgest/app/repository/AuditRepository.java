package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.data.entity.AuditLog;

import java.util.List;

public class AuditRepository extends BaseRepository {
    public AuditRepository(Context c) {
        super(c);
    }

    public List<AuditLog> recent(String entity, int limit) {
        return db.auditDao().recent(entity == null ? "" : entity, limit);
    }

    public List<AuditLog> forEntity(String id) {
        return db.auditDao().forEntity(id);
    }
}
