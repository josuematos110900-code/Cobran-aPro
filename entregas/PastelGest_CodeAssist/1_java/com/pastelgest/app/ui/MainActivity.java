package com.pastelgest.app.ui;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.pastelgest.app.R;

/** Ecrã principal com navegação inferior entre os cinco módulos principais. */
public class MainActivity extends BaseActivity {
    private static final String KEY_TAB = "tab";
    private int current = R.id.nav_dashboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_PastelGest);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        nav.setOnItemReselectedListener(item -> { });
        if (savedInstanceState != null) current = savedInstanceState.getInt(KEY_TAB, R.id.nav_dashboard);
        nav.setSelectedItemId(current);
        if (savedInstanceState == null) show(current);
    }

    private void show(int itemId) {
        current = itemId;
        String tag = "tab_" + itemId;
        Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
        if (f == null) f = create(itemId);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.main_container, f, tag)
                .commit();
    }

    private Fragment create(int itemId) {
        if (itemId == R.id.nav_sales) return new SalesFragment();
        if (itemId == R.id.nav_production) return new ProductionFragment();
        if (itemId == R.id.nav_stock) return new StockFragment();
        if (itemId == R.id.nav_more) return new MoreFragment();
        return new DashboardFragment();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_TAB, current);
    }
}
