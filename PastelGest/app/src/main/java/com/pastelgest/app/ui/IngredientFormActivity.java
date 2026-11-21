package com.pastelgest.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Money;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Supplier;
import com.pastelgest.app.repository.IngredientRepository;
import com.pastelgest.app.repository.SupplierRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Criação e edição de ingredientes. */
public class IngredientFormActivity extends BaseActivity {
    private EditText name, minStock, notes, initialQty, initialValue;
    private AutoCompleteTextView category;
    private Dropdown<Integer> unitType;
    private Dropdown<String> unit;
    private Dropdown<Supplier> supplier;
    private TextInputLayout nameLayout, minLayout, qtyLayout;
    private Ingredient editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ingredient_form);
        String id = extraId();
        setupToolbar(id == null ? "Novo ingrediente" : "Editar ingrediente", true);
        name = findViewById(R.id.in_name);
        nameLayout = findViewById(R.id.in_name_layout);
        category = findViewById(R.id.in_category);
        minStock = findViewById(R.id.in_min_stock);
        minLayout = findViewById(R.id.in_min_stock_layout);
        notes = findViewById(R.id.in_notes);
        initialQty = findViewById(R.id.in_initial_qty);
        qtyLayout = findViewById(R.id.in_initial_qty_layout);
        initialValue = findViewById(R.id.in_initial_value);
        unitType = new Dropdown<>(findViewById(R.id.in_unit_type));
        unit = new Dropdown<>(findViewById(R.id.in_unit));
        supplier = new Dropdown<>(findViewById(R.id.in_supplier));

        String[] typeLabels = getResources().getStringArray(R.array.unit_types);
        unitType.setItems(Arrays.asList(Units.TYPE_MASS, Units.TYPE_VOLUME, Units.TYPE_UNIT), t -> typeLabels[t - 1]);
        unitType.setListener(t -> {
            unit.setItems(Arrays.asList(Units.compatible(t)), u -> u);
            unit.select(0);
        });
        unit.setListener(u -> {
            minLayout.setSuffixText(u);
            qtyLayout.setSuffixText(u);
        });
        unitType.select(0);
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        if (id != null) findViewById(R.id.in_initial_card).setVisibility(View.GONE);
        work(() -> {
            IngredientRepository repo = new IngredientRepository(this);
            Set<String> cats = new LinkedHashSet<>(repo.categories());
            cats.addAll(Arrays.asList(getResources().getStringArray(R.array.ingredient_categories)));
            List<Object> out = new ArrayList<>();
            out.add(new ArrayList<>(cats));
            out.add(new SupplierRepository(this).search(""));
            out.add(id == null ? null : repo.get(id));
            return out;
        }, data -> {
            @SuppressWarnings("unchecked") List<String> cats = (List<String>) data.get(0);
            @SuppressWarnings("unchecked") List<Supplier> sups = (List<Supplier>) data.get(1);
            Ui.suggestions(category, cats);
            List<Supplier> withNone = new ArrayList<>();
            Supplier none = new Supplier();
            none.name = "— Sem fornecedor —";
            withNone.add(none);
            withNone.addAll(sups);
            supplier.setItems(withNone, s -> s.name);
            editing = (Ingredient) data.get(2);
            if (editing != null) fill(editing);
        });
    }

    private void fill(Ingredient i) {
        name.setText(i.name);
        category.setText(i.category, false);
        unitType.selectWhere(t -> t == i.unitType);
        unit.selectWhere(u -> u.equals(i.displayUnit));
        minStock.setText(Units.formatInput(i.minStockMilli, i.displayUnit));
        notes.setText(i.notes);
        if (i.supplierId != null) supplier.selectWhere(s -> i.supplierId.equals(s.id));
    }

    private void save() {
        Ui.clearErrors(nameLayout, minLayout, qtyLayout);
        try {
            Ingredient in = new Ingredient();
            in.id = editing == null ? "" : editing.id;
            in.name = Ui.text(name);
            if (in.name.isEmpty()) {
                nameLayout.setError("Indique o nome.");
                return;
            }
            in.category = category.getText().toString().trim();
            Integer type = unitType.selected();
            String u = unit.selected();
            if (type == null || u == null) throw new BusinessException("Seleccione o tipo e a unidade de medida.");
            in.unitType = type;
            in.displayUnit = u;
            String min = Ui.text(minStock);
            in.minStockMilli = min.isEmpty() ? 0 : Units.parseToBaseMilli(min, u);
            Supplier s = supplier.selected();
            in.supplierId = s == null || s.id.isEmpty() ? null : s.id;
            in.notes = Ui.text(notes);
            long qty = 0, value = 0;
            if (editing == null && !Ui.text(initialQty).isEmpty()) {
                qty = Units.parseToBaseMilli(Ui.text(initialQty), u);
                value = Money.parseOptional(Ui.text(initialValue));
                if (qty > 0 && value == 0) {
                    throw new BusinessException("Indique o valor pago pelo stock inicial para calcular correctamente o custo.");
                }
            }
            final long q = qty, val = value;
            work(() -> new IngredientRepository(this).save(in, q, val), saved -> {
                Ui.toast(this, "Ingrediente guardado.");
                finish();
            });
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }
}
