package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Production;
import com.pastelgest.app.data.entity.ProductionItem;
import com.pastelgest.app.data.model.ProductionItemRow;
import com.pastelgest.app.data.model.ProductionRow;

import java.util.List;

@Dao
public interface ProductionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Production p);

    @Update
    void update(Production p);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertItem(ProductionItem item);

    @Query("SELECT * FROM productions WHERE id = :id")
    Production get(String id);

    @Query("SELECT p.*, r.name AS recipeName FROM productions p JOIN recipes r ON r.id = p.recipeId WHERE p.deleted = 0 AND p.date >= :from AND p.date < :to ORDER BY p.date DESC, p.createdAt DESC")
    List<ProductionRow> list(long from, long to);

    @Query("SELECT p.*, r.name AS recipeName FROM productions p JOIN recipes r ON r.id = p.recipeId WHERE p.id = :id")
    ProductionRow getRow(String id);

    @Query("SELECT pi.*, i.name AS ingredientName, i.displayUnit AS displayUnit FROM production_items pi JOIN ingredients i ON i.id = pi.ingredientId WHERE pi.productionId = :productionId ORDER BY i.name")
    List<ProductionItemRow> items(String productionId);

    @Query("SELECT COALESCE(SUM(goodQty), 0) FROM productions WHERE deleted = 0 AND date >= :from AND date < :to")
    long producedBetween(long from, long to);

    @Query("SELECT COALESCE(SUM(lostQty), 0) FROM productions WHERE deleted = 0 AND date >= :from AND date < :to")
    long lostBetween(long from, long to);

    @Query("SELECT COALESCE(SUM(totalCostCents), 0) FROM productions WHERE deleted = 0 AND date >= :from AND date < :to")
    long costBetween(long from, long to);

    @Query("SELECT COUNT(*) FROM productions WHERE date >= :from AND date < :to")
    int countBetween(long from, long to);
}
