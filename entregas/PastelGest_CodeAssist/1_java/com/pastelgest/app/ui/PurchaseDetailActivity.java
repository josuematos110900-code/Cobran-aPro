package com.pastelgest.app.ui;

import android.content.Intent;

import com.pastelgest.app.R;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.AuditLog;
import com.pastelgest.app.data.entity.Purchase;
import com.pastelgest.app.data.entity.PurchasePayment;
import com.pastelgest.app.data.model.PurchaseItemRow;
import com.pastelgest.app.data.model.PurchaseRow;
import com.pastelgest.app.repository.AuditRepository;
import com.pastelgest.app.repository.PurchaseRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Detalhe da compra: itens, pagamentos, correcção e anulação auditadas. */
public class PurchaseDetailActivity extends DetailActivity {
    private PurchaseRow row;

    @Override
    protected String screenTitle() {
        return "Compra";
    }

    @Override
    protected void load() {
        work(() -> {
            PurchaseRepository repo = new PurchaseRepository(this);
            List<Object> out = new ArrayList<>();
            out.add(repo.getRow(id));
            out.add(repo.items(id));
            out.add(repo.payments(id));
            out.add(new AuditRepository(this).forEntity(id));
            return out;
        }, data -> {
            row = (PurchaseRow) data.get(0);
            if (row == null) {
                finish();
                return;
            }
            @SuppressWarnings("unchecked") List<PurchaseItemRow> items = (List<PurchaseItemRow>) data.get(1);
            @SuppressWarnings("unchecked") List<PurchasePayment> pays = (List<PurchasePayment>) data.get(2);
            @SuppressWarnings("unchecked") List<AuditLog> audit = (List<AuditLog>) data.get(3);
            render(items, pays, audit);
        });
    }

    private void render(List<PurchaseItemRow> items, List<PurchasePayment> pays, List<AuditLog> audit) {
        clear();
        Purchase p = row.purchase;
        boolean active = Purchase.ACTIVE.equals(p.status);
        header(p.number, Fmt.date(p.date) + " • " + (row.supplierName == null ? "Sem fornecedor" : row.supplierName),
                Fmt.money(p.totalCents), active ? p.paymentStatus : "ANULADA");
        long remaining = p.totalCents - p.paidCents;
        if (active) {
            if (remaining > 0) action("Pagar", R.drawable.ic_card, v -> pay(remaining));
            action("Corrigir", R.drawable.ic_edit, v -> {
                Intent i = new Intent(this, PurchaseFormActivity.class);
                i.putExtra(EXTRA_ID, id);
                i.putExtra(EXTRA_MODE, PurchaseFormActivity.MODE_CORRECT);
                startActivity(i);
                finish();
            });
            action("Anular", R.drawable.ic_block, v -> cancel());
        }
        kv("Subtotal", Fmt.money(p.subtotalCents));
        kv("Desconto", Fmt.money(p.discountCents));
        kv("Total", Fmt.money(p.totalCents));
        kv("Pago", Fmt.money(p.paidCents), Row.TONE_GREEN);
        kv("Por pagar", Fmt.money(Math.max(0, remaining)), remaining > 0 ? Row.TONE_RED : Row.TONE_NONE);
        if (p.paymentMethod != null) kv("Forma de pagamento", p.paymentMethod);
        if (p.correctsPurchaseId != null) kv("Corrige a compra", p.correctsPurchaseId.substring(0, 8));
        if (p.cancelReason != null) kv("Motivo da anulação", p.cancelReason, Row.TONE_RED);
        if (p.notes != null && !p.notes.isEmpty()) kv("Observações", p.notes);

        section("Produtos (" + items.size() + ")");
        for (PurchaseItemRow it : items) {
            String unit = it.item.enteredUnit;
            row(new Row(it.item.ingredientId, it.ingredientName, Units.format(it.item.qtyMilli, unit) + " × "
                    + Fmt.money(it.item.unitPriceCents) + "/" + unit
                    + (it.item.allocatedTotalCents != it.item.lineTotalCents ? " • custo após desconto " + Fmt.money(it.item.allocatedTotalCents) : ""),
                    Fmt.money(it.item.lineTotalCents)).icon(R.drawable.ic_kitchen, Row.TONE_ACCENT),
                    v -> Ui.open(this, IngredientDetailActivity.class, it.item.ingredientId));
        }
        section("Pagamentos");
        if (pays.isEmpty()) emptyLine("Sem pagamentos registados.");
        for (PurchasePayment pp : pays) {
            row(new Row(pp.id, Fmt.nz(pp.method), Fmt.dateTime(pp.date) + " • " + Fmt.nz(pp.note), Fmt.money(pp.amountCents))
                    .icon(R.drawable.ic_card, pp.amountCents < 0 ? Row.TONE_ORANGE : Row.TONE_GREEN).noChevron(), null);
        }
        section("Auditoria");
        for (AuditLog a : audit) {
            row(new Row(a.id, a.action, Fmt.dateTime(a.date) + " • " + a.details, null).icon(R.drawable.ic_history, Row.TONE_NONE).noChevron(), null);
        }
    }

    private void pay(long remaining) {
        Dialogs.payment(this, "Pagamento ao fornecedor", "Em falta: " + Fmt.money(remaining), remaining,
                (amount, method, note) -> work(() -> {
                    new PurchaseRepository(this).pay(id, amount, method);
                    return true;
                }, r -> {
                    Ui.success(this, "Pagamento registado.");
                    load();
                }));
    }

    private void cancel() {
        Ui.askText(this, "Anular compra", "As quantidades desta compra serão retiradas do stock. "
                + "Só é possível se ainda não tiverem sido consumidas.", null, "Anular", reason -> work(() -> {
            new PurchaseRepository(this).cancel(id, reason);
            return true;
        }, r -> {
            Ui.success(this, "Compra anulada e stock estornado.");
            load();
        }));
    }
}
