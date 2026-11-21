package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.model.SaleRow;
import com.pastelgest.app.repository.SaleRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class SalesFragment extends ListFragment {
    @Override protected String title() { return getString(R.string.nav_sales); }
    @Override protected String subtitle() { return "Registo, comprovativos e anulações"; }
    @Override protected String[] filterLabels() { return Periods.LABELS; }
    @Override protected String fabLabel() { return getString(R.string.quick_sale); }
    @Override protected String emptyText() { return "Nenhuma venda neste período."; }
    @Override protected int emptyIcon() { return R.drawable.ic_sales; }

    @Override
    protected SimpleListActivity.Page load(String query, int filter, int tab) {
        Period p = Periods.forFilter(filter);
        List<SaleRow> sales = new SaleRepository(requireContext()).list(p.start, p.end, query);
        return toPage(sales);
    }

    static SimpleListActivity.Page toPage(List<SaleRow> sales) {
        List<Row> rows = new ArrayList<>();
        long total = 0, received = 0;
        int active = 0;
        for (SaleRow s : sales) {
            boolean cancelled = Sale.CANCELLED.equals(s.sale.status);
            boolean credit = Sale.TYPE_CREDIT.equals(s.sale.paymentType);
            Row r = new Row(s.sale.id, s.sale.number,
                    Fmt.dateTime(s.sale.date) + (s.clientName != null ? " • " + s.clientName : ""),
                    Fmt.money(s.sale.totalCents))
                    .icon(R.drawable.ic_sales, cancelled ? Row.TONE_NONE : credit ? Row.TONE_ORANGE : Row.TONE_GREEN);
            if (cancelled) r.badge("ANULADA", Row.TONE_RED);
            else if (credit) r.badge(s.sale.paidCents >= s.sale.totalCents ? "FIADA • PAGA" : "FIADA", Row.TONE_ORANGE);
            else r.badge(Fmt.nz(s.sale.paymentMethod), Row.TONE_GREEN);
            rows.add(r);
            if (!cancelled) {
                total += s.sale.totalCents;
                received += s.sale.paidCents;
                active++;
            }
        }
        return new SimpleListActivity.Page(rows, active + " vendas • total " + Fmt.money(total) + " • recebido " + Fmt.money(received));
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(requireContext(), SaleDetailActivity.class, row.id);
    }

    @Override
    protected void onFab() {
        Ui.open(requireContext(), SaleFormActivity.class, null);
    }
}
