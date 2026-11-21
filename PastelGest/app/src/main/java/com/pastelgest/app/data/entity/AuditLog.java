package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

/** Histórico de auditoria (registo imutável). */
@Entity(tableName = "audit_log", indices = {@Index("entity"), @Index("entityId"), @Index("date")})
public class AuditLog extends SyncEntity {
    public String entity;
    public String entityId;
    public String action;
    public String details;
    public String device;
    public long date;
}
