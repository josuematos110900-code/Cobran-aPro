package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Purchase;
import com.pastelgest.app.data.entity.PurchaseItem;
import com.pastelgest.app.data.entity.PurchasePayment;
import com.pastelgest.app.data.entity.StockMovement;
import com.pastelgest.app.data.entity.SyncEntity;
import com.pastelgest.app.data.model.PurchaseItemRow;
import com.pastelgest.app.data.model.PurchaseRow;
import com.pastelgest.app.util.Fmt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PurchaseRepository extends BaseRepository {
    private final StockService stock;

    public PurchaseRepository(Context c) {
        super(c);
        stock = new StockService(db);
    }

    /** Linha de rascunho de compra. */
    public static class DraftItem {
        public String ingredientId;
        public String ingredientName;
        public String unit;
        public long qtyMilli;
        public long unitPriceCents;
        public long lineDiscountCents;

        /** quantidade (na unidade introduzida) x preço - desconto. */
        public long lineTotal() {
            BigDecimal q = Units.fromBaseMilli(qtyMilli, unit);
            long gross = q.multiply(BigDecimal.valueOf(unitPriceCents)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            return gross - lineDiscountCents;
        }
    }

    /** Rascunho de compra. O id é gerado ao abrir o formulário (evita duplicados). */
    public static class Draft {
        public String id = SyncEntity.newId();
        public String supplierId;
        public long date = System.currentTimeMillis();
        public long discountCents;
        public long paidNowCents;
        public String paymentMethod;
        public String notes;
        public final List<DraftItem> items = new ArrayList<>();

        public long subtotal() {
            long s = 0;
            for (DraftItem i : items) s += i.lineTotal();
            return s;
        }

        public long total() {
            return subtotal() - discountCents;
        }
    }

    public List<PurchaseRow> list(String status, long from, long to) {
        return db.purchaseDao().list(status == null ? "" : status, from, to);
    }

    public PurchaseRow getRow(String id) {
        return db.purchaseDao().getRow(id);
    }

    public List<PurchaseItemRow> items(String id) {
        return db.purchaseDao().items(id);
    }

    public List<PurchasePayment> payments(String id) {
        return db.purchaseDao().payments(id);
    }

    public Purchase create(Draft d) {
        return tx(() -> createInternal(d, null));
    }

    private Purchase createInternal(Draft d, String correctsId) {
        if (db.purchaseDao().get(d.id) != null) {
            throw new BusinessException("Esta compra já foi registada.");
        }
        if (d.items.isEmpty()) throw new BusinessException("Adicione pelo menos um produto à compra.");
        Set<String> seen = new HashSet<>();
        long[] lineTotals = new long[d.items.size()];
        for (int k = 0; k < d.items.size(); k++) {
            DraftItem it = d.items.get(k);
            Ingredient ing = stock.ingredient(it.ingredientId);
            if (ing.deleted == 1) throw new BusinessException("O ingrediente " + ing.name + " foi eliminado.");
            if (!seen.add(it.ingredientId)) {
                throw new BusinessException("O ingrediente " + ing.name + " aparece repetido. Junte as quantidades numa só linha.");
            }
            if (!Units.isCompatible(ing.unitType, it.unit)) {
                throw new BusinessException("Unidade " + it.unit + " incompatível com " + ing.name + ".");
            }
            if (it.qtyMilli <= 0) throw new BusinessException("Quantidade inválida para " + ing.name + ".");
            if (it.unitPriceCents < 0) throw new BusinessException("Preço inválido para " + ing.name + ".");
            if (it.lineDiscountCents < 0) throw new BusinessException("Desconto inválido para " + ing.name + ".");
            lineTotals[k] = it.lineTotal();
            if (lineTotals[k] < 0) throw new BusinessException("O desconto de " + ing.name + " é superior ao valor da linha.");
        }
        long subtotal = d.subtotal();
        if (d.discountCents < 0 || d.discountCents > subtotal) {
            throw new BusinessException("O desconto da compra tem de estar entre 0 e o subtotal.");
        }
        long total = subtotal - d.discountCents;
        if (d.paidNowCents < 0 || d.paidNowCents > total) {
            throw new BusinessException("O valor pago tem de estar entre 0 e o total da compra.");
        }
        if (d.paidNowCents > 0 && (d.paymentMethod == null || d.paymentMethod.isEmpty())) {
            throw new BusinessException("Seleccione a forma de pagamento.");
        }
        long[] allocated = CostMath.allocateDiscount(lineTotals, d.discountCents);

        Purchase p = new Purchase();
        p.id = d.id;
        p.initNew();
        p.number = documentNumber("C", d.date, db.purchaseDao().countBetween(
                com.pastelgest.app.core.Period.of(com.pastelgest.app.core.Period.DAY, d.date, java.util.TimeZone.getDefault()).start,
                com.pastelgest.app.core.Period.of(com.pastelgest.app.core.Period.DAY, d.date, java.util.TimeZone.getDefault()).end));
        p.supplierId = d.supplierId == null || d.supplierId.isEmpty() ? null : d.supplierId;
        p.date = d.date;
        p.subtotalCents = subtotal;
        p.discountCents = d.discountCents;
        p.totalCents = total;
        p.paidCents = d.paidNowCents;
        p.paymentMethod = d.paymentMethod;
        p.paymentStatus = paymentStatus(total, d.paidNowCents);
        p.status = Purchase.ACTIVE;
        p.correctsPurchaseId = correctsId;
        p.notes = trim(d.notes);
        db.purchaseDao().insert(p);

        for (int k = 0; k < d.items.size(); k++) {
            DraftItem it = d.items.get(k);
            PurchaseItem pi = new PurchaseItem();
            pi.initNew();
            pi.purchaseId = p.id;
            pi.ingredientId = it.ingredientId;
            pi.enteredUnit = it.unit;
            pi.qtyMilli = it.qtyMilli;
            pi.unitPriceCents = it.unitPriceCents;
            pi.lineDiscountCents = it.lineDiscountCents;
            pi.lineTotalCents = lineTotals[k];
            pi.allocatedTotalCents = allocated[k];
            db.purchaseDao().insertItem(pi);

            Ingredient ing = stock.ingredient(it.ingredientId);
            stock.ingredientIn(ing, it.qtyMilli, allocated[k], StockMovement.PURCHASE, "COMPRA", p.id,
                    "Compra " + p.number, d.date);
            ing.lastPurchaseCostCents = allocated[k];
            ing.lastPurchaseQtyMilli = it.qtyMilli;
            ing.lastPurchaseDate = d.date;
            if (ing.supplierId == null && p.supplierId != null) ing.supplierId = p.supplierId;
            db.ingredientDao().update(ing);
        }
        if (d.paidNowCents > 0) {
            insertPayment(p.id, d.paidNowCents, d.paymentMethod, d.date, "Pagamento no acto da compra");
        }
        audit("compra", p.id, correctsId == null ? "CRIAR" : "CORRIGIR",
                p.number + " | total " + Fmt.money(total) + " | pago " + Fmt.money(d.paidNowCents)
                        + (correctsId == null ? "" : " | corrige " + correctsId));
        return p;
    }

    private void insertPayment(String purchaseId, long amount, String method, long date, String note) {
        PurchasePayment pp = new PurchasePayment();
        pp.initNew();
        pp.purchaseId = purchaseId;
        pp.amountCents = amount;
        pp.method = method;
        pp.date = date;
        pp.note = note;
        db.purchaseDao().insertPayment(pp);
    }

    static String paymentStatus(long total, long paid) {
        if (paid >= total) return Purchase.PAID;
        if (paid > 0) return Purchase.PARTIAL;
        return Purchase.PENDING;
    }

    /** Regista pagamento (total ou parcial) de uma compra pendente. */
    public void pay(String purchaseId, long amount, String method) {
        tx(() -> {
            Purchase p = db.purchaseDao().get(purchaseId);
            if (p == null) throw new BusinessException("Compra não encontrada.");
            if (!Purchase.ACTIVE.equals(p.status)) throw new BusinessException("A compra está anulada.");
            long remaining = p.totalCents - p.paidCents;
            if (remaining <= 0) throw new BusinessException("Esta compra já está paga.");
            if (amount <= 0 || amount > remaining) {
                throw new BusinessException("O valor tem de estar entre 0 e " + Fmt.money(remaining) + ".");
            }
            if (method == null || method.isEmpty()) throw new BusinessException("Seleccione a forma de pagamento.");
            insertPayment(p.id, amount, method, System.currentTimeMillis(), "Pagamento ao fornecedor");
            p.paidCents = db.purchaseDao().paidTotal(p.id);
            p.paymentStatus = paymentStatus(p.totalCents, p.paidCents);
            p.touch();
            db.purchaseDao().update(p);
            audit("compra", p.id, "PAGAMENTO", Fmt.money(amount) + " via " + method);
            return null;
        });
    }

    /** Anula uma compra, retirando do stock as quantidades que entraram. */
    public void cancel(String purchaseId, String reason) {
        tx(() -> {
            cancelInternal(purchaseId, required(reason, "Indique o motivo da anulação."));
            return null;
        });
    }

    private void cancelInternal(String purchaseId, String reason) {
        Purchase p = db.purchaseDao().get(purchaseId);
        if (p == null) throw new BusinessException("Compra não encontrada.");
        if (!Purchase.ACTIVE.equals(p.status)) throw new BusinessException("A compra já está anulada.");
        long now = System.currentTimeMillis();
        for (PurchaseItemRow row : db.purchaseDao().items(p.id)) {
            Ingredient ing = stock.ingredient(row.item.ingredientId);
            if (ing.stockQtyMilli < row.item.qtyMilli) {
                throw new BusinessException("Não é possível anular: parte de " + ing.name
                        + " já foi consumida (stock actual " + Units.format(ing.stockQtyMilli, ing.displayUnit)
                        + "). Registe um ajuste de stock em alternativa.");
            }
            stock.ingredientOutWithValue(ing, row.item.qtyMilli, row.item.allocatedTotalCents,
                    StockMovement.PURCHASE_REVERSAL, "COMPRA", p.id, "Anulação " + p.number + ": " + reason, now);
        }
        p.status = Purchase.CANCELLED;
        p.cancelReason = reason;
        p.touch();
        db.purchaseDao().update(p);
        audit("compra", p.id, "ANULAR", p.number + " | " + reason
                + (p.paidCents > 0 ? " | valor já pago a regularizar com o fornecedor: " + Fmt.money(p.paidCents) : ""));
    }

    /**
     * Corrige uma compra: anula a original (com estorno de stock) e regista a
     * versão corrigida, ligadas entre si e registadas na auditoria.
     */
    public Purchase correct(String originalId, Draft corrected, String reason) {
        return tx(() -> {
            String why = required(reason, "Indique o motivo da correcção.");
            Purchase original = db.purchaseDao().get(originalId);
            if (original == null) throw new BusinessException("Compra não encontrada.");
            cancelInternal(originalId, "Correcção: " + why);
            // O dinheiro já pago transita para a compra corrigida através de um
            // movimento negativo na original e positivo na nova (sem duplicar saídas de caixa).
            long carry = Math.min(original.paidCents, corrected.total());
            corrected.paidNowCents = carry;
            if (carry > 0) {
                if (corrected.paymentMethod == null || corrected.paymentMethod.isEmpty()) {
                    corrected.paymentMethod = original.paymentMethod;
                }
                insertPayment(original.id, -carry, original.paymentMethod, System.currentTimeMillis(),
                        "Transferido para a compra corrigida");
                original = db.purchaseDao().get(originalId);
                original.paidCents = db.purchaseDao().paidTotal(originalId);
                original.touch();
                db.purchaseDao().update(original);
            }
            return createInternal(corrected, originalId);
        });
    }

    /** Rascunho pré-preenchido a partir de uma compra existente (para correcção). */
    public Draft draftFrom(String purchaseId) {
        Purchase p = db.purchaseDao().get(purchaseId);
        Draft d = new Draft();
        d.supplierId = p.supplierId;
        d.date = p.date;
        d.discountCents = p.discountCents;
        d.paidNowCents = p.paidCents;
        d.paymentMethod = p.paymentMethod;
        d.notes = p.notes;
        for (PurchaseItemRow r : db.purchaseDao().items(purchaseId)) {
            DraftItem it = new DraftItem();
            it.ingredientId = r.item.ingredientId;
            it.ingredientName = r.ingredientName;
            it.unit = r.item.enteredUnit;
            it.qtyMilli = r.item.qtyMilli;
            it.unitPriceCents = r.item.unitPriceCents;
            it.lineDiscountCents = r.item.lineDiscountCents;
            d.items.add(it);
        }
        return d;
    }
}
