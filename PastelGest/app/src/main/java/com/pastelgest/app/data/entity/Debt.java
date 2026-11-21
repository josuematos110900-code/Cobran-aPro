package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Conta a receber gerada por uma venda fiada. */
@Entity(tableName = "debts", indices = {@Index("clientId"), @Index(value = "saleId", unique = true), @Index("status")}, foreignKeys = {@ForeignKey(entity = Client.class, parentColumns = "id", childColumns = "clientId"), @ForeignKey(entity = Sale.class, parentColumns = "id", childColumns = "saleId")})
public class Debt extends SyncEntity {
    public static final String OPEN = "ABERTA";
    public static final String PAID = "PAGA";
    public static final String CANCELLED = "ANULADA";

    public String clientId;
    public String saleId;
    public long originalCents;
    /** Cache: pago (pagamentos não estornados). */
    public long paidCents;
    /** Cache: original - pago. */
    public long balanceCents;
    public long dueDate;
    public String status;
}
