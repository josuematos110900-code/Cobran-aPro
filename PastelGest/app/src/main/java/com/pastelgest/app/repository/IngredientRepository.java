package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.StockMovement;
import com.pastelgest.app.data.model.MovementRow;
import com.pastelgest.app.util.Fmt;

import java.util.List;

public class IngredientRepository extends BaseRepository {
    private final StockService stock;

    public IngredientRepository(Context c) {
        super(c);
        stock = new StockService(db);
    }

    public List<Ingredient> search(String q) {
        return db.ingredientDao().search(q == null ? "" : q.trim());
    }

    public Ingredient get(String id) {
        return db.ingredientDao().get(id);
    }

    public List<MovementRow> movements(String id) {
        return db.ingredientDao().movements(id);
    }

    public List<Ingredient> lowStock() {
        return db.ingredientDao().lowStock();
    }

    public List<String> categories() {
        return db.ingredientDao().categories();
    }

    /**
     * Cria ou actualiza um ingrediente. No caso de criação, pode registar um stock
     * inicial com o respectivo valor total.
     */
    public Ingredient save(Ingredient input, long initialQtyMilli, long initialValueCents) {
        return tx(() -> {
            String name = required(input.name, "Indique o nome do ingrediente.");
            if (input.unitType < Units.TYPE_MASS || input.unitType > Units.TYPE_UNIT) {
                throw new BusinessException("Seleccione o tipo de unidade.");
            }
            if (!Units.isCompatible(input.unitType, input.displayUnit)) {
                throw new BusinessException("A unidade não é compatível com o tipo seleccionado.");
            }
            if (input.minStockMilli < 0) throw new BusinessException("O stock mínimo não pode ser negativo.");
            boolean isNew = input.id == null || input.id.isEmpty() || db.ingredientDao().get(input.id) == null;
            String excl = isNew ? "" : input.id;
            if (db.ingredientDao().countByName(name, excl) > 0) {
                throw new BusinessException("Já existe um ingrediente com o nome \"" + name + "\".");
            }
            if (isNew) {
                Ingredient i = new Ingredient();
                i.id = input.id == null ? "" : input.id;
                i.initNew();
                copyEditable(input, i, name);
                db.ingredientDao().insert(i);
                if (initialQtyMilli > 0) {
                    stock.ingredientIn(i, initialQtyMilli, initialValueCents, StockMovement.INITIAL,
                            "INGREDIENTE", i.id, "Stock inicial", System.currentTimeMillis());
                    i.lastPurchaseCostCents = initialValueCents;
                    i.lastPurchaseQtyMilli = initialQtyMilli;
                    i.lastPurchaseDate = System.currentTimeMillis();
                    db.ingredientDao().update(i);
                }
                audit("ingrediente", i.id, "CRIAR", name + (initialQtyMilli > 0
                        ? " | stock inicial " + Units.format(initialQtyMilli, i.displayUnit) + " = " + Fmt.money(initialValueCents) : ""));
                return i;
            } else {
                Ingredient i = db.ingredientDao().get(input.id);
                if (i.unitType != input.unitType && db.ingredientDao().countMovements(i.id) > 0) {
                    throw new BusinessException("Não é possível alterar o tipo de unidade de um ingrediente com movimentações.");
                }
                String before = describe(i);
                copyEditable(input, i, name);
                i.touch();
                db.ingredientDao().update(i);
                audit("ingrediente", i.id, "EDITAR", "Antes: " + before + " | Depois: " + describe(i));
                return i;
            }
        });
    }

    private static void copyEditable(Ingredient from, Ingredient to, String name) {
        to.name = name;
        to.category = trim(from.category);
        to.unitType = from.unitType;
        to.displayUnit = from.displayUnit;
        to.minStockMilli = from.minStockMilli;
        to.supplierId = from.supplierId == null || from.supplierId.isEmpty() ? null : from.supplierId;
        to.notes = trim(from.notes);
    }

    private static String describe(Ingredient i) {
        return i.name + ", " + Fmt.nz(i.category) + ", " + i.displayUnit + ", mín. " + Units.format(i.minStockMilli, i.displayUnit);
    }

    /** Elimina logicamente (o histórico de movimentações é preservado). */
    public void archive(String id) {
        tx(() -> {
            Ingredient i = stock.ingredient(id);
            int recipes = db.recipeDao().countRecipesUsing(id);
            if (recipes > 0) {
                throw new BusinessException("Este ingrediente é usado em " + recipes
                        + " receita(s). Retire-o das receitas antes de o eliminar.");
            }
            i.deleted = 1;
            i.touch();
            db.ingredientDao().update(i);
            audit("ingrediente", id, "ELIMINAR", i.name + " | stock na eliminação: "
                    + Units.format(i.stockQtyMilli, i.displayUnit) + " (" + Fmt.money(i.stockValueCents) + ")");
            return null;
        });
    }

    /** Regista desperdício/perda de ingrediente. */
    public void registerWaste(String id, long qtyMilli, String reason) {
        tx(() -> {
            String why = required(reason, "Indique o motivo do desperdício.");
            Ingredient i = stock.ingredient(id);
            long value = stock.ingredientOut(i, qtyMilli, StockMovement.WASTE, "DESPERDICIO", null, why, System.currentTimeMillis());
            audit("ingrediente", id, "DESPERDICIO", Units.format(qtyMilli, i.displayUnit) + " = " + Fmt.money(value) + " | " + why);
            return null;
        });
    }

    /** Ajuste de inventário para a quantidade real contada. */
    public void adjustTo(String id, long countedQtyMilli, String reason) {
        tx(() -> {
            String why = required(reason, "Indique o motivo do ajuste.");
            if (countedQtyMilli < 0) throw new BusinessException("A quantidade não pode ser negativa.");
            Ingredient i = stock.ingredient(id);
            long diff = countedQtyMilli - i.stockQtyMilli;
            if (diff == 0) throw new BusinessException("A quantidade contada é igual ao stock actual.");
            long now = System.currentTimeMillis();
            long value;
            if (diff < 0) {
                value = -stock.ingredientOut(i, -diff, StockMovement.ADJUST, "AJUSTE", null, why, now);
            } else {
                java.math.BigDecimal unit = i.stockQtyMilli > 0
                        ? CostMath.costPerMilli(i.stockValueCents, i.stockQtyMilli)
                        : CostMath.costPerMilli(i.lastPurchaseCostCents, i.lastPurchaseQtyMilli);
                value = CostMath.cost(diff, unit);
                stock.ingredientIn(i, diff, value, StockMovement.ADJUST, "AJUSTE", null, why, now);
            }
            audit("ingrediente", id, "AJUSTE", "Diferença " + Units.format(diff, i.displayUnit) + " (" + Fmt.money(value) + ") | " + why);
            return null;
        });
    }
}
