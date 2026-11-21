package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.core.ProductionPlanner;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.entity.Production;
import com.pastelgest.app.data.entity.ProductionItem;
import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.data.entity.RecipeCostSnapshot;
import com.pastelgest.app.data.entity.StockMovement;
import com.pastelgest.app.data.model.ProductionItemRow;
import com.pastelgest.app.data.model.ProductionRow;
import com.pastelgest.app.data.model.RecipeIngredientRow;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class ProductionRepository extends BaseRepository {
    private final StockService stock;
    private final RecipeRepository recipes;

    public ProductionRepository(Context c) {
        super(c);
        stock = new StockService(db);
        recipes = new RecipeRepository(c);
    }

    /** Necessidade de um ingrediente para a produção. */
    public static class Need {
        public String ingredientId;
        public String name;
        public String unit;
        public long requiredMilli;
        public long availableMilli;
        public long estimatedCostCents;

        public long missingMilli() {
            return Math.max(0, requiredMilli - availableMilli);
        }
    }

    /** Plano de produção: necessidades, disponibilidade e custo estimado. */
    public static class Plan {
        public Recipe recipe;
        public long quantity;
        public final List<Need> needs = new ArrayList<>();
        public long ingredientsCostCents;
        public long packagingCostCents;
        public long otherCostCents;

        public boolean feasible() {
            for (Need n : needs) if (n.missingMilli() > 0) return false;
            return true;
        }

        public List<Need> missing() {
            List<Need> out = new ArrayList<>();
            for (Need n : needs) if (n.missingMilli() > 0) out.add(n);
            return out;
        }

        public long totalCostCents() {
            return ingredientsCostCents + packagingCostCents + otherCostCents;
        }
    }

    public List<ProductionRow> list(long from, long to) {
        return db.productionDao().list(from, to);
    }

    public ProductionRow getRow(String id) {
        return db.productionDao().getRow(id);
    }

    public List<ProductionItemRow> items(String id) {
        return db.productionDao().items(id);
    }

    /** Calcula as necessidades de ingredientes e verifica a disponibilidade. */
    public Plan plan(String recipeId, long quantity) {
        Recipe r = db.recipeDao().get(recipeId);
        if (r == null || r.deleted == 1) throw new BusinessException("Seleccione uma receita válida.");
        List<RecipeIngredientRow> rows = db.recipeDao().ingredientRows(recipeId);
        if (rows.isEmpty()) throw new BusinessException("A receita não tem ingredientes.");
        Plan p = new Plan();
        p.recipe = r;
        p.quantity = quantity;
        for (RecipeIngredientRow row : rows) {
            Need n = new Need();
            n.ingredientId = row.ri.ingredientId;
            n.name = row.ingredientName;
            n.unit = row.displayUnit;
            n.requiredMilli = ProductionPlanner.required(row.ri.qtyMilli, r.yieldQty, quantity);
            n.availableMilli = row.stockQtyMilli;
            if (n.requiredMilli <= row.stockQtyMilli) {
                n.estimatedCostCents = CostMath.consumptionValue(n.requiredMilli, row.stockQtyMilli, row.stockValueCents);
            } else {
                n.estimatedCostCents = CostMath.cost(n.requiredMilli, RecipeRepository.currentCostPerMilli(
                        row.stockQtyMilli, row.stockValueCents, row.lastPurchaseCostCents, row.lastPurchaseQtyMilli));
            }
            p.ingredientsCostCents += n.estimatedCostCents;
            p.needs.add(n);
        }
        p.packagingCostCents = r.packagingCostCents * quantity;
        p.otherCostCents = ProductionPlanner.scaledBatchCost(r.otherCostCents, r.yieldQty, quantity);
        return p;
    }

    /** Texto com os ingredientes em falta. */
    public static String missingText(Plan plan) {
        StringBuilder sb = new StringBuilder("Ingredientes insuficientes:");
        for (Need n : plan.missing()) {
            sb.append("\n• ").append(n.name).append(": faltam ").append(Units.format(n.missingMilli(), n.unit))
                    .append(" (necessário ").append(Units.format(n.requiredMilli, n.unit))
                    .append(", disponível ").append(Units.format(n.availableMilli, n.unit)).append(")");
        }
        return sb.toString();
    }

    /**
     * Confirma a produção: desconta os ingredientes, regista o custo real e dá
     * entrada dos pastéis bons no stock de produtos acabados. Tudo numa transacção.
     *
     * @param id       id gerado ao abrir o formulário (impede confirmação dupla)
     * @param quantity pastéis a produzir
     * @param lost     pastéis perdidos durante a produção (o custo é absorvido pelos bons)
     */
    public Production confirm(String id, String recipeId, long quantity, long lost, String notes) {
        return tx(() -> {
            if (db.productionDao().get(id) != null) throw new BusinessException("Esta produção já foi confirmada.");
            if (quantity <= 0) throw new BusinessException("A quantidade a produzir tem de ser superior a zero.");
            if (lost < 0) throw new BusinessException("As perdas não podem ser negativas.");
            if (lost >= quantity) {
                throw new BusinessException("As perdas têm de ser inferiores à quantidade produzida. "
                        + "Para um lote totalmente perdido registe o desperdício dos ingredientes.");
            }
            Plan plan = plan(recipeId, quantity);
            if (!plan.feasible()) throw new BusinessException(missingText(plan));
            Recipe r = plan.recipe;
            Product product = r.productId == null ? null : db.productDao().get(r.productId);
            if (product == null) throw new BusinessException("A receita não tem produto acabado associado. Edite e grave a receita.");

            long now = System.currentTimeMillis();
            long good = quantity - lost;
            RecipeCostSnapshot snap = recipes.takeSnapshot(r, "Produção");

            Production p = new Production();
            p.id = id;
            p.initNew();
            Period day = Period.of(Period.DAY, now, TimeZone.getDefault());
            p.number = documentNumber("P", now, db.productionDao().countBetween(day.start, day.end));
            p.recipeId = r.id;
            p.productId = product.id;
            p.date = now;
            p.plannedQty = quantity;
            p.goodQty = good;
            p.lostQty = lost;
            p.costSnapshotId = snap.id;
            p.notes = trim(notes);
            db.productionDao().insert(p);

            long ingredientsCost = 0;
            for (Need n : plan.needs) {
                Ingredient ing = stock.ingredient(n.ingredientId);
                long value = stock.ingredientOut(ing, n.requiredMilli, StockMovement.PRODUCTION, "PRODUCAO", p.id,
                        "Produção " + p.number, now);
                ingredientsCost += value;
                ProductionItem pi = new ProductionItem();
                pi.initNew();
                pi.productionId = p.id;
                pi.ingredientId = n.ingredientId;
                pi.qtyMilli = n.requiredMilli;
                pi.valueCents = value;
                db.productionDao().insertItem(pi);
            }
            p.ingredientsCostCents = ingredientsCost;
            p.packagingCostCents = r.packagingCostCents * good;
            p.otherCostCents = ProductionPlanner.scaledBatchCost(r.otherCostCents, r.yieldQty, quantity);
            p.totalCostCents = p.ingredientsCostCents + p.packagingCostCents + p.otherCostCents;
            p.unitCostCents = com.pastelgest.app.core.Money.divide(p.totalCostCents, good);
            p.touch();
            db.productionDao().update(p);
            stock.productIn(product, good, p.totalCostCents, ProductMovement.PRODUCTION, "PRODUCAO", p.id,
                    "Produção " + p.number, now);
            audit("producao", p.id, "CONFIRMAR", p.number + " | " + r.name + " | " + quantity + " produzidos, "
                    + lost + " perdidos | custo " + Fmt.money(p.totalCostCents) + " | " + Fmt.money(p.unitCostCents) + "/un");
            return p;
        });
    }
}
