package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.daviddunn.retirementplanner.app.export.PdfReportSupport.*;

/** Report-only vector PDF composition. Input has no execution services or mutable plan references. */
public final class MonteCarloPdfExporter {
    public void export(MonteCarloPdfReport report, Path destination) throws IOException {
        Objects.requireNonNull(report); Objects.requireNonNull(destination);
        Path target = destination.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".monte-carlo-report-", ".pdf");
        try {
            try (var document = new PDDocument()) {
                var metadata = document.getDocumentInformation();
                metadata.setTitle(report.title() + " — " + report.type());
                metadata.setSubject("Completed frozen Monte Carlo results; no analysis rerun for export.");
                metadata.setCreator("Retirement Planner");
                try (var writer = new Pages(document, report)) { writer.render(); }
                pageNumbers(document);
                document.save(temporary.toFile());
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static final class Pages implements AutoCloseable {
        private final PDDocument document;
        private final MonteCarloPdfReport report;
        private PDPageContentStream out;
        private float y;
        private String sectionTitle;
        Pages(PDDocument document, MonteCarloPdfReport report) { this.document = document; this.report = report; }
        private void page() throws IOException {
            close();
            var page = new PDPage(PDRectangle.LETTER); document.addPage(page); out = new PDPageContentStream(document, page);
            text(out, "Retirement Planner", 36, 765, 9, true, INK);
            text(out, report.type(), 350, 765, 9, false, INK);
            line(out, 36, 755, 576, 755, new Color(205, 214, 225), .6f);
            y = 735;
        }
        private void ensure(float required) throws IOException { if (y - required < 42) page(); }
        void render() throws IOException {
            page(); paragraph(report.title(), 20, true); paragraph(report.type(), 13, true);
            paragraph("Exported: " + DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(report.exportedAt()), 8, false);
            for (String line : report.context()) paragraph(line, 9, false);
            ensure(310);
            heading(report.chart().title());
            new MonteCarloPdfChart(report.chart(), out, y - 8).draw(); y -= 260;
            for (int i = 0; i < report.chart().markers().size(); i++) paragraph("SS" + (i + 1) + ": " + report.chart().markers().get(i).label(), 8, false);
            if (!report.chart().periods().isEmpty()) {
                paragraph("Reference period shading: Roth (top-edge strip); RMD (bottom-edge strip).", 8, false);
                for (var p : report.chart().periods()) paragraph(p.label() + ": " + p.firstYear() + "–" + p.lastYear(), 8, false);
            }
            paragraph(report.chart().explanation(), 9, false);
            for (var section : report.sections()) {
                float introduction = 30;
                for (String paragraph : section.paragraphs()) for (String line : paragraph.split("\n")) introduction += wrapped(line, 540, 10).size() * 13 + 3;
                if (!section.tables().isEmpty()) {
                    var firstTable = section.tables().getFirst();
                    introduction += rowHeight(firstTable.headings(), firstTable.widths(), 9);
                    if (!firstTable.rows().isEmpty()) introduction += rowHeight(firstTable.rows().getFirst(), firstTable.widths(), 9);
                }
                ensure(section.tables().isEmpty() ? 80 : Math.min(650, introduction));
                sectionTitle = section.title(); heading(section.title());
                for (String paragraph : section.paragraphs()) paragraph(paragraph, 10, false);
                for (var table : section.tables()) table(table);
            }
        }
        private void heading(String value) throws IOException {
            ensure(46); y -= 6; paragraph(value, 12, true);
        }
        private void paragraph(String value, float size, boolean bold) throws IOException {
            for (String paragraph : value.split("\n")) {
                var lines = wrapped(paragraph, 540, size);
                // Keep a short paragraph together; long paragraphs can continue without clipping.
                ensure(Math.min(lines.size(), 3) * (size + 3) + 3);
                for (String line : lines) { ensure(size + 3); text(out, line, 36, y, size, bold, INK); y -= size + 3; }
                y -= 3;
            }
        }
        private void table(Table table) throws IOException {
            float header = rowHeight(table.headings(), table.widths(), 9);
            float first = table.rows().isEmpty() ? 20 : rowHeight(table.rows().getFirst(), table.widths(), 9);
            ensure(header + first); row(table.headings(), table.widths(), true, header, false);
            int index = 0;
            for (var row : table.rows()) {
                float height = rowHeight(row, table.widths(), 9);
                if (y - height < 42) {
                    page(); paragraph(sectionTitle + " (continued)", 11, true);
                    row(table.headings(), table.widths(), true, header, false);
                }
                row(row, table.widths(), false, height, index++ % 2 == 0);
            }
            if (table.rows().isEmpty()) paragraph("No observations available.", 9, false);
            y -= 8;
        }
        private float rowHeight(List<String> row, List<Integer> widths, float size) throws IOException {
            int lines = 1;
            for (int i = 0; i < row.size(); i++) lines = Math.max(lines, wrapped(row.get(i), widths.get(i) - 12, size).size());
            return lines * 12 + 8;
        }
        private void row(List<String> row, List<Integer> widths, boolean header, float height, boolean shaded) throws IOException {
            if (header || shaded) rect(out, 36, y - height + 4, 540, height, header ? new Color(225, 232, 241) : new Color(246, 248, 251));
            float x = 36;
            for (int i = 0; i < row.size(); i++) {
                float baseline = y - 9;
                for (var line : wrapped(row.get(i), widths.get(i) - 12, 9)) {
                    boolean numeric = !header && i > 0;
                    float textWidth = NORMAL.getStringWidth(safe(line)) / 1000 * 9;
                    text(out, line, numeric ? x + widths.get(i) - textWidth - 6 : x + 6, baseline, 9, header, INK);
                    baseline -= 12;
                }
                x += widths.get(i);
            }
            y -= height;
        }
        private static List<String> wrapped(String value, float width, float size) throws IOException {
            // Reuse existing word wrapping, adding bounded splitting for unusually long values/names.
            List<String> result = new ArrayList<>();
            for (String line : wrap(value, width, size)) {
                String remaining = line;
                while (BOLD.getStringWidth(remaining) / 1000 * size > width) {
                    int end = remaining.length();
                    while (end > 1 && BOLD.getStringWidth(remaining.substring(0, end)) / 1000 * size > width) end--;
                    result.add(remaining.substring(0, end)); remaining = remaining.substring(end);
                }
                if (!remaining.isEmpty()) result.add(remaining);
            }
            return result.isEmpty() ? List.of("") : result;
        }
        @Override public void close() throws IOException { if (out != null) { out.close(); out = null; } }
    }
}
