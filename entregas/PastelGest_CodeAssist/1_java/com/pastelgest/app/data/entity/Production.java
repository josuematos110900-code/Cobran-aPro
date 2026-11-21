package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Ordem de produção confirmada. */
@Entity(tableName = "productions", indices = {@Index("recipeId"), @Index("productId"), @Index("date")}, foreignKeys = {@ForeignKey(entity = Recipe.class, parentColumns = "id", childColumns = "recipeId"), @ForeignKey(entity = Product.class, parentColumns = "id", childColumns = "productId")})
public class Production extends SyncEntity {
    public String number;
    public String recipeId;
    public String productId;
    public long date;
    public long plannedQty;
    public long goodQty;
    public long lostQty;
    public long ingredientsCostCents;
    public long packagingCostCents;
    public long otherCostCents;
    public long totalCostCents;
    public long unitCostCents;
    /** Id do snapshot de custo da receita no momento da produção. */
    public String costSnapshotId;
    public String notes;
}
