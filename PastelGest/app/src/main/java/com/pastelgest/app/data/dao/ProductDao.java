package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.model.ProductMovementRow;

import java.util.List;

@Dao
public interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Product p);

    @Update
    void update(Product p);

    @Query("SELECT * FROM products WHERE id = :id")
    Product get(String id);

    @Query("SELECT * FROM products WHERE deleted = 0 AND (:q = '' OR name LIKE '%' || :q || '%') ORDER BY name COLLATE NOCASE")
    List<Product> search(String q);

    @Query("SELECT * FROM products WHERE deleted = 0 AND stockQty > 0 ORDER BY name COLLATE NOCASE")
    List<Product> available();

    @Query("SELECT * FROM products WHERE deleted = 0 AND stockQty <= minStock ORDER BY name COLLATE NOCASE")
    List<Product> lowStock();

    @Query("SELECT COUNT(*) FROM products WHERE deleted = 0 AND LOWER(name) = LOWER(:name) AND id <> :excludeId")
    int countByName(String name, String excludeId);

    @Query("SELECT COALESCE(SUM(stockValueCents), 0) FROM products WHERE deleted = 0")
    long totalStockValue();

    @Query("SELECT COALESCE(SUM(stockQty), 0) FROM products WHERE deleted = 0")
    long totalUnits();

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertMovement(ProductMovement m);

    @Query("SELECT m.*, p.name AS productName FROM product_movements m JOIN products p ON p.id = m.productId WHERE m.productId = :productId ORDER BY m.date DESC, m.createdAt DESC")
    List<ProductMovementRow> movements(String productId);

    @Query("SELECT m.*, p.name AS productName FROM product_movements m JOIN products p ON p.id = m.productId WHERE m.type = :type AND m.date >= :from AND m.date < :to ORDER BY m.date DESC")
    List<ProductMovementRow> movementsOfType(String type, long from, long to);

    @Query("SELECT COALESCE(-SUM(valueCents), 0) FROM product_movements WHERE type = :type AND date >= :from AND date < :to")
    long outflowValue(String type, long from, long to);

    @Query("UPDATE products SET stockQty = COALESCE((SELECT SUM(qty) FROM product_movements m WHERE m.productId = products.id), 0), stockValueCents = COALESCE((SELECT SUM(valueCents) FROM product_movements m WHERE m.productId = products.id), 0)")
    void recomputeAllStock();
}
