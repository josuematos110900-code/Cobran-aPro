package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Recebimento de cliente (registo imutável; estornos são novos registos negativos). */
@Entity(tableName = "payments", indices = {@Index("clientId"), @Index("saleId"), @Index("debtId"), @Index("date")}, foreignKeys = {@ForeignKey(entity = Client.class, parentColumns = "id", childColumns = "clientId"), @ForeignKey(entity = Sale.class, parentColumns = "id", childColumns = "saleId"), @ForeignKey(entity = Debt.class, parentColumns = "id", childColumns = "debtId")})
public class Payment extends SyncEntity {
    public static final String KIND_SALE = "VENDA";
    public static final String KIND_DEBT = "DIVIDA";
    public static final String KIND_REVERSAL = "ESTORNO";

    public String clientId;
    public String saleId;
    public String debtId;
    /** Valor com sinal: estornos são negativos. */
    public long amountCents;
    public String method;
    public String kind;
    public String receiptNumber;
    public long date;
    public String note;
}
