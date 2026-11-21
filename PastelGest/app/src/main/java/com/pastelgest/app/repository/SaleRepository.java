package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.core.Money;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.data.entity.Debt;
import com.pastelgest.app.data.entity.Payment;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.entity.SaleItem;
import com.pastelgest.app.data.entity.SyncEntity;
import com.pastelgest.app.data.model.PaymentRow;
import com.pastelgest.app.data.model.SaleRow;
import com.pastelgest.app.util.Fmt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public class SaleRepository extends BaseRepository {
    private final StockService stock;
    private final SettingsRepository settings;

    public SaleRepository(Context c) {
        super(c);
        stock = new StockService(db);
        settings = new SettingsRepository(c);
    }

    public static class DraftItem {
        public String productId;
        public String productName;
        public long qty;
        public long unitPriceCents;
        public long discountCents;

        public long gross() {
            return qty * unitPriceCents;
        }

        public long net() {
            return gross() - discountCents;
        }
    }

    /** Rascunho de venda. O id é gerado ao abrir o ecrã: duas gravações do mesmo rascunho são impossíveis. */
    public static class Draft {
        public String id = SyncEntity.newId();
        public String clientId;
        public final List<DraftItem> items = new ArrayList<>();
        public long globalDiscountCents;
        public String paymentType = Sale.TYPE_PAID;
        public String paymentMethod;
        /** Venda fiada: entrada paga no momento (0 = nada). */
        public long downPaymentCents;
        public long dueDate;
        public String notes;

        public long subtotal() {
            long s = 0;
            for (DraftItem i : items) s += i.gross();
            return s;
        }

        public long lineDiscounts() {
            long s = 0;
            for (DraftItem i : items) s += i.discountCents;
            return s;
        }

        public long totalDiscount() {
            return lineDiscounts() + globalDiscountCents;
        }

        public long total() {
            return subtotal() - totalDiscount();
        }
    }

    public List<SaleRow> list(long from, long to, String q) {
        return db.saleDao().list(from, to, q == null ? "" : q.trim());
    }

    public SaleRow getRow(String id) {
        return db.saleDao().getRow(id);
    }

    public List<SaleItem> items(String saleId) {
        return db.saleDao().items(saleId);
    }

    public List<PaymentRow> payments(String saleId) {
        return db.debtDao().paymentsBySale(saleId);
    }

    public Debt debtFor(String saleId) {
        return db.debtDao().bySale(saleId);
    }

    public Sale create(Draft d) {
        return tx(() -> {
            if (db.saleDao().get(d.id) != null) throw new BusinessException("Esta venda já foi registada.");
            if (d.items.isEmpty()) throw new BusinessException("Adicione pelo menos um produto.");
            Set<String> seen = new HashSet<>();
            for (DraftItem it : d.items) {
                if (!seen.add(it.productId)) throw new BusinessException(it.productName + " está repetido. Ajuste a quantidade.");
                if (it.qty <= 0) throw new BusinessException("Quantidade inválida para " + it.productName + ".");
                if (it.unitPriceCents < 0) throw new BusinessException("Preço inválido para " + it.productName + ".");
                if (it.discountCents < 0 || it.discountCents > it.gross()) {
                    throw new BusinessException("Desconto inválido para " + it.productName + ".");
                }
            }
            long subtotal = d.subtotal();
            if (d.globalDiscountCents < 0 || d.globalDiscountCents > subtotal - d.lineDiscounts()) {
                throw new BusinessException("Desconto global inválido.");
            }
            BigDecimal maxPct = settings.maxDiscountPercent();
            long maxDiscount = Money.percentOf(subtotal, maxPct);
            if (d.totalDiscount() > maxDiscount) {
                throw new BusinessException("Desconto acima do autorizado (" + maxPct.stripTrailingZeros().toPlainString()
                        + "% = " + Fmt.money(maxDiscount) + "). Pode alterar o limite nas Definições.");
            }
            long total = d.total();
            boolean credit = Sale.TYPE_CREDIT.equals(d.paymentType);
            Client client = null;
            if (d.clientId != null && !d.clientId.isEmpty()) {
                client = db.clientDao().get(d.clientId);
                if (client == null) throw new BusinessException("Cliente não encontrado.");
            }
            if (credit) {
                if (client == null) throw new BusinessException("Uma venda fiada exige um cliente.");
                if (d.downPaymentCents < 0 || d.downPaymentCents >= total) {
                    throw new BusinessException("A entrada tem de ser inferior ao total da venda fiada.");
                }
            }
            long paidNow = credit ? d.downPaymentCents : total;
            if (paidNow > 0 && (d.paymentMethod == null || d.paymentMethod.isEmpty())) {
                throw new BusinessException("Seleccione o método de pagamento.");
            }

            long now = System.currentTimeMillis();
            Sale s = new Sale();
            s.id = d.id;
            s.initNew();
            Period day = Period.of(Period.DAY, now, TimeZone.getDefault());
            s.number = documentNumber("V", now, db.saleDao().countBetween(day.start, day.end));
            s.clientId = client == null ? null : client.id;
            s.date = now;
            s.subtotalCents = subtotal;
            s.discountCents = d.totalDiscount();
            s.totalCents = total;
            s.paymentMethod = paidNow > 0 ? d.paymentMethod : null;
            s.paymentType = credit ? Sale.TYPE_CREDIT : Sale.TYPE_PAID;
            s.status = Sale.ACTIVE;
            s.notes = trim(d.notes);
            s.paidCents = paidNow;
            db.saleDao().insert(s);

            long[] nets = new long[d.items.size()];
            for (int k = 0; k < nets.length; k++) nets[k] = d.items.get(k).net();
            long[] finals = CostMath.allocateDiscount(nets, d.globalDiscountCents);
            long cogs = 0;
            for (int k = 0; k < d.items.size(); k++) {
                DraftItem it = d.items.get(k);
                Product p = stock.product(it.productId);
                if (p.deleted == 1) throw new BusinessException(p.name + " foi eliminado.");
                long itemCogs = stock.productOut(p, it.qty, ProductMovement.SALE, "VENDA", s.id, "Venda " + s.number, now);
                cogs += itemCogs;
                SaleItem si = new SaleItem();
                si.initNew();
                si.saleId = s.id;
                si.productId = p.id;
                si.productName = p.name;
                si.qty = it.qty;
                si.unitPriceCents = it.unitPriceCents;
                si.discountCents = it.gross() - finals[k];
                si.lineTotalCents = finals[k];
                si.cogsCents = itemCogs;
                db.saleDao().insertItem(si);
            }
            s.cogsCents = cogs;
            s.touch();
            db.saleDao().update(s);

            String debtId = null;
            if (credit) {
                Debt debt = new Debt();
                debt.initNew();
                debt.clientId = client.id;
                debt.saleId = s.id;
                debt.originalCents = total;
                debt.paidCents = paidNow;
                debt.balanceCents = total - paidNow;
                debt.dueDate = d.dueDate;
                debt.status = Debt.OPEN;
                db.debtDao().insert(debt);
                debtId = debt.id;
            }
            if (paidNow > 0) {
                Payment pay = newPayment(s.clientId, s.id, debtId, paidNow, d.paymentMethod,
                        credit ? Payment.KIND_DEBT : Payment.KIND_SALE, credit ? "Entrada da venda " + s.number : "Venda " + s.number);
                db.debtDao().insertPayment(pay);
            }
            audit("venda", s.id, "CRIAR", s.number + " | " + (credit ? "FIADA" : "PAGA") + " | total "
                    + Fmt.money(total) + " | desconto " + Fmt.money(s.discountCents) + " | CPV " + Fmt.money(cogs));
            return s;
        });
    }

    Payment newPayment(String clientId, String saleId, String debtId, long amount, String method, String kind, String note) {
        Payment p = new Payment();
        p.initNew();
        p.clientId = clientId;
        p.saleId = saleId;
        p.debtId = debtId;
        p.amountCents = amount;
        p.method = method;
        p.kind = kind;
        p.date = System.currentTimeMillis();
        p.note = note;
        p.receiptNumber = "R-" + Fmt.stamp(p.date) + "-" + p.id.substring(0, 6).toUpperCase();
        return p;
    }

    /**
     * Anula uma venda: devolve os pastéis ao stock ao custo original, estorna os
     * valores recebidos e anula a dívida. A venda e os seus registos mantêm-se.
     */
    public void cancel(String saleId, String reason) {
        tx(() -> {
            String why = required(reason, "Indique o motivo da anulação.");
            Sale s = db.saleDao().get(saleId);
            if (s == null) throw new BusinessException("Venda não encontrada.");
            if (!Sale.ACTIVE.equals(s.status)) throw new BusinessException("A venda já está anulada.");
            long now = System.currentTimeMillis();
            for (SaleItem it : db.saleDao().items(s.id)) {
                Product p = stock.product(it.productId);
                stock.productIn(p, it.qty, it.cogsCents, ProductMovement.SALE_CANCEL, "VENDA", s.id,
                        "Anulação " + s.number + ": " + why, now);
            }
            long paid = db.debtDao().paidForSale(s.id);
            Debt debt = db.debtDao().bySale(s.id);
            if (paid > 0) {
                Payment rev = newPayment(s.clientId, s.id, debt == null ? null : debt.id, -paid,
                        s.paymentMethod, Payment.KIND_REVERSAL, "Estorno por anulação da venda " + s.number);
                db.debtDao().insertPayment(rev);
            }
            if (debt != null) {
                debt.status = Debt.CANCELLED;
                debt.paidCents = db.debtDao().paidForDebt(debt.id);
                debt.balanceCents = 0;
                debt.touch();
                db.debtDao().update(debt);
            }
            s.status = Sale.CANCELLED;
            s.cancelReason = why;
            s.cancelledAt = now;
            s.paidCents = db.debtDao().paidForSale(s.id);
            s.touch();
            db.saleDao().update(s);
            audit("venda", s.id, "ANULAR", s.number + " | " + why + " | estornado " + Fmt.money(paid));
            return null;
        });
    }
}
