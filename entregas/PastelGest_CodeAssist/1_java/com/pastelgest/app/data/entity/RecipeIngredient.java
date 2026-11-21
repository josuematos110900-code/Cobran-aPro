package com.pastelgest.app.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Ingrediente de uma receita. */
@Entity(tableName = "recipe_ingredients", indices = {@Index("recipeId"), @Index("ingredientId")}, foreignKeys = {@ForeignKey(entity = Recipe.class, parentColumns = "id", childColumns = "recipeId"), @ForeignKey(entity = Ingredient.class, parentColumns = "id", childColumns = "ingredientId")})
public class RecipeIngredient extends SyncEntity {
    public String recipeId;
    public String ingredientId;
    public long qtyMilli;
    public String enteredUnit;
}
