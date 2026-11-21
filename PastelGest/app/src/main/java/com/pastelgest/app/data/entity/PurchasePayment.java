package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Pagamento a fornecedor relativo a uma compra (registo imutável). */
@Entity(tableName = "purchase_payments", indices = {@Index("purchaseId"), @Index("date")}, foreignKeys = {@ForeignKey(entity = Purchase.class, parentColumns = "id", childColumns = "purchaseId")})
public class PurchasePayment extends SyncEntity {
    public String purchaseId;
    public long amountCents;
    public String method;
    public long date;
    public String note;
}
