package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.ProductionItem;

/** Ingrediente consumido com nome. */
public class ProductionItemRow {
    @Embedded
    public ProductionItem item;
    public String ingredientName;
    public String displayUnit;
}
