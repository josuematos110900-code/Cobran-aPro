package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.repository.IngredientRepository;
import com.pastelgest.app.repository.ProductRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Stock de ingredientes e de produtos acabados. */
public class StockFragment extends ListFragment {
    @Override protected String title() { return getString(R.string.nav_stock); }
    @Override protected String subtitle() { return "Ingredientes, produtos acabados e alertas"; }
    @Override protected String[] filterLabels() { return new String[]{"Todos", "Stock baixo"}; }
    @Override protected String[] tabLabels() { return new String[]{"Ingredientes", "Produtos acabados"}; }
    @Override protected String fabLabel() { return tabIndex == 0 ? "Novo ingrediente" : "Novo produto"; }
    @Override protected String emptyText() { return tabIndex == 0 ? "Ainda não registou ingredientes." : "Ainda não existem produtos. Crie uma receita ou um produto."; }

    @Override
    protected SimpleListActivity.Page load(String query, int filter, int tab) {
        List<Row> rows = new ArrayList<>();
        long value = 0;
        int low = 0;
        if (tab == 0) {
            for (Ingredient i : new IngredientRepository(requireContext()).search(query)) {
                boolean isLow = i.stockQtyMilli <= i.minStockMilli;
                if (filter == 1 && !isLow) continue;
                Row r = new Row(i.id, i.name, Fmt.nz(i.category) + (i.category == null || i.category.isEmpty() ? "" : " • ")
                        + "Valor " + Fmt.money(i.stockValueCents), Units.format(i.stockQtyMilli, i.displayUnit))
                        .icon(R.drawable.ic_kitchen, isLow ? Row.TONE_RED : Row.TONE_ACCENT);
                if (isLow) r.badge(i.stockQtyMilli <= 0 ? "SEM STOCK" : "STOCK BAIXO", Row.TONE_RED);
                rows.add(r);
                value += i.stockValueCents;
                if (isLow) low++;
            }
            return new SimpleListActivity.Page(rows, rows.size() + " ingredientes • valor " + Fmt.money(value) + " • " + low + " com stock baixo");
        }
        for (Product p : new ProductRepository(requireContext()).search(query)) {
            boolean isLow = p.stockQty <= p.minStock;
            if (filter == 1 && !isLow) continue;
            Row r = new Row(p.id, p.name, "Preço " + Fmt.money(p.salePriceCents) + " • valor " + Fmt.money(p.stockValueCents),
                    p.stockQty + " un").icon(R.drawable.ic_cake, isLow ? Row.TONE_ORANGE : Row.TONE_GREEN);
            if (isLow) r.badge(p.stockQty <= 0 ? "ESGOTADO" : "STOCK BAIXO", Row.TONE_ORANGE);
            rows.add(r);
            value += p.stockValueCents;
        }
        return new SimpleListActivity.Page(rows, rows.size() + " produtos • valor ao custo " + Fmt.money(value));
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(requireContext(), tabIndex == 0 ? IngredientDetailActivity.class : ProductDetailActivity.class, row.id);
    }

    @Override
    protected void onFab() {
        Ui.open(requireContext(), tabIndex == 0 ? IngredientFormActivity.class : ProductFormActivity.class, null);
    }
}
