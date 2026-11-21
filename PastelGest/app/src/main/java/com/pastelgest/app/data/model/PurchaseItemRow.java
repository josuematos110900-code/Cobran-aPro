package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.PurchaseItem;

/** Linha de compra com dados do ingrediente. */
public class PurchaseItemRow {
    @Embedded
    public PurchaseItem item;
    public String ingredientName;
    public int unitType;
}
