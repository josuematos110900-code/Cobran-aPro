package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.CostMath;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.model.ProductMovementRow;
import com.pastelgest.app.repository.ProductRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Produto acabado: stock, custo médio e movimentações. */
public class ProductDetailActivity extends DetailActivity {
    private Product p;

    @Override
    protected String screenTitle() {
        return "Produto acabado";
    }

    @Override
    protected void load() {
        work(() -> {
            ProductRepository repo = new ProductRepository(this);
            List<Object> out = new ArrayList<>();
            out.add(repo.get(id));
            out.add(repo.movements(id));
            return out;
        }, data -> {
            p = (Product) data.get(0);
            if (p == null) {
                finish();
                return;
            }
            @SuppressWarnings("unchecked") List<ProductMovementRow> moves = (List<ProductMovementRow>) data.get(1);
            render(moves);
        });
    }

    private void render(List<ProductMovementRow> moves) {
        clear();
        long unitCost = p.stockQty > 0 ? CostMath.consumptionValue(1, p.stockQty, p.stockValueCents) : 0;
        header(p.name, "Preço de venda " + Fmt.money(p.salePriceCents), p.stockQty + " un",
                p.deleted == 1 ? "ELIMINADO" : p.stockQty <= p.minStock ? "STOCK BAIXO" : "DISPONÍVEL");
        if (p.deleted == 0) {
            action("Editar", R.drawable.ic_edit, v -> Ui.open(this, ProductFormActivity.class, id));
            if (p.recipeId != null) action("Produzir", R.drawable.ic_flame, v -> Ui.open(this, ProductionFormActivity.class, p.recipeId));
            action("Desperdício", R.drawable.ic_waste, v -> waste());
            action("Ajustar", R.drawable.ic_tune, v -> adjust());
            action("Eliminar", R.drawable.ic_delete, v -> archive());
        }
        kv("Valor em stock (custo)", Fmt.money(p.stockValueCents));
        kv("Custo médio por pastel", Fmt.money(unitCost));
        if (unitCost > 0) kv("Margem por pastel", Fmt.money(p.salePriceCents - unitCost), Ui.moneyTone(p.salePriceCents - unitCost));
        kv("Stock mínimo", p.minStock + " un");
        section("Movimentações (" + moves.size() + ")");
        if (moves.isEmpty()) emptyLine("Sem movimentações. O stock aumenta com produções.");
        for (ProductMovementRow m : moves) {
            boolean in = m.m.qty > 0;
            Row r = new Row(m.m.id, label(m.m.type), Fmt.dateTime(m.m.date) + (m.m.note == null ? "" : " • " + m.m.note),
                    (in ? "+" : "") + m.m.qty + " un")
                    .icon(in ? R.drawable.ic_add : R.drawable.ic_sales, in ? Row.TONE_GREEN : Row.TONE_ORANGE)
                    .badge(Fmt.money(m.m.valueCents), in ? Row.TONE_GREEN : Row.TONE_ORANGE).noChevron();
            final String ref = m.m.refId;
            if ("VENDA".equals(m.m.refType) && ref != null) row(r, v -> Ui.open(this, SaleDetailActivity.class, ref));
            else if ("PRODUCAO".equals(m.m.refType) && ref != null) row(r, v -> Ui.open(this, ProductionDetailActivity.class, ref));
            else row(r, null);
        }
    }

    private static String label(String type) {
        switch (type) {
            case ProductMovement.PRODUCTION: return "Entrada por produção";
            case ProductMovement.SALE: return "Saída por venda";
            case ProductMovement.SALE_CANCEL: return "Devolução (venda anulada)";
            case ProductMovement.WASTE: return "Desperdício";
            case ProductMovement.ADJUST: return "Ajuste de inventário";
            default: return type;
        }
    }

    private long parseUnits(String q) {
        try {
            return Long.parseLong(q.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("Use números inteiros.");
        }
    }

    private void waste() {
        Dialogs.qtyReason(this, "Registar desperdício", "Disponível: " + p.stockQty + " un. O custo dos pastéis perdidos conta como perda.",
                "Pastéis perdidos", "un", null, "Registar", (q, reason) -> {
                    try {
                        long n = parseUnits(q);
                        work(() -> {
                            new ProductRepository(this).registerWaste(id, n, reason);
                            return true;
                        }, r -> {
                            Ui.success(this, "Desperdício registado.");
                            load();
                        });
                    } catch (BusinessException e) {
                        Ui.showError(this, e);
                    }
                });
    }

    private void adjust() {
        Dialogs.qtyReason(this, "Ajuste de inventário", "Indique a quantidade real contada.", "Quantidade contada", "un",
                String.valueOf(p.stockQty), "Ajustar", (q, reason) -> {
                    try {
                        long n = parseUnits(q);
                        work(() -> {
                            new ProductRepository(this).adjustTo(id, n, reason);
                            return true;
                        }, r -> {
                            Ui.success(this, "Stock ajustado.");
                            load();
                        });
                    } catch (BusinessException e) {
                        Ui.showError(this, e);
                    }
                });
    }

    private void archive() {
        Ui.confirm(this, "Eliminar produto", "O produto deixa de aparecer nas listas. O histórico de vendas é preservado.",
                "Eliminar", () -> work(() -> {
                    new ProductRepository(this).archive(id);
                    return true;
                }, r -> finish()));
    }
}
