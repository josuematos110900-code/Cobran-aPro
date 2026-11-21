package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.data.model.ClientBalance;
import com.pastelgest.app.repository.ClientRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class ClientListActivity extends SimpleListActivity {
    @Override protected String title() { return "Clientes"; }
    @Override protected String[] filterLabels() { return new String[]{"Todos", "Com dívidas"}; }
    @Override protected int emptyIcon() { return R.drawable.ic_people; }
    @Override protected String emptyText() { return "Ainda não registou clientes."; }
    @Override protected String fabLabel() { return "Novo cliente"; }
    @Override protected Runnable fabAction() { return () -> Ui.open(this, ClientFormActivity.class, null); }

    @Override
    protected Page load(String query, int filter) {
        List<Row> rows = new ArrayList<>();
        long total = 0;
        for (ClientBalance c : new ClientRepository(this).search(query)) {
            if (filter == 1 && c.balanceCents <= 0) continue;
            Row r = new Row(c.client.id, c.client.name, Fmt.nz(c.client.phone), c.balanceCents > 0 ? Fmt.money(c.balanceCents) : null)
                    .icon(R.drawable.ic_person, c.balanceCents > 0 ? Row.TONE_ORANGE : Row.TONE_ACCENT);
            r.valueTone = Row.TONE_RED;
            rows.add(r);
            total += c.balanceCents;
        }
        return new Page(rows, rows.size() + " clientes • em dívida " + Fmt.money(total));
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(this, ClientDetailActivity.class, row.id);
    }
}
