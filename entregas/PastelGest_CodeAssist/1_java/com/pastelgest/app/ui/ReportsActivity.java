package com.pastelgest.app.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.repository.ReportRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.ReportExporter;
import com.pastelgest.app.util.ShareHelper;

import java.util.Arrays;

/** Relatórios por período com exportação para CSV e PDF. */
public class ReportsActivity extends BaseActivity {
    public static final String EXTRA_TYPE = "report_type";
    public static final String EXTRA_PERIOD = "report_period";
    private static final String[] PERIODS = {"Hoje", "Semana", "Mês", "Ano", "Personalizado"};
    private static final int[] KINDS = {Period.DAY, Period.WEEK, Period.MONTH, Period.YEAR};

    private Dropdown<String> type;
    private int periodIndex = 2;
    private long from = System.currentTimeMillis(), to = System.currentTimeMillis();
    private EditText fromField, toField;
    private ReportRepository.Report current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        setupToolbar("Relatórios", true);
        type = new Dropdown<>(findViewById(R.id.rp_type));
        type.setItems(Arrays.asList(ReportRepository.ALL), s -> s);
        String pre = getIntent().getStringExtra(EXTRA_TYPE);
        if (pre == null || !type.selectWhere(s -> s.equals(pre))) type.select(0);
        periodIndex = getIntent().getIntExtra(EXTRA_PERIOD, 2);
        ChipGroup group = findViewById(R.id.rp_period);
        for (int i = 0; i < PERIODS.length; i++) {
            Chip c = new Chip(this);
            c.setText(PERIODS[i]);
            c.setCheckable(true);
            c.setId(View.generateViewId());
            c.setTag(i);
            group.addView(c);
            if (i == periodIndex) c.setChecked(true);
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            periodIndex = (Integer) g.findViewById(ids.get(0)).getTag();
            findViewById(R.id.rp_custom).setVisibility(periodIndex == 4 ? View.VISIBLE : View.GONE);
        });
        fromField = findViewById(R.id.rp_from);
        toField = findViewById(R.id.rp_to);
        fromField.setText(Fmt.date(from));
        toField.setText(Fmt.date(to));
        fromField.setOnClickListener(v -> Ui.pickDate(this, from, d -> {
            from = d;
            fromField.setText(Fmt.date(d));
        }));
        toField.setOnClickListener(v -> Ui.pickDate(this, to, d -> {
            to = d;
            toField.setText(Fmt.date(d));
        }));
        findViewById(R.id.rp_generate).setOnClickListener(v -> generate());
        findViewById(R.id.rp_close).setOnClickListener(v -> finish());
        findViewById(R.id.rp_csv).setOnClickListener(v -> export(false));
        findViewById(R.id.rp_pdf).setOnClickListener(v -> export(true));
        findViewById(R.id.rp_summary_card).setVisibility(View.GONE);
        generate();
    }

    private void generate() {
        try {
            String t = type.selected();
            if (t == null) throw new BusinessException("Seleccione o tipo de relatório.");
            Period p = periodIndex == 4 ? Period.custom(from, to) : Period.of(KINDS[periodIndex]);
            String label = Fmt.date(p.start) + " – " + Fmt.date(p.end - 1);
            work(() -> new ReportRepository(this).build(t, p, label), this::render);
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }

    private void render(ReportRepository.Report r) {
        current = r;
        findViewById(R.id.rp_summary_card).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.rp_title)).setText(r.title);
        ((TextView) findViewById(R.id.rp_period_label)).setText(r.periodLabel);
        LinearLayout summary = findViewById(R.id.rp_summary);
        summary.removeAllViews();
        for (String[] s : r.summary) Ui.addKv(summary, s[0], s.length > 1 ? s[1] : "", Row.TONE_NONE);
        LinearLayout table = findViewById(R.id.rp_table);
        table.removeAllViews();
        table.addView(tableRow(r.headers, true));
        int limit = Math.min(r.rows.size(), 300);
        for (int i = 0; i < limit; i++) table.addView(tableRow(r.rows.get(i), false));
        if (r.rows.size() > limit) {
            TextView more = new TextView(this);
            more.setText("… mais " + (r.rows.size() - limit) + " linhas (exporte para ver todas)");
            more.setPadding(16, 12, 16, 12);
            table.addView(more);
        }
        if (r.rows.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Sem registos no período.");
            none.setPadding(16, 12, 16, 12);
            table.addView(none);
        }
    }

    private View tableRow(String[] cells, boolean head) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        float d = getResources().getDisplayMetrics().density;
        row.setPadding((int) (8 * d), (int) (6 * d), (int) (8 * d), (int) (6 * d));
        if (head) row.setBackgroundColor(ContextCompat.getColor(this, R.color.pg_accent_soft));
        for (String c : cells) {
            TextView t = new TextView(this);
            t.setText(c);
            t.setWidth((int) (120 * d));
            t.setMaxLines(2);
            t.setGravity(Gravity.START);
            t.setTextSize(13);
            t.setTextColor(ContextCompat.getColor(this, R.color.pg_text));
            if (head) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(t);
        }
        return row;
    }

    private void export(boolean pdf) {
        if (current == null) {
            Ui.toast(this, "Gere primeiro o relatório.");
            return;
        }
        ReportRepository.Report r = current;
        work(() -> pdf ? ReportExporter.pdf(this, r) : ReportExporter.csv(this, r),
                f -> ShareHelper.share(this, f, pdf ? "application/pdf" : "text/csv", r.title));
    }
}
