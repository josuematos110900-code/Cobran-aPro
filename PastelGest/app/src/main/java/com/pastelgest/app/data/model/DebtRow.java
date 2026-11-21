package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.Debt;

/** Dívida com dados do cliente e da venda. */
public class DebtRow {
    @Embedded
    public Debt debt;
    public String clientName;
    public String clientPhone;
    public String saleNumber;
    public long saleDate;
}
