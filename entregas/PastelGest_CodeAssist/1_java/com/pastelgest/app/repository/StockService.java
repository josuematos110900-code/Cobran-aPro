package com.pastelgest.app.repository;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.AppDatabase;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.entity.StockMovement;

/**
 * Única porta de alteração de stock. Tem de ser chamada dentro de uma
 * transacção. Cada alteração gera uma movimentação imutável e actualiza as
 * caches de quantidade/valor; nunca permite stock negativo.
 */
final class StockService {
    private final AppDatabase db;

    StockService(AppDatabase db) {
        this.db = db;
    }

    Ingredient ingredient(String id) {
        Ingredient i = db.ingredientDao().get(id);
        if (i == null) throw new BusinessException("Ingrediente não encontrado.");
        return i;
    }

    Product product(String id) {
        Product p = db.productDao().get(id);
        if (p == null) throw new BusinessException("Produto não encontrado.");
        return p;
    }

    /** Entrada de ingrediente com valor conhecido. */
    void ingredientIn(Ingredient ing, long qtyMilli, long valueCents, String type,
                      String refType, String refId, String note, long date) {
        if (qtyMilli <= 0) throw new BusinessException("Quantidade de entrada inválida.");
        if (valueCents < 0) throw new BusinessException("Valor de entrada inválido.");
        StockMovement m = movement(ing.id, type, qtyMilli, valueCents, refType, refId, note, date);
        db.ingredientDao().insertMovement(m);
        ing.stockQtyMilli += qtyMilli;
        ing.stockValueCents += valueCents;
        ing.touch();
        db.ingredientDao().update(ing);
    }

    /** Saída de ingrediente ao custo médio. Devolve o valor retirado. */
    long ingredientOut(Ingredient ing, long qtyMilli, String type,
                       String refType, String refId, String note, long date) {
        if (qtyMilli <= 0) throw new BusinessException("Quantidade de saída inválida.");
        if (qtyMilli > ing.stockQtyMilli) {
            throw new BusinessException("Stock insuficiente de " + ing.name + ": disponível "
                    + Units.format(ing.stockQtyMilli, ing.displayUnit) + ", necessário "
                    + Units.format(qtyMilli, ing.displayUnit) + ".");
        }
        long value = CostMath.consumptionValue(qtyMilli, ing.stockQtyMilli, ing.stockValueCents);
        return ingredientOutWithValue(ing, qtyMilli, value, type, refType, refId, note, date);
    }

    /** Saída com valor explícito (estorno de compra). */
    long ingredientOutWithValue(Ingredient ing, long qtyMilli, long value, String type,
                                String refType, String refId, String note, long date) {
        if (qtyMilli > ing.stockQtyMilli) throw new BusinessException("Stock insuficiente de " + ing.name + ".");
        if (qtyMilli == ing.stockQtyMilli) value = ing.stockValueCents;
        value = Math.min(value, ing.stockValueCents);
        StockMovement m = movement(ing.id, type, -qtyMilli, -value, refType, refId, note, date);
        db.ingredientDao().insertMovement(m);
        ing.stockQtyMilli -= qtyMilli;
        ing.stockValueCents -= value;
        ing.touch();
        db.ingredientDao().update(ing);
        return value;
    }

    void productIn(Product p, long qty, long valueCents, String type,
                   String refType, String refId, String note, long date) {
        if (qty <= 0) throw new BusinessException("Quantidade de entrada inválida.");
        ProductMovement m = productMovement(p.id, type, qty, valueCents, refType, refId, note, date);
        db.productDao().insertMovement(m);
        p.stockQty += qty;
        p.stockValueCents += valueCents;
        p.touch();
        db.productDao().update(p);
    }

    long productOut(Product p, long qty, String type, String refType, String refId, String note, long date) {
        if (qty <= 0) throw new BusinessException("Quantidade inválida.");
        if (qty > p.stockQty) {
            throw new BusinessException("Stock insuficiente de " + p.name + ": disponível " + p.stockQty + " un.");
        }
        long value = CostMath.consumptionValue(qty, p.stockQty, p.stockValueCents);
        ProductMovement m = productMovement(p.id, type, -qty, -value, refType, refId, note, date);
        db.productDao().insertMovement(m);
        p.stockQty -= qty;
        p.stockValueCents -= value;
        p.touch();
        db.productDao().update(p);
        return value;
    }

    private static StockMovement movement(String ingredientId, String type, long qty, long value,
                                          String refType, String refId, String note, long date) {
        StockMovement m = new StockMovement();
        m.initNew();
        m.ingredientId = ingredientId;
        m.type = type;
        m.qtyMilli = qty;
        m.valueCents = value;
        m.refType = refType;
        m.refId = refId;
        m.note = note;
        m.date = date;
        return m;
    }

    private static ProductMovement productMovement(String productId, String type, long qty, long value,
                                                   String refType, String refId, String note, long date) {
        ProductMovement m = new ProductMovement();
        m.initNew();
        m.productId = productId;
        m.type = type;
        m.qty = qty;
        m.valueCents = value;
        m.refType = refType;
        m.refId = refId;
        m.note = note;
        m.date = date;
        return m;
    }
}
