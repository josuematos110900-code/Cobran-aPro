package com.pastelgest.app.ui;

import com.pastelgest.app.R;
import com.pastelgest.app.data.entity.AuditLog;
import com.pastelgest.app.repository.AuditRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Histórico de auditoria das operações importantes (só leitura). */
public class AuditActivity extends SimpleListActivity {
    private static final String[] FILTERS = {"Tudo", "Vendas", "Compras", "Produção", "Dívidas", "Despesas", "Stock", "Definições"};
    private static final String[] ENTITIES = {"", "venda", "compra", "producao", "divida", "despesa", "ingrediente", "definicao"};

    @Override protected String title() { return "Auditoria"; }
    @Override protected String[] filterLabels() { return FILTERS; }
    @Override protected int emptyIcon() { return R.drawable.ic_history; }

    @Override
    protected Page load(String query, int filter) {
        List<Row> rows = new ArrayList<>();
        String q = query.toLowerCase(Fmt.PT);
        for (AuditLog a : new AuditRepository(this).recent(ENTITIES[filter], 500)) {
            if (!q.isEmpty() && !Fmt.nz(a.details).toLowerCase(Fmt.PT).contains(q)) continue;
            rows.add(new Row(a.id, a.entity + " • " + a.action, Fmt.dateTime(a.date) + " • disp. " + Fmt.nz(a.device) + "\n" + Fmt.nz(a.details), null)
                    .icon(R.drawable.ic_history, Row.TONE_NONE).noChevron().tag(a));
        }
        return new Page(rows, "Últimos " + rows.size() + " registos (imutáveis)");
    }

    @Override
    protected void onRowClick(Row row) {
        AuditLog a = (AuditLog) row.tag;
        Ui.info(this, a.entity + " • " + a.action, Fmt.dateTime(a.date) + "\nDispositivo: " + Fmt.nz(a.device) + "\n\n" + Fmt.nz(a.details));
    }
}
