package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.Client;

/** Cliente com saldo em dívida. */
public class ClientBalance {
    @Embedded
    public Client client;
    public long balanceCents;
}
