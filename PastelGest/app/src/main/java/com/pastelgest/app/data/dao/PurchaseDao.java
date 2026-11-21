package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Purchase;
import com.pastelgest.app.data.entity.PurchaseItem;
import com.pastelgest.app.data.entity.PurchasePayment;
import com.pastelgest.app.data.model.PurchaseItemRow;
import com.pastelgest.app.data.model.PurchaseRow;

import java.util.List;

@Dao
public interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Purchase p);

    @Update
    void update(Purchase p);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertItem(PurchaseItem item);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPayment(PurchasePayment p);

    @Query("SELECT * FROM purchases WHERE id = :id")
    Purchase get(String id);

    @Query("SELECT p.*, s.name AS supplierName FROM purchases p LEFT JOIN suppliers s ON s.id = p.supplierId WHERE p.deleted = 0 AND (:status = '' OR p.paymentStatus = :status) AND p.date >= :from AND p.date < :to ORDER BY p.date DESC, p.createdAt DESC")
    List<PurchaseRow> list(String status, long from, long to);

    @Query("SELECT p.*, s.name AS supplierName FROM purchases p LEFT JOIN suppliers s ON s.id = p.supplierId WHERE p.id = :id")
    PurchaseRow getRow(String id);

    @Query("SELECT pi.*, i.name AS ingredientName, i.unitType AS unitType FROM purchase_items pi JOIN ingredients i ON i.id = pi.ingredientId WHERE pi.purchaseId = :purchaseId ORDER BY i.name")
    List<PurchaseItemRow> items(String purchaseId);

    @Query("SELECT * FROM purchase_payments WHERE purchaseId = :purchaseId ORDER BY date DESC")
    List<PurchasePayment> payments(String purchaseId);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM purchase_payments WHERE purchaseId = :purchaseId")
    long paidTotal(String purchaseId);

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM purchases WHERE deleted = 0 AND status = 'ACTIVA' AND date >= :from AND date < :to")
    long totalBetween(long from, long to);

    @Query("SELECT COALESCE(SUM(totalCents - paidCents), 0) FROM purchases WHERE deleted = 0 AND status = 'ACTIVA' AND totalCents > paidCents")
    long totalPayable();

    @Query("SELECT COUNT(*) FROM purchases WHERE date >= :from AND date < :to")
    int countBetween(long from, long to);

    @Query("UPDATE purchases SET paidCents = COALESCE((SELECT SUM(amountCents) FROM purchase_payments pp WHERE pp.purchaseId = purchases.id), 0)")
    void recomputePaid();
}
