package com.pastelgest.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.pastelgest.app.R;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.ui.adapter.RowAdapter;

/** Ecrã de detalhe genérico: cabeçalho, acções, informação e secções de histórico. */
public abstract class DetailActivity extends BaseActivity {
    protected TextView title, subtitle, value, status;
    protected ImageView photo;
    protected LinearLayout actions, info, sections;
    protected String id;

    protected abstract String screenTitle();

    /** Carrega e apresenta os dados (chamado em onResume). */
    protected abstract void load();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        setupToolbar(screenTitle(), true);
        id = extraId();
        title = findViewById(R.id.detail_title);
        subtitle = findViewById(R.id.detail_subtitle);
        value = findViewById(R.id.detail_value);
        status = findViewById(R.id.detail_status);
        photo = findViewById(R.id.detail_photo);
        actions = findViewById(R.id.detail_actions);
        info = findViewById(R.id.detail_info);
        sections = findViewById(R.id.detail_sections);
        if (id == null) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (id != null) load();
    }

    protected void clear() {
        actions.removeAllViews();
        info.removeAllViews();
        sections.removeAllViews();
    }

    protected void header(String t, String sub, String val, String st) {
        title.setText(t);
        subtitle.setText(sub);
        subtitle.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        value.setText(val);
        value.setVisibility(val == null ? View.GONE : View.VISIBLE);
        status.setText(st);
        status.setVisibility(st == null || st.isEmpty() ? View.GONE : View.VISIBLE);
    }

    protected void kv(String label, String val) {
        Ui.addKv(info, label, val, Row.TONE_NONE);
    }

    protected void kv(String label, String val, int tone) {
        Ui.addKv(info, label, val, tone);
    }

    protected void action(String label, int icon, View.OnClickListener l) {
        MaterialButton b = (MaterialButton) LayoutInflater.from(this).inflate(R.layout.item_action, actions, false);
        b.setText(label);
        b.setIconResource(icon);
        b.setOnClickListener(l);
        actions.addView(b);
    }

    protected void section(String text) {
        TextView t = (TextView) LayoutInflater.from(this).inflate(R.layout.item_section_title, sections, false);
        t.setText(text);
        sections.addView(t);
    }

    protected View row(Row r, View.OnClickListener l) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_row, sections, false);
        RowAdapter.bind(v, r);
        if (l != null) v.setOnClickListener(l);
        else v.setClickable(false);
        sections.addView(v);
        return v;
    }

    protected void emptyLine(String text) {
        Row r = new Row(null, text, null, null).icon(R.drawable.ic_info, Row.TONE_NONE).noChevron();
        row(r, null);
    }
}
