package com.pastelgest.app.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Money;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.repository.ProductRepository;

/** Produto acabado (pastel pronto a vender). */
public class ProductFormActivity extends BaseActivity {
    private EditText name, price, minStock;
    private TextInputLayout nameLayout;
    private Product editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);
        String id = extraId();
        setupToolbar(id == null ? "Novo produto" : "Editar produto", true);
        name = findViewById(R.id.pf_name);
        nameLayout = findViewById(R.id.pf_name_layout);
        price = findViewById(R.id.pf_price);
        minStock = findViewById(R.id.pf_min_stock);
        ((TextView) findViewById(R.id.pf_info)).setText("O stock de produtos aumenta apenas através de produções "
                + "(ou de ajustes de inventário) e diminui com vendas e desperdícios.");
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        if (id != null) {
            work(() -> new ProductRepository(this).get(id), p -> {
                editing = p;
                name.setText(p.name);
                price.setText(Money.formatInput(p.salePriceCents));
                minStock.setText(String.valueOf(p.minStock));
            });
        }
    }

    private void save() {
        nameLayout.setError(null);
        try {
            Product p = new Product();
            p.id = editing == null ? "" : editing.id;
            p.name = Ui.text(name);
            if (p.name.isEmpty()) {
                nameLayout.setError("Indique o nome.");
                return;
            }
            p.salePriceCents = Money.parseNonNegative(Ui.text(price));
            String m = Ui.text(minStock);
            try {
                p.minStock = m.isEmpty() ? 0 : Long.parseLong(m.replace(",", ".").split("\\.")[0]);
            } catch (NumberFormatException e) {
                throw new BusinessException("Stock mínimo inválido.");
            }
            p.recipeId = editing == null ? null : editing.recipeId;
            work(() -> new ProductRepository(this).save(p), saved -> {
                Ui.toast(this, "Produto guardado.");
                finish();
            });
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }
}
