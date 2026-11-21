package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Linha de venda. */
@Entity(tableName = "sale_items", indices = {@Index("saleId"), @Index("productId")}, foreignKeys = {@ForeignKey(entity = Sale.class, parentColumns = "id", childColumns = "saleId"), @ForeignKey(entity = Product.class, parentColumns = "id", childColumns = "productId")})
public class SaleItem extends SyncEntity {
    public String saleId;
    public String productId;
    public String productName;
    public long qty;
    public long unitPriceCents;
    public long discountCents;
    public long lineTotalCents;
    public long cogsCents;
}
