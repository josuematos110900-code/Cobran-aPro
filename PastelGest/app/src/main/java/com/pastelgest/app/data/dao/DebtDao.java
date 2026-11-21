package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Debt;
import com.pastelgest.app.data.entity.Payment;
import com.pastelgest.app.data.model.DebtRow;
import com.pastelgest.app.data.model.PaymentRow;

import java.util.List;

@Dao
public interface DebtDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Debt d);

    @Update
    void update(Debt d);

    @Query("SELECT * FROM debts WHERE id = :id")
    Debt get(String id);

    @Query("SELECT * FROM debts WHERE saleId = :saleId")
    Debt bySale(String saleId);

    @Query("SELECT d.*, c.name AS clientName, c.phone AS clientPhone, s.number AS saleNumber, s.date AS saleDate FROM debts d JOIN clients c ON c.id = d.clientId JOIN sales s ON s.id = d.saleId WHERE d.deleted = 0 AND (:status = '' OR d.status = :status) ORDER BY d.dueDate ASC, s.date ASC")
    List<DebtRow> list(String status);

    @Query("SELECT d.*, c.name AS clientName, c.phone AS clientPhone, s.number AS saleNumber, s.date AS saleDate FROM debts d JOIN clients c ON c.id = d.clientId JOIN sales s ON s.id = d.saleId WHERE d.clientId = :clientId AND d.deleted = 0 ORDER BY s.date DESC")
    List<DebtRow> byClient(String clientId);

    @Query("SELECT d.*, c.name AS clientName, c.phone AS clientPhone, s.number AS saleNumber, s.date AS saleDate FROM debts d JOIN clients c ON c.id = d.clientId JOIN sales s ON s.id = d.saleId WHERE d.id = :id")
    DebtRow getRow(String id);

    @Query("SELECT COALESCE(SUM(balanceCents), 0) FROM debts WHERE status = 'ABERTA' AND deleted = 0")
    long totalOpen();

    @Query("SELECT COUNT(*) FROM debts WHERE status = 'ABERTA' AND deleted = 0 AND dueDate > 0 AND dueDate < :now")
    int countOverdue(long now);

    @Query("SELECT COUNT(*) FROM debts WHERE status = 'ABERTA' AND deleted = 0")
    int countOpen();

    // ---- Pagamentos ----
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPayment(Payment p);

    @Query("SELECT * FROM payments WHERE id = :id")
    Payment getPayment(String id);

    @Query("SELECT p.*, c.name AS clientName, s.number AS saleNumber FROM payments p LEFT JOIN clients c ON c.id = p.clientId LEFT JOIN sales s ON s.id = p.saleId WHERE p.id = :id")
    PaymentRow getPaymentRow(String id);

    @Query("SELECT p.*, c.name AS clientName, s.number AS saleNumber FROM payments p LEFT JOIN clients c ON c.id = p.clientId LEFT JOIN sales s ON s.id = p.saleId WHERE p.clientId = :clientId ORDER BY p.date DESC")
    List<PaymentRow> paymentsByClient(String clientId);

    @Query("SELECT p.*, c.name AS clientName, s.number AS saleNumber FROM payments p LEFT JOIN clients c ON c.id = p.clientId LEFT JOIN sales s ON s.id = p.saleId WHERE p.saleId = :saleId ORDER BY p.date DESC")
    List<PaymentRow> paymentsBySale(String saleId);

    @Query("SELECT p.*, c.name AS clientName, s.number AS saleNumber FROM payments p LEFT JOIN clients c ON c.id = p.clientId LEFT JOIN sales s ON s.id = p.saleId WHERE p.date >= :from AND p.date < :to ORDER BY p.date DESC")
    List<PaymentRow> paymentsBetween(long from, long to);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM payments WHERE saleId = :saleId")
    long paidForSale(String saleId);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM payments WHERE debtId = :debtId")
    long paidForDebt(String debtId);

    /** Dinheiro efectivamente recebido no período (estornos já são negativos). */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM payments WHERE date >= :from AND date < :to")
    long receivedBetween(long from, long to);

    @Query("SELECT COUNT(*) FROM payments WHERE date >= :from AND date < :to")
    int countPaymentsBetween(long from, long to);

    /** Todos os pagamentos de uma venda fiada (incluindo a entrada) têm debtId preenchido. */
    @Query("UPDATE debts SET paidCents = COALESCE((SELECT SUM(amountCents) FROM payments p WHERE p.debtId = debts.id), 0)")
    void recomputePaid();

    @Query("UPDATE debts SET balanceCents = MAX(originalCents - paidCents, 0), status = CASE WHEN status = 'ANULADA' THEN 'ANULADA' WHEN originalCents - paidCents <= 0 THEN 'PAGA' ELSE 'ABERTA' END")
    void recomputeBalances();
}
