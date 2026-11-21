package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.StockMovement;
import com.pastelgest.app.data.model.MovementRow;
import com.pastelgest.app.data.model.NamedTotal;

import java.util.List;

@Dao
public interface IngredientDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Ingredient i);

    @Update
    void update(Ingredient i);

    @Query("SELECT * FROM ingredients WHERE id = :id")
    Ingredient get(String id);

    @Query("SELECT * FROM ingredients WHERE deleted = 0 AND (:q = '' OR name LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') ORDER BY name COLLATE NOCASE")
    List<Ingredient> search(String q);

    @Query("SELECT * FROM ingredients WHERE deleted = 0 AND stockQtyMilli <= minStockMilli ORDER BY name COLLATE NOCASE")
    List<Ingredient> lowStock();

    @Query("SELECT COUNT(*) FROM ingredients WHERE deleted = 0 AND stockQtyMilli > 0")
    int countInStock();

    @Query("SELECT COUNT(*) FROM ingredients WHERE deleted = 0")
    int countActive();

    @Query("SELECT COALESCE(SUM(stockValueCents), 0) FROM ingredients WHERE deleted = 0")
    long totalStockValue();

    @Query("SELECT COUNT(*) FROM ingredients WHERE deleted = 0 AND LOWER(name) = LOWER(:name) AND id <> :excludeId")
    int countByName(String name, String excludeId);

    @Query("SELECT DISTINCT category FROM ingredients WHERE deleted = 0 AND category IS NOT NULL AND category <> '' ORDER BY category")
    List<String> categories();

    // ---- Movimentações ----
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertMovement(StockMovement m);

    @Query("SELECT m.*, i.name AS ingredientName, i.displayUnit AS displayUnit FROM stock_movements m JOIN ingredients i ON i.id = m.ingredientId WHERE m.ingredientId = :ingredientId ORDER BY m.date DESC, m.createdAt DESC")
    List<MovementRow> movements(String ingredientId);

    @Query("SELECT m.*, i.name AS ingredientName, i.displayUnit AS displayUnit FROM stock_movements m JOIN ingredients i ON i.id = m.ingredientId WHERE m.date >= :from AND m.date < :to ORDER BY m.date DESC")
    List<MovementRow> movementsBetween(long from, long to);

    @Query("SELECT m.*, i.name AS ingredientName, i.displayUnit AS displayUnit FROM stock_movements m JOIN ingredients i ON i.id = m.ingredientId WHERE m.type = :type AND m.date >= :from AND m.date < :to ORDER BY m.date DESC")
    List<MovementRow> movementsOfType(String type, long from, long to);

    @Query("SELECT COUNT(*) FROM stock_movements WHERE ingredientId = :ingredientId")
    int countMovements(String ingredientId);

    /** Valor (positivo) das saídas de um tipo num período. */
    @Query("SELECT COALESCE(-SUM(valueCents), 0) FROM stock_movements WHERE type = :type AND date >= :from AND date < :to")
    long outflowValue(String type, long from, long to);

    @Query("SELECT i.id AS id, i.name AS name, -SUM(m.qtyMilli) AS qty, -SUM(m.valueCents) AS total, i.displayUnit AS unit, i.unitType AS unitType FROM stock_movements m JOIN ingredients i ON i.id = m.ingredientId WHERE m.type = 'PRODUCAO' AND m.date >= :from AND m.date < :to GROUP BY i.id ORDER BY total DESC LIMIT :limit")
    List<NamedTotal> mostUsed(long from, long to, int limit);

    /** Recalcula as caches de stock a partir das movimentações (após sincronização). */
    @Query("UPDATE ingredients SET stockQtyMilli = COALESCE((SELECT SUM(qtyMilli) FROM stock_movements m WHERE m.ingredientId = ingredients.id), 0), stockValueCents = COALESCE((SELECT SUM(valueCents) FROM stock_movements m WHERE m.ingredientId = ingredients.id), 0)")
    void recomputeAllStock();
}
