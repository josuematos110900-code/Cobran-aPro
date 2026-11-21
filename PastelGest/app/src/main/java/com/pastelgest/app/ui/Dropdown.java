package com.pastelgest.app.ui;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;

/** Lista de selecção (exposed dropdown Material) com objectos associados. */
public class Dropdown<T> {

    public interface Labeler<T> {
        String label(T item);
    }

    public interface Listener<T> {
        void onSelected(T item);
    }

    public interface Matcher<T> {
        boolean matches(T item);
    }

    private final AutoCompleteTextView view;
    private final List<T> items = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private int selected = -1;
    private Listener<T> listener;

    public Dropdown(AutoCompleteTextView view) {
        this.view = view;
        view.setOnItemClickListener((parent, v, position, id) -> {
            String label = (String) parent.getItemAtPosition(position);
            selected = labels.indexOf(label);
            if (listener != null && selected >= 0) listener.onSelected(items.get(selected));
        });
    }

    public void setItems(List<T> list, Labeler<T> labeler) {
        items.clear();
        labels.clear();
        items.addAll(list);
        for (T t : list) labels.add(labeler.label(t));
        view.setAdapter(new NoFilterAdapter(view.getContext(), new ArrayList<>(labels)));
        if (selected >= items.size()) clear();
    }

    public void setListener(Listener<T> l) {
        listener = l;
    }

    public T selected() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    public int selectedIndex() {
        return selected;
    }

    public void select(int index) {
        if (index < 0 || index >= items.size()) {
            clear();
            return;
        }
        selected = index;
        view.setText(labels.get(index), false);
        if (listener != null) listener.onSelected(items.get(index));
    }

    public boolean selectWhere(Matcher<T> m) {
        for (int i = 0; i < items.size(); i++) {
            if (m.matches(items.get(i))) {
                select(i);
                return true;
            }
        }
        return false;
    }

    public void clear() {
        selected = -1;
        view.setText("", false);
    }

    public List<T> items() {
        return items;
    }

    public void setEnabled(boolean enabled) {
        view.setEnabled(enabled);
    }

    /**
     * Adaptador sem filtragem: num menu de selecção, a lista completa deve
     * aparecer sempre, mesmo depois de um item já estar escolhido.
     */
    static final class NoFilterAdapter extends ArrayAdapter<String> {
        private final List<String> all;

        NoFilterAdapter(android.content.Context c, List<String> items) {
            super(c, android.R.layout.simple_list_item_1, items);
            all = new ArrayList<>(items);
        }

        @Override
        public android.widget.Filter getFilter() {
            return new android.widget.Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults r = new FilterResults();
                    r.values = all;
                    r.count = all.size();
                    return r;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }

                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    return (String) resultValue;
                }
            };
        }
    }
}
