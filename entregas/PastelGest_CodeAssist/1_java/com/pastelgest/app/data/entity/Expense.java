package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

/** Despesa operacional. */
@Entity(tableName = "expenses", indices = {@Index("date"), @Index("category")})
public class Expense extends SyncEntity {
    public static final String ACTIVE = "ACTIVA";
    public static final String CANCELLED = "ANULADA";

    public String category;
    public String description;
    public long amountCents;
    public long date;
    public String paymentMethod;
    /** 1 = despesa fixa, 0 = variável. */
    public int fixed;
    public String receiptPath;
    public String status;
    public String cancelReason;
}
