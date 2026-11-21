package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

/** Fornecedor de ingredientes. */
@Entity(tableName = "suppliers", indices = {@Index("name")})
public class Supplier extends SyncEntity {
    public String name;
    public String phone;
    public String email;
    public String address;
    public String nif;
    public String notes;
}
