package com.pastelgest.app.ui;

import android.view.View;

import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.StockMovement;
import com.pastelgest.app.data.entity.Supplier;
import com.pastelgest.app.data.model.MovementRow;
import com.pastelgest.app.repository.IngredientRepository;
import com.pastelgest.app.repository.SupplierRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Ficha do ingrediente com histórico de entradas e saídas. */
public class IngredientDetailActivity extends DetailActivity {
    private Ingredient ing;

    @Override
    protected String screenTitle() {
        return "Ingrediente";
    }

    @Override
    protected void load() {
        work(() -> {
            IngredientRepository repo = new IngredientRepository(this);
            List<Object> out = new ArrayList<>();
            Ingredient i = repo.get(id);
            out.add(i);
            out.add(repo.movements(id));
            out.add(i == null ? null : new SupplierRepository(this).get(i.supplierId));
            return out;
        }, data -> {
            ing = (Ingredient) data.get(0);
            if (ing == null) {
                finish();
                return;
            }
            @SuppressWarnings("unchecked") List<MovementRow> moves = (List<MovementRow>) data.get(1);
            Supplier s = (Supplier) data.get(2);
            render(moves, s);
        });
    }

    private void render(List<MovementRow> moves, Supplier s) {
        clear();
        boolean low = ing.stockQtyMilli <= ing.minStockMilli;
        header(ing.name, Fmt.nz(ing.category), Units.format(ing.stockQtyMilli, ing.displayUnit),
                ing.deleted == 1 ? "ELIMINADO" : low ? "STOCK BAIXO" : "STOCK OK");
        if (ing.deleted == 0) {
            action("Editar", R.drawable.ic_edit, v -> Ui.open(this, IngredientFormActivity.class, id));
            action("Desperdício", R.drawable.ic_waste, v -> waste());
            action("Ajustar", R.drawable.ic_tune, v -> adjust());
            action("Eliminar", R.drawable.ic_delete, v -> archive());
        }
        kv("Valor em stock", Fmt.money(ing.stockValueCents));
        kv("Custo médio", ing.stockQtyMilli > 0
                ? Fmt.money(CostMath.pricePerUnit(ing.stockValueCents, ing.stockQtyMilli, ing.displayUnit)) + "/" + ing.displayUnit : "—");
        kv("Último preço de compra", ing.lastPurchaseQtyMilli > 0
                ? Fmt.money(CostMath.pricePerUnit(ing.lastPurchaseCostCents, ing.lastPurchaseQtyMilli, ing.displayUnit)) + "/" + ing.displayUnit : "—");
        kv("Última compra", Fmt.date(ing.lastPurchaseDate));
        kv("Stock mínimo", Units.format(ing.minStockMilli, ing.displayUnit), low ? Row.TONE_RED : Row.TONE_NONE);
        kv("Fornecedor", s == null ? "—" : s.name);
        if (ing.notes != null && !ing.notes.isEmpty()) kv("Observações", ing.notes);

        section("Histórico de movimentações (" + moves.size() + ")");
        if (moves.isEmpty()) emptyLine("Sem movimentações.");
        for (MovementRow m : moves) {
            boolean in = m.m.qtyMilli > 0;
            Row r = new Row(m.m.id, label(m.m.type), Fmt.dateTime(m.m.date) + (m.m.note == null ? "" : " • " + m.m.note),
                    (in ? "+" : "") + Units.format(m.m.qtyMilli, ing.displayUnit))
                    .icon(in ? R.drawable.ic_add : R.drawable.ic_waste, in ? Row.TONE_GREEN : Row.TONE_RED)
                    .badge(Fmt.money(m.m.valueCents), in ? Row.TONE_GREEN : Row.TONE_RED).noChevron();
            r.valueTone = in ? Row.TONE_GREEN : Row.TONE_RED;
            View.OnClickListener open = null;
            if ("COMPRA".equals(m.m.refType) && m.m.refId != null) {
                final String ref = m.m.refId;
                row(r, v -> Ui.open(this, PurchaseDetailActivity.class, ref));
            } else if ("PRODUCAO".equals(m.m.refType) && m.m.refId != null) {
                final String ref = m.m.refId;
                row(r, v -> Ui.open(this, ProductionDetailActivity.class, ref));
            } else {
                row(r, open);
            }
        }
    }

    static String label(String type) {
        switch (type) {
            case StockMovement.INITIAL: return "Stock inicial";
            case StockMovement.PURCHASE: return "Entrada por compra";
            case StockMovement.PURCHASE_REVERSAL: return "Estorno de compra";
            case StockMovement.PRODUCTION: return "Consumo na produção";
            case StockMovement.WASTE: return "Desperdício";
            case StockMovement.ADJUST: return "Ajuste de inventário";
            default: return type;
        }
    }

    private void waste() {
        Dialogs.qtyReason(this, "Registar desperdício", "Disponível: " + Units.format(ing.stockQtyMilli, ing.displayUnit)
                        + ". O valor desperdiçado conta como perda no lucro.", "Quantidade desperdiçada", ing.displayUnit, null,
                "Registar", (q, reason) -> {
                    try {
                        long qty = Units.parseToBaseMilli(q, ing.displayUnit);
                        work(() -> {
                            new IngredientRepository(this).registerWaste(id, qty, reason);
                            return true;
                        }, r -> {
                            Ui.success(this, "Desperdício registado.");
                            load();
                        });
                    } catch (BusinessException e) {
                        Ui.showError(this, e);
                    }
                });
    }

    private void adjust() {
        Dialogs.qtyReason(this, "Ajuste de inventário", "Indique a quantidade real contada. A diferença fica registada.",
                "Quantidade contada", ing.displayUnit, Units.formatInput(ing.stockQtyMilli, ing.displayUnit), "Ajustar",
                (q, reason) -> {
                    try {
                        long qty = Units.parseToBaseMilli(q, ing.displayUnit);
                        work(() -> {
                            new IngredientRepository(this).adjustTo(id, qty, reason);
                            return true;
                        }, r -> {
                            Ui.success(this, "Stock ajustado.");
                            load();
                        });
                    } catch (BusinessException e) {
                        Ui.showError(this, e);
                    }
                });
    }

    private void archive() {
        Ui.confirm(this, "Eliminar ingrediente", "O ingrediente deixa de aparecer nas listas. O histórico de movimentações, "
                + "compras e produções é preservado.", "Eliminar", () -> work(() -> {
            new IngredientRepository(this).archive(id);
            return true;
        }, r -> {
            Ui.toast(this, "Ingrediente eliminado.");
            finish();
        }));
    }
}
