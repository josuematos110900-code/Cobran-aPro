package com.pastelgest.app.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.Supplier;
import com.pastelgest.app.repository.SupplierRepository;

public class SupplierFormActivity extends BaseActivity {
    private static final int MENU_DELETE = 1;
    private EditText name, phone, email, nif, address, notes;
    private TextInputLayout nameLayout;
    private Supplier editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supplier_form);
        String id = extraId();
        setupToolbar(id == null ? "Novo fornecedor" : "Editar fornecedor", true);
        name = findViewById(R.id.sf_name);
        nameLayout = findViewById(R.id.sf_name_layout);
        phone = findViewById(R.id.sf_phone);
        email = findViewById(R.id.sf_email);
        nif = findViewById(R.id.sf_nif);
        address = findViewById(R.id.sf_address);
        notes = findViewById(R.id.sf_notes);
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        if (id != null) {
            work(() -> new SupplierRepository(this).get(id), s -> {
                editing = s;
                name.setText(s.name);
                phone.setText(s.phone);
                email.setText(s.email);
                nif.setText(s.nif);
                address.setText(s.address);
                notes.setText(s.notes);
                invalidateOptionsMenu();
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (editing != null) menu.add(0, MENU_DELETE, 0, "Eliminar").setIcon(R.drawable.ic_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_DELETE) {
            Ui.confirm(this, "Eliminar fornecedor", "O fornecedor deixa de aparecer nas listas. As compras antigas mantêm-se.",
                    "Eliminar", () -> work(() -> {
                        new SupplierRepository(this).archive(editing.id);
                        return true;
                    }, r -> finish()));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        nameLayout.setError(null);
        Supplier s = new Supplier();
        s.id = editing == null ? "" : editing.id;
        s.name = Ui.text(name);
        if (s.name.isEmpty()) {
            nameLayout.setError("Indique o nome.");
            return;
        }
        s.phone = Ui.text(phone);
        s.email = Ui.text(email);
        s.nif = Ui.text(nif);
        s.address = Ui.text(address);
        s.notes = Ui.text(notes);
        work(() -> new SupplierRepository(this).save(s), saved -> {
            Ui.toast(this, "Fornecedor guardado.");
            finish();
        });
    }
}
