package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.data.entity.Purchase;
import com.pastelgest.app.data.model.PurchaseRow;
import com.pastelgest.app.repository.PurchaseRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class PurchaseListActivity extends SimpleListActivity {
    private static final String[] FILTERS = {"Mês", "Tudo", "Pendentes", "Pagas", "Anuladas"};

    @Override protected String title() { return "Compras"; }
    @Override protected String[] filterLabels() { return FILTERS; }
    @Override protected int emptyIcon() { return R.drawable.ic_cart; }
    @Override protected String emptyText() { return "Sem compras para este filtro."; }
    @Override protected String fabLabel() { return "Nova compra"; }
    @Override protected Runnable fabAction() { return () -> Ui.open(this, PurchaseFormActivity.class, null); }

    @Override
    protected Page load(String query, int filter) {
        Period p = filter == 0 ? Period.of(Period.MONTH) : new Period(0, Long.MAX_VALUE / 2, Period.CUSTOM);
        List<Row> rows = new ArrayList<>();
        long total = 0, due = 0;
        String q = query.toLowerCase(Fmt.PT);
        for (PurchaseRow r : new PurchaseRepository(this).list("", p.start, p.end)) {
            Purchase pu = r.purchase;
            boolean cancelled = Purchase.CANCELLED.equals(pu.status);
            if (filter == 2 && (cancelled || Purchase.PAID.equals(pu.paymentStatus))) continue;
            if (filter == 3 && (cancelled || !Purchase.PAID.equals(pu.paymentStatus))) continue;
            if (filter == 4 && !cancelled) continue;
            String sup = r.supplierName == null ? "Sem fornecedor" : r.supplierName;
            if (!q.isEmpty() && !sup.toLowerCase(Fmt.PT).contains(q) && !pu.number.toLowerCase(Fmt.PT).contains(q)) continue;
            Row row = new Row(pu.id, sup, pu.number + " • " + Fmt.date(pu.date), Fmt.money(pu.totalCents))
                    .icon(R.drawable.ic_cart, cancelled ? Row.TONE_NONE : Row.TONE_ACCENT);
            if (cancelled) row.badge("ANULADA", Row.TONE_RED);
            else if (Purchase.PAID.equals(pu.paymentStatus)) row.badge("PAGA", Row.TONE_GREEN);
            else row.badge(pu.paymentStatus + " • falta " + Fmt.money(pu.totalCents - pu.paidCents), Row.TONE_ORANGE);
            rows.add(row);
            if (!cancelled) {
                total += pu.totalCents;
                due += pu.totalCents - pu.paidCents;
            }
        }
        return new Page(rows, rows.size() + " compras • total " + Fmt.money(total) + " • por pagar " + Fmt.money(due));
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(this, PurchaseDetailActivity.class, row.id);
    }
}
