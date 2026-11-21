package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.pastelgest.app.data.entity.AuditLog;

import java.util.List;

@Dao
public interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(AuditLog a);

    @Query("SELECT * FROM audit_log WHERE (:entity = '' OR entity = :entity) ORDER BY date DESC LIMIT :limit")
    List<AuditLog> recent(String entity, int limit);

    @Query("SELECT * FROM audit_log WHERE entityId = :entityId ORDER BY date DESC")
    List<AuditLog> forEntity(String entityId);
}
