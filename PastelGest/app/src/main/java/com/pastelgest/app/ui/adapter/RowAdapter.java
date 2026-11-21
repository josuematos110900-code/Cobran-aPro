package com.pastelgest.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.pastelgest.app.R;
import com.pastelgest.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Adaptador único para todas as listas da aplicação. */
public class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

    public interface OnRowClick {
        void onClick(Row row);
    }

    private final List<Row> items = new ArrayList<>();
    private final OnRowClick listener;
    private OnRowClick longListener;

    public RowAdapter(OnRowClick listener) {
        this.listener = listener;
    }

    public void setOnLongClick(OnRowClick l) {
        longListener = l;
    }

    public void setItems(List<Row> rows) {
        items.clear();
        items.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Row r = items.get(position);
        bind(h.itemView, r);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(r);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (longListener == null) return false;
            longListener.onClick(r);
            return true;
        });
    }

    /** Preenche uma vista item_row (usado também fora de listas). */
    public static void bind(View v, Row r) {
        TextView title = v.findViewById(R.id.row_title);
        TextView subtitle = v.findViewById(R.id.row_subtitle);
        TextView value = v.findViewById(R.id.row_value);
        TextView badge = v.findViewById(R.id.row_badge);
        ImageView icon = v.findViewById(R.id.row_icon);
        FrameLayout box = v.findViewById(R.id.row_icon_box);
        ImageView chevron = v.findViewById(R.id.row_chevron);

        title.setText(r.title);
        subtitle.setText(r.subtitle);
        subtitle.setVisibility(r.subtitle == null || r.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        value.setText(r.value == null ? "" : r.value);
        value.setVisibility(r.value == null || r.value.isEmpty() ? View.GONE : View.VISIBLE);
        value.setTextColor(ContextCompat.getColor(v.getContext(), Ui.toneText(r.valueTone)));
        icon.setImageResource(r.icon);
        icon.setColorFilter(ContextCompat.getColor(v.getContext(), Ui.toneIcon(r.iconTone)));
        box.setBackgroundResource(Ui.toneBox(r.iconTone));
        if (r.badge != null && !r.badge.isEmpty()) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(r.badge);
            badge.setBackgroundResource(Ui.toneBadge(r.badgeTone));
            badge.setTextColor(ContextCompat.getColor(v.getContext(), Ui.toneIcon(r.badgeTone)));
        } else {
            badge.setVisibility(View.GONE);
        }
        chevron.setVisibility(r.chevron ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }
}
