package com.pastelgest.app.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.ui.adapter.Row;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** Utilitários de interface: mensagens, diálogos, cores e campos. */
public final class Ui {
    private static final String TAG = "PastelGest";

    public interface TextCallback {
        void onText(String text);
    }

    public interface DateCallback {
        void onDate(long millis);
    }

    private Ui() { }

    public static String text(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void success(Activity a, String msg) {
        View root = a.findViewById(android.R.id.content);
        if (root != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        else toast(a, msg);
    }

    /** Apresenta o erro: regras de negócio como mensagem clara; erros técnicos com detalhe. */
    public static void showError(Context c, Throwable t) {
        String msg;
        if (t instanceof BusinessException) {
            msg = t.getMessage();
        } else if (t instanceof android.database.sqlite.SQLiteConstraintException) {
            Log.e(TAG, "Restrição da base de dados", t);
            msg = "A operação viola uma regra de integridade dos dados e foi cancelada. Nada foi alterado.";
        } else {
            Log.e(TAG, "Erro inesperado", t);
            msg = "Ocorreu um erro inesperado e a operação foi cancelada (nada foi alterado).\n\n" + t.getMessage();
        }
        new MaterialAlertDialogBuilder(c)
                .setTitle(t instanceof BusinessException ? "Atenção" : "Erro")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    public static void confirm(Context c, String title, String message, String positive, Runnable onYes) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .show();
    }

    public static void info(Context c, String title, String message) {
        new MaterialAlertDialogBuilder(c).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    /** Pede um texto obrigatório (ex.: motivo de anulação). */
    public static void askText(Context c, String title, String info, String hint, String positive, TextCallback cb) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_text, null);
        TextView infoView = v.findViewById(R.id.dt_info);
        EditText input = v.findViewById(R.id.dt_input);
        TextInputLayout layout = v.findViewById(R.id.dt_input_layout);
        infoView.setText(info);
        infoView.setVisibility(info == null ? View.GONE : View.VISIBLE);
        if (hint != null) layout.setHint(hint);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(v)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(positive, null)
                .create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String t = text(input);
            if (t.isEmpty()) {
                layout.setError("Campo obrigatório");
                return;
            }
            d.dismiss();
            cb.onText(t);
        }));
        d.show();
    }

    public static void pickDate(Context c, long initial, DateCallback cb) {
        Calendar cal = Calendar.getInstance();
        if (initial > 0) cal.setTimeInMillis(initial);
        new DatePickerDialog(c, (view, y, m, d) -> {
            Calendar r = Calendar.getInstance();
            r.set(y, m, d, 12, 0, 0);
            r.set(Calendar.MILLISECOND, 0);
            cb.onDate(r.getTimeInMillis());
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    public static List<String> paymentMethods(Context c) {
        return Arrays.asList(c.getResources().getStringArray(R.array.payment_methods));
    }

    public static void suggestions(AutoCompleteTextView v, List<String> values) {
        v.setAdapter(new ArrayAdapter<>(v.getContext(), android.R.layout.simple_list_item_1, values));
    }

    public static void clearErrors(TextInputLayout... layouts) {
        for (TextInputLayout l : layouts) l.setError(null);
    }

    /** Adiciona uma linha etiqueta/valor (item_kv) a um contentor. */
    public static TextView addKv(LinearLayout container, String label, String value, int tone) {
        View v = LayoutInflater.from(container.getContext()).inflate(R.layout.item_kv, container, false);
        ((TextView) v.findViewById(R.id.kv_label)).setText(label);
        TextView val = v.findViewById(R.id.kv_value);
        val.setText(value);
        val.setTextColor(ContextCompat.getColor(container.getContext(), toneText(tone)));
        container.addView(v);
        return val;
    }

    public static int toneText(int tone) {
        switch (tone) {
            case Row.TONE_GREEN: return R.color.pg_green;
            case Row.TONE_RED: return R.color.pg_red;
            case Row.TONE_ORANGE: return R.color.pg_orange;
            case Row.TONE_ACCENT: return R.color.pg_accent;
            default: return R.color.pg_text;
        }
    }

    public static int toneIcon(int tone) {
        switch (tone) {
            case Row.TONE_GREEN: return R.color.pg_green;
            case Row.TONE_RED: return R.color.pg_red;
            case Row.TONE_ORANGE: return R.color.pg_orange;
            default: return R.color.pg_navy;
        }
    }

    public static int toneBox(int tone) {
        switch (tone) {
            case Row.TONE_GREEN: return R.drawable.bg_icon_green;
            case Row.TONE_RED: return R.drawable.bg_icon_red;
            case Row.TONE_ORANGE: return R.drawable.bg_icon_orange;
            default: return R.drawable.bg_icon_accent;
        }
    }

    public static int toneBadge(int tone) {
        switch (tone) {
            case Row.TONE_GREEN: return R.drawable.bg_badge_green;
            case Row.TONE_RED: return R.drawable.bg_badge_red;
            case Row.TONE_ORANGE: return R.drawable.bg_badge_orange;
            default: return R.drawable.bg_badge_accent;
        }
    }

    public static int moneyTone(long cents) {
        return cents < 0 ? Row.TONE_RED : (cents > 0 ? Row.TONE_GREEN : Row.TONE_NONE);
    }

    public static void open(Context c, Class<?> cls, String id) {
        Intent i = new Intent(c, cls);
        if (id != null) i.putExtra(BaseActivity.EXTRA_ID, id);
        c.startActivity(i);
    }
}
