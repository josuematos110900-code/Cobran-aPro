package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.data.entity.Expense;
import com.pastelgest.app.repository.ExpenseRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class ExpenseListActivity extends SimpleListActivity {
    private static final String[] FILTERS = {"Mês", "Semana", "Hoje", "Ano", "Fixas (mês)", "Variáveis (mês)"};

    @Override protected String title() { return "Despesas"; }
    @Override protected String[] filterLabels() { return FILTERS; }
    @Override protected int emptyIcon() { return R.drawable.ic_money; }
    @Override protected String emptyText() { return "Sem despesas neste período."; }
    @Override protected String fabLabel() { return "Nova despesa"; }
    @Override protected Runnable fabAction() { return () -> Ui.open(this, ExpenseFormActivity.class, null); }

    @Override
    protected Page load(String query, int filter) {
        int kind = filter == 1 ? Period.WEEK : filter == 2 ? Period.DAY : filter == 3 ? Period.YEAR : Period.MONTH;
        int fixed = filter == 4 ? 1 : filter == 5 ? 0 : -1;
        Period p = Period.of(kind);
        List<Row> rows = new ArrayList<>();
        long total = 0;
        String q = query.toLowerCase(Fmt.PT);
        for (Expense e : new ExpenseRepository(this).list(p.start, p.end, fixed)) {
            if (!q.isEmpty() && !e.description.toLowerCase(Fmt.PT).contains(q) && !e.category.toLowerCase(Fmt.PT).contains(q)) continue;
            boolean cancelled = Expense.CANCELLED.equals(e.status);
            Row r = new Row(e.id, e.description, e.category + " • " + Fmt.date(e.date) + " • " + Fmt.nz(e.paymentMethod),
                    Fmt.money(e.amountCents)).icon(R.drawable.ic_money, cancelled ? Row.TONE_NONE : Row.TONE_RED)
                    .badge(cancelled ? "ANULADA" : e.fixed == 1 ? "FIXA" : "VARIÁVEL", cancelled ? Row.TONE_RED : Row.TONE_ACCENT);
            r.valueTone = cancelled ? Row.TONE_NONE : Row.TONE_RED;
            rows.add(r);
            if (!cancelled) total += e.amountCents;
        }
        return new Page(rows, rows.size() + " despesas • total " + Fmt.money(total));
    }

    @Override
    protected void onRowClick(Row row) {
        Ui.open(this, ExpenseFormActivity.class, row.id);
    }
}
