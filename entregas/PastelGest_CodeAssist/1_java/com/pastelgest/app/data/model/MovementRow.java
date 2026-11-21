package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.StockMovement;

/** Movimentação de stock com dados do ingrediente. */
public class MovementRow {
    @Embedded
    public StockMovement m;
    public String ingredientName;
    public String displayUnit;
}
