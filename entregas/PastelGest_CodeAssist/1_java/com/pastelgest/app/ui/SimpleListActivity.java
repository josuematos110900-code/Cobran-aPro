package com.pastelgest.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.pastelgest.app.R;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.ui.adapter.RowAdapter;

import java.util.List;

/** Ecrã de lista genérico: pesquisa, filtros, estado vazio e botão de criação. */
public abstract class SimpleListActivity extends BaseActivity {
    protected RowAdapter adapter;
    protected EditText search;
    protected ChipGroup filters;
    protected TextView summary;
    protected ExtendedFloatingActionButton fab;
    private View empty;
    private RecyclerView list;
    protected int filterIndex;
    private Runnable pendingSearch;

    /** Resultado do carregamento: linhas e um texto de resumo opcional. */
    public static class Page {
        public List<Row> rows;
        public String summary;

        public Page(List<Row> rows, String summary) {
            this.rows = rows;
            this.summary = summary;
        }
    }

    protected abstract String title();

    /** Executado em segundo plano. */
    protected abstract Page load(String query, int filter);

    protected abstract void onRowClick(Row row);

    protected String[] filterLabels() {
        return null;
    }

    protected String fabLabel() {
        return getString(R.string.action_new);
    }

    /** null = sem botão de criação. */
    protected Runnable fabAction() {
        return null;
    }

    protected String emptyText() {
        return getString(R.string.empty_default);
    }

    protected int emptyIcon() {
        return R.drawable.ic_inventory;
    }

    protected boolean showSearch() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar(title(), true);
        search = findViewById(R.id.search_input);
        filters = findViewById(R.id.filter_group);
        summary = findViewById(R.id.list_summary);
        fab = findViewById(R.id.fab);
        empty = findViewById(R.id.empty_view);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter(this::onRowClick);
        list.setAdapter(adapter);
        ((TextView) findViewById(R.id.empty_text)).setText(emptyText());
        ((ImageView) findViewById(R.id.empty_icon)).setImageResource(emptyIcon());
        findViewById(R.id.search_layout).setVisibility(showSearch() ? View.VISIBLE : View.GONE);

        String[] labels = filterLabels();
        if (labels == null) {
            findViewById(R.id.filter_scroll).setVisibility(View.GONE);
        } else {
            for (int i = 0; i < labels.length; i++) {
                Chip chip = new Chip(this);
                chip.setText(labels[i]);
                chip.setCheckable(true);
                chip.setId(View.generateViewId());
                chip.setTag(i);
                filters.addView(chip);
                if (i == 0) chip.setChecked(true);
            }
            filters.setOnCheckedStateChangeListener((g, ids) -> {
                if (ids.isEmpty()) return;
                Chip c = g.findViewById(ids.get(0));
                filterIndex = (Integer) c.getTag();
                reload();
            });
        }
        Runnable action = fabAction();
        if (action == null) fab.setVisibility(View.GONE);
        else {
            fab.setText(fabLabel());
            fab.setOnClickListener(v -> action.run());
        }
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy > 6) fab.shrink();
                else if (dy < -6) fab.extend();
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null) list.removeCallbacks(pendingSearch);
                pendingSearch = SimpleListActivity.this::reload;
                list.postDelayed(pendingSearch, 250);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    protected void reload() {
        String q = Ui.text(search);
        int f = filterIndex;
        work(() -> load(q, f), page -> {
            adapter.setItems(page.rows);
            list.scheduleLayoutAnimation();
            empty.setVisibility(page.rows.isEmpty() ? View.VISIBLE : View.GONE);
            summary.setVisibility(page.summary == null ? View.GONE : View.VISIBLE);
            summary.setText(page.summary);
        });
    }
}
