package com.pastelgest.app.ui;

import android.content.Intent;
import android.net.Uri;

import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.data.entity.Debt;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.entity.SyncEntity;
import com.pastelgest.app.data.model.DebtRow;
import com.pastelgest.app.data.model.PaymentRow;
import com.pastelgest.app.data.model.SaleRow;
import com.pastelgest.app.repository.ClientRepository;
import com.pastelgest.app.repository.DebtRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.ReceiptPdf;
import com.pastelgest.app.util.ShareHelper;

import java.util.ArrayList;
import java.util.List;

/** Cliente: saldo, dívidas, histórico de compras e de pagamentos. */
public class ClientDetailActivity extends DetailActivity {
    private Client client;
    private long balance;

    @Override
    protected String screenTitle() {
        return "Cliente";
    }

    @Override
    protected void load() {
        work(() -> {
            ClientRepository repo = new ClientRepository(this);
            DebtRepository debts = new DebtRepository(this);
            List<Object> out = new ArrayList<>();
            out.add(repo.get(id));
            out.add(repo.balance(id));
            out.add(repo.totalPurchased(id));
            out.add(debts.byClient(id));
            out.add(repo.sales(id));
            out.add(debts.paymentsByClient(id));
            return out;
        }, data -> {
            client = (Client) data.get(0);
            if (client == null) {
                finish();
                return;
            }
            balance = (Long) data.get(1);
            @SuppressWarnings("unchecked") List<DebtRow> d = (List<DebtRow>) data.get(3);
            @SuppressWarnings("unchecked") List<SaleRow> s = (List<SaleRow>) data.get(4);
            @SuppressWarnings("unchecked") List<PaymentRow> p = (List<PaymentRow>) data.get(5);
            render((Long) data.get(2), d, s, p);
        });
    }

    private void render(long purchased, List<DebtRow> debts, List<SaleRow> sales, List<PaymentRow> pays) {
        clear();
        header(client.name, Fmt.nz(client.phone), Fmt.money(balance), balance > 0 ? "EM DÍVIDA" : "SEM DÍVIDAS");
        action("Editar", R.drawable.ic_edit, v -> Ui.open(this, ClientFormActivity.class, id));
        if (balance > 0) action("Receber", R.drawable.ic_card, v -> payAll());
        if (client.phone != null && !client.phone.isEmpty()) {
            action("Ligar", R.drawable.ic_phone, v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + client.phone))));
        }
        action("Eliminar", R.drawable.ic_delete, v -> Ui.confirm(this, "Eliminar cliente",
                "O cliente deixa de aparecer nas listas. O histórico é preservado.", "Eliminar", () -> work(() -> {
                    new ClientRepository(this).archive(id);
                    return true;
                }, r -> finish())));
        kv("Total comprado", Fmt.money(purchased));
        kv("Saldo em dívida", Fmt.money(balance), balance > 0 ? Row.TONE_RED : Row.TONE_NONE);
        if (client.address != null && !client.address.isEmpty()) kv("Morada", client.address);
        if (client.notes != null && !client.notes.isEmpty()) kv("Observações", client.notes);

        section("Dívidas");
        if (debts.isEmpty()) emptyLine("Sem vendas a crédito.");
        long now = System.currentTimeMillis();
        for (DebtRow d : debts) {
            boolean open = Debt.OPEN.equals(d.debt.status);
            boolean late = open && d.debt.dueDate > 0 && d.debt.dueDate < now;
            Row r = new Row(d.debt.id, d.saleNumber, Fmt.date(d.saleDate) + " • original " + Fmt.money(d.debt.originalCents)
                    + (d.debt.dueDate > 0 ? " • vence " + Fmt.date(d.debt.dueDate) : ""), Fmt.money(d.debt.balanceCents))
                    .icon(R.drawable.ic_wallet, late ? Row.TONE_RED : open ? Row.TONE_ORANGE : Row.TONE_GREEN)
                    .badge(late ? "VENCIDA" : d.debt.status, late ? Row.TONE_RED : open ? Row.TONE_ORANGE : Row.TONE_GREEN);
            row(r, v -> {
                if (open) payDebt(d);
                else Ui.open(this, SaleDetailActivity.class, d.debt.saleId);
            });
        }
        section("Histórico de compras (" + sales.size() + ")");
        if (sales.isEmpty()) emptyLine("Sem compras registadas.");
        for (SaleRow s : sales) {
            boolean cancelled = Sale.CANCELLED.equals(s.sale.status);
            row(new Row(s.sale.id, s.sale.number, Fmt.dateTime(s.sale.date), Fmt.money(s.sale.totalCents))
                            .icon(R.drawable.ic_sales, cancelled ? Row.TONE_NONE : Row.TONE_GREEN)
                            .badge(cancelled ? "ANULADA" : s.sale.paymentType, cancelled ? Row.TONE_RED : Row.TONE_ACCENT),
                    v -> Ui.open(this, SaleDetailActivity.class, s.sale.id));
        }
        section("Pagamentos (" + pays.size() + ")");
        if (pays.isEmpty()) emptyLine("Sem pagamentos.");
        for (PaymentRow p : pays) {
            row(new Row(p.payment.id, Fmt.nz(p.payment.method) + " • " + Fmt.nz(p.saleNumber),
                    Fmt.dateTime(p.payment.date) + " • " + Fmt.nz(p.payment.receiptNumber), Fmt.money(p.payment.amountCents))
                    .icon(R.drawable.ic_card, p.payment.amountCents < 0 ? Row.TONE_RED : Row.TONE_GREEN), v -> receipt(p));
        }
    }

    private void payDebt(DebtRow d) {
        final String paymentId = SyncEntity.newId();
        Dialogs.payment(this, "Pagamento da venda " + d.saleNumber, "Saldo em dívida: " + Fmt.money(d.debt.balanceCents),
                d.debt.balanceCents, (amount, method, note) -> work(() ->
                        new DebtRepository(this).pay(paymentId, d.debt.id, amount, method, note), p -> {
                    Ui.success(this, "Pagamento registado.");
                    load();
                }));
    }

    private void payAll() {
        Dialogs.payment(this, "Pagamento do cliente", "Saldo total: " + Fmt.money(balance)
                        + ". O valor é aplicado às dívidas mais antigas primeiro.", balance,
                (amount, method, note) -> work(() -> new DebtRepository(this).payClient(id, amount, method), list -> {
                    Ui.success(this, list.size() + " pagamento(s) registado(s).");
                    load();
                }));
    }

    private void receipt(PaymentRow p) {
        work(() -> {
            DebtRepository repo = new DebtRepository(this);
            DebtRow d = p.payment.debtId == null ? null : repo.getRow(p.payment.debtId);
            return ReceiptPdf.payment(this, p, d);
        }, f -> ShareHelper.share(this, f, "application/pdf", "Comprovativo de pagamento"));
    }
}
