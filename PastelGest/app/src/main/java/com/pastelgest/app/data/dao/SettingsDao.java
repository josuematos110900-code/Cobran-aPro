package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.pastelgest.app.data.entity.AppSetting;

import java.util.List;

@Dao
public interface SettingsDao {
    @Upsert
    void upsert(AppSetting s);

    @Query("SELECT * FROM app_settings WHERE id = :key")
    AppSetting get(String key);

    @Query("SELECT * FROM app_settings")
    List<AppSetting> all();
}
