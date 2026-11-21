package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Ingrediente. Stock em milésimas da unidade base (ver core.Units). */
@Entity(tableName = "ingredients", indices = {@Index("name"), @Index("supplierId")}, foreignKeys = {@ForeignKey(entity = Supplier.class, parentColumns = "id", childColumns = "supplierId")})
public class Ingredient extends SyncEntity {
    public String name;
    public String category;
    /** Units.TYPE_MASS / TYPE_VOLUME / TYPE_UNIT */
    public int unitType;
    /** Unidade preferida para apresentação (kg, g, l, ml, un). */
    public String displayUnit;
    /** Cache: soma das movimentações de stock (milésimas base). */
    public long stockQtyMilli;
    /** Cache: valor do stock em cêntimos (custo médio ponderado). */
    public long stockValueCents;
    public long minStockMilli;
    /** Última compra: valor e quantidade (para preço de referência). */
    public long lastPurchaseCostCents;
    public long lastPurchaseQtyMilli;
    public long lastPurchaseDate;
    public String supplierId;
    public String notes;
}
