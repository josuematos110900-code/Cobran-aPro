package com.pastelgest.app.ui;

import android.os.Bundle;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.repository.ClientRepository;

public class ClientFormActivity extends BaseActivity {
    private EditText name, phone, address, notes;
    private TextInputLayout nameLayout;
    private Client editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_form);
        String id = extraId();
        setupToolbar(id == null ? "Novo cliente" : "Editar cliente", true);
        name = findViewById(R.id.cf_name);
        nameLayout = findViewById(R.id.cf_name_layout);
        phone = findViewById(R.id.cf_phone);
        address = findViewById(R.id.cf_address);
        notes = findViewById(R.id.cf_notes);
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        if (id != null) {
            work(() -> new ClientRepository(this).get(id), c -> {
                editing = c;
                name.setText(c.name);
                phone.setText(c.phone);
                address.setText(c.address);
                notes.setText(c.notes);
            });
        }
    }

    private void save() {
        nameLayout.setError(null);
        Client c = new Client();
        c.id = editing == null ? "" : editing.id;
        c.name = Ui.text(name);
        if (c.name.isEmpty()) {
            nameLayout.setError("Indique o nome.");
            return;
        }
        c.phone = Ui.text(phone);
        c.address = Ui.text(address);
        c.notes = Ui.text(notes);
        work(() -> new ClientRepository(this).save(c), saved -> {
            Ui.toast(this, "Cliente guardado.");
            setResult(RESULT_OK, getIntent().putExtra(EXTRA_ID, saved.id));
            finish();
        });
    }
}
