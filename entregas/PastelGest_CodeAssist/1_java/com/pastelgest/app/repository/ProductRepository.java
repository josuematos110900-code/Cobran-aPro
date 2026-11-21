package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.model.ProductMovementRow;
import com.pastelgest.app.util.Fmt;

import java.util.List;

public class ProductRepository extends BaseRepository {
    private final StockService stock;

    public ProductRepository(Context c) {
        super(c);
        stock = new StockService(db);
    }

    public List<Product> search(String q) {
        return db.productDao().search(q == null ? "" : q.trim());
    }

    public List<Product> available() {
        return db.productDao().available();
    }

    public Product get(String id) {
        return db.productDao().get(id);
    }

    public List<ProductMovementRow> movements(String id) {
        return db.productDao().movements(id);
    }

    public Product save(Product input) {
        return tx(() -> {
            String name = required(input.name, "Indique o nome do produto.");
            if (input.salePriceCents < 0) throw new BusinessException("O preço não pode ser negativo.");
            if (input.minStock < 0) throw new BusinessException("O stock mínimo não pode ser negativo.");
            Product existing = input.id == null || input.id.isEmpty() ? null : db.productDao().get(input.id);
            if (db.productDao().countByName(name, existing == null ? "" : existing.id) > 0) {
                throw new BusinessException("Já existe um produto com este nome.");
            }
            Product p = existing != null ? existing : new Product();
            if (existing == null) p.initNew();
            String before = existing == null ? "" : existing.name + " " + Fmt.money(existing.salePriceCents);
            p.name = name;
            p.salePriceCents = input.salePriceCents;
            p.minStock = input.minStock;
            if (input.recipeId != null) p.recipeId = input.recipeId;
            if (existing == null) {
                db.productDao().insert(p);
                audit("produto", p.id, "CRIAR", name + " " + Fmt.money(p.salePriceCents));
            } else {
                p.touch();
                db.productDao().update(p);
                audit("produto", p.id, "EDITAR", "Antes: " + before + " | Depois: " + name + " " + Fmt.money(p.salePriceCents));
            }
            return p;
        });
    }

    public void registerWaste(String id, long qty, String reason) {
        tx(() -> {
            String why = required(reason, "Indique o motivo do desperdício.");
            Product p = stock.product(id);
            long value = stock.productOut(p, qty, ProductMovement.WASTE, "DESPERDICIO", null, why, System.currentTimeMillis());
            audit("produto", id, "DESPERDICIO", qty + " un = " + Fmt.money(value) + " | " + why);
            return null;
        });
    }

    public void adjustTo(String id, long counted, String reason) {
        tx(() -> {
            String why = required(reason, "Indique o motivo do ajuste.");
            if (counted < 0) throw new BusinessException("A quantidade não pode ser negativa.");
            Product p = stock.product(id);
            long diff = counted - p.stockQty;
            if (diff == 0) throw new BusinessException("A quantidade contada é igual ao stock actual.");
            long now = System.currentTimeMillis();
            long value;
            if (diff < 0) {
                value = -stock.productOut(p, -diff, ProductMovement.ADJUST, "AJUSTE", null, why, now);
            } else {
                long unitCost;
                if (p.stockQty > 0) {
                    unitCost = CostMath.consumptionValue(1, p.stockQty, p.stockValueCents);
                } else {
                    com.pastelgest.app.data.entity.Recipe r = p.recipeId == null ? null : db.recipeDao().get(p.recipeId);
                    unitCost = r == null ? 0 : r.lastUnitCostCents;
                }
                value = unitCost * diff;
                stock.productIn(p, diff, value, ProductMovement.ADJUST, "AJUSTE", null, why, now);
            }
            audit("produto", id, "AJUSTE", "Diferença " + diff + " un (" + Fmt.money(value) + ") | " + why);
            return null;
        });
    }

    public void archive(String id) {
        tx(() -> {
            Product p = stock.product(id);
            if (p.stockQty > 0) {
                throw new BusinessException("O produto ainda tem " + p.stockQty
                        + " un em stock. Venda ou registe o desperdício antes de o eliminar.");
            }
            p.deleted = 1;
            p.touch();
            db.productDao().update(p);
            audit("produto", id, "ELIMINAR", p.name);
            return null;
        });
    }
}
