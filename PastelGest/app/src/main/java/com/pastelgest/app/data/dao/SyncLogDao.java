package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.pastelgest.app.data.entity.SyncLog;

import java.util.List;

@Dao
public interface SyncLogDao {
    @Insert
    long insert(SyncLog log);

    @Query("SELECT * FROM sync_log ORDER BY startedAt DESC LIMIT :limit")
    List<SyncLog> recent(int limit);

    @Query("DELETE FROM sync_log WHERE localId NOT IN (SELECT localId FROM sync_log ORDER BY startedAt DESC LIMIT 200)")
    void trim();
}
