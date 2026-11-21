package com.pastelgest.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Money;

/** Diálogos de quantidade+motivo e de pagamento. */
public final class Dialogs {

    public interface QtyCallback {
        void onResult(String qtyText, String reason);
    }

    public interface PaymentCallback {
        void onResult(long amountCents, String method, String note);
    }

    private Dialogs() { }

    public static void qtyReason(Context c, String title, String info, String qtyHint, String suffix,
                                 String initialQty, String positive, QtyCallback cb) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_qty_reason, null);
        ((TextView) v.findViewById(R.id.dq_info)).setText(info);
        TextInputLayout qtyLayout = v.findViewById(R.id.dq_qty_layout);
        TextInputLayout reasonLayout = v.findViewById(R.id.dq_reason_layout);
        EditText qty = v.findViewById(R.id.dq_qty);
        EditText reason = v.findViewById(R.id.dq_reason);
        qtyLayout.setHint(qtyHint);
        qtyLayout.setSuffixText(suffix);
        if (initialQty != null) qty.setText(initialQty);
        AlertDialog d = new MaterialAlertDialogBuilder(c).setTitle(title).setView(v)
                .setNegativeButton("Cancelar", null).setPositiveButton(positive, null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            Ui.clearErrors(qtyLayout, reasonLayout);
            if (Ui.text(qty).isEmpty()) {
                qtyLayout.setError("Indique a quantidade.");
                return;
            }
            if (Ui.text(reason).isEmpty()) {
                reasonLayout.setError("Indique o motivo.");
                return;
            }
            d.dismiss();
            cb.onResult(Ui.text(qty), Ui.text(reason));
        }));
        d.show();
    }

    public static void payment(Context c, String title, String info, long suggestedCents, PaymentCallback cb) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_payment, null);
        ((TextView) v.findViewById(R.id.dp_info)).setText(info);
        EditText amount = v.findViewById(R.id.dp_amount);
        EditText note = v.findViewById(R.id.dp_note);
        TextInputLayout amountLayout = v.findViewById(R.id.dp_amount_layout);
        Dropdown<String> method = new Dropdown<>(v.findViewById(R.id.dp_method));
        method.setItems(Ui.paymentMethods(c), m -> m);
        method.select(0);
        if (suggestedCents > 0) amount.setText(Money.formatInput(suggestedCents));
        AlertDialog d = new MaterialAlertDialogBuilder(c).setTitle(title).setView(v)
                .setNegativeButton("Cancelar", null).setPositiveButton("Registar pagamento", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            amountLayout.setError(null);
            try {
                long val = Money.parseNonNegative(Ui.text(amount));
                if (val <= 0) throw new BusinessException("O valor tem de ser superior a zero.");
                // Desactiva o botão imediatamente para impedir pagamentos em duplicado.
                b.setEnabled(false);
                d.dismiss();
                cb.onResult(val, method.selected(), Ui.text(note));
            } catch (BusinessException e) {
                amountLayout.setError(e.getMessage());
            }
        }));
        d.show();
    }
}
