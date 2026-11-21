package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Production;
import com.pastelgest.app.data.entity.RecipeCostSnapshot;
import com.pastelgest.app.data.model.ProductionItemRow;
import com.pastelgest.app.data.model.ProductionRow;
import com.pastelgest.app.repository.ProductionRepository;
import com.pastelgest.app.repository.RecipeRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Produção confirmada com o custo real histórico. */
public class ProductionDetailActivity extends DetailActivity {

    @Override
    protected String screenTitle() {
        return "Produção";
    }

    @Override
    protected void load() {
        work(() -> {
            ProductionRepository repo = new ProductionRepository(this);
            List<Object> out = new ArrayList<>();
            ProductionRow r = repo.getRow(id);
            out.add(r);
            out.add(repo.items(id));
            out.add(r == null || r.production.costSnapshotId == null ? null : new RecipeRepository(this).snapshot(r.production.costSnapshotId));
            return out;
        }, data -> {
            ProductionRow r = (ProductionRow) data.get(0);
            if (r == null) {
                finish();
                return;
            }
            @SuppressWarnings("unchecked") List<ProductionItemRow> items = (List<ProductionItemRow>) data.get(1);
            render(r, items, (RecipeCostSnapshot) data.get(2));
        });
    }

    private void render(ProductionRow row, List<ProductionItemRow> items, RecipeCostSnapshot snap) {
        clear();
        Production p = row.production;
        header(row.recipeName, p.number + " • " + Fmt.dateTime(p.date), p.goodQty + " pastéis", "CUSTO " + Fmt.money(p.unitCostCents) + "/UN");
        action("Ver receita", R.drawable.ic_book, v -> Ui.open(this, RecipeDetailActivity.class, p.recipeId));
        action("Ver produto", R.drawable.ic_cake, v -> Ui.open(this, ProductDetailActivity.class, p.productId));
        kv("Quantidade produzida", p.plannedQty + " un");
        kv("Pastéis bons", p.goodQty + " un", Row.TONE_GREEN);
        kv("Perdas", p.lostQty + " un", p.lostQty > 0 ? Row.TONE_RED : Row.TONE_NONE);
        kv("Custo dos ingredientes", Fmt.money(p.ingredientsCostCents));
        kv("Embalagem", Fmt.money(p.packagingCostCents));
        kv("Outros custos", Fmt.money(p.otherCostCents));
        kv("Custo total", Fmt.money(p.totalCostCents));
        kv("Custo real por pastel", Fmt.money(p.unitCostCents), Row.TONE_ACCENT);
        if (snap != null) kv("Custo da receita na data", Fmt.money(snap.unitCostCents) + "/un (teórico)");
        if (p.notes != null && !p.notes.isEmpty()) kv("Observações", p.notes);
        section("Ingredientes consumidos");
        for (ProductionItemRow it : items) {
            row(new Row(it.item.ingredientId, it.ingredientName, Units.format(it.item.qtyMilli, it.displayUnit),
                    Fmt.money(it.item.valueCents)).icon(R.drawable.ic_kitchen, Row.TONE_ACCENT),
                    v -> Ui.open(this, IngredientDetailActivity.class, it.item.ingredientId));
        }
    }
}
