package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.data.entity.Debt;
import com.pastelgest.app.data.entity.Payment;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.model.DebtRow;
import com.pastelgest.app.data.model.PaymentRow;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

public class DebtRepository extends BaseRepository {
    private final SaleRepository sales;

    public DebtRepository(Context c) {
        super(c);
        sales = new SaleRepository(c);
    }

    public List<DebtRow> list(String status) {
        return db.debtDao().list(status == null ? "" : status);
    }

    public List<DebtRow> byClient(String clientId) {
        return db.debtDao().byClient(clientId);
    }

    public DebtRow getRow(String id) {
        return db.debtDao().getRow(id);
    }

    public PaymentRow paymentRow(String id) {
        return db.debtDao().getPaymentRow(id);
    }

    public List<PaymentRow> paymentsByClient(String clientId) {
        return db.debtDao().paymentsByClient(clientId);
    }

    /**
     * Regista um pagamento total ou parcial de uma dívida.
     * @param paymentId id gerado ao abrir o diálogo (impede pagamentos duplicados)
     */
    public Payment pay(String paymentId, String debtId, long amount, String method, String note) {
        return tx(() -> payInternal(paymentId, debtId, amount, method, note));
    }

    private Payment payInternal(String paymentId, String debtId, long amount, String method, String note) {
        if (db.debtDao().getPayment(paymentId) != null) throw new BusinessException("Este pagamento já foi registado.");
        Debt d = db.debtDao().get(debtId);
        if (d == null) throw new BusinessException("Dívida não encontrada.");
        if (!Debt.OPEN.equals(d.status)) throw new BusinessException("Esta dívida não está em aberto.");
        if (amount <= 0) throw new BusinessException("O valor tem de ser superior a zero.");
        if (amount > d.balanceCents) {
            throw new BusinessException("O valor excede o saldo em dívida (" + Fmt.money(d.balanceCents) + ").");
        }
        if (method == null || method.isEmpty()) throw new BusinessException("Seleccione o método de pagamento.");
        Payment p = sales.newPayment(d.clientId, d.saleId, d.id, amount, method, Payment.KIND_DEBT,
                note == null || note.trim().isEmpty() ? "Pagamento de dívida" : note.trim());
        p.id = paymentId;
        db.debtDao().insertPayment(p);
        d.paidCents = db.debtDao().paidForDebt(d.id);
        d.balanceCents = Math.max(0, d.originalCents - d.paidCents);
        d.status = d.balanceCents == 0 ? Debt.PAID : Debt.OPEN;
        d.touch();
        db.debtDao().update(d);
        Sale s = db.saleDao().get(d.saleId);
        if (s != null) {
            s.paidCents = db.debtDao().paidForSale(s.id);
            s.touch();
            db.saleDao().update(s);
        }
        audit("divida", d.id, "PAGAMENTO", Fmt.money(amount) + " via " + method + " | saldo " + Fmt.money(d.balanceCents));
        return p;
    }

    /**
     * Pagamento global de um cliente, distribuído pelas dívidas mais antigas.
     * Devolve os pagamentos criados.
     */
    public List<Payment> payClient(String clientId, long amount, String method) {
        return tx(() -> {
            if (amount <= 0) throw new BusinessException("O valor tem de ser superior a zero.");
            long balance = db.clientDao().balance(clientId);
            if (amount > balance) throw new BusinessException("O valor excede o saldo do cliente (" + Fmt.money(balance) + ").");
            List<DebtRow> debts = db.debtDao().byClient(clientId);
            List<Payment> out = new ArrayList<>();
            long left = amount;
            for (int i = debts.size() - 1; i >= 0 && left > 0; i--) {
                Debt d = debts.get(i).debt;
                if (!Debt.OPEN.equals(d.status) || d.balanceCents <= 0) continue;
                long part = Math.min(left, d.balanceCents);
                out.add(payInternal(Sale.newId(), d.id, part, method, "Pagamento global do cliente"));
                left -= part;
            }
            return out;
        });
    }
}
