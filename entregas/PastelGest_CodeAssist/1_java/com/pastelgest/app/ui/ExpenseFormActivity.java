package com.pastelgest.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Money;
import com.pastelgest.app.data.entity.Expense;
import com.pastelgest.app.data.entity.SyncEntity;
import com.pastelgest.app.repository.ExpenseRepository;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.ImageStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Registo e edição de despesas (a edição fica registada na auditoria). */
public class ExpenseFormActivity extends BaseActivity {
    private static final int MENU_CANCEL = 1;
    private AutoCompleteTextView category;
    private EditText description, amount, date;
    private Dropdown<String> method;
    private MaterialSwitch fixed;
    private ImageView receiptImage;
    private TextView receiptStatus;
    private long dateMillis = System.currentTimeMillis();
    private String receiptPath;
    private Expense editing;
    private final String newId = SyncEntity.newId();
    private ActivityResultLauncher<String> pickImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_form);
        String id = extraId();
        setupToolbar(id == null ? "Nova despesa" : "Editar despesa", true);
        category = findViewById(R.id.ef_category);
        description = findViewById(R.id.ef_description);
        amount = findViewById(R.id.ef_amount);
        date = findViewById(R.id.ef_date);
        fixed = findViewById(R.id.ef_fixed);
        receiptImage = findViewById(R.id.ef_receipt_image);
        receiptStatus = findViewById(R.id.ef_receipt_status);
        method = new Dropdown<>(findViewById(R.id.ef_method));
        method.setItems(Ui.paymentMethods(this), m -> m);
        method.select(0);
        date.setText(Fmt.date(dateMillis));
        date.setOnClickListener(v -> Ui.pickDate(this, dateMillis, d -> {
            dateMillis = d;
            date.setText(Fmt.date(d));
        }));
        pickImage = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            String target = editing == null ? newId : editing.id;
            work(() -> ImageStore.copy(this, uri, "receipt_" + target), path -> {
                receiptPath = path;
                showReceipt();
            });
        });
        findViewById(R.id.ef_receipt_pick).setOnClickListener(v -> pickImage.launch("image/*"));
        findViewById(R.id.ef_receipt_remove).setOnClickListener(v -> {
            receiptPath = null;
            showReceipt();
        });
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        work(() -> {
            ExpenseRepository repo = new ExpenseRepository(this);
            Set<String> cats = new LinkedHashSet<>(Arrays.asList(ExpenseRepository.DEFAULT_CATEGORIES));
            cats.addAll(repo.categories());
            List<Object> out = new ArrayList<>();
            out.add(new ArrayList<>(cats));
            out.add(id == null ? null : repo.get(id));
            return out;
        }, data -> {
            @SuppressWarnings("unchecked") List<String> cats = (List<String>) data.get(0);
            Ui.suggestions(category, cats);
            editing = (Expense) data.get(1);
            if (editing != null) fill(editing);
            showReceipt();
        });
    }

    private void fill(Expense e) {
        category.setText(e.category, false);
        description.setText(e.description);
        amount.setText(Money.formatInput(e.amountCents));
        dateMillis = e.date;
        date.setText(Fmt.date(e.date));
        method.selectWhere(m -> m.equals(e.paymentMethod));
        fixed.setChecked(e.fixed == 1);
        receiptPath = e.receiptPath;
        invalidateOptionsMenu();
    }

    private void showReceipt() {
        boolean has = receiptPath != null && new File(receiptPath).exists();
        receiptImage.setVisibility(has ? View.VISIBLE : View.GONE);
        receiptStatus.setText(has ? "Comprovativo anexado (guardado apenas neste dispositivo)." : getString(R.string.receipt_none));
        if (has) receiptImage.setImageURI(Uri.fromFile(new File(receiptPath)));
        findViewById(R.id.ef_receipt_remove).setVisibility(has ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (editing != null && Expense.ACTIVE.equals(editing.status)) {
            menu.add(0, MENU_CANCEL, 0, "Anular despesa").setIcon(R.drawable.ic_block).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_CANCEL) {
            Ui.askText(this, "Anular despesa", "A despesa fica registada como anulada e deixa de contar no lucro.",
                    null, "Anular", reason -> work(() -> {
                        new ExpenseRepository(this).cancel(editing.id, reason);
                        return true;
                    }, r -> {
                        Ui.toast(this, "Despesa anulada.");
                        finish();
                    }));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        try {
            Expense e = new Expense();
            e.id = editing == null ? newId : editing.id;
            e.category = category.getText().toString().trim();
            e.description = Ui.text(description);
            e.amountCents = Money.parseNonNegative(Ui.text(amount));
            e.date = dateMillis;
            e.paymentMethod = method.selected();
            e.fixed = fixed.isChecked() ? 1 : 0;
            e.receiptPath = receiptPath;
            work(() -> new ExpenseRepository(this).save(e), saved -> {
                Ui.toast(this, "Despesa guardada.");
                finish();
            });
        } catch (BusinessException ex) {
            Ui.showError(this, ex);
        }
    }
}
