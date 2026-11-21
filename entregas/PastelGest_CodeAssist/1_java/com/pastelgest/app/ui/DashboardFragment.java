package com.pastelgest.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.pastelgest.app.R;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.repository.ReportRepository;
import com.pastelgest.app.repository.SettingsRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.ui.adapter.RowAdapter;
import com.pastelgest.app.ui.widget.BarChartView;
import com.pastelgest.app.util.Async;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.Prefs;

import java.io.File;

/** Painel inicial: indicadores calculados a partir da base de dados. */
public class DashboardFragment extends Fragment {
    private View root;
    private int period = Period.DAY;
    private String businessName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        root = v;
        MaterialButtonToggleGroup toggle = v.findViewById(R.id.dash_period);
        toggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            period = id == R.id.dash_period_week ? Period.WEEK : id == R.id.dash_period_month ? Period.MONTH : Period.DAY;
            reload();
        });
        SwipeRefreshLayout refresh = v.findViewById(R.id.dash_refresh);
        refresh.setOnRefreshListener(this::reload);
        v.findViewById(R.id.dash_new_sale).setOnClickListener(x -> Ui.open(requireContext(), SaleFormActivity.class, null));
        v.findViewById(R.id.dash_new_production).setOnClickListener(x -> Ui.open(requireContext(), ProductionFormActivity.class, null));
        v.findViewById(R.id.dash_new_purchase).setOnClickListener(x -> Ui.open(requireContext(), PurchaseFormActivity.class, null));
        v.findViewById(R.id.dash_new_expense).setOnClickListener(x -> Ui.open(requireContext(), ExpenseFormActivity.class, null));
        v.findViewById(R.id.dash_sync).setOnClickListener(x -> Ui.open(requireContext(), SyncActivity.class, null));
        ((TextView) v.findViewById(R.id.dash_date)).setText(Fmt.date(System.currentTimeMillis()));
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        final int p = period;
        final android.content.Context ctx = requireContext().getApplicationContext();
        Async.run(() -> {
            businessName = new SettingsRepository(ctx).businessName();
            return new ReportRepository(ctx).dashboard(Period.of(p));
        }, this::render, e -> {
            if (!isAdded()) return;
            ((SwipeRefreshLayout) root.findViewById(R.id.dash_refresh)).setRefreshing(false);
            Ui.showError(requireContext(), e);
        });
    }

    private void render(ReportRepository.Dashboard d) {
        if (!isAdded()) return;
        ((SwipeRefreshLayout) root.findViewById(R.id.dash_refresh)).setRefreshing(false);
        ((TextView) root.findViewById(R.id.dash_business)).setText(businessName);
        ImageView logo = root.findViewById(R.id.dash_logo);
        String logoPath = Prefs.str(requireContext(), Prefs.LOGO_PATH, null);
        if (logoPath != null && new File(logoPath).exists()) {
            logo.clearColorFilter();
            logo.setImageTintList(null);
            logo.setPadding(0, 0, 0, 0);
            logo.setImageURI(Uri.fromFile(new File(logoPath)));
        }
        ((TextView) root.findViewById(R.id.dash_sales_value)).setText(Fmt.money(d.salesCents));
        ((TextView) root.findViewById(R.id.dash_profit_value)).setText(Fmt.money(d.profit.netProfitCents));
        ((TextView) root.findViewById(R.id.dash_received_value)).setText(Fmt.money(d.profit.in.receivedCents));

        kpi(R.id.kpi_expenses, "Despesas", Fmt.money(d.expensesCents), R.drawable.ic_money, Row.TONE_RED);
        kpi(R.id.kpi_gross, "Lucro bruto", Fmt.money(d.profit.grossProfitCents), R.drawable.ic_trending,
                d.profit.grossProfitCents < 0 ? Row.TONE_RED : Row.TONE_GREEN);
        kpi(R.id.kpi_produced, "Pastéis produzidos", d.produced + " un", R.drawable.ic_flame, Row.TONE_ACCENT);
        kpi(R.id.kpi_sold, "Pastéis vendidos", d.sold + " un", R.drawable.ic_sales, Row.TONE_GREEN);
        kpi(R.id.kpi_debts, "Dívidas pendentes", Fmt.money(d.debtsOpenCents), R.drawable.ic_wallet,
                d.debtsOpenCents > 0 ? Row.TONE_ORANGE : Row.TONE_ACCENT);
        kpi(R.id.kpi_ingredients, "Ingredientes em stock", d.ingredientsInStock + " de " + d.ingredientsTotal,
                R.drawable.ic_kitchen, d.lowIngredients.isEmpty() ? Row.TONE_ACCENT : Row.TONE_RED);
        kpi(R.id.kpi_stock_value, "Valor do stock", Fmt.money(d.profit.in.stockValueCents), R.drawable.ic_inventory, Row.TONE_ACCENT);
        kpi(R.id.kpi_products, "Pastéis prontos", d.productUnits + " un", R.drawable.ic_cake, Row.TONE_GREEN);

        String[] labels = new String[d.chartDays.length];
        for (int i = 0; i < labels.length; i++) labels[i] = Fmt.dayShort(d.chartDays[i]);
        ((BarChartView) root.findViewById(R.id.dash_chart)).setData(labels, d.chartSales, d.chartProfit);

        LinearLayout alerts = root.findViewById(R.id.dash_alerts);
        alerts.removeAllViews();
        for (Ingredient i : d.lowIngredients) {
            alert(alerts, new Row(i.id, i.name, "Stock baixo: " + Units.format(i.stockQtyMilli, i.displayUnit)
                    + " (mínimo " + Units.format(i.minStockMilli, i.displayUnit) + ")", null)
                    .icon(R.drawable.ic_warning, Row.TONE_RED), x -> Ui.open(requireContext(), IngredientDetailActivity.class, i.id));
        }
        for (Product p : d.lowProducts) {
            alert(alerts, new Row(p.id, p.name, "Pastéis prontos: " + p.stockQty + " un (mínimo " + p.minStock + ")", null)
                    .icon(R.drawable.ic_cake, Row.TONE_ORANGE), x -> Ui.open(requireContext(), ProductDetailActivity.class, p.id));
        }
        if (d.debtsOverdue > 0) {
            alert(alerts, new Row(null, d.debtsOverdue + " dívida(s) vencida(s)", "Total em aberto: " + Fmt.money(d.debtsOpenCents), null)
                    .icon(R.drawable.ic_wallet, Row.TONE_RED), x -> Ui.open(requireContext(), DebtListActivity.class, null));
        } else if (d.debtsOpenCents > 0) {
            alert(alerts, new Row(null, "Pagamentos pendentes", "Total por receber: " + Fmt.money(d.debtsOpenCents), null)
                    .icon(R.drawable.ic_wallet, Row.TONE_ORANGE), x -> Ui.open(requireContext(), DebtListActivity.class, null));
        }
        if (alerts.getChildCount() == 0) {
            alert(alerts, new Row(null, "Tudo em ordem", "Sem alertas de stock ou de pagamentos.", null)
                    .icon(R.drawable.ic_check, Row.TONE_GREEN).noChevron(), null);
        }
    }

    private void kpi(int includeId, String label, String value, int icon, int tone) {
        View card = root.findViewById(includeId);
        ((TextView) card.findViewById(R.id.kpi_label)).setText(label);
        TextView val = card.findViewById(R.id.kpi_value);
        val.setText(value);
        val.setTextColor(ContextCompat.getColor(requireContext(), tone == Row.TONE_RED ? R.color.pg_red : R.color.pg_text));
        ImageView ic = card.findViewById(R.id.kpi_icon);
        ic.setImageResource(icon);
        ic.setColorFilter(ContextCompat.getColor(requireContext(), Ui.toneIcon(tone)));
        ((FrameLayout) card.findViewById(R.id.kpi_icon_box)).setBackgroundResource(Ui.toneBox(tone));
    }

    private void alert(LinearLayout parent, Row r, View.OnClickListener l) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_row, parent, false);
        RowAdapter.bind(v, r);
        if (l != null) v.setOnClickListener(l);
        parent.addView(v);
    }
}
