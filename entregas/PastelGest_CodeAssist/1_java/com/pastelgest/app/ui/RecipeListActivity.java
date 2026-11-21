package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.repository.RecipeRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class RecipeListActivity extends SimpleListActivity {
    @Override protected String title() { return "Receitas e custos"; }
    @Override protected int emptyIcon() { return R.drawable.ic_book; }
    @Override protected String emptyText() { return "Ainda não criou receitas."; }
    @Override protected String fabLabel() { return "Nova receita"; }
    @Override protected Runnable fabAction() { return () -> Ui.open(this, RecipeFormActivity.class, null); }

    @Override
    protected Page load(String query, int filter) {
        List<Row> rows = new ArrayList<>();
        for (Recipe r : new RecipeRepository(this).search(query)) {
            Row row = new Row(r.id, r.name, "Rende " + r.yieldQty + " pastéis • preço " + Fmt.money(r.suggestedPriceCents),
                    Fmt.money(r.lastUnitCostCents)).icon(R.drawable.ic_book, Row.TONE_ACCENT).badge("custo/un", Row.TONE_ACCENT);
            rows.add(row);
        }
        return new Page(rows, rows.size() + " receitas • custo por pastel da última gravação");
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(this, RecipeDetailActivity.class, row.id);
    }
}
