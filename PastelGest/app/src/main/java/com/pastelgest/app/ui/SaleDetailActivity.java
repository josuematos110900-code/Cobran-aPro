package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.Debt;
import com.pastelgest.app.data.entity.Payment;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.entity.SaleItem;
import com.pastelgest.app.data.entity.SyncEntity;
import com.pastelgest.app.data.model.DebtRow;
import com.pastelgest.app.data.model.PaymentRow;
import com.pastelgest.app.data.model.SaleRow;
import com.pastelgest.app.repository.DebtRepository;
import com.pastelgest.app.repository.SaleRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.ReceiptPdf;
import com.pastelgest.app.util.ShareHelper;

import java.util.ArrayList;
import java.util.List;

/** Detalhe da venda: itens, pagamentos, comprovativo e anulação auditada. */
public class SaleDetailActivity extends DetailActivity {
    private SaleRow row;
    private DebtRow debt;
    private List<SaleItem> items;
    private List<PaymentRow> payments;

    @Override
    protected String screenTitle() {
        return "Venda";
    }

    @Override
    protected void load() {
        work(() -> {
            SaleRepository repo = new SaleRepository(this);
            List<Object> out = new ArrayList<>();
            out.add(repo.getRow(id));
            out.add(repo.items(id));
            out.add(repo.payments(id));
            Debt d = repo.debtFor(id);
            out.add(d == null ? null : new DebtRepository(this).getRow(d.id));
            return out;
        }, data -> {
            row = (SaleRow) data.get(0);
            if (row == null) {
                finish();
                return;
            }
            @SuppressWarnings("unchecked") List<SaleItem> it = (List<SaleItem>) data.get(1);
            @SuppressWarnings("unchecked") List<PaymentRow> pays = (List<PaymentRow>) data.get(2);
            items = it;
            payments = pays;
            debt = (DebtRow) data.get(3);
            render();
        });
    }

    private void render() {
        clear();
        Sale s = row.sale;
        boolean active = Sale.ACTIVE.equals(s.status);
        boolean credit = Sale.TYPE_CREDIT.equals(s.paymentType);
        String st = !active ? "ANULADA" : credit ? (debt != null && Debt.OPEN.equals(debt.debt.status) ? "FIADA • EM DÍVIDA" : "FIADA • PAGA") : "PAGA";
        header(s.number, Fmt.dateTime(s.date) + " • " + (row.clientName == null ? "Cliente ocasional" : row.clientName),
                Fmt.money(s.totalCents), st);
        action("Comprovativo", R.drawable.ic_share, v -> receipt());
        if (active && debt != null && Debt.OPEN.equals(debt.debt.status)) {
            action("Receber", R.drawable.ic_card, v -> pay());
        }
        if (row.sale.clientId != null) action("Cliente", R.drawable.ic_person, v -> Ui.open(this, ClientDetailActivity.class, row.sale.clientId));
        if (active) action("Anular", R.drawable.ic_block, v -> cancel());

        kv("Subtotal", Fmt.money(s.subtotalCents));
        kv("Descontos", Fmt.money(s.discountCents));
        kv("Total", Fmt.money(s.totalCents));
        kv("Recebido", Fmt.money(s.paidCents), Row.TONE_GREEN);
        if (debt != null) {
            kv("Saldo em dívida", Fmt.money(debt.debt.balanceCents), debt.debt.balanceCents > 0 ? Row.TONE_RED : Row.TONE_NONE);
            if (debt.debt.dueDate > 0) kv("Vencimento", Fmt.date(debt.debt.dueDate));
        }
        kv("Custo dos produtos (CPV)", Fmt.money(s.cogsCents));
        kv("Lucro bruto da venda", Fmt.money(s.totalCents - s.cogsCents), Ui.moneyTone(s.totalCents - s.cogsCents));
        if (s.paymentMethod != null) kv("Método de pagamento", s.paymentMethod);
        if (!active) {
            kv("Anulada em", Fmt.dateTime(s.cancelledAt), Row.TONE_RED);
            kv("Motivo", Fmt.nz(s.cancelReason), Row.TONE_RED);
        }
        if (s.notes != null && !s.notes.isEmpty()) kv("Observações", s.notes);

        section("Produtos");
        for (SaleItem it : items) {
            row(new Row(it.productId, it.productName, it.qty + " × " + Fmt.money(it.unitPriceCents)
                    + (it.discountCents > 0 ? " • desconto " + Fmt.money(it.discountCents) : "") + " • custo " + Fmt.money(it.cogsCents),
                    Fmt.money(it.lineTotalCents)).icon(R.drawable.ic_cake, Row.TONE_GREEN),
                    v -> Ui.open(this, ProductDetailActivity.class, it.productId));
        }
        section("Pagamentos");
        if (payments.isEmpty()) emptyLine("Sem pagamentos.");
        for (PaymentRow p : payments) {
            boolean rev = Payment.KIND_REVERSAL.equals(p.payment.kind);
            row(new Row(p.payment.id, rev ? "Estorno" : Fmt.nz(p.payment.method), Fmt.dateTime(p.payment.date) + " • " + Fmt.nz(p.payment.receiptNumber),
                    Fmt.money(p.payment.amountCents)).icon(R.drawable.ic_card, rev ? Row.TONE_RED : Row.TONE_GREEN),
                    v -> paymentReceipt(p));
        }
    }

    private void receipt() {
        work(() -> ReceiptPdf.sale(this, row, items, payments, debt),
                f -> ShareHelper.share(this, f, "application/pdf", "Comprovativo " + row.sale.number));
    }

    private void paymentReceipt(PaymentRow p) {
        work(() -> ReceiptPdf.payment(this, p, debt),
                f -> ShareHelper.share(this, f, "application/pdf", "Comprovativo de pagamento"));
    }

    private void pay() {
        final String paymentId = SyncEntity.newId();
        Dialogs.payment(this, "Receber pagamento", "Saldo em dívida: " + Fmt.money(debt.debt.balanceCents),
                debt.debt.balanceCents, (amount, method, note) -> work(() ->
                        new DebtRepository(this).pay(paymentId, debt.debt.id, amount, method, note), p -> {
                    Ui.success(this, "Pagamento registado.");
                    load();
                }));
    }

    private void cancel() {
        Ui.askText(this, "Anular venda " + row.sale.number,
                "Os pastéis voltam ao stock e os valores recebidos são estornados. A venda fica no histórico como anulada.",
                null, "Anular venda", reason -> work(() -> {
                    new SaleRepository(this).cancel(id, reason);
                    return true;
                }, r -> {
                    Ui.success(this, "Venda anulada.");
                    load();
                }));
    }
}
