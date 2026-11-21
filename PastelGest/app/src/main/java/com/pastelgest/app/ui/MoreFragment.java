package com.pastelgest.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pastelgest.app.R;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.ui.adapter.RowAdapter;

import java.util.ArrayList;
import java.util.List;

/** Menu com todos os módulos secundários. */
public class MoreFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        RecyclerView list = v.findViewById(R.id.more_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        List<Row> rows = new ArrayList<>();
        rows.add(item(PurchaseListActivity.class, "Compras", "Compras de ingredientes e pagamentos", R.drawable.ic_cart, Row.TONE_ACCENT));
        rows.add(item(RecipeListActivity.class, "Receitas e custos", "Custo real por pastel", R.drawable.ic_book, Row.TONE_ACCENT));
        rows.add(item(ClientListActivity.class, "Clientes", "Cadastro e histórico de compras", R.drawable.ic_people, Row.TONE_ACCENT));
        rows.add(item(DebtListActivity.class, "Dívidas", "Vendas fiadas e pagamentos", R.drawable.ic_wallet, Row.TONE_ORANGE));
        rows.add(item(ExpenseListActivity.class, "Despesas", "Despesas fixas e variáveis", R.drawable.ic_money, Row.TONE_RED));
        rows.add(item(ProfitActivity.class, "Lucro real", "Demonstração de resultados", R.drawable.ic_trending, Row.TONE_GREEN));
        rows.add(item(ReportsActivity.class, "Relatórios", "Vendas, stock, produção… em PDF e CSV", R.drawable.ic_chart, Row.TONE_ACCENT));
        rows.add(item(SupplierListActivity.class, "Fornecedores", "Contactos dos fornecedores", R.drawable.ic_truck, Row.TONE_ACCENT));
        rows.add(item(AuditActivity.class, "Auditoria", "Histórico de operações importantes", R.drawable.ic_history, Row.TONE_NONE));
        rows.add(item(SyncActivity.class, "Sincronização", "Supabase e trabalho offline", R.drawable.ic_cloud, Row.TONE_ACCENT));
        rows.add(item(SettingsActivity.class, "Definições", "Pastelaria, tema, PIN e cópias de segurança", R.drawable.ic_tune, Row.TONE_NONE));
        RowAdapter adapter = new RowAdapter(r -> startActivity(new Intent(requireContext(), (Class<?>) r.tag)));
        list.setAdapter(adapter);
        adapter.setItems(rows);
    }

    private static Row item(Class<?> cls, String title, String subtitle, int icon, int tone) {
        return new Row(cls.getSimpleName(), title, subtitle, null).icon(icon, tone).tag(cls);
    }
}
