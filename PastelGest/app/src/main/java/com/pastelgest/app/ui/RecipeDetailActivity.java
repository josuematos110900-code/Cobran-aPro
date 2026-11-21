package com.pastelgest.app.ui;

import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.pastelgest.app.R;
import com.pastelgest.app.core.RecipeCostCalculator;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.data.entity.RecipeCostSnapshot;
import com.pastelgest.app.data.model.RecipeIngredientRow;
import com.pastelgest.app.repository.ProductRepository;
import com.pastelgest.app.repository.RecipeRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Receita: ingredientes, custo actual detalhado e histórico de custos. */
public class RecipeDetailActivity extends DetailActivity {
    private Recipe recipe;

    @Override
    protected String screenTitle() {
        return "Receita";
    }

    @Override
    protected void load() {
        work(() -> {
            RecipeRepository repo = new RecipeRepository(this);
            List<Object> out = new ArrayList<>();
            Recipe r = repo.get(id);
            out.add(r);
            out.add(repo.ingredientRows(id));
            out.add(repo.computeCost(id));
            out.add(repo.snapshots(id));
            out.add(r == null || r.productId == null ? null : new ProductRepository(this).get(r.productId));
            return out;
        }, data -> {
            recipe = (Recipe) data.get(0);
            if (recipe == null) {
                finish();
                return;
            }
            @SuppressWarnings("unchecked") List<RecipeIngredientRow> rows = (List<RecipeIngredientRow>) data.get(1);
            RecipeCostCalculator.Result cost = (RecipeCostCalculator.Result) data.get(2);
            @SuppressWarnings("unchecked") List<RecipeCostSnapshot> snaps = (List<RecipeCostSnapshot>) data.get(3);
            render(rows, cost, snaps, (Product) data.get(4));
        });
    }

    private void render(List<RecipeIngredientRow> rows, RecipeCostCalculator.Result cost, List<RecipeCostSnapshot> snaps, Product product) {
        clear();
        if (recipe.photoPath != null && new File(recipe.photoPath).exists()) {
            photo.setVisibility(View.VISIBLE);
            photo.setImageURI(Uri.fromFile(new File(recipe.photoPath)));
        } else {
            photo.setVisibility(View.GONE);
        }
        header(recipe.name, "Rende " + recipe.yieldQty + " pastéis por lote", Fmt.money(cost.unitCostCents) + " / pastel",
                cost.missingPrices ? "CUSTO INCOMPLETO" : "CUSTO ACTUAL");
        if (recipe.deleted == 0) {
            action("Produzir", R.drawable.ic_flame, v -> Ui.open(this, ProductionFormActivity.class, id));
            action("Editar", R.drawable.ic_edit, v -> Ui.open(this, RecipeFormActivity.class, id));
            action("Gravar custo", R.drawable.ic_save, v -> work(() -> new RecipeRepository(this).refreshCost(id), s -> {
                Ui.success(this, "Custo gravado no histórico: " + Fmt.money(s.unitCostCents) + "/pastel");
                load();
            }));
            action("Eliminar", R.drawable.ic_delete, v -> Ui.confirm(this, "Eliminar receita",
                    "A receita deixa de aparecer nas listas. As produções antigas mantêm os seus custos.", "Eliminar",
                    () -> work(() -> {
                        new RecipeRepository(this).archive(id);
                        return true;
                    }, r -> finish())));
        }
        if (recipe.description != null && !recipe.description.isEmpty()) kv("Descrição", recipe.description);
        long price = product != null ? product.salePriceCents : recipe.suggestedPriceCents;
        kv("Produto associado", product == null ? "—" : product.name + " (" + product.stockQty + " un em stock)");
        kv("Preço de venda", Fmt.money(price));
        if (price > 0) {
            long m = price - cost.unitCostCents;
            kv("Margem por pastel", Fmt.money(m) + " (" + RecipeCostCalculator.marginPercent(price, cost.unitCostCents)
                    .toPlainString().replace('.', ',') + "%)", Ui.moneyTone(m));
        }

        section("Ingredientes por lote");
        for (RecipeIngredientRow r : rows) {
            RecipeCostCalculator.Line line = null;
            for (RecipeCostCalculator.Line l : cost.lines) if (l.ingredientId.equals(r.ri.ingredientId)) line = l;
            String c = line == null || !line.hasCost ? "sem preço" : Fmt.money(line.costCents);
            row(new Row(r.ri.ingredientId, r.ingredientName, Units.format(r.ri.qtyMilli, r.ri.enteredUnit)
                    + " • stock " + Units.format(r.stockQtyMilli, r.displayUnit), c)
                    .icon(R.drawable.ic_kitchen, line != null && line.hasCost ? Row.TONE_ACCENT : Row.TONE_ORANGE),
                    v -> Ui.open(this, IngredientDetailActivity.class, r.ri.ingredientId));
        }

        section("Custo actual do lote");
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, pad / 2);
        card.addView(box);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, 0, pad, 0);
        card.setLayoutParams(lp);
        RecipeFormActivity.renderCost(box, cost, price);
        sections.addView(card);

        section("Histórico de custos (" + snaps.size() + ")");
        if (snaps.isEmpty()) emptyLine("Sem histórico.");
        for (RecipeCostSnapshot s : snaps) {
            row(new Row(s.id, Fmt.money(s.unitCostCents) + " / pastel", Fmt.dateTime(s.date) + " • " + Fmt.nz(s.reason)
                    + " • lote " + Fmt.money(s.totalCostCents), null).icon(R.drawable.ic_history, Row.TONE_NONE).noChevron(), null);
        }
    }
}
