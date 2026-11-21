package com.pastelgest.app.util;

import android.content.Context;

import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.entity.SaleItem;
import com.pastelgest.app.data.model.DebtRow;
import com.pastelgest.app.data.model.PaymentRow;
import com.pastelgest.app.data.model.SaleRow;
import com.pastelgest.app.repository.SettingsRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Comprovativos em PDF (venda e pagamento). */
public final class ReceiptPdf {
    private static final int W = 300, H = 560, M = 18;

    private ReceiptPdf() { }

    private static void business(Context c, PdfWriter pdf, String docTitle) {
        SettingsRepository s = new SettingsRepository(c);
        String sub = join(s.get(SettingsRepository.BUSINESS_NIF, ""), s.get(SettingsRepository.BUSINESS_PHONE, ""));
        pdf.header(s.businessName(), docTitle + (sub.isEmpty() ? "" : " • " + sub), Prefs.str(c, Prefs.LOGO_PATH, null));
        String addr = s.get(SettingsRepository.BUSINESS_ADDRESS, "");
        if (!addr.isEmpty()) pdf.paragraph(addr);
    }

    private static String join(String a, String b) {
        if (a.isEmpty()) return b.isEmpty() ? "" : "Tel. " + b;
        return "NIF " + a + (b.isEmpty() ? "" : " • Tel. " + b);
    }

    private static void footer(Context c, PdfWriter pdf) {
        String f = new SettingsRepository(c).get(SettingsRepository.RECEIPT_FOOTER, "Obrigado pela preferência!");
        pdf.space(8);
        pdf.paragraph(f);
        pdf.paragraph("Documento emitido pelo PastelGest em " + Fmt.dateTime(System.currentTimeMillis()) + ".");
        pdf.paragraph("Este comprovativo não substitui a factura fiscal.");
    }

    public static File sale(Context c, SaleRow row, List<SaleItem> items, List<PaymentRow> payments, DebtRow debt) throws IOException {
        Sale s = row.sale;
        PdfWriter pdf = new PdfWriter(W, H, M);
        business(c, pdf, "Comprovativo de venda");
        pdf.keyValue("Venda", s.number, true);
        pdf.keyValue("Data", Fmt.dateTime(s.date), false);
        pdf.keyValue("Cliente", row.clientName == null ? "Cliente ocasional" : row.clientName, false);
        pdf.keyValue("Tipo", Sale.TYPE_CREDIT.equals(s.paymentType) ? "Venda a crédito (fiada)" : "Venda paga", false);
        if (Sale.CANCELLED.equals(s.status)) pdf.keyValue("ESTADO", "ANULADA — " + Fmt.nz(s.cancelReason), true);
        pdf.rule();
        for (SaleItem it : items) {
            pdf.keyValue(it.qty + " × " + it.productName, Fmt.money(it.lineTotalCents), false);
            if (it.discountCents > 0) pdf.keyValue("   " + Fmt.money(it.unitPriceCents) + "/un, desconto", "−" + Fmt.money(it.discountCents), false);
        }
        pdf.rule();
        pdf.keyValue("Subtotal", Fmt.money(s.subtotalCents), false);
        pdf.keyValue("Descontos", "−" + Fmt.money(s.discountCents), false);
        pdf.keyValue("TOTAL", Fmt.money(s.totalCents), true);
        pdf.keyValue("Pago", Fmt.money(s.paidCents), false);
        if (debt != null && "ABERTA".equals(debt.debt.status)) {
            pdf.keyValue("Em dívida", Fmt.money(debt.debt.balanceCents), true);
            if (debt.debt.dueDate > 0) pdf.keyValue("Vencimento", Fmt.date(debt.debt.dueDate), false);
        }
        if (!payments.isEmpty()) {
            pdf.rule();
            pdf.title("Pagamentos");
            for (PaymentRow p : payments) {
                pdf.keyValue(Fmt.date(p.payment.date) + " " + Fmt.nz(p.payment.method), Fmt.money(p.payment.amountCents), false);
            }
        }
        footer(c, pdf);
        File f = ShareHelper.cacheFile(c, "receipts", "Venda_" + s.number + ".pdf");
        pdf.save(f);
        return f;
    }

    public static File payment(Context c, PaymentRow p, DebtRow debt) throws IOException {
        PdfWriter pdf = new PdfWriter(W, 420, M);
        business(c, pdf, "Comprovativo de pagamento");
        pdf.keyValue("Recibo", Fmt.nz(p.payment.receiptNumber), true);
        pdf.keyValue("Data", Fmt.dateTime(p.payment.date), false);
        pdf.keyValue("Cliente", Fmt.nz(p.clientName), false);
        pdf.keyValue("Referente à venda", Fmt.nz(p.saleNumber), false);
        pdf.keyValue("Forma de pagamento", Fmt.nz(p.payment.method), false);
        pdf.rule();
        pdf.keyValue("VALOR RECEBIDO", Fmt.money(p.payment.amountCents), true);
        if (debt != null) {
            pdf.keyValue("Valor original da dívida", Fmt.money(debt.debt.originalCents), false);
            pdf.keyValue("Total pago até à data", Fmt.money(debt.debt.paidCents), false);
            pdf.keyValue("Saldo em dívida", Fmt.money(debt.debt.balanceCents), true);
        }
        footer(c, pdf);
        File f = ShareHelper.cacheFile(c, "receipts", "Pagamento_" + Fmt.nz(p.payment.receiptNumber) + ".pdf");
        pdf.save(f);
        return f;
    }
}
