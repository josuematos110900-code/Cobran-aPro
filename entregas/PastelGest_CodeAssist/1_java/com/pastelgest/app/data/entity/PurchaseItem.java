package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Linha de compra. */
@Entity(tableName = "purchase_items", indices = {@Index("purchaseId"), @Index("ingredientId")}, foreignKeys = {@ForeignKey(entity = Purchase.class, parentColumns = "id", childColumns = "purchaseId"), @ForeignKey(entity = Ingredient.class, parentColumns = "id", childColumns = "ingredientId")})
public class PurchaseItem extends SyncEntity {
    public String purchaseId;
    public String ingredientId;
    public String enteredUnit;
    public long qtyMilli;
    /** Preço por unidade introduzida (ex.: por kg). */
    public long unitPriceCents;
    public long lineDiscountCents;
    /** quantidade x preço - desconto da linha. */
    public long lineTotalCents;
    /** Valor final após repartição do desconto global (entra no stock). */
    public long allocatedTotalCents;
}
