package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Registo local de cada execução de sincronização (não é sincronizado). */
@Entity(tableName = "sync_log")
public class SyncLog {
    @PrimaryKey(autoGenerate = true)
    public long localId;
    public long startedAt;
    public long finishedAt;
    public int pushed;
    public int pulled;
    /** OK / ERRO / IGNORADO */
    public String status;
    public String message;
}
