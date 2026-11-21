package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Expense;
import com.pastelgest.app.data.model.CategoryTotal;

import java.util.List;

@Dao
public interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Expense e);

    @Update
    void update(Expense e);

    @Query("SELECT * FROM expenses WHERE id = :id")
    Expense get(String id);

    @Query("SELECT * FROM expenses WHERE deleted = 0 AND date >= :from AND date < :to AND (:fixed < 0 OR fixed = :fixed) ORDER BY date DESC, createdAt DESC")
    List<Expense> list(long from, long to, int fixed);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE deleted = 0 AND status = 'ACTIVA' AND date >= :from AND date < :to")
    long total(long from, long to);

    @Query("SELECT category AS category, SUM(amountCents) AS total, COUNT(*) AS count FROM expenses WHERE deleted = 0 AND status = 'ACTIVA' AND date >= :from AND date < :to GROUP BY category ORDER BY total DESC")
    List<CategoryTotal> byCategory(long from, long to);

    @Query("SELECT DISTINCT category FROM expenses WHERE deleted = 0 AND category IS NOT NULL AND category <> '' ORDER BY category")
    List<String> categories();
}
