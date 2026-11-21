package com.pastelgest.verify;

import static com.pastelgest.verify.Check.*;

import com.pastelgest.app.core.Money;
import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.data.entity.Expense;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.repository.ClientRepository;
import com.pastelgest.app.repository.ExpenseRepository;
import com.pastelgest.app.repository.ProductRepository;
import com.pastelgest.app.repository.SaleRepository;
import com.pastelgest.app.sync.AuthManager;
import com.pastelgest.app.sync.SyncException;
import com.pastelgest.app.sync.SyncManager;
import com.pastelgest.app.util.Prefs;

/**
 * Dois dispositivos da mesma conta a trabalhar offline e a sincronizar através
 * do servidor emulado: sem duplicações, sem perda de dados recentes e com
 * stocks consistentes.
 */
public class SyncTest {
    static String schema, supabaseSql;

    static FakeContext device(JdbcDb db) throws Exception {
        FakeContext c = IntegrationTest.newDevice(db);
        Prefs.put(c, Prefs.SUPABASE_URL, "https://exemplo.supabase.co");
        Prefs.put(c, Prefs.SUPABASE_KEY, "chave-publica");
        Prefs.put(c, Prefs.REFRESH_TOKEN, "refresh");
        Prefs.put(c, Prefs.ACCESS_TOKEN, "access");
        Prefs.putLong(c, Prefs.TOKEN_EXPIRES, Long.MAX_VALUE / 2);
        Prefs.putBool(c, Prefs.AUTO_SYNC, false);
        return c;
    }

    static SyncManager.Result sync(JdbcDb db, FakeContext c, FakeSupabase server) throws Exception {
        db.install();
        return new SyncManager(c, server.clientFor(c, "conta-1"), new AuthManager(c)).sync();
    }

    static long stockOf(JdbcDb db, String productId) throws Exception {
        return db.scalar("SELECT stockQty FROM products WHERE id='" + productId + "'");
    }

    static Sale sell(FakeContext c, String productId, long qty) {
        SaleRepository.Draft d = new SaleRepository.Draft();
        SaleRepository.DraftItem it = new SaleRepository.DraftItem();
        it.productId = productId;
        it.productName = "Pastel de nata";
        it.qty = qty;
        it.unitPriceCents = Money.parse("250");
        d.items.add(it);
        d.paymentMethod = "Dinheiro";
        return new SaleRepository(c).create(d);
    }

    public static int run() throws Exception {
        FakeSupabase server = new FakeSupabase(supabaseSql);

        // Dispositivo A: cenário completo do teste de integração (trabalho offline)
        IntegrationTest.schema = schema;
        JdbcDb dbA = new JdbcDb(schema);
        FakeContext a = device(dbA);
        // repete um cenário real resumido em A
        com.pastelgest.app.data.entity.Ingredient far = IntegrationTest.ingredient(a, "Farinha", 1, "kg", 0,
                IntegrationTest.kg("10"), Money.parse("12000"));
        com.pastelgest.app.data.entity.Recipe r = new com.pastelgest.app.data.entity.Recipe();
        r.name = "Pastel de nata";
        r.yieldQty = 10;
        r = new com.pastelgest.app.repository.RecipeRepository(a).save(r, java.util.Collections.singletonList(
                IntegrationTest.line(far, "g", IntegrationTest.g("1000"))));
        String productId = new com.pastelgest.app.repository.RecipeRepository(a).get(r.id).productId;
        new com.pastelgest.app.repository.ProductionRepository(a).confirm(
                com.pastelgest.app.data.entity.SyncEntity.newId(), r.id, 40, 0, null);
        sell(a, productId, 5);
        Client cl = new Client();
        cl.name = "João";
        cl.phone = "923456789";
        cl = new ClientRepository(a).save(cl);
        long pendingA = new SyncManager(a).pendingCount();
        ok("Dispositivo A trabalhou offline com " + pendingA + " registos por enviar", pendingA > 10);

        // Sem Internet: falha controlada, nada se perde
        server.offline = true;
        try {
            sync(dbA, a, server);
            ok("Sincronização offline deveria falhar", false);
        } catch (SyncException e) {
            ok("Sem Internet: erro controlado (" + e.getMessage() + ")", true);
        }
        eq("Sem Internet: registos pendentes mantêm-se", pendingA, new SyncManager(a).pendingCount());
        server.offline = false;

        SyncManager.Result ra = sync(dbA, a, server);
        eq("Quando a Internet regressa, A envia todos os pendentes", pendingA, ra.pushed);
        dbA.install();
        eq("A fica sem pendentes", 0, new SyncManager(a).pendingCount());
        int serverRows = 0;
        for (String t : new String[]{"ingredients", "stock_movements", "products", "product_movements", "sales", "sale_items", "payments", "audit_log"}) serverRows += server.rows(t);
        SyncManager.Result again = sync(dbA, a, server);
        eq("Segunda sincronização não reenvia nada", 0, again.pushed);
        int serverRows2 = 0;
        for (String t : new String[]{"ingredients", "stock_movements", "products", "product_movements", "sales", "sale_items", "payments", "audit_log"}) serverRows2 += server.rows(t);
        eq("Sem duplicações no servidor", serverRows, serverRows2);

        // Dispositivo B (novo telemóvel da mesma conta)
        JdbcDb dbB = new JdbcDb(schema);
        FakeContext b = device(dbB);
        SyncManager.Result rb = sync(dbB, b, server);
        ok("B recebe os dados de A (" + rb.pulled + " registos)", rb.pulled >= pendingA);
        eq("B: stock de pastéis recalculado a partir das movimentações", stockOf(dbA, productId), stockOf(dbB, productId));
        eq("B: mesmo número de vendas", dbA.scalar("SELECT COUNT(*) FROM sales"), dbB.scalar("SELECT COUNT(*) FROM sales"));
        eq("B: mesmo valor de stock de farinha", dbA.scalar("SELECT stockValueCents FROM ingredients"), dbB.scalar("SELECT stockValueCents FROM ingredients"));
        eq("B: registos recebidos não ficam marcados como pendentes", 0, dbB.scalar("SELECT COUNT(*) FROM sales WHERE syncState=1"));

        // Trabalho simultâneo offline em A e B
        dbB.install();
        Sale saleB = sell(b, productId, 3);
        dbA.install();
        Sale saleA = sell(a, productId, 2);
        Expense e = new Expense();
        e.category = "Gás"; e.description = "Botija"; e.amountCents = Money.parse("3500"); e.date = System.currentTimeMillis(); e.paymentMethod = "Dinheiro";
        new ExpenseRepository(a).save(e);
        sync(dbB, b, server);
        sync(dbA, a, server);
        sync(dbB, b, server);
        eq("Vendas de ambos os dispositivos presentes em A", 3, dbA.scalar("SELECT COUNT(*) FROM sales"));
        eq("Vendas de ambos os dispositivos presentes em B", 3, dbB.scalar("SELECT COUNT(*) FROM sales"));
        eq("Stock final igual em A e B (40 − 5 − 3 − 2)", 30, stockOf(dbA, productId));
        eq("Stock final em B", 30, stockOf(dbB, productId));
        eq("Despesa criada em A chega a B", 1, dbB.scalar("SELECT COUNT(*) FROM expenses"));
        IntegrationTest.ledgerConsistency(dbA, "Após sincronização (A)");
        IntegrationTest.ledgerConsistency(dbB, "Após sincronização (B)");

        // Conflito: o mesmo cliente editado nos dois dispositivos
        final String clientId = cl.id;
        dbA.install();
        Client ca = new ClientRepository(a).get(clientId);
        ca.name = "João (editado em A)";
        new ClientRepository(a).save(ca);
        Thread.sleep(5);
        dbB.install();
        Client cb = new ClientRepository(b).get(clientId);
        cb.name = "João Manuel (editado em B, mais recente)";
        new ClientRepository(b).save(cb);
        sync(dbB, b, server);   // B (mais recente) chega primeiro ao servidor
        sync(dbA, a, server);   // A tenta enviar a versão mais antiga
        sync(dbB, b, server);
        dbA.install();
        eqs("Conflito resolvido em A: prevalece a alteração mais recente", "João Manuel (editado em B, mais recente)", new ClientRepository(a).get(clientId).name);
        dbB.install();
        eqs("B não é substituído por dados antigos", "João Manuel (editado em B, mais recente)", new ClientRepository(b).get(clientId).name);

        // Anulação feita em B propaga-se e repõe stock em A
        dbB.install();
        new SaleRepository(b).cancel(saleA.id, "Teste de anulação remota");
        sync(dbB, b, server);
        sync(dbA, a, server);
        dbA.install();
        eqs("Anulação feita em B chega a A", Sale.CANCELLED, new SaleRepository(a).getRow(saleA.id).sale.status);
        eq("Stock reposto em A após anulação remota", 32, stockOf(dbA, productId));
        IntegrationTest.ledgerConsistency(dbA, "Após anulação remota (A)");

        ok("Todas as colunas enviadas existem no esquema Supabase", server.errors.isEmpty());
        if (!server.errors.isEmpty()) System.out.println(server.errors);
        ok("Registo local de sincronização preenchido", dbA.scalar("SELECT COUNT(*) FROM sync_log WHERE status='OK'") >= 3);
        return report("Teste de sincronização offline-first (2 dispositivos + Supabase emulado)");
    }
}
