package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.Payment;

/** Pagamento com nome do cliente. */
public class PaymentRow {
    @Embedded
    public Payment payment;
    public String clientName;
    public String saleNumber;
}
