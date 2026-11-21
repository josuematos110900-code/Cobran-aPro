package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Venda. */
@Entity(tableName = "sales", indices = {@Index("clientId"), @Index("date"), @Index("status")}, foreignKeys = {@ForeignKey(entity = Client.class, parentColumns = "id", childColumns = "clientId")})
public class Sale extends SyncEntity {
    public static final String ACTIVE = "ACTIVA";
    public static final String CANCELLED = "ANULADA";
    public static final String TYPE_PAID = "PAGA";
    public static final String TYPE_CREDIT = "FIADA";

    public String number;
    public String clientId;
    public long date;
    public long subtotalCents;
    public long discountCents;
    public long totalCents;
    /** Custo dos produtos vendidos (custo histórico de produção). */
    public long cogsCents;
    /** Cache: valor recebido (pagamentos não estornados). */
    public long paidCents;
    public String paymentMethod;
    public String paymentType;
    public String status;
    public String cancelReason;
    public long cancelledAt;
    public String notes;
}
