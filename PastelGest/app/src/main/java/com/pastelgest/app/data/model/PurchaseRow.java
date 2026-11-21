package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.Purchase;

/** Compra com nome do fornecedor. */
public class PurchaseRow {
    @Embedded
    public Purchase purchase;
    public String supplierName;
}
