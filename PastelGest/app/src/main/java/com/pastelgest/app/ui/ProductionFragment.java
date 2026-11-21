package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.data.model.ProductionRow;
import com.pastelgest.app.repository.ProductionRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class ProductionFragment extends ListFragment {
    @Override protected String title() { return getString(R.string.nav_production); }
    @Override protected String subtitle() { return "Ordens de produção e custos reais"; }
    @Override protected String[] filterLabels() { return Periods.LABELS; }
    @Override protected String fabLabel() { return "Nova produção"; }
    @Override protected String emptyText() { return "Nenhuma produção neste período."; }
    @Override protected int emptyIcon() { return R.drawable.ic_flame; }

    @Override
    protected SimpleListActivity.Page load(String query, int filter, int tab) {
        Period p = Periods.forFilter(filter);
        List<Row> rows = new ArrayList<>();
        long good = 0, cost = 0;
        String q = query.toLowerCase(Fmt.PT);
        for (ProductionRow r : new ProductionRepository(requireContext()).list(p.start, p.end)) {
            if (!q.isEmpty() && !r.recipeName.toLowerCase(Fmt.PT).contains(q) && !r.production.number.toLowerCase(Fmt.PT).contains(q)) continue;
            Row row = new Row(r.production.id, r.recipeName,
                    r.production.number + " • " + Fmt.dateTime(r.production.date),
                    r.production.goodQty + " un")
                    .icon(R.drawable.ic_flame, Row.TONE_ACCENT)
                    .badge(Fmt.money(r.production.unitCostCents) + "/un", Row.TONE_ACCENT);
            if (r.production.lostQty > 0) row.subtitle += " • " + r.production.lostQty + " perdidos";
            rows.add(row);
            good += r.production.goodQty;
            cost += r.production.totalCostCents;
        }
        return new SimpleListActivity.Page(rows, good + " pastéis produzidos • custo " + Fmt.money(cost));
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(requireContext(), ProductionDetailActivity.class, row.id);
    }

    @Override
    protected void onFab() {
        Ui.open(requireContext(), ProductionFormActivity.class, null);
    }
}
