package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.*;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Structured landscape report. All coordinates are layout only; monetary values arrive formatted. */
public final class IntegratedAnalyzerPdfExporter {
    public void export(IntegratedAnalyzerReport report, Path destination) throws IOException {
        Objects.requireNonNull(report);
        Objects.requireNonNull(destination);
        Path target = destination.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".integrated-analysis-", ".pdf");
        try {
            try (PDDocument document = new PDDocument()) {
                document.getDocumentInformation().setTitle(IntegratedAnalyzerReport.TITLE + " - " + report.analysisType());
                document.getDocumentInformation().setAuthor("Retirement Planner");
                document.getDocumentInformation().setSubject("Completed analysis results; no analysis was rerun for export.");
                try (Renderer renderer = new Renderer(document, report)) {
                    renderer.render();
                }
                document.save(temporary.toFile());
            }
            // A renderer failure never damages an existing destination.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static final class Renderer implements AutoCloseable {
        private static final float WIDTH = 792;
        private static final float HEIGHT = 612;
        private static final float MARGIN = 36;
        private static final float CONTENT_WIDTH = WIDTH - 2 * MARGIN;
        private static final Color INK = Color.decode("#142536");
        private static final Color BLUE = Color.decode("#173f78");
        private static final Color MUTED = Color.decode("#526477");
        private static final Color LIGHT = Color.decode("#f0f4f8");
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDDocument document;
        private final IntegratedAnalyzerReport report;
        private PDPageContentStream content;
        private float y;

        Renderer(PDDocument document, IntegratedAnalyzerReport report) {
            this.document = document;
            this.report = report;
        }

        void render() throws IOException {
            newPage(false);
            text(IntegratedAnalyzerReport.TITLE, MARGIN, 570, 19, true, BLUE);
            text(report.analysisType(), MARGIN, 548, 15, true, INK);
            text(report.household(), MARGIN, 529, 10, true, INK);
            var dateTime = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC);
            text("Run started: " + dateTime.format(report.runStarted()) + "  |  Exported: " + dateTime.format(report.exportedAt()), MARGIN, 514, 8, false, MUTED);
            y = 499;
            for (String line : report.context()) {
                for (String wrapped : wrap(line, 9, CONTENT_WIDTH)) {
                    text(wrapped, MARGIN, y, 9, false, INK);
                    y -= 11;
                }
            }
            float mapTop = Math.min(438, y - 10);
            heatMap(mapTop);
            selectedSummary(mapTop);
            newPage(true);
            for (var section : report.assumptions()) section(section);
            for (var table : report.rankedTables()) {
                newPage(true);
                table(table);
            }
            newPage(true);
            for (var section : report.results()) section(section);
            close();
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(index),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.setStrokingColor(Color.decode("#c4ccd7"));
                    footer.moveTo(MARGIN, 30);
                    footer.lineTo(WIDTH - MARGIN, 30);
                    footer.stroke();
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.setNonStrokingColor(MUTED);
                    footer.newLineAtOffset(MARGIN, 18);
                    footer.showText(report.analysisType() + "  |  Retirement Planner  |  Page " + (index + 1) + " of " + document.getNumberOfPages());
                    footer.endText();
                }
            }
        }

        private void newPage(boolean continuation) throws IOException {
            close();
            PDPage page = new PDPage(new PDRectangle(WIDTH, HEIGHT));
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = HEIGHT - MARGIN;
            if (continuation) {
                text(report.analysisType(), MARGIN, y, 11, true, BLUE);
                y -= 18;
                text(report.household(), MARGIN, y, 9, false, MUTED);
                y -= 24;
            }
        }

        private void heatMap(float top) throws IOException {
            var map = report.heatMap();
            text("CLAIMING-AGE HEAT MAP", MARGIN, top, 11, true, BLUE);
            text("Metric: " + map.metric(), MARGIN, top - 15, 9, true, INK);
            float gridX = MARGIN + 33;
            float gridTop = top - 49;
            float cellWidth = 49;
            float cellHeight = 29;
            text("Primary Claiming Age", gridX + 155, top - 30, 9, true, INK);
            text("Spouse", MARGIN, gridTop + 14, 7, true, INK);
            text("Age", MARGIN, gridTop + 4, 7, true, INK);
            for (int column = 0; column < map.primaryAges().size(); column++) {
                centered(Integer.toString(map.primaryAges().get(column)), gridX + column * cellWidth,
                        gridTop + 5, cellWidth - 3, 9, true);
            }
            for (int row = 0; row < map.spouseAges().size(); row++) {
                text(Integer.toString(map.spouseAges().get(row)), MARGIN + 12, gridTop - row * cellHeight - 13, 9, true, INK);
            }
            for (var cell : map.cells()) {
                int column = map.primaryAges().indexOf(cell.primaryAge());
                int row = map.spouseAges().indexOf(cell.spouseAge());
                float x = gridX + column * cellWidth;
                float bottom = gridTop - (row + 1) * cellHeight;
                rectangle(x, bottom, cellWidth - 3, cellHeight - 3, Color.decode(cell.tier().color));
                if (cell.optimal() || cell.selected()) {
                    content.setStrokingColor(Color.decode(cell.selected() ? ClaimingHeatMapPalette.SELECTED_BORDER : ClaimingHeatMapPalette.OPTIMAL_BORDER));
                    content.setLineWidth(cell.selected() ? 2 : 1);
                    content.addRect(x + 1, bottom + 1, cellWidth - 5, cellHeight - 5);
                    content.stroke();
                }
                var lines = wrap(cell.value(), 7.5f, cellWidth - 7);
                float baseline = bottom + (cellHeight - 3 + lines.size() * 8.5f) / 2 - 7;
                for (String line : lines) {
                    centered(line, x, baseline, cellWidth - 3, 7.5f, cell.optimal());
                    baseline -= 8.5f;
                }
                if (cell.optimal()) star(x + cellWidth - 8, bottom + cellHeight - 8, 3);
            }
            float legendY = gridTop - map.spouseAges().size() * cellHeight - 16;
            int index = 0;
            for (var tier : ClaimingHeatMapPalette.values()) {
                float x = MARGIN + index % 3 * 164;
                float baseline = legendY - index / 3 * 15;
                rectangle(x, baseline - 1, 10, 9, Color.decode(tier.color));
                String range = tier == ClaimingHeatMapPalette.UNAVAILABLE ? "" : " (" + tier.help
                        .replace("At least ", ">=").replace("Below ", "<").replace(" to below ", " to <") + ")";
                text(tier.label + range, x + 14, baseline, 7, false, INK);
                index++;
            }
            y = legendY - 34;
            for (String line : wrap(map.explanation(), 7.5f, 490)) {
                text(line, MARGIN, y, 7.5f, false, MUTED);
                y -= 9;
            }
        }

        private void selectedSummary(float top) throws IOException {
            float x = 540;
            float width = WIDTH - MARGIN - x;
            rectangle(x - 10, 70, width + 10, top - 58, LIGHT);
            text("SELECTED STRATEGY", x, top - 7, 11, true, BLUE);
            float baseline = top - 28;
            for (int i = 0; i < report.selectedSummary().size(); i++) {
                for (String line : wrap(report.selectedSummary().get(i), 9, width - 8)) {
                    text(line, x, baseline, 9, i < 2, INK);
                    baseline -= 11;
                }
                baseline -= 5;
            }
        }

        private void section(Section section) throws IOException {
            ensure(54);
            text(section.title(), MARGIN, y, 12, true, BLUE);
            y -= 21;
            for (String value : section.lines()) {
                for (String line : wrap(value, 10, CONTENT_WIDTH)) {
                    ensure(14);
                    text(line, MARGIN, y, 10, false, INK);
                    y -= 13;
                }
                y -= 5;
            }
            y -= 12;
        }

        private void table(Table table) throws IOException {
            float sum = table.columns().stream().mapToInt(Column::width).sum();
            float[] widths = new float[table.columns().size()];
            for (int i = 0; i < widths.length; i++) widths[i] = CONTENT_WIDTH * table.columns().get(i).width() / sum;
            tableHeader(table, widths);
            int rowNumber = 0;
            for (var row : table.rows()) {
                var lines = new ArrayList<List<String>>();
                int max = 1;
                for (int i = 0; i < row.size(); i++) {
                    var wrapped = wrap(row.get(i), 9, widths[i] - 12);
                    lines.add(wrapped);
                    max = Math.max(max, wrapped.size());
                }
                float height = max * 11 + 12;
                if (y - height < 44) {
                    newPage(true);
                    tableHeader(table, widths);
                }
                if (rowNumber++ % 2 == 0) rectangle(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT);
                float x = MARGIN;
                for (int i = 0; i < lines.size(); i++) {
                    float baseline = y - 13;
                    for (var line : lines.get(i)) {
                        float tx = table.columns().get(i).numeric() ? x + widths[i] - 6 - textWidth(line, 9, false) : x + 6;
                        text(line, tx, baseline, 9, false, INK);
                        baseline -= 11;
                    }
                    x += widths[i];
                }
                y -= height;
            }
            if (table.rows().isEmpty()) { text("No ranked rows available.", MARGIN, y - 15, 10, false, INK); y -= 30; }
        }

        private void tableHeader(Table table, float[] widths) throws IOException {
            text(table.title(), MARGIN, y, 12, true, BLUE);
            y -= 15;
            int max = 1;
            List<List<String>> labels = new ArrayList<>();
            for (int i = 0; i < widths.length; i++) {
                var lines = wrap(table.columns().get(i).title(), 9, widths[i] - 12);
                labels.add(lines);
                max = Math.max(max, lines.size());
            }
            float height = max * 11 + 12;
            rectangle(MARGIN, y - height, CONTENT_WIDTH, height, BLUE);
            float x = MARGIN;
            for (int i = 0; i < labels.size(); i++) {
                float baseline = y - 13;
                for (String line : labels.get(i)) { text(line, x + 6, baseline, 9, true, Color.WHITE); baseline -= 11; }
                x += widths[i];
            }
            y -= height;
        }

        private void ensure(float required) throws IOException { if (y - required < 40) newPage(true); }

        private List<String> wrap(String value, float size, float width) throws IOException {
            List<String> result = new ArrayList<>();
            for (String paragraph : safe(Objects.requireNonNullElse(value, "Unavailable")).split("\n", -1)) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" +")) {
                    if (!line.isEmpty() && textWidth(line + " " + word, size, true) > width) {
                        result.add(line.toString()); line.setLength(0);
                    }
                    // Long tokens (including money) wrap without losing characters.
                    for (char c : word.toCharArray()) {
                        if (textWidth(line.toString() + c, size, true) > width && !line.isEmpty()) {
                            result.add(line.toString()); line.setLength(0);
                        }
                        line.append(c);
                    }
                    line.append(' ');
                }
                result.add(line.toString().stripTrailing());
            }
            return result;
        }

        private void text(String value, float x, float baseline, float size, boolean strong, Color color) throws IOException {
            content.beginText();
            content.setFont(strong ? bold : regular, size);
            content.setNonStrokingColor(color);
            content.newLineAtOffset(x, baseline);
            content.showText(safe(value));
            content.endText();
        }

        private float textWidth(String value, float size, boolean strong) throws IOException {
            return (strong ? bold : regular).getStringWidth(safe(value)) / 1000 * size;
        }

        private void centered(String value, float x, float baseline, float width, float size, boolean strong) throws IOException {
            text(value, x + (width - textWidth(value, size, strong)) / 2, baseline, size, strong, INK);
        }

        private void rectangle(float x, float bottom, float width, float height, Color color) throws IOException {
            content.setNonStrokingColor(color);
            content.addRect(x, bottom, width, height);
            content.fill();
        }

        private void star(float x, float y, float radius) throws IOException {
            content.setNonStrokingColor(Color.decode(ClaimingHeatMapPalette.OPTIMAL_BORDER));
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI / 2 + i * Math.PI / 5;
                double distance = i % 2 == 0 ? radius : radius * 0.4;
                float px = x + (float) (Math.cos(angle) * distance);
                float py = y + (float) (Math.sin(angle) * distance);
                if (i == 0) content.moveTo(px, py); else content.lineTo(px, py);
            }
            content.closePath();
            content.fill();
        }

        private String safe(String value) {
            StringBuilder result = new StringBuilder();
            value = value.replace('\u2011', '-').replace('\u2212', '-').replace('\u2265', '>').replace('\u00a0', ' ');
            for (int point : value.codePoints().toArray()) {
                if (point == '\n') { result.append('\n'); continue; }
                String character = new String(Character.toChars(point));
                try { regular.encode(character); result.append(character); }
                catch (IllegalArgumentException | IOException unsupported) { result.append('?'); }
            }
            return result.toString();
        }

        @Override public void close() throws IOException {
            if (content != null) { content.close(); content = null; }
        }
    }
}
