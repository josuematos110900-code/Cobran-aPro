package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.data.entity.Expense;
import com.pastelgest.app.data.model.CategoryTotal;
import com.pastelgest.app.util.Fmt;

import java.util.List;

public class ExpenseRepository extends BaseRepository {
    public static final String[] DEFAULT_CATEGORIES = {
            "Renda", "Electricidade", "Água", "Gás", "Salários", "Transporte",
            "Embalagens", "Manutenção", "Impostos e taxas", "Comunicações", "Marketing", "Outras"
    };

    public ExpenseRepository(Context c) {
        super(c);
    }

    public List<Expense> list(long from, long to, int fixedFilter) {
        return db.expenseDao().list(from, to, fixedFilter);
    }

    public Expense get(String id) {
        return db.expenseDao().get(id);
    }

    public List<CategoryTotal> byCategory(long from, long to) {
        return db.expenseDao().byCategory(from, to);
    }

    public List<String> categories() {
        return db.expenseDao().categories();
    }

    public Expense save(Expense input) {
        return tx(() -> {
            String cat = required(input.category, "Seleccione a categoria.");
            String desc = required(input.description, "Indique a descrição da despesa.");
            if (input.amountCents <= 0) throw new BusinessException("O valor tem de ser superior a zero.");
            if (input.date <= 0) throw new BusinessException("Indique a data.");
            if (input.date > System.currentTimeMillis() + 86_400_000L) {
                throw new BusinessException("A data não pode estar no futuro.");
            }
            if (input.paymentMethod == null || input.paymentMethod.isEmpty()) {
                throw new BusinessException("Seleccione a forma de pagamento.");
            }
            Expense existing = input.id == null || input.id.isEmpty() ? null : db.expenseDao().get(input.id);
            if (existing != null && Expense.CANCELLED.equals(existing.status)) {
                throw new BusinessException("Não é possível editar uma despesa anulada.");
            }
            Expense e = existing != null ? existing : new Expense();
            String before = existing == null ? "" : describe(existing);
            if (existing == null) {
                e.id = input.id == null ? "" : input.id;
                e.initNew();
                e.status = Expense.ACTIVE;
            }
            e.category = cat;
            e.description = desc;
            e.amountCents = input.amountCents;
            e.date = input.date;
            e.paymentMethod = input.paymentMethod;
            e.fixed = input.fixed;
            e.receiptPath = input.receiptPath;
            if (existing == null) {
                db.expenseDao().insert(e);
                audit("despesa", e.id, "CRIAR", describe(e));
            } else {
                e.touch();
                db.expenseDao().update(e);
                audit("despesa", e.id, "EDITAR", "Antes: " + before + " | Depois: " + describe(e));
            }
            return e;
        });
    }

    private static String describe(Expense e) {
        return e.category + " | " + e.description + " | " + Fmt.money(e.amountCents) + " | " + Fmt.date(e.date);
    }

    public void cancel(String id, String reason) {
        tx(() -> {
            String why = required(reason, "Indique o motivo da anulação.");
            Expense e = db.expenseDao().get(id);
            if (e == null) throw new BusinessException("Despesa não encontrada.");
            if (Expense.CANCELLED.equals(e.status)) throw new BusinessException("A despesa já está anulada.");
            e.status = Expense.CANCELLED;
            e.cancelReason = why;
            e.touch();
            db.expenseDao().update(e);
            audit("despesa", id, "ANULAR", describe(e) + " | " + why);
            return null;
        });
    }
}
