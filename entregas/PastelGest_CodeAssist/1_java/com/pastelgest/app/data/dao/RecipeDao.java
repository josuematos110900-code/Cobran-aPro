package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.data.entity.RecipeCostSnapshot;
import com.pastelgest.app.data.entity.RecipeIngredient;
import com.pastelgest.app.data.model.RecipeIngredientRow;

import java.util.List;

@Dao
public interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Recipe r);

    @Update
    void update(Recipe r);

    @Query("SELECT * FROM recipes WHERE id = :id")
    Recipe get(String id);

    @Query("SELECT * FROM recipes WHERE deleted = 0 AND (:q = '' OR name LIKE '%' || :q || '%') ORDER BY name COLLATE NOCASE")
    List<Recipe> search(String q);

    @Query("SELECT COUNT(*) FROM recipes WHERE deleted = 0 AND LOWER(name) = LOWER(:name) AND id <> :excludeId")
    int countByName(String name, String excludeId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertIngredient(RecipeIngredient ri);

    @Update
    void updateIngredient(RecipeIngredient ri);

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId AND deleted = 0")
    List<RecipeIngredient> ingredients(String recipeId);

    @Query("SELECT ri.*, i.name AS ingredientName, i.unitType AS unitType, i.displayUnit AS displayUnit, i.stockQtyMilli AS stockQtyMilli, i.stockValueCents AS stockValueCents, i.lastPurchaseCostCents AS lastPurchaseCostCents, i.lastPurchaseQtyMilli AS lastPurchaseQtyMilli, i.deleted AS ingredientDeleted FROM recipe_ingredients ri JOIN ingredients i ON i.id = ri.ingredientId WHERE ri.recipeId = :recipeId AND ri.deleted = 0 ORDER BY i.name")
    List<RecipeIngredientRow> ingredientRows(String recipeId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertSnapshot(RecipeCostSnapshot s);

    @Query("SELECT * FROM recipe_cost_snapshots WHERE id = :id")
    RecipeCostSnapshot getSnapshot(String id);

    @Query("SELECT * FROM recipe_cost_snapshots WHERE recipeId = :recipeId ORDER BY date DESC")
    List<RecipeCostSnapshot> snapshots(String recipeId);

    @Query("SELECT COUNT(*) FROM recipes WHERE deleted = 0 AND id IN (SELECT recipeId FROM recipe_ingredients WHERE ingredientId = :ingredientId AND deleted = 0)")
    int countRecipesUsing(String ingredientId);
}
