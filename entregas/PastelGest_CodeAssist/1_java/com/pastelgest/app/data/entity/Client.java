package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

/** Cliente. */
@Entity(tableName = "clients", indices = {@Index("name"), @Index("phone")})
public class Client extends SyncEntity {
    public String name;
    public String phone;
    public String address;
    public String notes;
}
