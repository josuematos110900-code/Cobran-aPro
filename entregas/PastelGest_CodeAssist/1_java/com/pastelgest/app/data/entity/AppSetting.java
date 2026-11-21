package com.pastelgest.app.data.entity;

import androidx.room.Entity;

/** Configuração partilhada (sincronizada). A chave é o id. */
@Entity(tableName = "app_settings")
public class AppSetting extends SyncEntity {
    public String value;
}
