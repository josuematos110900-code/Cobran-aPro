package com.pastelgest.app.util;

import android.content.Context;

import com.pastelgest.app.repository.ReportRepository;
import com.pastelgest.app.repository.SettingsRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/** Exportação de relatórios para CSV (Excel, separador ;) e PDF (A4 horizontal). */
public final class ReportExporter {
    private ReportExporter() { }

    private static String safeName(String s) {
        return s.replaceAll("[^A-Za-z0-9À-ÿ]+", "_");
    }

    public static File csv(Context c, ReportRepository.Report r) throws IOException {
        File f = ShareHelper.cacheFile(c, "exports", safeName(r.title) + "_" + Fmt.fileStamp(System.currentTimeMillis()) + ".csv");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write('﻿');
            w.write(cell(r.title) + "\n" + cell(r.periodLabel) + "\n\n");
            w.write(line(r.headers));
            for (String[] row : r.rows) w.write(line(row));
            w.write("\n");
            for (String[] s : r.summary) w.write(line(s));
        }
        return f;
    }

    private static String line(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(cell(cells[i]));
        }
        return sb.append("\n").toString();
    }

    private static String cell(String v) {
        if (v == null) return "";
        String s = v.replace(" ", " ");
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    public static File pdf(Context c, ReportRepository.Report r) throws IOException {
        PdfWriter pdf = new PdfWriter(842, 595, 32);
        pdf.header(new SettingsRepository(c).businessName(), r.title + " • " + r.periodLabel, Prefs.str(c, Prefs.LOGO_PATH, null));
        if (!r.summary.isEmpty()) {
            pdf.title("Resumo");
            for (String[] s : r.summary) pdf.keyValue(s[0], s.length > 1 ? s[1] : "", false);
            pdf.space(8);
        }
        pdf.title("Detalhe (" + r.rows.size() + " registos)");
        if (r.rows.isEmpty()) pdf.paragraph("Sem registos no período.");
        else pdf.table(r.headers, r.rows);
        pdf.space(8);
        pdf.paragraph("Gerado pelo PastelGest em " + Fmt.dateTime(System.currentTimeMillis()) + ". Valores em Kz.");
        File f = ShareHelper.cacheFile(c, "exports", safeName(r.title) + "_" + Fmt.fileStamp(System.currentTimeMillis()) + ".pdf");
        pdf.save(f);
        return f;
    }
}
