package com.pastelgest.app.data.model;

import androidx.room.Embedded;
import com.pastelgest.app.data.entity.RecipeIngredient;

/** Ingrediente de receita com stock e custo actual. */
public class RecipeIngredientRow {
    @Embedded
    public RecipeIngredient ri;
    public String ingredientName;
    public int unitType;
    public String displayUnit;
    public long stockQtyMilli;
    public long stockValueCents;
    public long lastPurchaseCostCents;
    public long lastPurchaseQtyMilli;
    public int ingredientDeleted;
}
