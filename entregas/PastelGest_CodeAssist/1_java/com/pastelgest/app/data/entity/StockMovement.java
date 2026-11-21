package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Movimentação de stock de ingrediente (registo imutável). */
@Entity(tableName = "stock_movements", indices = {@Index("ingredientId"), @Index("date"), @Index("type"), @Index({"refType", "refId"})}, foreignKeys = {@ForeignKey(entity = Ingredient.class, parentColumns = "id", childColumns = "ingredientId")})
public class StockMovement extends SyncEntity {
    public static final String INITIAL = "INICIAL";
    public static final String PURCHASE = "COMPRA";
    public static final String PURCHASE_REVERSAL = "ESTORNO_COMPRA";
    public static final String PRODUCTION = "PRODUCAO";
    public static final String WASTE = "DESPERDICIO";
    public static final String ADJUST = "AJUSTE";

    public String ingredientId;
    public String type;
    /** Quantidade com sinal (entrada > 0, saída < 0). */
    public long qtyMilli;
    /** Valor com sinal, em cêntimos. */
    public long valueCents;
    public String refType;
    public String refId;
    public String note;
    public long date;
}
