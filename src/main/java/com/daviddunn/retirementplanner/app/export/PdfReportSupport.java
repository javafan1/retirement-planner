package com.daviddunn.retirementplanner.app.export;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/** Shared PDF primitives, extracted unchanged from the Results projection-chart renderer. */
final class PdfReportSupport {
    static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final Color INK = new Color(25, 40, 65);
    static final Color ORANGE = new Color(232, 137, 0);
    static final PDRectangle LANDSCAPE = new PDRectangle(792, 612);
    private PdfReportSupport() { }
    static void pageNumbers(PDDocument document) throws IOException {
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            var page = document.getPage(i);
            try (var footer = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                text(footer, "Page " + (i + 1) + " of " + document.getNumberOfPages(),
                        page.getMediaBox().getWidth() - 105, 18, 8, false, Color.GRAY);
            }
        }
    }
    static List<String> wrap(String text, float width, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : safe(text).split(" ")) {
            String next = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && BOLD.getStringWidth(next) / 1000 * size > width) { lines.add(current); current = word; }
            else current = next;
        }
        lines.add(current); return lines;
    }
    static String currency(BigDecimal value) { return String.format(Locale.US, "$%,.0f", value); }
    static void text(PDPageContentStream out, String value, float x, float y, float size, boolean bold, Color color) throws IOException {
        out.beginText(); out.setFont(bold ? BOLD : NORMAL, size); out.setNonStrokingColor(color);
        out.newLineAtOffset(x, y); out.showText(safe(value)); out.endText();
    }
    static String safe(String value) {
        StringBuilder text = new StringBuilder();
        for (char c : value.replace('\n', ' ').replace('\u2212', '-').toCharArray()) {
            try { NORMAL.encode(String.valueOf(c)); text.append(c); }
            catch (IllegalArgumentException | IOException exception) { text.append('?'); }
        }
        return text.toString();
    }
    static void rect(PDPageContentStream out, float x, float y, float w, float h, Color color) throws IOException {
        out.setNonStrokingColor(color); out.addRect(x, y, w, h); out.fill();
    }
    static void line(PDPageContentStream out, float x1, float y1, float x2, float y2, Color color, float width) throws IOException {
        out.setStrokingColor(color); out.setLineWidth(width); out.moveTo(x1, y1); out.lineTo(x2, y2); out.stroke();
    }
}
