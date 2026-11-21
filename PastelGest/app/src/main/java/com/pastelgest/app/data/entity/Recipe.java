package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

/** Receita de um pastel. */
@Entity(tableName = "recipes", indices = {@Index("name"), @Index("productId")})
public class Recipe extends SyncEntity {
    public String name;
    public String description;
    public String photoPath;
    /** Número de pastéis produzidos por lote. */
    public long yieldQty;
    /** Custo de embalagem por pastel. */
    public long packagingCostCents;
    /** Outros custos por lote (gás, energia, mão-de-obra...). */
    public long otherCostCents;
    public long suggestedPriceCents;
    /** Produto acabado gerado por esta receita. */
    public String productId;
    /** Último custo calculado por pastel (cache). */
    public long lastUnitCostCents;
}
