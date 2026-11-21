package com.pastelgest.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Money;
import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.repository.ClientRepository;
import com.pastelgest.app.repository.ProductRepository;
import com.pastelgest.app.repository.SaleRepository;
import com.pastelgest.app.repository.SettingsRepository;
import com.pastelgest.app.util.Fmt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Registo de venda: produtos acabados, descontos, pagamento ou venda fiada. */
public class SaleFormActivity extends BaseActivity {
    private Dropdown<Client> client;
    private Dropdown<String> method;
    private EditText discount, down, due, notes;
    private LinearLayout itemsBox;
    private TextView itemsEmpty, subtotalView, discountsView, totalView, limitView, clientInfo;
    private MaterialButtonToggleGroup type;
    private final SaleRepository.Draft draft = new SaleRepository.Draft();
    private List<Product> products = new ArrayList<>();
    private BigDecimal maxDiscountPct = BigDecimal.TEN;
    private boolean saved;
    private ActivityResultLauncher<Intent> newClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale_form);
        setupToolbar("Nova venda", true);
        client = new Dropdown<>(findViewById(R.id.sa_client));
        method = new Dropdown<>(findViewById(R.id.sa_method));
        discount = findViewById(R.id.sa_discount);
        down = findViewById(R.id.sa_down);
        due = findViewById(R.id.sa_due);
        notes = findViewById(R.id.sa_notes);
        itemsBox = findViewById(R.id.sa_items);
        itemsEmpty = findViewById(R.id.sa_items_empty);
        subtotalView = findViewById(R.id.sa_subtotal);
        discountsView = findViewById(R.id.sa_discounts);
        totalView = findViewById(R.id.sa_total);
        limitView = findViewById(R.id.sa_discount_limit);
        clientInfo = findViewById(R.id.sa_client_info);
        type = findViewById(R.id.sa_type);
        method.setItems(Ui.paymentMethods(this), m -> m);
        method.select(0);
        type.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) updateType();
        });
        due.setOnClickListener(v -> Ui.pickDate(this, draft.dueDate, d -> {
            draft.dueDate = d;
            due.setText(Fmt.date(d));
        }));
        Watcher.on(this::refreshTotals, discount, down);
        findViewById(R.id.sa_add_item).setOnClickListener(v -> editItem(-1));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        newClient = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            if (res.getResultCode() == RESULT_OK && res.getData() != null) loadClients(res.getData().getStringExtra(EXTRA_ID));
        });
        client.setListener(c -> {
            if (c.id.equals("__new__")) {
                client.clear();
                newClient.launch(new Intent(this, ClientFormActivity.class));
                return;
            }
            draft.clientId = c.id.isEmpty() ? null : c.id;
            if (draft.clientId != null) {
                work(() -> new ClientRepository(this).balance(c.id), bal ->
                        clientInfo.setText(bal > 0 ? "Saldo em dívida: " + Fmt.money(bal) : "Sem dívidas em aberto."));
            } else clientInfo.setText("");
        });
        work(() -> {
            maxDiscountPct = new SettingsRepository(this).maxDiscountPercent();
            return new ProductRepository(this).available();
        }, list -> {
            products = list;
            if (products.isEmpty()) {
                Ui.info(this, "Sem pastéis em stock", "Não existem produtos acabados disponíveis. Registe primeiro uma produção.");
            }
            limitView.setText("Desconto máximo autorizado: " + maxDiscountPct.stripTrailingZeros().toPlainString() + "% do subtotal.");
            renderItems();
        });
        loadClients(null);
        updateType();
    }

    private void loadClients(String selectId) {
        work(() -> new ClientRepository(this).all(), list -> {
            List<Client> all = new ArrayList<>();
            Client none = new Client();
            none.name = "— Cliente ocasional —";
            all.add(none);
            Client add = new Client();
            add.id = "__new__";
            add.name = "+ Novo cliente";
            all.add(add);
            all.addAll(list);
            client.setItems(all, c -> c.phone == null || c.phone.isEmpty() ? c.name : c.name + " • " + c.phone);
            if (selectId != null) client.selectWhere(c -> selectId.equals(c.id));
            else if (draft.clientId == null) client.select(0);
        });
    }

    private boolean isCredit() {
        return type.getCheckedButtonId() == R.id.sa_type_credit;
    }

    private void updateType() {
        boolean credit = isCredit();
        findViewById(R.id.sa_down_layout).setVisibility(credit ? View.VISIBLE : View.GONE);
        findViewById(R.id.sa_due_layout).setVisibility(credit ? View.VISIBLE : View.GONE);
        refreshTotals();
    }

    private void refreshTotals() {
        try {
            draft.globalDiscountCents = Money.parseOptional(Ui.text(discount));
        } catch (BusinessException e) {
            draft.globalDiscountCents = 0;
        }
        subtotalView.setText("Subtotal: " + Fmt.money(draft.subtotal()));
        discountsView.setText("Descontos: " + Fmt.money(draft.totalDiscount()));
        totalView.setText("Total: " + Fmt.money(draft.total()));
        boolean credit = isCredit();
        long downV = 0;
        try {
            downV = credit ? Money.parseOptional(Ui.text(down)) : 0;
        } catch (BusinessException ignored) {
            // valor inválido é validado ao gravar
        }
        findViewById(R.id.sa_method_layout).setVisibility(!credit || downV > 0 ? View.VISIBLE : View.GONE);
    }

    private void renderItems() {
        itemsBox.removeAllViews();
        for (int i = 0; i < draft.items.size(); i++) {
            SaleRepository.DraftItem it = draft.items.get(i);
            View line = LayoutInflater.from(this).inflate(R.layout.item_line, itemsBox, false);
            ((TextView) line.findViewById(R.id.line_title)).setText(it.productName);
            ((TextView) line.findViewById(R.id.line_subtitle)).setText(it.qty + " × " + Fmt.money(it.unitPriceCents)
                    + (it.discountCents > 0 ? " − desc. " + Fmt.money(it.discountCents) : ""));
            ((TextView) line.findViewById(R.id.line_value)).setText(Fmt.money(it.net()));
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
        if (products.isEmpty()) {
            Ui.info(this, "Sem pastéis em stock", "Registe primeiro uma produção.");
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_sale_item, null);
        Dropdown<Product> prod = new Dropdown<>(v.findViewById(R.id.ds_product));
        TextView stock = v.findViewById(R.id.ds_stock);
        EditText qty = v.findViewById(R.id.ds_qty);
        EditText price = v.findViewById(R.id.ds_price);
        EditText disc = v.findViewById(R.id.ds_discount);
        TextView preview = v.findViewById(R.id.ds_preview);
        prod.setItems(products, p -> p.name + " — " + Fmt.money(p.salePriceCents));
        prod.setListener(p -> {
            stock.setText("Em stock: " + p.stockQty + " un");
            if (index < 0 || !p.id.equals(draft.items.get(index).productId)) price.setText(Money.formatInput(p.salePriceCents));
        });
        Runnable upd = () -> {
            try {
                preview.setText("Total da linha: " + Fmt.money(build(prod, qty, price, disc).net()));
            } catch (BusinessException e) {
                preview.setText(e.getMessage());
            }
        };
        Watcher.on(upd, qty, price, disc);
        if (index >= 0) {
            SaleRepository.DraftItem it = draft.items.get(index);
            prod.selectWhere(p -> p.id.equals(it.productId));
            qty.setText(String.valueOf(it.qty));
            price.setText(Money.formatInput(it.unitPriceCents));
            if (it.discountCents > 0) disc.setText(Money.formatInput(it.discountCents));
        } else {
            qty.setText("1");
        }
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(index >= 0 ? "Editar produto" : "Adicionar produto")
                .setView(v)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            try {
                SaleRepository.DraftItem it = build(prod, qty, price, disc);
                for (int k = 0; k < draft.items.size(); k++) {
                    if (k != index && draft.items.get(k).productId.equals(it.productId)) {
                        throw new BusinessException("Este produto já está na venda. Edite a linha existente.");
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

    private SaleRepository.DraftItem build(Dropdown<Product> prod, EditText qty, EditText price, EditText disc) {
        Product p = prod.selected();
        if (p == null) throw new BusinessException("Seleccione o produto.");
        SaleRepository.DraftItem it = new SaleRepository.DraftItem();
        it.productId = p.id;
        it.productName = p.name;
        try {
            it.qty = Long.parseLong(Ui.text(qty));
        } catch (NumberFormatException e) {
            throw new BusinessException("Quantidade inválida (use números inteiros).");
        }
        if (it.qty <= 0) throw new BusinessException("A quantidade tem de ser superior a zero.");
        if (it.qty > p.stockQty) throw new BusinessException("Só existem " + p.stockQty + " un em stock.");
        it.unitPriceCents = Money.parseNonNegative(Ui.text(price));
        it.discountCents = Money.parseOptional(Ui.text(disc));
        if (it.discountCents > it.gross()) throw new BusinessException("O desconto é superior ao valor da linha.");
        return it;
    }

    private void save() {
        if (saved || isBusy()) return;
        try {
            if (draft.items.isEmpty()) throw new BusinessException("Adicione pelo menos um produto.");
            draft.globalDiscountCents = Money.parseOptional(Ui.text(discount));
            draft.paymentType = isCredit() ? Sale.TYPE_CREDIT : Sale.TYPE_PAID;
            draft.downPaymentCents = isCredit() ? Money.parseOptional(Ui.text(down)) : 0;
            draft.paymentMethod = method.selected();
            draft.notes = Ui.text(notes);
            if (!isCredit()) draft.dueDate = 0;
            work(() -> new SaleRepository(this).create(draft), s -> {
                saved = true;
                Ui.toast(this, "Venda " + s.number + " registada.");
                Ui.open(this, SaleDetailActivity.class, s.id);
                finish();
            });
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }
}
