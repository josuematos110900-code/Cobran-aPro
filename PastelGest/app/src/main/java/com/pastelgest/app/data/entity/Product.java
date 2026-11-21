package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

/** Produto acabado (pastel pronto a vender). */
@Entity(tableName = "products", indices = {@Index("name"), @Index("recipeId")})
public class Product extends SyncEntity {
    public String name;
    public String recipeId;
    public long salePriceCents;
    /** Cache: unidades em stock. */
    public long stockQty;
    /** Cache: valor do stock ao custo de produção. */
    public long stockValueCents;
    public long minStock;
}
