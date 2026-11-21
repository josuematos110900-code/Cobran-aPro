package com.pastelgest.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.ui.adapter.RowAdapter;
import com.pastelgest.app.util.Async;

/** Fragmento de lista genérico para os separadores principais. */
public abstract class ListFragment extends Fragment {
    protected RowAdapter adapter;
    protected EditText search;
    protected TextView summary, headerTitle, headerSubtitle;
    protected ExtendedFloatingActionButton fab;
    protected TabLayout tabs;
    protected ChipGroup filters;
    protected View empty, progress;
    protected RecyclerView list;
    protected int filterIndex;
    protected int tabIndex;
    private Runnable pendingSearch;

    protected abstract String title();

    protected abstract String subtitle();

    protected abstract String[] filterLabels();

    /** Executado em segundo plano. */
    protected abstract SimpleListActivity.Page load(String query, int filter, int tab);

    protected abstract void onRowClick(Row row);

    protected abstract void onFab();

    protected String fabLabel() {
        return getString(R.string.action_new);
    }

    protected String[] tabLabels() {
        return null;
    }

    protected String emptyText() {
        return getString(R.string.empty_default);
    }

    protected int emptyIcon() {
        return R.drawable.ic_inventory;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        headerTitle = v.findViewById(R.id.header_title);
        headerSubtitle = v.findViewById(R.id.header_subtitle);
        headerTitle.setText(title());
        headerSubtitle.setText(subtitle());
        search = v.findViewById(R.id.search_input);
        summary = v.findViewById(R.id.list_summary);
        fab = v.findViewById(R.id.fab);
        tabs = v.findViewById(R.id.tabs);
        filters = v.findViewById(R.id.filter_group);
        empty = v.findViewById(R.id.empty_view);
        progress = v.findViewById(R.id.progress);
        list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RowAdapter(this::onRowClick);
        list.setAdapter(adapter);
        ((ImageView) v.findViewById(R.id.empty_icon)).setImageResource(emptyIcon());
        fab.setText(fabLabel());
        fab.setOnClickListener(x -> onFab());

        String[] tl = tabLabels();
        if (tl != null) {
            tabs.setVisibility(View.VISIBLE);
            for (String t : tl) tabs.addTab(tabs.newTab().setText(t));
            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    tabIndex = tab.getPosition();
                    fab.setText(fabLabel());
                    reload();
                }

                @Override public void onTabUnselected(TabLayout.Tab tab) { }
                @Override public void onTabReselected(TabLayout.Tab tab) { }
            });
        }
        String[] labels = filterLabels();
        for (int i = 0; i < labels.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            chip.setTag(i);
            filters.addView(chip);
            if (i == filterIndex) chip.setChecked(true);
        }
        filters.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            filterIndex = (Integer) g.findViewById(ids.get(0)).getTag();
            reload();
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
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
                pendingSearch = ListFragment.this::reload;
                list.postDelayed(pendingSearch, 250);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    protected void reload() {
        if (getView() == null) return;
        String q = Ui.text(search);
        int f = filterIndex, t = tabIndex;
        progress.setVisibility(View.VISIBLE);
        Async.run(() -> load(q, f, t), page -> {
            if (!isAdded()) return;
            progress.setVisibility(View.GONE);
            adapter.setItems(page.rows);
            list.scheduleLayoutAnimation();
            ((TextView) empty.findViewById(R.id.empty_text)).setText(emptyText());
            empty.setVisibility(page.rows.isEmpty() ? View.VISIBLE : View.GONE);
            summary.setVisibility(page.summary == null ? View.GONE : View.VISIBLE);
            summary.setText(page.summary);
        }, e -> {
            if (!isAdded()) return;
            progress.setVisibility(View.GONE);
            Ui.showError(requireContext(), e);
        });
    }
}
