package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.Period;
import com.pastelgest.app.core.ProfitCalculator;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Expense;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.entity.Purchase;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.entity.StockMovement;
import com.pastelgest.app.data.model.CategoryTotal;
import com.pastelgest.app.data.model.DebtRow;
import com.pastelgest.app.data.model.NamedTotal;
import com.pastelgest.app.data.model.ProductionRow;
import com.pastelgest.app.data.model.PurchaseRow;
import com.pastelgest.app.data.model.SaleRow;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Indicadores do painel, lucro real e relatórios. Só leitura. */
public class ReportRepository extends BaseRepository {

    public static final String R_SALES = "Vendas";
    public static final String R_PURCHASES = "Compras";
    public static final String R_PRODUCTION = "Produção";
    public static final String R_STOCK = "Stock";
    public static final String R_EXPENSES = "Despesas";
    public static final String R_DEBTS = "Dívidas";
    public static final String R_PROFIT = "Lucro real";
    public static final String R_TOP_PRODUCTS = "Produtos mais vendidos";
    public static final String R_TOP_INGREDIENTS = "Ingredientes mais utilizados";
    public static final String R_WASTE = "Desperdícios";
    public static final String[] ALL = {R_SALES, R_PURCHASES, R_PRODUCTION, R_STOCK, R_EXPENSES, R_DEBTS,
            R_PROFIT, R_TOP_PRODUCTS, R_TOP_INGREDIENTS, R_WASTE};

    public ReportRepository(Context c) {
        super(c);
    }

    /** Resultado genérico de relatório (ecrã, CSV e PDF). */
    public static class Report {
        public String title;
        public String periodLabel;
        public String[] headers;
        public final List<String[]> rows = new ArrayList<>();
        public final List<String[]> summary = new ArrayList<>();
    }

    public static class Dashboard {
        public long salesCents;
        public long expensesCents;
        public ProfitCalculator.Report profit;
        public long produced;
        public long sold;
        public long debtsOpenCents;
        public int debtsOverdue;
        public int ingredientsInStock;
        public int ingredientsTotal;
        public List<Ingredient> lowIngredients;
        public List<Product> lowProducts;
        public long productUnits;
        public long[] chartDays;
        public long[] chartSales;
        public long[] chartProfit;
    }

    public ProfitCalculator.Report profit(Period p) {
        ProfitCalculator.Input in = new ProfitCalculator.Input();
        in.revenueCents = db.saleDao().revenue(p.start, p.end);
        in.cogsCents = db.saleDao().cogs(p.start, p.end);
        in.discountsCents = db.saleDao().discounts(p.start, p.end);
        in.salesCount = db.saleDao().activeCount(p.start, p.end);
        in.operatingExpensesCents = db.expenseDao().total(p.start, p.end);
        in.ingredientWasteCents = db.ingredientDao().outflowValue(StockMovement.WASTE, p.start, p.end);
        in.productWasteCents = db.productDao().outflowValue(ProductMovement.WASTE, p.start, p.end);
        in.adjustmentLossCents = db.ingredientDao().outflowValue(StockMovement.ADJUST, p.start, p.end)
                + db.productDao().outflowValue(ProductMovement.ADJUST, p.start, p.end);
        in.receivedCents = db.debtDao().receivedBetween(p.start, p.end);
        in.receivableCents = db.debtDao().totalOpen();
        in.purchasesCents = db.purchaseDao().totalBetween(p.start, p.end);
        in.productionCostCents = db.productionDao().costBetween(p.start, p.end);
        in.stockValueCents = db.ingredientDao().totalStockValue() + db.productDao().totalStockValue();
        return ProfitCalculator.compute(in);
    }

    public Dashboard dashboard(Period p) {
        Dashboard d = new Dashboard();
        d.profit = profit(p);
        d.salesCents = d.profit.in.revenueCents;
        d.expensesCents = d.profit.in.operatingExpensesCents;
        d.produced = db.productionDao().producedBetween(p.start, p.end);
        d.sold = db.saleDao().unitsSold(p.start, p.end);
        d.debtsOpenCents = db.debtDao().totalOpen();
        d.debtsOverdue = db.debtDao().countOverdue(System.currentTimeMillis());
        d.ingredientsInStock = db.ingredientDao().countInStock();
        d.ingredientsTotal = db.ingredientDao().countActive();
        d.lowIngredients = db.ingredientDao().lowStock();
        d.lowProducts = db.productDao().lowStock();
        d.productUnits = db.productDao().totalUnits();
        long[] days = Period.lastDays(7, System.currentTimeMillis());
        d.chartDays = new long[7];
        d.chartSales = new long[7];
        d.chartProfit = new long[7];
        for (int i = 0; i < 7; i++) {
            Period dp = new Period(days[i], days[i + 1], Period.DAY);
            ProfitCalculator.Report r = profit(dp);
            d.chartDays[i] = days[i];
            d.chartSales[i] = r.in.revenueCents;
            d.chartProfit[i] = r.netProfitCents;
        }
        return d;
    }

    public Report build(String type, Period p, String periodLabel) {
        Report r = new Report();
        r.title = "Relatório de " + type.toLowerCase(Fmt.PT);
        r.periodLabel = periodLabel;
        switch (type) {
            case R_SALES: sales(r, p); break;
            case R_PURCHASES: purchases(r, p); break;
            case R_PRODUCTION: production(r, p); break;
            case R_STOCK: stock(r); break;
            case R_EXPENSES: expenses(r, p); break;
            case R_DEBTS: debts(r); break;
            case R_PROFIT: profitReport(r, p); break;
            case R_TOP_PRODUCTS: topProducts(r, p); break;
            case R_TOP_INGREDIENTS: topIngredients(r, p); break;
            case R_WASTE: waste(r, p); break;
            default: throw new IllegalArgumentException(type);
        }
        if (R_PROFIT.equals(type)) r.title = "Relatório de lucro real";
        return r;
    }

    private void sales(Report r, Period p) {
        r.headers = new String[]{"Data", "Número", "Cliente", "Tipo", "Estado", "Total", "Recebido", "CPV"};
        long total = 0, paid = 0, cogs = 0;
        int n = 0;
        for (SaleRow s : db.saleDao().list(p.start, p.end, "")) {
            r.rows.add(new String[]{Fmt.dateTime(s.sale.date), s.sale.number, s.clientName == null ? "—" : s.clientName,
                    s.sale.paymentType, s.sale.status, Fmt.money(s.sale.totalCents), Fmt.money(s.sale.paidCents), Fmt.money(s.sale.cogsCents)});
            if (Sale.ACTIVE.equals(s.sale.status)) {
                total += s.sale.totalCents;
                paid += s.sale.paidCents;
                cogs += s.sale.cogsCents;
                n++;
            }
        }
        r.summary.add(new String[]{"Vendas activas", String.valueOf(n)});
        r.summary.add(new String[]{"Total vendido", Fmt.money(total)});
        r.summary.add(new String[]{"Recebido destas vendas", Fmt.money(paid)});
        r.summary.add(new String[]{"Por receber destas vendas", Fmt.money(total - paid)});
        r.summary.add(new String[]{"Custo dos produtos vendidos", Fmt.money(cogs)});
        r.summary.add(new String[]{"Lucro bruto", Fmt.money(total - cogs)});
    }

    private void purchases(Report r, Period p) {
        r.headers = new String[]{"Data", "Número", "Fornecedor", "Estado", "Pagamento", "Total", "Pago"};
        long total = 0, paid = 0;
        for (PurchaseRow row : db.purchaseDao().list("", p.start, p.end)) {
            r.rows.add(new String[]{Fmt.date(row.purchase.date), row.purchase.number, row.supplierName == null ? "—" : row.supplierName,
                    row.purchase.status, row.purchase.paymentStatus, Fmt.money(row.purchase.totalCents), Fmt.money(row.purchase.paidCents)});
            if (Purchase.ACTIVE.equals(row.purchase.status)) {
                total += row.purchase.totalCents;
                paid += row.purchase.paidCents;
            }
        }
        r.summary.add(new String[]{"Total investido em compras", Fmt.money(total)});
        r.summary.add(new String[]{"Pago a fornecedores", Fmt.money(paid)});
        r.summary.add(new String[]{"Por pagar", Fmt.money(total - paid)});
        r.summary.add(new String[]{"Nota", "Compras entram em stock; só o consumo conta como custo."});
    }

    private void production(Report r, Period p) {
        r.headers = new String[]{"Data", "Número", "Receita", "Produzidos", "Perdidos", "Custo total", "Custo/un"};
        long good = 0, lost = 0, cost = 0;
        for (ProductionRow row : db.productionDao().list(p.start, p.end)) {
            r.rows.add(new String[]{Fmt.dateTime(row.production.date), row.production.number, row.recipeName,
                    String.valueOf(row.production.goodQty), String.valueOf(row.production.lostQty),
                    Fmt.money(row.production.totalCostCents), Fmt.money(row.production.unitCostCents)});
            good += row.production.goodQty;
            lost += row.production.lostQty;
            cost += row.production.totalCostCents;
        }
        r.summary.add(new String[]{"Pastéis bons", String.valueOf(good)});
        r.summary.add(new String[]{"Pastéis perdidos", String.valueOf(lost)});
        r.summary.add(new String[]{"Custo de produção", Fmt.money(cost)});
        r.summary.add(new String[]{"Custo médio por pastel", Fmt.money(good == 0 ? 0 : com.pastelgest.app.core.Money.divide(cost, good))});
    }

    private void stock(Report r) {
        r.periodLabel = "Situação actual (" + Fmt.dateTime(System.currentTimeMillis()) + ")";
        r.headers = new String[]{"Tipo", "Nome", "Categoria", "Quantidade", "Mínimo", "Valor", "Alerta"};
        long ingValue = 0, prodValue = 0;
        for (Ingredient i : db.ingredientDao().search("")) {
            boolean low = i.stockQtyMilli <= i.minStockMilli;
            r.rows.add(new String[]{"Ingrediente", i.name, Fmt.nz(i.category), Units.format(i.stockQtyMilli, i.displayUnit),
                    Units.format(i.minStockMilli, i.displayUnit), Fmt.money(i.stockValueCents), low ? "STOCK BAIXO" : ""});
            ingValue += i.stockValueCents;
        }
        for (Product p : db.productDao().search("")) {
            boolean low = p.stockQty <= p.minStock;
            r.rows.add(new String[]{"Produto", p.name, "Pastel", p.stockQty + " un", p.minStock + " un",
                    Fmt.money(p.stockValueCents), low ? "STOCK BAIXO" : ""});
            prodValue += p.stockValueCents;
        }
        r.summary.add(new String[]{"Valor em ingredientes", Fmt.money(ingValue)});
        r.summary.add(new String[]{"Valor em produtos acabados", Fmt.money(prodValue)});
        r.summary.add(new String[]{"Valor total do stock", Fmt.money(ingValue + prodValue)});
    }

    private void expenses(Report r, Period p) {
        r.headers = new String[]{"Data", "Categoria", "Descrição", "Tipo", "Pagamento", "Estado", "Valor"};
        for (Expense e : db.expenseDao().list(p.start, p.end, -1)) {
            r.rows.add(new String[]{Fmt.date(e.date), e.category, e.description, e.fixed == 1 ? "Fixa" : "Variável",
                    e.paymentMethod, e.status, Fmt.money(e.amountCents)});
        }
        for (CategoryTotal c : db.expenseDao().byCategory(p.start, p.end)) {
            r.summary.add(new String[]{c.category + " (" + c.count + ")", Fmt.money(c.total)});
        }
        r.summary.add(new String[]{"Total de despesas", Fmt.money(db.expenseDao().total(p.start, p.end))});
    }

    private void debts(Report r) {
        r.periodLabel = "Dívidas em aberto (" + Fmt.date(System.currentTimeMillis()) + ")";
        r.headers = new String[]{"Cliente", "Telefone", "Venda", "Data", "Vencimento", "Original", "Pago", "Saldo"};
        long total = 0;
        long now = System.currentTimeMillis();
        int overdue = 0;
        for (DebtRow d : db.debtDao().list("ABERTA")) {
            boolean late = d.debt.dueDate > 0 && d.debt.dueDate < now;
            if (late) overdue++;
            r.rows.add(new String[]{d.clientName, Fmt.nz(d.clientPhone), d.saleNumber, Fmt.date(d.saleDate),
                    Fmt.date(d.debt.dueDate) + (late ? " (vencida)" : ""), Fmt.money(d.debt.originalCents),
                    Fmt.money(d.debt.paidCents), Fmt.money(d.debt.balanceCents)});
            total += d.debt.balanceCents;
        }
        r.summary.add(new String[]{"Dívidas em aberto", String.valueOf(r.rows.size())});
        r.summary.add(new String[]{"Dívidas vencidas", String.valueOf(overdue)});
        r.summary.add(new String[]{"Total por receber", Fmt.money(total)});
    }

    private void profitReport(Report r, Period p) {
        ProfitCalculator.Report pr = profit(p);
        r.headers = new String[]{"Indicador", "Valor"};
        addProfitRows(r.rows, pr);
        r.summary.add(new String[]{"Lucro líquido", Fmt.money(pr.netProfitCents)});
        r.summary.add(new String[]{"Margem líquida", pr.netMarginPct + "%"});
    }

    /** Linhas da demonstração de resultados (reutilizado no ecrã Lucro real). */
    public static void addProfitRows(List<String[]> rows, ProfitCalculator.Report pr) {
        rows.add(new String[]{"Receita total de vendas", Fmt.money(pr.in.revenueCents)});
        rows.add(new String[]{"(−) Custo dos produtos vendidos", Fmt.money(pr.in.cogsCents)});
        rows.add(new String[]{"= Lucro bruto", Fmt.money(pr.grossProfitCents)});
        rows.add(new String[]{"(−) Despesas operacionais", Fmt.money(pr.in.operatingExpensesCents)});
        rows.add(new String[]{"(−) Desperdício de ingredientes", Fmt.money(pr.in.ingredientWasteCents)});
        rows.add(new String[]{"(−) Desperdício de produtos", Fmt.money(pr.in.productWasteCents)});
        rows.add(new String[]{"(−) Ajustes de inventário", Fmt.money(pr.in.adjustmentLossCents)});
        rows.add(new String[]{"= Lucro líquido", Fmt.money(pr.netProfitCents)});
        rows.add(new String[]{"Margem bruta / líquida", pr.grossMarginPct + "% / " + pr.netMarginPct + "%"});
        rows.add(new String[]{"Valor recebido (caixa)", Fmt.money(pr.in.receivedCents)});
        rows.add(new String[]{"Valor por receber (total actual)", Fmt.money(pr.in.receivableCents)});
        rows.add(new String[]{"Investido em compras", Fmt.money(pr.in.purchasesCents)});
        rows.add(new String[]{"Custo de produção do período", Fmt.money(pr.in.productionCostCents)});
        rows.add(new String[]{"Valor do stock actual", Fmt.money(pr.in.stockValueCents)});
        rows.add(new String[]{"Descontos concedidos", Fmt.money(pr.in.discountsCents)});
    }

    private void topProducts(Report r, Period p) {
        r.headers = new String[]{"#", "Produto", "Quantidade", "Receita"};
        int k = 1;
        for (NamedTotal t : db.saleDao().topProducts(p.start, p.end, 50)) {
            r.rows.add(new String[]{String.valueOf(k++), t.name, t.qty + " un", Fmt.money(t.total)});
        }
        r.summary.add(new String[]{"Produtos diferentes vendidos", String.valueOf(r.rows.size())});
    }

    private void topIngredients(Report r, Period p) {
        r.headers = new String[]{"#", "Ingrediente", "Quantidade usada", "Custo"};
        int k = 1;
        for (NamedTotal t : db.ingredientDao().mostUsed(p.start, p.end, 50)) {
            String unit = Units.bestDisplayUnit(t.unitType, t.qty);
            r.rows.add(new String[]{String.valueOf(k++), t.name, Units.format(t.qty, unit), Fmt.money(t.total)});
        }
        r.summary.add(new String[]{"Ingredientes utilizados", String.valueOf(r.rows.size())});
    }

    private void waste(Report r, Period p) {
        r.headers = new String[]{"Data", "Tipo", "Item", "Quantidade", "Valor", "Motivo"};
        long total = 0;
        for (com.pastelgest.app.data.model.MovementRow m : db.ingredientDao().movementsOfType(StockMovement.WASTE, p.start, p.end)) {
            r.rows.add(new String[]{Fmt.dateTime(m.m.date), "Ingrediente", m.ingredientName, Units.format(-m.m.qtyMilli, m.displayUnit),
                    Fmt.money(-m.m.valueCents), Fmt.nz(m.m.note)});
            total -= m.m.valueCents;
        }
        for (com.pastelgest.app.data.model.ProductMovementRow m : db.productDao().movementsOfType(ProductMovement.WASTE, p.start, p.end)) {
            r.rows.add(new String[]{Fmt.dateTime(m.m.date), "Produto", m.productName, (-m.m.qty) + " un",
                    Fmt.money(-m.m.valueCents), Fmt.nz(m.m.note)});
            total -= m.m.valueCents;
        }
        r.summary.add(new String[]{"Pastéis perdidos na produção", String.valueOf(db.productionDao().lostBetween(p.start, p.end))});
        r.summary.add(new String[]{"Valor total desperdiçado", Fmt.money(total)});
    }
}
