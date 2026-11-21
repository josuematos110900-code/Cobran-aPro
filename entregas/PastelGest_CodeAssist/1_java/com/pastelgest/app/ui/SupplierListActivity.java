package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.Supplier;
import com.pastelgest.app.repository.SupplierRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class SupplierListActivity extends SimpleListActivity {
    @Override protected String title() { return "Fornecedores"; }
    @Override protected int emptyIcon() { return R.drawable.ic_truck; }
    @Override protected String emptyText() { return "Ainda não registou fornecedores."; }
    @Override protected String fabLabel() { return "Novo fornecedor"; }
    @Override protected Runnable fabAction() { return () -> Ui.open(this, SupplierFormActivity.class, null); }

    @Override
    protected Page load(String query, int filter) {
        List<Row> rows = new ArrayList<>();
        for (Supplier s : new SupplierRepository(this).search(query)) {
            rows.add(new Row(s.id, s.name, Fmt.nz(s.phone) + (Fmt.empty(s.email) ? "" : " • " + s.email), null)
                    .icon(R.drawable.ic_truck, Row.TONE_ACCENT));
        }
        return new Page(rows, rows.size() + " fornecedores");
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(this, SupplierFormActivity.class, row.id);
    }
}
