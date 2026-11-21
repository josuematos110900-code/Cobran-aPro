package com.pastelgest.verify;

import static com.pastelgest.verify.Check.*;

import com.pastelgest.app.core.Money;
import com.pastelgest.app.core.Period;
import com.pastelgest.app.core.ProfitCalculator;
import com.pastelgest.app.core.RecipeCostCalculator;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.*;
import com.pastelgest.app.repository.*;
import com.pastelgest.app.util.Prefs;

import java.util.Arrays;
import java.util.List;

/** Cenário completo de uma pastelaria, executado contra os repositórios reais. */
public class IntegrationTest {
    public static String schema;

    static long kz(String s) { return Money.parse(s); }
    static long kg(String s) { return Units.parseToBaseMilli(s, Units.KG); }
    static long g(String s) { return Units.parseToBaseMilli(s, Units.G); }
    static long l(String s) { return Units.parseToBaseMilli(s, Units.L); }
    static long ml(String s) { return Units.parseToBaseMilli(s, Units.ML); }
    static long un(String s) { return Units.parseToBaseMilli(s, Units.UN); }

    public static FakeContext newDevice(JdbcDb db) throws Exception {
        db.install();
        FakeContext ctx = new FakeContext();
        // URL sem https => sincronização desactivada nos testes locais
        Prefs.put(ctx, Prefs.SUPABASE_URL, "http://desactivado");
        Prefs.put(ctx, Prefs.SUPABASE_KEY, "x");
        return ctx;
    }

    static Ingredient ingredient(FakeContext c, String name, int type, String unit, long min, long initQty, long initValue) {
        Ingredient i = new Ingredient();
        i.name = name;
        i.category = "Teste";
        i.unitType = type;
        i.displayUnit = unit;
        i.minStockMilli = min;
        return new IngredientRepository(c).save(i, initQty, initValue);
    }

    static PurchaseRepository.DraftItem item(Ingredient i, String unit, long qty, long price, long disc) {
        PurchaseRepository.DraftItem it = new PurchaseRepository.DraftItem();
        it.ingredientId = i.id;
        it.ingredientName = i.name;
        it.unit = unit;
        it.qtyMilli = qty;
        it.unitPriceCents = price;
        it.lineDiscountCents = disc;
        return it;
    }

    static RecipeRepository.DraftLine line(Ingredient i, String unit, long qty) {
        RecipeRepository.DraftLine d = new RecipeRepository.DraftLine();
        d.ingredientId = i.id;
        d.ingredientName = i.name;
        d.unitType = i.unitType;
        d.unit = unit;
        d.qtyMilli = qty;
        return d;
    }

    /** Verifica que todas as caches de stock/saldos coincidem com os registos-razão e nunca são negativas. */
    static void ledgerConsistency(JdbcDb db, String when) throws Exception {
        eq(when + ": ingredientes com qtd ≠ soma das movimentações", 0, db.scalar(
                "SELECT COUNT(*) FROM ingredients i WHERE i.stockQtyMilli <> COALESCE((SELECT SUM(qtyMilli) FROM stock_movements m WHERE m.ingredientId=i.id),0) OR i.stockValueCents <> COALESCE((SELECT SUM(valueCents) FROM stock_movements m WHERE m.ingredientId=i.id),0)"));
        eq(when + ": produtos com stock ≠ soma das movimentações", 0, db.scalar(
                "SELECT COUNT(*) FROM products p WHERE p.stockQty <> COALESCE((SELECT SUM(qty) FROM product_movements m WHERE m.productId=p.id),0) OR p.stockValueCents <> COALESCE((SELECT SUM(valueCents) FROM product_movements m WHERE m.productId=p.id),0)"));
        eq(when + ": stocks negativos", 0, db.scalar("SELECT (SELECT COUNT(*) FROM ingredients WHERE stockQtyMilli<0 OR stockValueCents<0)+(SELECT COUNT(*) FROM products WHERE stockQty<0 OR stockValueCents<0)"));
        eq(when + ": dívidas com pago ≠ soma dos pagamentos", 0, db.scalar(
                "SELECT COUNT(*) FROM debts d WHERE d.paidCents <> COALESCE((SELECT SUM(amountCents) FROM payments p WHERE p.debtId=d.id),0)"));
        eq(when + ": vendas com recebido ≠ soma dos pagamentos", 0, db.scalar(
                "SELECT COUNT(*) FROM sales s WHERE s.paidCents <> COALESCE((SELECT SUM(amountCents) FROM payments p WHERE p.saleId=s.id),0)"));
        eq(when + ": compras com pago ≠ soma dos pagamentos", 0, db.scalar(
                "SELECT COUNT(*) FROM purchases p WHERE p.paidCents <> COALESCE((SELECT SUM(amountCents) FROM purchase_payments x WHERE x.purchaseId=p.id),0)"));
    }

    public static int run() throws Exception {
        JdbcDb db = new JdbcDb(schema);
        FakeContext c = newDevice(db);
        IngredientRepository ingRepo = new IngredientRepository(c);
        PurchaseRepository purRepo = new PurchaseRepository(c);
        RecipeRepository recRepo = new RecipeRepository(c);
        ProductionRepository prodRepo = new ProductionRepository(c);
        SaleRepository saleRepo = new SaleRepository(c);
        DebtRepository debtRepo = new DebtRepository(c);
        ExpenseRepository expRepo = new ExpenseRepository(c);
        ProductRepository productRepo = new ProductRepository(c);
        ReportRepository reports = new ReportRepository(c);

        // ---------------- Ingredientes
        Supplier sup = new Supplier();
        sup.name = "Armazém Kinaxixi";
        sup.phone = "923000000";
        sup = new SupplierRepository(c).save(sup);
        Ingredient farinha = ingredient(c, "Farinha", Units.TYPE_MASS, Units.KG, kg("2"), 0, 0);
        Ingredient leite = ingredient(c, "Leite", Units.TYPE_VOLUME, Units.L, l("1"), 0, 0);
        Ingredient ovos = ingredient(c, "Ovos", Units.TYPE_UNIT, Units.UN, un("12"), 0, 0);
        Ingredient acucar = ingredient(c, "Açúcar", Units.TYPE_MASS, Units.KG, kg("1"), kg("5"), kz("4000"));
        eq("Stock inicial de açúcar (milésimas de g)", kg("5"), ingRepo.get(acucar.id).stockQtyMilli);
        eq("Valor do stock inicial de açúcar", kz("4000"), ingRepo.get(acucar.id).stockValueCents);
        fails("Ingrediente com nome duplicado", "Já existe", () -> ingredient(c, "farinha", Units.TYPE_MASS, Units.G, 0, 0, 0));
        fails("Unidade incompatível (massa em ml)", "compatível", () -> ingredient(c, "Sal", Units.TYPE_MASS, Units.ML, 0, 0, 0));

        // ---------------- Compra com vários produtos, desconto e pagamento parcial
        PurchaseRepository.Draft d = new PurchaseRepository.Draft();
        d.supplierId = sup.id;
        d.items.add(item(farinha, Units.KG, kg("10"), kz("1200"), 0));      // 12 000
        d.items.add(item(leite, Units.L, l("5"), kz("900"), 0));            //  4 500
        d.items.add(item(ovos, Units.UN, un("30"), kz("100"), 0));          //  3 000
        d.discountCents = kz("1000");
        d.paidNowCents = kz("10000");
        d.paymentMethod = "Dinheiro";
        Purchase p1 = purRepo.create(d);
        eq("Total da compra (19 500 − 1 000)", kz("18500"), p1.totalCents);
        eqs("Estado de pagamento", Purchase.PARTIAL, purRepo.getRow(p1.id).purchase.paymentStatus);
        eq("Soma do valor que entrou em stock = total da compra", kz("18500"),
                db.scalar("SELECT SUM(valueCents) FROM stock_movements WHERE refId='" + p1.id + "'"));
        eq("Stock de farinha após compra", kg("10"), ingRepo.get(farinha.id).stockQtyMilli);
        fails("Compra registada duas vezes (mesmo id)", "já foi registada", () -> purRepo.create(d));
        fails("Pagamento acima do valor em falta", "entre 0 e", () -> purRepo.pay(p1.id, kz("9000"), "Dinheiro"));
        purRepo.pay(p1.id, kz("8500"), "Transferência");
        eqs("Compra paga após segundo pagamento", Purchase.PAID, purRepo.getRow(p1.id).purchase.paymentStatus);
        fails("Pagamento de compra já paga", "já está paga", () -> purRepo.pay(p1.id, kz("1"), "Dinheiro"));

        // ---------------- Receita e custo por pastel
        Recipe r = new Recipe();
        r.name = "Pastel de nata";
        r.yieldQty = 20;
        r.packagingCostCents = kz("25");
        r.otherCostCents = kz("300");
        r.suggestedPriceCents = kz("250");
        r = recRepo.save(r, Arrays.asList(line(farinha, Units.G, g("500")), line(leite, Units.ML, ml("250")),
                line(ovos, Units.UN, un("4")), line(acucar, Units.G, g("200"))));
        RecipeCostCalculator.Result cost = recRepo.computeCost(r.id);
        // custos médios após o desconto global proporcional: farinha 11 384,62/10kg ...
        long farinhaVal = ingRepo.get(farinha.id).stockValueCents, leiteVal = ingRepo.get(leite.id).stockValueCents,
                ovosVal = ingRepo.get(ovos.id).stockValueCents;
        long expectedIng = Math.round(farinhaVal * 0.05 + leiteVal * 0.05 + ovosVal * (4.0 / 30) + 4000_00 * 0.04);
        ok("Custo dos ingredientes calculado com custo médio e conversões kg/g e l/ml (±1 cêntimo)",
                Math.abs(cost.ingredientsCostCents - expectedIng) <= 1);
        eq("Custo de embalagem do lote (25 × 20)", kz("500"), cost.packagingCostCents);
        eq("Custo por pastel = total / rendimento", Money.divide(cost.totalCostCents, 20), cost.unitCostCents);
        Recipe saved = recRepo.get(r.id);
        ok("Produto acabado criado automaticamente", saved.productId != null && productRepo.get(saved.productId) != null);
        eq("Snapshot de custo histórico gravado", 1, recRepo.snapshots(r.id).size());
        fails("Ingrediente usado numa receita não pode ser eliminado", "receita", () -> ingRepo.archive(farinha.id));

        // ---------------- Produção
        ProductionRepository.Plan plan = prodRepo.plan(r.id, 50);
        eq("Farinha necessária para 50 pastéis (500 g × 50/20)", g("1250"), plan.needs.stream().filter(n -> n.ingredientId.equals(farinha.id)).findFirst().get().requiredMilli);
        ok("Plano viável com o stock actual", plan.feasible());
        long farinhaAntes = ingRepo.get(farinha.id).stockQtyMilli;
        fails("Produção impossível (10 000 pastéis) apresenta os ingredientes em falta", "Ingredientes insuficientes",
                () -> prodRepo.confirm(SyncEntity.newId(), saved.id, 10_000, 0, null));
        eq("Produção recusada não altera o stock (transacção)", farinhaAntes, ingRepo.get(farinha.id).stockQtyMilli);
        eq("Produção recusada não deixa registos", 0, db.scalar("SELECT COUNT(*) FROM productions"));
        String prodId = SyncEntity.newId();
        Production prod = prodRepo.confirm(prodId, saved.id, 50, 2, "Fornada da manhã");
        fails("Confirmar a mesma produção duas vezes", "já foi confirmada", () -> prodRepo.confirm(prodId, saved.id, 50, 2, null));
        eq("Pastéis bons em stock", 48, productRepo.get(saved.productId).stockQty);
        eq("Farinha descontada (10 kg − 1,25 kg)", kg("8,75"), ingRepo.get(farinha.id).stockQtyMilli);
        eq("Valor do produto acabado = custo total da produção", prod.totalCostCents, productRepo.get(saved.productId).stockValueCents);
        eq("Custo total = ingredientes + embalagem (48 × 25) + outros (300 × 50/20)",
                prod.ingredientsCostCents + kz("1200") + kz("750"), prod.totalCostCents);
        eq("Custo por pastel absorve as perdas (total / 48)", Money.divide(prod.totalCostCents, 48), prod.unitCostCents);
        eq("Itens de produção registados", 4, prodRepo.items(prod.id).size());
        fails("Perdas iguais à quantidade", "inferiores", () -> prodRepo.confirm(SyncEntity.newId(), saved.id, 5, 5, null));

        // ---------------- Custo histórico: subida de preço não altera produções antigas
        long unitCostBefore = prod.unitCostCents;
        PurchaseRepository.Draft d2 = new PurchaseRepository.Draft();
        d2.items.add(item(farinha, Units.KG, kg("5"), kz("3000"), 0));
        d2.paidNowCents = 0;
        purRepo.create(d2);
        eq("Custo da produção antiga mantém-se após nova compra mais cara", unitCostBefore, prodRepo.getRow(prod.id).production.unitCostCents);
        ok("Custo actual da receita sobe com o novo preço médio", recRepo.computeCost(r.id).unitCostCents > cost.unitCostCents);
        eqs("Compra sem pagamento fica pendente", Purchase.PENDING, purRepo.list("", 0, Long.MAX_VALUE / 2).get(0).purchase.paymentStatus);

        // ---------------- Venda paga com desconto
        long cogsExpected = com.pastelgest.app.core.CostMath.consumptionValue(10, 48, productRepo.get(saved.productId).stockValueCents);
        SaleRepository.Draft s1 = new SaleRepository.Draft();
        SaleRepository.DraftItem si = new SaleRepository.DraftItem();
        si.productId = saved.productId;
        si.productName = "Pastel de nata";
        si.qty = 10;
        si.unitPriceCents = kz("250");
        s1.items.add(si);
        s1.globalDiscountCents = kz("100");
        s1.paymentMethod = "Multicaixa Express";
        Sale sale1 = saleRepo.create(s1);
        eq("Total da venda (2 500 − 100)", kz("2400"), sale1.totalCents);
        eq("Venda paga: recebido = total", kz("2400"), saleRepo.getRow(sale1.id).sale.paidCents);
        eq("CPV = custo médio histórico × 10", cogsExpected, saleRepo.getRow(sale1.id).sale.cogsCents);
        eq("Stock de pastéis após venda", 38, productRepo.get(saved.productId).stockQty);
        eq("Ingredientes NÃO são descontados na venda", kg("13,75"), ingRepo.get(farinha.id).stockQtyMilli);
        fails("Venda registada duas vezes (mesmo id)", "já foi registada", () -> saleRepo.create(s1));

        SaleRepository.Draft big = new SaleRepository.Draft();
        SaleRepository.DraftItem bi = new SaleRepository.DraftItem();
        bi.productId = saved.productId; bi.productName = "Pastel de nata"; bi.qty = 1; bi.unitPriceCents = kz("250"); bi.discountCents = kz("100");
        big.items.add(bi);
        big.paymentMethod = "Dinheiro";
        fails("Desconto acima do autorizado (40% > 10%)", "acima do autorizado", () -> saleRepo.create(big));
        SaleRepository.Draft tooMany = new SaleRepository.Draft();
        SaleRepository.DraftItem ti = new SaleRepository.DraftItem();
        ti.productId = saved.productId; ti.productName = "Pastel de nata"; ti.qty = 999; ti.unitPriceCents = kz("250");
        tooMany.items.add(ti);
        tooMany.paymentMethod = "Dinheiro";
        fails("Venda acima do stock", "Stock insuficiente", () -> saleRepo.create(tooMany));
        eq("Venda recusada não altera stock", 38, productRepo.get(saved.productId).stockQty);

        // ---------------- Venda fiada e dívidas
        com.pastelgest.app.data.entity.Client cl = new com.pastelgest.app.data.entity.Client();
        cl.name = "Maria Lopes";
        cl.phone = "+244 923 111 222";
        cl = new ClientRepository(c).save(cl);
        final String clientId = cl.id;
        SaleRepository.Draft s2 = new SaleRepository.Draft();
        SaleRepository.DraftItem s2i = new SaleRepository.DraftItem();
        s2i.productId = saved.productId; s2i.productName = "Pastel de nata"; s2i.qty = 8; s2i.unitPriceCents = kz("250");
        s2.items.add(s2i);
        s2.paymentType = Sale.TYPE_CREDIT;
        fails("Venda fiada sem cliente", "exige um cliente", () -> saleRepo.create(s2));
        s2.clientId = clientId;
        s2.downPaymentCents = kz("500");
        s2.paymentMethod = "Dinheiro";
        Sale sale2 = saleRepo.create(s2);
        Debt debt = saleRepo.debtFor(sale2.id);
        eq("Dívida = total − entrada (2 000 − 500)", kz("1500"), debt.balanceCents);
        eq("Saldo do cliente", kz("1500"), new ClientRepository(c).balance(clientId));
        String payId = SyncEntity.newId();
        debtRepo.pay(payId, debt.id, kz("600"), "Dinheiro", null);
        fails("Pagamento duplicado (mesmo id)", "já foi registado", () -> debtRepo.pay(payId, debt.id, kz("600"), "Dinheiro", null));
        fails("Pagamento acima do saldo", "excede", () -> debtRepo.pay(SyncEntity.newId(), debt.id, kz("1000"), "Dinheiro", null));
        eq("Saldo após pagamento parcial", kz("900"), debtRepo.getRow(debt.id).debt.balanceCents);
        debtRepo.payClient(clientId, kz("900"), "Transferência");
        eqs("Dívida liquidada", Debt.PAID, debtRepo.getRow(debt.id).debt.status);
        eq("Venda fiada totalmente recebida", kz("2000"), saleRepo.getRow(sale2.id).sale.paidCents);
        fails("Pagamento de dívida já paga", "não está em aberto", () -> debtRepo.pay(SyncEntity.newId(), debt.id, kz("1"), "Dinheiro", null));

        // segunda venda fiada que fica em aberto
        SaleRepository.Draft s3 = new SaleRepository.Draft();
        SaleRepository.DraftItem s3i = new SaleRepository.DraftItem();
        s3i.productId = saved.productId; s3i.productName = "Pastel de nata"; s3i.qty = 4; s3i.unitPriceCents = kz("250");
        s3.items.add(s3i);
        s3.paymentType = Sale.TYPE_CREDIT;
        s3.clientId = clientId;
        Sale sale3 = saleRepo.create(s3);
        eq("Venda fiada sem entrada: nada recebido", 0, saleRepo.getRow(sale3.id).sale.paidCents);

        // ---------------- Anulação auditada
        saleRepo.cancel(sale1.id, "Cliente devolveu");
        eq("Pastéis devolvidos ao stock após anulação", 38 - 8 - 4 + 10, productRepo.get(saved.productId).stockQty);
        eq("Estorno do valor recebido", -kz("2400"), db.scalar("SELECT SUM(amountCents) FROM payments WHERE kind='ESTORNO'"));
        fails("Anular venda já anulada", "já está anulada", () -> saleRepo.cancel(sale1.id, "x"));
        fails("Anular sem motivo", "motivo", () -> saleRepo.cancel(sale3.id, " "));

        // ---------------- Despesas
        Expense e1 = new Expense();
        e1.category = "Gás"; e1.description = "Botija"; e1.amountCents = kz("3500"); e1.date = System.currentTimeMillis(); e1.paymentMethod = "Dinheiro";
        expRepo.save(e1);
        Expense e2 = new Expense();
        e2.category = "Transporte"; e2.description = "Táxi"; e2.amountCents = kz("1000"); e2.date = System.currentTimeMillis(); e2.paymentMethod = "Dinheiro";
        e2 = expRepo.save(e2);
        expRepo.cancel(e2.id, "Lançada por engano");
        fails("Despesa com valor zero", "superior a zero", () -> { Expense x = new Expense(); x.category = "a"; x.description = "b"; x.date = 1; x.paymentMethod = "Dinheiro"; expRepo.save(x); });

        // ---------------- Desperdícios e ajustes
        ingRepo.registerWaste(leite.id, ml("500"), "Leite azedo");
        productRepo.registerWaste(saved.productId, 2, "Caíram ao chão");
        fails("Desperdício acima do stock", "Stock insuficiente", () -> ingRepo.registerWaste(ovos.id, un("1000"), "x"));
        eq("Ovos após produção (30 − 10)", un("20"), ingRepo.get(ovos.id).stockQtyMilli);
        fails("Ajuste igual ao stock actual", "igual ao stock", () -> ingRepo.adjustTo(ovos.id, un("20"), "x"));
        ingRepo.adjustTo(ovos.id, un("18"), "Contagem física: 2 partidos");
        eq("Ajuste de inventário de ovos", un("18"), ingRepo.get(ovos.id).stockQtyMilli);

        // ---------------- Anulação e correcção de compra
        Ingredient canela = ingredient(c, "Canela", Units.TYPE_MASS, Units.G, 0, 0, 0);
        PurchaseRepository.Draft d3 = new PurchaseRepository.Draft();
        d3.items.add(item(canela, Units.G, g("100"), kz("5"), 0));
        d3.paidNowCents = kz("500");
        d3.paymentMethod = "Dinheiro";
        Purchase p3 = purRepo.create(d3);
        PurchaseRepository.Draft corr = purRepo.draftFrom(p3.id);
        corr.items.get(0).qtyMilli = g("200");
        Purchase p3b = purRepo.correct(p3.id, corr, "Quantidade errada");
        eqs("Compra original anulada pela correcção", Purchase.CANCELLED, purRepo.getRow(p3.id).purchase.status);
        eq("Stock de canela = quantidade corrigida", g("200"), ingRepo.get(canela.id).stockQtyMilli);
        eq("Valor pago transita sem duplicar saídas de caixa", kz("500"),
                db.scalar("SELECT SUM(amountCents) FROM purchase_payments WHERE purchaseId IN ('" + p3.id + "','" + p3b.id + "')"));
        fails("Anular compra cujos ingredientes já foram consumidos", "já foi consumida", () -> purRepo.cancel(p1.id, "teste"));

        // ---------------- Lucro real
        Period today = Period.of(Period.DAY);
        ProfitCalculator.Report pr = reports.profit(today);
        long revenue = sale2.totalCents + sale3.totalCents;  // venda 1 anulada
        eq("Receita = vendas activas (inclui fiadas)", revenue, pr.in.revenueCents);
        long cogs = db.scalar("SELECT SUM(cogsCents) FROM sales WHERE status='ACTIVA'");
        eq("CPV = custo histórico dos pastéis vendidos", cogs, pr.in.cogsCents);
        eq("Despesas operacionais (anuladas excluídas)", kz("3500"), pr.in.operatingExpensesCents);
        long wasteIng = -db.scalar("SELECT SUM(valueCents) FROM stock_movements WHERE type='DESPERDICIO'");
        long wasteProd = -db.scalar("SELECT SUM(valueCents) FROM product_movements WHERE type='DESPERDICIO'");
        long adjust = -db.scalar("SELECT COALESCE(SUM(valueCents),0) FROM stock_movements WHERE type='AJUSTE'");
        eq("Lucro líquido = vendas − CPV − despesas − desperdícios − ajustes", revenue - cogs - kz("3500") - wasteIng - wasteProd - adjust, pr.netProfitCents);
        eq("Valor recebido (caixa) = entradas − estornos", kz("500") + kz("600") + kz("900") + kz("2400") - kz("2400"), pr.in.receivedCents);
        eq("Valor por receber = venda fiada em aberto", sale3.totalCents, pr.in.receivableCents);
        ok("Compras não reduzem o lucro (investido em compras reportado à parte)", pr.in.purchasesCents > 0);
        eq("Valor do stock = ingredientes + produtos", db.scalar("SELECT (SELECT SUM(stockValueCents) FROM ingredients WHERE deleted=0)+(SELECT SUM(stockValueCents) FROM products WHERE deleted=0)"), pr.in.stockValueCents);

        // ---------------- Relatórios
        for (String type : ReportRepository.ALL) {
            ReportRepository.Report rep = reports.build(type, today, "hoje");
            ok("Relatório \"" + type + "\" gerado (" + rep.rows.size() + " linhas)", rep.headers != null && rep.headers.length > 0);
        }
        ReportRepository.Dashboard dash = reports.dashboard(today);
        eq("Painel: pastéis produzidos hoje", 48, dash.produced);
        eq("Painel: pastéis vendidos hoje (vendas activas)", 12, dash.sold);
        ok("Painel: alerta de stock baixo de leite (4,5 l > mín 1 l → sem alerta)", dash.lowIngredients.stream().noneMatch(i -> i.name.equals("Leite")));

        // ---------------- Auditoria e integridade
        ok("Auditoria regista as operações importantes (" + db.scalar("SELECT COUNT(*) FROM audit_log") + ")",
                db.scalar("SELECT COUNT(DISTINCT entity) FROM audit_log") >= 8);
        eq("Registos por sincronizar marcados (syncState=1) — offline", 0, db.scalar("SELECT COUNT(*) FROM sales WHERE syncState<>1"));
        ledgerConsistency(db, "Integridade");
        ok("Nenhum aviso de mapeamento de colunas", db.warnings.isEmpty());
        if (!db.warnings.isEmpty()) System.out.println(db.warnings);
        return report("Teste de integração dos repositórios (Room sobre SQLite)");
    }
}
