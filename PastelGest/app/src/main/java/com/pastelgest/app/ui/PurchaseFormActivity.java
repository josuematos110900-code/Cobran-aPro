package com.pastelgest.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Money;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Supplier;
import com.pastelgest.app.repository.IngredientRepository;
import com.pastelgest.app.repository.PurchaseRepository;
import com.pastelgest.app.repository.SupplierRepository;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Registo de compra com vários ingredientes; também usado para corrigir uma compra. */
public class PurchaseFormActivity extends BaseActivity {
    public static final String MODE_CORRECT = "correct";

    private Dropdown<Supplier> supplier;
    private Dropdown<String> method;
    private EditText date, discount, paidAmount, notes;
    private MaterialSwitch paidNow;
    private LinearLayout itemsBox;
    private TextView itemsEmpty, subtotalView, totalView;
    private PurchaseRepository.Draft draft = new PurchaseRepository.Draft();
    private List<Ingredient> ingredients = new ArrayList<>();
    private String correctingId;
    private boolean paidEdited;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_form);
        boolean correcting = MODE_CORRECT.equals(getIntent().getStringExtra(EXTRA_MODE));
        correctingId = correcting ? extraId() : null;
        setupToolbar(correcting ? "Corrigir compra" : "Nova compra", true);
        supplier = new Dropdown<>(findViewById(R.id.pu_supplier));
        method = new Dropdown<>(findViewById(R.id.pu_method));
        date = findViewById(R.id.pu_date);
        discount = findViewById(R.id.pu_discount);
        paidAmount = findViewById(R.id.pu_paid_amount);
        notes = findViewById(R.id.pu_notes);
        paidNow = findViewById(R.id.pu_paid_now);
        itemsBox = findViewById(R.id.pu_items);
        itemsEmpty = findViewById(R.id.pu_items_empty);
        subtotalView = findViewById(R.id.pu_subtotal);
        totalView = findViewById(R.id.pu_total);
        method.setItems(Ui.paymentMethods(this), m -> m);
        method.select(0);
        date.setOnClickListener(v -> Ui.pickDate(this, draft.date, d -> {
            draft.date = d;
            date.setText(Fmt.date(d));
        }));
        paidNow.setChecked(true);
        paidNow.setOnCheckedChangeListener((b, c) -> updatePaymentVisibility());
        Watcher.on(this::refreshTotals, discount);
        paidAmount.setOnFocusChangeListener((v, f) -> {
            if (f) paidEdited = true;
        });
        findViewById(R.id.pu_add_item).setOnClickListener(v -> editItem(-1));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        if (correcting) {
            TextView note = findViewById(R.id.pu_correction_note);
            note.setVisibility(View.VISIBLE);
            note.setText("A compra original será anulada (com estorno do stock) e substituída por esta versão. "
                    + "O valor já pago transita automaticamente. A operação fica registada na auditoria.");
            findViewById(R.id.pu_paid_now).setVisibility(View.GONE);
            findViewById(R.id.pu_paid_amount_layout).setVisibility(View.GONE);
        }
        work(() -> {
            List<Object> out = new ArrayList<>();
            out.add(new SupplierRepository(this).search(""));
            out.add(new IngredientRepository(this).search(""));
            out.add(correctingId == null ? null : new PurchaseRepository(this).draftFrom(correctingId));
            return out;
        }, data -> {
            @SuppressWarnings("unchecked") List<Supplier> sups = (List<Supplier>) data.get(0);
            @SuppressWarnings("unchecked") List<Ingredient> ings = (List<Ingredient>) data.get(1);
            ingredients = ings;
            List<Supplier> withNone = new ArrayList<>();
            Supplier none = new Supplier();
            none.name = "— Sem fornecedor —";
            withNone.add(none);
            withNone.addAll(sups);
            supplier.setItems(withNone, s -> s.name);
            supplier.select(0);
            if (data.get(2) != null) {
                PurchaseRepository.Draft d = (PurchaseRepository.Draft) data.get(2);
                draft.items.addAll(d.items);
                draft.date = d.date;
                draft.discountCents = d.discountCents;
                draft.paymentMethod = d.paymentMethod;
                draft.notes = d.notes;
                draft.supplierId = d.supplierId;
                if (d.supplierId != null) supplier.selectWhere(s -> d.supplierId.equals(s.id));
                discount.setText(d.discountCents > 0 ? Money.formatInput(d.discountCents) : "");
                notes.setText(d.notes);
                if (d.paymentMethod != null) method.selectWhere(m -> m.equals(d.paymentMethod));
            }
            if (ingredients.isEmpty()) {
                Ui.info(this, "Sem ingredientes", "Registe primeiro os ingredientes no separador Stock.");
            }
            date.setText(Fmt.date(draft.date));
            renderItems();
        });
        updatePaymentVisibility();
    }

    private void updatePaymentVisibility() {
        boolean on = paidNow.isChecked() || correctingId != null;
        findViewById(R.id.pu_paid_amount_layout).setVisibility(paidNow.isChecked() && correctingId == null ? View.VISIBLE : View.GONE);
        findViewById(R.id.pu_method_layout).setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private long discountValue() {
        try {
            return Money.parseOptional(Ui.text(discount));
        } catch (BusinessException e) {
            return 0;
        }
    }

    private void refreshTotals() {
        long sub = draft.subtotal();
        long total = sub - discountValue();
        subtotalView.setText("Subtotal: " + Fmt.money(sub));
        totalView.setText("Total: " + Fmt.money(total));
        if (!paidEdited && correctingId == null) paidAmount.setText(total > 0 ? Money.formatInput(total) : "");
    }

    private void renderItems() {
        itemsBox.removeAllViews();
        for (int i = 0; i < draft.items.size(); i++) {
            PurchaseRepository.DraftItem it = draft.items.get(i);
            View line = LayoutInflater.from(this).inflate(R.layout.item_line, itemsBox, false);
            ((TextView) line.findViewById(R.id.line_title)).setText(it.ingredientName);
            ((TextView) line.findViewById(R.id.line_subtitle)).setText(Units.format(it.qtyMilli, it.unit) + " × "
                    + Fmt.money(it.unitPriceCents) + "/" + it.unit
                    + (it.lineDiscountCents > 0 ? " − desc. " + Fmt.money(it.lineDiscountCents) : ""));
            ((TextView) line.findViewById(R.id.line_value)).setText(Fmt.money(it.lineTotal()));
            final int idx = i;
            line.setOnClickListener(v -> editItem(idx));
            line.findViewById(R.id.line_remove).setOnClickListener(v -> {
                draft.items.remove(idx);
                renderItems();
            });
            itemsBox.addView(line);
        }
        itemsEmpty.setVisibility(draft.items.isEmpty() ? View.VISIBLE : View.GONE);
        refreshTotals();
    }

    private void editItem(int index) {
        if (ingredients.isEmpty()) {
            Ui.info(this, "Sem ingredientes", "Registe primeiro os ingredientes no separador Stock.");
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_purchase_item, null);
        Dropdown<Ingredient> ing = new Dropdown<>(v.findViewById(R.id.di_ingredient));
        Dropdown<String> unit = new Dropdown<>(v.findViewById(R.id.di_unit));
        EditText qty = v.findViewById(R.id.di_qty);
        EditText price = v.findViewById(R.id.di_price);
        EditText disc = v.findViewById(R.id.di_discount);
        TextView preview = v.findViewById(R.id.di_preview);
        ing.setItems(ingredients, i -> i.name + " (" + Units.format(i.stockQtyMilli, i.displayUnit) + ")");
        ing.setListener(i -> {
            unit.setItems(Arrays.asList(Units.compatible(i.unitType)), u -> u);
            unit.selectWhere(u -> u.equals(i.displayUnit));
            if (i.lastPurchaseQtyMilli > 0 && Ui.text(price).isEmpty()) {
                price.setText(Money.formatInput(com.pastelgest.app.core.CostMath.pricePerUnit(
                        i.lastPurchaseCostCents, i.lastPurchaseQtyMilli, i.displayUnit)));
            }
        });
        Runnable updatePreview = () -> {
            try {
                PurchaseRepository.DraftItem t = buildItem(ing, unit, qty, price, disc);
                preview.setText("Total da linha: " + Fmt.money(t.lineTotal()));
            } catch (BusinessException e) {
                preview.setText(e.getMessage());
            }
        };
        unit.setListener(u -> {
            ((com.google.android.material.textfield.TextInputLayout) v.findViewById(R.id.di_qty_layout)).setSuffixText(u);
            updatePreview.run();
        });
        Watcher.on(updatePreview, qty, price, disc);
        if (index >= 0) {
            PurchaseRepository.DraftItem it = draft.items.get(index);
            ing.selectWhere(i -> i.id.equals(it.ingredientId));
            unit.selectWhere(u -> u.equals(it.unit));
            qty.setText(Units.formatInput(it.qtyMilli, it.unit));
            price.setText(Money.formatInput(it.unitPriceCents));
            if (it.lineDiscountCents > 0) disc.setText(Money.formatInput(it.lineDiscountCents));
        }
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(index >= 0 ? "Editar produto" : "Adicionar produto")
                .setView(v)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            try {
                PurchaseRepository.DraftItem it = buildItem(ing, unit, qty, price, disc);
                for (int k = 0; k < draft.items.size(); k++) {
                    if (k != index && draft.items.get(k).ingredientId.equals(it.ingredientId)) {
                        throw new BusinessException("Este ingrediente já está na compra. Edite a linha existente.");
                    }
                }
                if (index >= 0) draft.items.set(index, it);
                else draft.items.add(it);
                d.dismiss();
                renderItems();
            } catch (BusinessException e) {
                preview.setText(e.getMessage());
            }
        }));
        d.show();
    }

    private PurchaseRepository.DraftItem buildItem(Dropdown<Ingredient> ing, Dropdown<String> unit,
                                                   EditText qty, EditText price, EditText disc) {
        Ingredient i = ing.selected();
        if (i == null) throw new BusinessException("Seleccione o ingrediente.");
        String u = unit.selected();
        if (u == null) throw new BusinessException("Seleccione a unidade.");
        PurchaseRepository.DraftItem it = new PurchaseRepository.DraftItem();
        it.ingredientId = i.id;
        it.ingredientName = i.name;
        it.unit = u;
        it.qtyMilli = Units.parseToBaseMilli(Ui.text(qty), u);
        if (it.qtyMilli <= 0) throw new BusinessException("A quantidade tem de ser superior a zero.");
        it.unitPriceCents = Money.parseNonNegative(Ui.text(price));
        it.lineDiscountCents = Money.parseOptional(Ui.text(disc));
        if (it.lineTotal() < 0) throw new BusinessException("O desconto é superior ao valor da linha.");
        return it;
    }

    private void save() {
        try {
            Supplier s = supplier.selected();
            draft.supplierId = s == null || s.id.isEmpty() ? null : s.id;
            draft.discountCents = Money.parseOptional(Ui.text(discount));
            draft.notes = Ui.text(notes);
            draft.paymentMethod = method.selected();
            if (correctingId == null) {
                draft.paidNowCents = paidNow.isChecked() ? Money.parseNonNegative(Ui.text(paidAmount)) : 0;
            }
            if (draft.items.isEmpty()) throw new BusinessException("Adicione pelo menos um produto à compra.");
            if (correctingId != null) {
                Ui.askText(this, "Motivo da correcção", "Explique o que está a ser corrigido.", null, "Corrigir",
                        reason -> work(() -> new PurchaseRepository(this).correct(correctingId, draft, reason), p -> {
                            Ui.toast(this, "Compra corrigida: " + p.number);
                            Ui.open(this, PurchaseDetailActivity.class, p.id);
                            finish();
                        }));
            } else {
                work(() -> new PurchaseRepository(this).create(draft), p -> {
                    Ui.toast(this, "Compra registada: " + p.number + ". Stock actualizado.");
                    finish();
                });
            }
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }
}
