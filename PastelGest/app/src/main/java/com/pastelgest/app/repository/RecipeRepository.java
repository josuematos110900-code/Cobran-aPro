package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.core.RecipeCostCalculator;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.data.entity.RecipeCostSnapshot;
import com.pastelgest.app.data.entity.RecipeIngredient;
import com.pastelgest.app.data.model.RecipeIngredientRow;
import com.pastelgest.app.util.Fmt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecipeRepository extends BaseRepository {

    public RecipeRepository(Context c) {
        super(c);
    }

    /** Linha de rascunho de receita. */
    public static class DraftLine {
        public String ingredientId;
        public String ingredientName;
        public int unitType;
        public String unit;
        public long qtyMilli;
    }

    public List<Recipe> search(String q) {
        return db.recipeDao().search(q == null ? "" : q.trim());
    }

    public Recipe get(String id) {
        return db.recipeDao().get(id);
    }

    public List<RecipeIngredientRow> ingredientRows(String recipeId) {
        return db.recipeDao().ingredientRows(recipeId);
    }

    public List<RecipeCostSnapshot> snapshots(String recipeId) {
        return db.recipeDao().snapshots(recipeId);
    }

    public RecipeCostSnapshot snapshot(String id) {
        return db.recipeDao().getSnapshot(id);
    }

    /**
     * Custo actual por milésima de unidade base de um ingrediente: custo médio do
     * stock; se não houver stock, preço da última compra.
     */
    public static BigDecimal currentCostPerMilli(long stockQty, long stockValue, long lastCost, long lastQty) {
        if (stockQty > 0 && stockValue > 0) return CostMath.costPerMilli(stockValue, stockQty);
        if (lastQty > 0) return CostMath.costPerMilli(lastCost, lastQty);
        return BigDecimal.ZERO;
    }

    /** Calcula o custo actual da receita gravada. */
    public RecipeCostCalculator.Result computeCost(String recipeId) {
        Recipe r = db.recipeDao().get(recipeId);
        if (r == null) throw new BusinessException("Receita não encontrada.");
        List<RecipeCostCalculator.Line> lines = new ArrayList<>();
        for (RecipeIngredientRow row : db.recipeDao().ingredientRows(recipeId)) {
            lines.add(new RecipeCostCalculator.Line(row.ri.ingredientId, row.ingredientName, row.ri.qtyMilli,
                    currentCostPerMilli(row.stockQtyMilli, row.stockValueCents, row.lastPurchaseCostCents, row.lastPurchaseQtyMilli)));
        }
        return RecipeCostCalculator.calculate(lines, r.yieldQty, r.packagingCostCents, r.otherCostCents);
    }

    /** Pré-visualização do custo de um rascunho (formulário). */
    public RecipeCostCalculator.Result previewCost(List<DraftLine> draft, long yieldQty, long packaging, long other) {
        List<RecipeCostCalculator.Line> lines = new ArrayList<>();
        for (DraftLine d : draft) {
            com.pastelgest.app.data.entity.Ingredient i = db.ingredientDao().get(d.ingredientId);
            if (i == null) continue;
            lines.add(new RecipeCostCalculator.Line(i.id, i.name, d.qtyMilli,
                    currentCostPerMilli(i.stockQtyMilli, i.stockValueCents, i.lastPurchaseCostCents, i.lastPurchaseQtyMilli)));
        }
        return RecipeCostCalculator.calculate(lines, yieldQty, packaging, other);
    }

    /**
     * Grava a receita e os seus ingredientes. Se não existir produto acabado
     * associado, cria-o automaticamente. Grava sempre um snapshot de custo.
     */
    public Recipe save(Recipe input, List<DraftLine> lines) {
        return tx(() -> {
            String name = required(input.name, "Indique o nome do pastel.");
            if (input.yieldQty <= 0) throw new BusinessException("O rendimento tem de ser pelo menos 1 pastel.");
            if (input.packagingCostCents < 0 || input.otherCostCents < 0 || input.suggestedPriceCents < 0) {
                throw new BusinessException("Os custos e o preço não podem ser negativos.");
            }
            if (lines.isEmpty()) throw new BusinessException("Adicione pelo menos um ingrediente à receita.");
            Set<String> seen = new HashSet<>();
            for (DraftLine l : lines) {
                com.pastelgest.app.data.entity.Ingredient i = db.ingredientDao().get(l.ingredientId);
                if (i == null || i.deleted == 1) throw new BusinessException("Ingrediente inválido na receita.");
                if (!seen.add(l.ingredientId)) throw new BusinessException(i.name + " está repetido na receita.");
                if (l.qtyMilli <= 0) throw new BusinessException("Quantidade inválida para " + i.name + ".");
                if (!Units.isCompatible(i.unitType, l.unit)) {
                    throw new BusinessException("Unidade " + l.unit + " incompatível com " + i.name + ".");
                }
            }
            Recipe existing = input.id == null || input.id.isEmpty() ? null : db.recipeDao().get(input.id);
            if (db.recipeDao().countByName(name, existing == null ? "" : existing.id) > 0) {
                throw new BusinessException("Já existe uma receita com este nome.");
            }
            Recipe r = existing != null ? existing : new Recipe();
            if (existing == null) r.initNew();
            r.name = name;
            r.description = trim(input.description);
            r.photoPath = input.photoPath;
            r.yieldQty = input.yieldQty;
            r.packagingCostCents = input.packagingCostCents;
            r.otherCostCents = input.otherCostCents;
            r.suggestedPriceCents = input.suggestedPriceCents;

            if (r.productId == null || db.productDao().get(r.productId) == null) {
                Product p = new Product();
                p.initNew();
                p.name = name;
                p.recipeId = r.id;
                p.salePriceCents = input.suggestedPriceCents;
                if (db.productDao().countByName(name, "") > 0) p.name = name + " (" + r.id.substring(0, 4) + ")";
                db.productDao().insert(p);
                r.productId = p.id;
                audit("produto", p.id, "CRIAR", "Criado a partir da receita " + name);
            }

            if (existing == null) {
                db.recipeDao().insert(r);
            } else {
                r.touch();
                db.recipeDao().update(r);
                for (RecipeIngredient old : db.recipeDao().ingredients(r.id)) {
                    old.deleted = 1;
                    old.touch();
                    db.recipeDao().updateIngredient(old);
                }
            }
            for (DraftLine l : lines) {
                RecipeIngredient ri = new RecipeIngredient();
                ri.initNew();
                ri.recipeId = r.id;
                ri.ingredientId = l.ingredientId;
                ri.qtyMilli = l.qtyMilli;
                ri.enteredUnit = l.unit;
                db.recipeDao().insertIngredient(ri);
            }
            RecipeCostSnapshot snap = takeSnapshot(r, existing == null ? "Receita criada" : "Receita alterada");
            audit("receita", r.id, existing == null ? "CRIAR" : "EDITAR",
                    name + " | rendimento " + r.yieldQty + " | custo/pastel " + Fmt.money(snap.unitCostCents));
            return r;
        });
    }

    /** Grava o custo actual como histórico (chamado dentro de transacção). */
    RecipeCostSnapshot takeSnapshot(Recipe r, String reason) {
        RecipeCostCalculator.Result res = computeCost(r.id);
        RecipeCostSnapshot s = new RecipeCostSnapshot();
        s.initNew();
        s.recipeId = r.id;
        s.date = System.currentTimeMillis();
        s.yieldQty = res.yieldQty;
        s.ingredientsCostCents = res.ingredientsCostCents;
        s.packagingCostCents = res.packagingCostCents;
        s.otherCostCents = res.otherCostCents;
        s.totalCostCents = res.totalCostCents;
        s.unitCostCents = res.unitCostCents;
        s.reason = reason;
        JSONArray arr = new JSONArray();
        try {
            for (RecipeCostCalculator.Line l : res.lines) {
                JSONObject o = new JSONObject();
                o.put("ingredientId", l.ingredientId);
                o.put("name", l.name);
                o.put("qtyMilli", l.qtyMilli);
                o.put("costCents", l.costCents);
                arr.put(o);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        s.detailsJson = arr.toString();
        db.recipeDao().insertSnapshot(s);
        r.lastUnitCostCents = res.unitCostCents;
        r.touch();
        db.recipeDao().update(r);
        return s;
    }

    /** Recalcula e grava o custo actual (botão "Actualizar custo"). */
    public RecipeCostSnapshot refreshCost(String recipeId) {
        return tx(() -> {
            Recipe r = db.recipeDao().get(recipeId);
            if (r == null) throw new BusinessException("Receita não encontrada.");
            return takeSnapshot(r, "Actualização de custo");
        });
    }

    public void archive(String id) {
        tx(() -> {
            Recipe r = db.recipeDao().get(id);
            if (r == null) throw new BusinessException("Receita não encontrada.");
            r.deleted = 1;
            r.touch();
            db.recipeDao().update(r);
            audit("receita", id, "ELIMINAR", r.name);
            return null;
        });
    }
}
