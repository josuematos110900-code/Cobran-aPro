package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Supplier;

import java.util.List;

@Dao
public interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Supplier s);

    @Update
    void update(Supplier s);

    @Query("SELECT * FROM suppliers WHERE id = :id")
    Supplier get(String id);

    @Query("SELECT * FROM suppliers WHERE deleted = 0 AND (:q = '' OR name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%') ORDER BY name COLLATE NOCASE")
    List<Supplier> search(String q);

    @Query("SELECT COUNT(*) FROM suppliers WHERE deleted = 0 AND LOWER(name) = LOWER(:name) AND id <> :excludeId")
    int countByName(String name, String excludeId);
}
