package com.pastelgest.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.pastelgest.app.R;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.core.ProfitCalculator;
import com.pastelgest.app.repository.ReportRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Lucro real: demonstração de resultados do período. */
public class ProfitActivity extends BaseActivity {
    private static final String[] LABELS = {"Hoje", "Semana", "Mês", "Ano"};
    private static final int[] KINDS = {Period.DAY, Period.WEEK, Period.MONTH, Period.YEAR};
    private int selected = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profit);
        setupToolbar("Lucro real", true);
        ChipGroup group = findViewById(R.id.pr_period);
        for (int i = 0; i < LABELS.length; i++) {
            Chip c = new Chip(this);
            c.setText(LABELS[i]);
            c.setCheckable(true);
            c.setId(View.generateViewId());
            c.setTag(i);
            group.addView(c);
            if (i == selected) c.setChecked(true);
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            selected = (Integer) g.findViewById(ids.get(0)).getTag();
            load();
        });
        findViewById(R.id.pr_report).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportsActivity.class);
            i.putExtra(ReportsActivity.EXTRA_TYPE, ReportRepository.R_PROFIT);
            i.putExtra(ReportsActivity.EXTRA_PERIOD, selected);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        Period p = Period.of(KINDS[selected]);
        work(() -> new ReportRepository(this).profit(p), r -> render(r, p));
    }

    private void render(ProfitCalculator.Report r, Period p) {
        ((TextView) findViewById(R.id.pr_period_label)).setText(Fmt.date(p.start) + " – " + Fmt.date(p.end - 1));
        TextView net = findViewById(R.id.pr_net);
        net.setText(Fmt.money(r.netProfitCents));
        net.setTextColor(ContextCompat.getColor(this, r.netProfitCents < 0 ? R.color.pg_red : R.color.pg_green));
        ((TextView) findViewById(R.id.pr_margin)).setText("Margem líquida " + r.netMarginPct + "% • " + r.in.salesCount + " vendas");
        LinearLayout rows = findViewById(R.id.pr_rows);
        rows.removeAllViews();
        List<String[]> lines = new ArrayList<>();
        ReportRepository.addProfitRows(lines, r);
        for (String[] l : lines) {
            int tone = Row.TONE_NONE;
            if (l[0].startsWith("=")) tone = l[0].contains("líquido") ? Ui.moneyTone(r.netProfitCents) : Ui.moneyTone(r.grossProfitCents);
            else if (l[0].startsWith("(−)")) tone = Row.TONE_RED;
            else if (l[0].startsWith("Valor recebido")) tone = Row.TONE_GREEN;
            else if (l[0].startsWith("Valor por receber")) tone = Row.TONE_ORANGE;
            Ui.addKv(rows, l[0], l[1], tone);
        }
    }
}
