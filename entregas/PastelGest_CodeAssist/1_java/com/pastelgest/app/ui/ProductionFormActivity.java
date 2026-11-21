package com.pastelgest.app.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.data.entity.SyncEntity;
import com.pastelgest.app.repository.ProductionRepository;
import com.pastelgest.app.repository.RecipeRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.List;

/** Ordem de produção: calcula necessidades, verifica o stock e confirma. */
public class ProductionFormActivity extends BaseActivity {
    private Dropdown<Recipe> recipe;
    private EditText qty, lost, notes;
    private TextView status;
    private LinearLayout needs, costs;
    private final String productionId = SyncEntity.newId();
    private ProductionRepository.Plan lastPlan;
    private boolean confirmed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_production_form);
        setupToolbar("Nova produção", true);
        recipe = new Dropdown<>(findViewById(R.id.po_recipe));
        qty = findViewById(R.id.po_qty);
        lost = findViewById(R.id.po_lost);
        notes = findViewById(R.id.po_notes);
        status = findViewById(R.id.po_status);
        needs = findViewById(R.id.po_needs);
        costs = findViewById(R.id.po_costs);
        recipe.setListener(r -> {
            if (Ui.text(qty).isEmpty()) qty.setText(String.valueOf(r.yieldQty));
            check();
        });
        findViewById(R.id.po_check).setOnClickListener(v -> check());
        findViewById(R.id.btn_save).setOnClickListener(v -> confirm());
        Watcher.on(() -> lastPlan = null, qty);
        work(() -> new RecipeRepository(this).search(""), list -> {
            recipe.setItems(list, r -> r.name + " (rende " + r.yieldQty + ")");
            if (list.isEmpty()) {
                Ui.info(this, "Sem receitas", "Crie primeiro uma receita em Mais › Receitas e custos.");
            }
            String pre = extraId();
            if (pre != null) recipe.selectWhere(r -> r.id.equals(pre));
        });
    }

    private long parseInt(EditText e, String field) {
        String t = Ui.text(e);
        if (t.isEmpty()) return 0;
        try {
            return Long.parseLong(t.replace(",", ".").split("\\.")[0]);
        } catch (NumberFormatException ex) {
            throw new BusinessException(field + " inválido.");
        }
    }

    private void check() {
        try {
            Recipe r = recipe.selected();
            if (r == null) throw new BusinessException("Seleccione a receita.");
            long q = parseInt(qty, "Quantidade");
            if (q <= 0) throw new BusinessException("Indique a quantidade a produzir.");
            work(() -> new ProductionRepository(this).plan(r.id, q), this::renderPlan);
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }

    private void renderPlan(ProductionRepository.Plan p) {
        lastPlan = p;
        needs.removeAllViews();
        costs.removeAllViews();
        for (ProductionRepository.Need n : p.needs) {
            boolean ok = n.missingMilli() == 0;
            Ui.addKv(needs, n.name + (ok ? "" : " — faltam " + Units.format(n.missingMilli(), n.unit)),
                    Units.format(n.requiredMilli, n.unit) + " / " + Units.format(n.availableMilli, n.unit),
                    ok ? Row.TONE_GREEN : Row.TONE_RED);
        }
        Ui.addKv(costs, "Ingredientes (custo médio)", Fmt.money(p.ingredientsCostCents), Row.TONE_NONE);
        Ui.addKv(costs, "Embalagem", Fmt.money(p.packagingCostCents), Row.TONE_NONE);
        Ui.addKv(costs, "Outros custos", Fmt.money(p.otherCostCents), Row.TONE_NONE);
        Ui.addKv(costs, "Custo total estimado", Fmt.money(p.totalCostCents()), Row.TONE_ACCENT);
        Ui.addKv(costs, "Custo estimado por pastel", Fmt.money(com.pastelgest.app.core.Money.divide(p.totalCostCents(), p.quantity)), Row.TONE_ACCENT);
        boolean feasible = p.feasible();
        status.setText(feasible ? "Stock suficiente. Pode confirmar a produção."
                : "Stock insuficiente. Registe as compras em falta antes de produzir.");
        status.setTextColor(ContextCompat.getColor(this, feasible ? R.color.pg_green : R.color.pg_red));
        findViewById(R.id.btn_save).setEnabled(feasible);
    }

    private void confirm() {
        if (confirmed) return;
        try {
            Recipe r = recipe.selected();
            if (r == null) throw new BusinessException("Seleccione a receita.");
            long q = parseInt(qty, "Quantidade");
            long l = parseInt(lost, "Perdas");
            if (lastPlan == null || lastPlan.quantity != q || !lastPlan.recipe.id.equals(r.id)) {
                check();
                return;
            }
            if (!lastPlan.feasible()) {
                Ui.showError(this, new BusinessException(ProductionRepository.missingText(lastPlan)));
                return;
            }
            List<ProductionRepository.Need> list = lastPlan.needs;
            StringBuilder sb = new StringBuilder("Serão descontados do stock:\n");
            for (ProductionRepository.Need n : list) sb.append("• ").append(n.name).append(": ").append(Units.format(n.requiredMilli, n.unit)).append("\n");
            sb.append("\nEntram em stock ").append(q - l).append(" pastéis").append(l > 0 ? " (" + l + " perdidos)." : ".");
            Ui.confirm(this, "Confirmar produção", sb.toString(), "Confirmar", () ->
                    work(() -> new ProductionRepository(this).confirm(productionId, r.id, q, l, Ui.text(notes)), p -> {
                        confirmed = true;
                        Ui.toast(this, "Produção " + p.number + " confirmada. Custo: " + Fmt.money(p.unitCostCents) + "/pastel");
                        Ui.open(this, ProductionDetailActivity.class, p.id);
                        finish();
                    }));
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }
}
