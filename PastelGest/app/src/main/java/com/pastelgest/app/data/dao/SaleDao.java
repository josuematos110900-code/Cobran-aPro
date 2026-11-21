package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.entity.SaleItem;
import com.pastelgest.app.data.model.NamedTotal;
import com.pastelgest.app.data.model.SaleRow;

import java.util.List;

@Dao
public interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Sale s);

    @Update
    void update(Sale s);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertItem(SaleItem item);

    @Query("SELECT * FROM sales WHERE id = :id")
    Sale get(String id);

    @Query("SELECT s.*, c.name AS clientName FROM sales s LEFT JOIN clients c ON c.id = s.clientId WHERE s.id = :id")
    SaleRow getRow(String id);

    @Query("SELECT s.*, c.name AS clientName FROM sales s LEFT JOIN clients c ON c.id = s.clientId WHERE s.deleted = 0 AND s.date >= :from AND s.date < :to AND (:q = '' OR s.number LIKE '%' || :q || '%' OR c.name LIKE '%' || :q || '%') ORDER BY s.date DESC, s.createdAt DESC")
    List<SaleRow> list(long from, long to, String q);

    @Query("SELECT s.*, c.name AS clientName FROM sales s LEFT JOIN clients c ON c.id = s.clientId WHERE s.clientId = :clientId ORDER BY s.date DESC")
    List<SaleRow> byClient(String clientId);

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    List<SaleItem> items(String saleId);

    @Query("SELECT COUNT(*) FROM sales WHERE date >= :from AND date < :to")
    int countBetween(long from, long to);

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM sales WHERE deleted = 0 AND status = 'ACTIVA' AND date >= :from AND date < :to")
    long revenue(long from, long to);

    @Query("SELECT COALESCE(SUM(cogsCents), 0) FROM sales WHERE deleted = 0 AND status = 'ACTIVA' AND date >= :from AND date < :to")
    long cogs(long from, long to);

    @Query("SELECT COALESCE(SUM(discountCents), 0) FROM sales WHERE deleted = 0 AND status = 'ACTIVA' AND date >= :from AND date < :to")
    long discounts(long from, long to);

    @Query("SELECT COUNT(*) FROM sales WHERE deleted = 0 AND status = 'ACTIVA' AND date >= :from AND date < :to")
    long activeCount(long from, long to);

    @Query("SELECT COALESCE(SUM(si.qty), 0) FROM sale_items si JOIN sales s ON s.id = si.saleId WHERE s.deleted = 0 AND s.status = 'ACTIVA' AND s.date >= :from AND s.date < :to")
    long unitsSold(long from, long to);

    @Query("SELECT si.productId AS id, si.productName AS name, SUM(si.qty) AS qty, SUM(si.lineTotalCents) AS total, 'un' AS unit, 3 AS unitType FROM sale_items si JOIN sales s ON s.id = si.saleId WHERE s.deleted = 0 AND s.status = 'ACTIVA' AND s.date >= :from AND s.date < :to GROUP BY si.productId ORDER BY qty DESC LIMIT :limit")
    List<NamedTotal> topProducts(long from, long to, int limit);

    @Query("UPDATE sales SET paidCents = COALESCE((SELECT SUM(amountCents) FROM payments p WHERE p.saleId = sales.id), 0)")
    void recomputePaid();
}
