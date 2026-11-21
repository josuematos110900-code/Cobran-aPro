package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Ingrediente consumido numa produção. */
@Entity(tableName = "production_items", indices = {@Index("productionId"), @Index("ingredientId")}, foreignKeys = {@ForeignKey(entity = Production.class, parentColumns = "id", childColumns = "productionId"), @ForeignKey(entity = Ingredient.class, parentColumns = "id", childColumns = "ingredientId")})
public class ProductionItem extends SyncEntity {
    public String productionId;
    public String ingredientId;
    public long qtyMilli;
    public long valueCents;
}
