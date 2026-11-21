package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.Production;

/** Produção com nome da receita. */
public class ProductionRow {
    @Embedded
    public Production production;
    public String recipeName;
}
