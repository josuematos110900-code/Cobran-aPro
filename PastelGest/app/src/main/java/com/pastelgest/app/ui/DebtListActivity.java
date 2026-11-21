package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.Debt;
import com.pastelgest.app.data.model.DebtRow;
import com.pastelgest.app.repository.DebtRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Contas a receber (vendas fiadas). */
public class DebtListActivity extends SimpleListActivity {
    private static final String[] FILTERS = {"Em aberto", "Vencidas", "Pagas", "Todas"};

    @Override protected String title() { return "Dívidas"; }
    @Override protected String[] filterLabels() { return FILTERS; }
    @Override protected int emptyIcon() { return R.drawable.ic_wallet; }
    @Override protected String emptyText() { return "Sem dívidas para este filtro."; }

    @Override
    protected Page load(String query, int filter) {
        String status = filter == 0 || filter == 1 ? Debt.OPEN : filter == 2 ? Debt.PAID : "";
        long now = System.currentTimeMillis();
        List<Row> rows = new ArrayList<>();
        long total = 0;
        String q = query.toLowerCase(Fmt.PT);
        for (DebtRow d : new DebtRepository(this).list(status)) {
            boolean open = Debt.OPEN.equals(d.debt.status);
            boolean late = open && d.debt.dueDate > 0 && d.debt.dueDate < now;
            if (filter == 1 && !late) continue;
            if (!q.isEmpty() && !d.clientName.toLowerCase(Fmt.PT).contains(q) && !Fmt.nz(d.clientPhone).contains(q)) continue;
            Row r = new Row(d.debt.id, d.clientName, d.saleNumber + " • " + Fmt.date(d.saleDate)
                    + (d.debt.dueDate > 0 ? " • vence " + Fmt.date(d.debt.dueDate) : ""), Fmt.money(d.debt.balanceCents))
                    .icon(R.drawable.ic_wallet, late ? Row.TONE_RED : open ? Row.TONE_ORANGE : Row.TONE_GREEN)
                    .badge(late ? "VENCIDA" : d.debt.status, late ? Row.TONE_RED : open ? Row.TONE_ORANGE : Row.TONE_GREEN)
                    .tag(d.debt.clientId);
            r.valueTone = open ? Row.TONE_RED : Row.TONE_NONE;
            rows.add(r);
            total += d.debt.balanceCents;
        }
        return new Page(rows, rows.size() + " dívidas • saldo " + Fmt.money(total));
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(this, ClientDetailActivity.class, (String) row.tag);
    }
}
