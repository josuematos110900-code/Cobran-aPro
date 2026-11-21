package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.Sale;

/** Venda com nome do cliente. */
public class SaleRow {
    @Embedded
    public Sale sale;
    public String clientName;
}
