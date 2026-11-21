package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Custo histórico de uma receita num dado momento (não muda com novos preços). */
@Entity(tableName = "recipe_cost_snapshots", indices = {@Index("recipeId"), @Index("date")}, foreignKeys = {@ForeignKey(entity = Recipe.class, parentColumns = "id", childColumns = "recipeId")})
public class RecipeCostSnapshot extends SyncEntity {
    public String recipeId;
    public long date;
    public long yieldQty;
    public long ingredientsCostCents;
    public long packagingCostCents;
    public long otherCostCents;
    public long totalCostCents;
    public long unitCostCents;
    /** Detalhe por ingrediente (JSON). */
    public String detailsJson;
    public String reason;
}
