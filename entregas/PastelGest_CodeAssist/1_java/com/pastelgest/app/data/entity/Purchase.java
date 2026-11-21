package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Compra de ingredientes. */
@Entity(tableName = "purchases", indices = {@Index("supplierId"), @Index("date"), @Index("correctsPurchaseId")}, foreignKeys = {@ForeignKey(entity = Supplier.class, parentColumns = "id", childColumns = "supplierId")})
public class Purchase extends SyncEntity {
    public static final String ACTIVE = "ACTIVA";
    public static final String CANCELLED = "ANULADA";
    public static final String PAID = "PAGA";
    public static final String PENDING = "PENDENTE";
    public static final String PARTIAL = "PARCIAL";

    public String number;
    public String supplierId;
    public long date;
    public long subtotalCents;
    public long discountCents;
    public long totalCents;
    /** Cache: soma de purchase_payments. */
    public long paidCents;
    public String paymentMethod;
    public String paymentStatus;
    public String status;
    /** Se esta compra corrige outra, id da compra original (anulada). */
    public String correctsPurchaseId;
    public String cancelReason;
    public String notes;
}
