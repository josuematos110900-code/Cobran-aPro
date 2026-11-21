package com.pastelgest.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Campos comuns a todas as tabelas sincronizadas com o Supabase.
 * A chave primária é um UUID gerado no dispositivo: o mesmo registo tem o
 * mesmo identificador local e remoto, o que evita duplicações na sincronização.
 */
public abstract class SyncEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    public long createdAt;
    public long updatedAt;
    /** 1 = eliminado logicamente (o histórico é preservado). */
    public int deleted;
    /** 1 = alterado localmente e ainda não enviado ao servidor. */
    public int syncState;

    /** Inicializa um novo registo. */
    public void initNew() {
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        createdAt = now;
        updatedAt = now;
        syncState = 1;
    }

    /** Marca o registo como alterado. */
    public void touch() {
        long now = System.currentTimeMillis();
        updatedAt = Math.max(now, updatedAt + 1);
        syncState = 1;
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
