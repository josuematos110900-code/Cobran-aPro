package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Movimentação de stock de produto acabado (registo imutável). */
@Entity(tableName = "product_movements", indices = {@Index("productId"), @Index("date"), @Index("type"), @Index({"refType", "refId"})}, foreignKeys = {@ForeignKey(entity = Product.class, parentColumns = "id", childColumns = "productId")})
public class ProductMovement extends SyncEntity {
    public static final String PRODUCTION = "PRODUCAO";
    public static final String SALE = "VENDA";
    public static final String SALE_CANCEL = "ANULACAO_VENDA";
    public static final String WASTE = "DESPERDICIO";
    public static final String ADJUST = "AJUSTE";

    public String productId;
    public String type;
    public long qty;
    public long valueCents;
    public String refType;
    public String refId;
    public String note;
    public long date;
}
