package com.pastelgest.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Escrita simples de documentos PDF com paginação automática (android.graphics.pdf). */
public class PdfWriter {
    private final PdfDocument doc = new PdfDocument();
    private final int width, height, margin;
    private PdfDocument.Page page;
    private Canvas canvas;
    private float y;
    private int pageNo;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint();
    private final Paint fill = new Paint();

    public PdfWriter(int width, int height, int margin) {
        this.width = width;
        this.height = height;
        this.margin = margin;
        text.setColor(Color.rgb(15, 23, 42));
        text.setTextSize(9);
        bold.setColor(Color.rgb(11, 37, 69));
        bold.setTextSize(9);
        bold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        line.setColor(Color.rgb(203, 213, 225));
        line.setStrokeWidth(0.6f);
        fill.setColor(Color.rgb(11, 37, 69));
        newPage();
    }

    public int contentWidth() {
        return width - 2 * margin;
    }

    private void newPage() {
        if (page != null) doc.finishPage(page);
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(width, height, pageNo).create());
        canvas = page.getCanvas();
        y = margin;
    }

    private void ensure(float h) {
        if (y + h > height - margin) newPage();
    }

    public void header(String title, String subtitle, String logoPath) {
        float h = 46;
        ensure(h + 8);
        canvas.drawRoundRect(new RectF(margin, y, width - margin, y + h), 8, 8, fill);
        float x = margin + 10;
        if (logoPath != null && new File(logoPath).exists()) {
            Bitmap b = BitmapFactory.decodeFile(logoPath);
            if (b != null) {
                canvas.drawBitmap(b, null, new RectF(x, y + 7, x + 32, y + 39), null);
                x += 40;
            }
        }
        Paint white = new Paint(bold);
        white.setColor(Color.WHITE);
        white.setTextSize(13);
        canvas.drawText(title, x, y + 20, white);
        Paint whiteSmall = new Paint(text);
        whiteSmall.setColor(Color.rgb(219, 234, 254));
        whiteSmall.setTextSize(8);
        canvas.drawText(subtitle, x, y + 34, whiteSmall);
        y += h + 12;
    }

    public void title(String s) {
        ensure(20);
        Paint p = new Paint(bold);
        p.setTextSize(11);
        canvas.drawText(s, margin, y + 10, p);
        y += 18;
    }

    public void paragraph(String s) {
        for (String l : wrap(s, text, contentWidth())) {
            ensure(12);
            canvas.drawText(l, margin, y + 9, text);
            y += 12;
        }
    }

    public void keyValue(String k, String v, boolean strong) {
        ensure(14);
        Paint p = strong ? bold : text;
        canvas.drawText(k, margin, y + 9, p);
        float w = p.measureText(v);
        canvas.drawText(v, width - margin - w, y + 9, p);
        y += 14;
    }

    public void rule() {
        ensure(6);
        canvas.drawLine(margin, y + 2, width - margin, y + 2, line);
        y += 6;
    }

    public void space(float h) {
        y += h;
    }

    /** Tabela com colunas de largura proporcional ao conteúdo; repete o cabeçalho em cada página. */
    public void table(String[] headers, java.util.List<String[]> rows) {
        int n = headers.length;
        float[] w = new float[n];
        for (int i = 0; i < n; i++) w[i] = Math.max(bold.measureText(headers[i]), 30);
        for (String[] r : rows) {
            for (int i = 0; i < n && i < r.length; i++) w[i] = Math.max(w[i], Math.min(text.measureText(r[i] == null ? "" : r[i]), 160));
        }
        float sum = 0;
        for (float x : w) sum += x;
        float scale = contentWidth() / sum;
        for (int i = 0; i < n; i++) w[i] *= scale;
        drawRow(headers, w, true);
        for (String[] r : rows) {
            if (y + 13 > height - margin) {
                newPage();
                drawRow(headers, w, true);
            }
            drawRow(r, w, false);
        }
    }

    private void drawRow(String[] cells, float[] w, boolean head) {
        ensure(13);
        float x = margin;
        Paint p = head ? bold : text;
        for (int i = 0; i < w.length; i++) {
            String c = i < cells.length && cells[i] != null ? cells[i] : "";
            canvas.drawText(ellipsize(c, p, w[i] - 4), x + 2, y + 9, p);
            x += w[i];
        }
        y += 12;
        canvas.drawLine(margin, y, width - margin, y, line);
        y += 1;
    }

    private static String ellipsize(String s, Paint p, float max) {
        if (p.measureText(s) <= max) return s;
        while (s.length() > 1 && p.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static java.util.List<String> wrap(String s, Paint p, float max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String para : s.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String test = cur.length() == 0 ? word : cur + " " + word;
                if (p.measureText(test) > max && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(test);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    public void save(File f) throws IOException {
        doc.finishPage(page);
        try (FileOutputStream os = new FileOutputStream(f)) {
            doc.writeTo(os);
        } finally {
            doc.close();
        }
    }
}
