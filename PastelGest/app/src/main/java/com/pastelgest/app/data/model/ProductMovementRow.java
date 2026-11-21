package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.ProductMovement;

/** Movimentação de produto com nome. */
public class ProductMovementRow {
    @Embedded
    public ProductMovement m;
    public String productName;
}
