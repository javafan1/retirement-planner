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
                document.getDocumentInformation().setTitle(title(report) + " - " + report.analysisType());
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

    private static String title(IntegratedAnalyzerReport report) {
        if (report.visualization() instanceof TablesOnly) return "Deterministic Quick Comparison";
        if (report.visualization() instanceof SocialSecurityGrid) return "Social Security Claiming Analysis";
        return report.analysisType().equals("Social Security Only") ? "Social Security Claiming Analysis" : IntegratedAnalyzerReport.TITLE;
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
            if (report.visualization() instanceof SocialSecurityGrid || report.visualization() instanceof TablesOnly) {
                additionalReport();
                close();
                pageNumbers();
                return;
            }
            newPage(false);
            text(title(report), MARGIN, 570, 19, true, BLUE);
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
            if (report.visualization() instanceof Individual individual) individualMap(mapTop, individual);
            else heatMap(mapTop);
            selectedSummary(mapTop);
            newPage(true);
            for (var section : report.assumptions()) section(section);
            for (var table : report.rankedTables()) {
                if (report.visualization() instanceof Individual) ensure(100); else newPage(true);
                table(table);
                if (report.visualization() instanceof Individual) y -= 24;
            }
            if (!(report.visualization() instanceof Individual)) newPage(true);
            for (var section : report.results()) section(section);
            close();
            pageNumbers();
        }

        private void pageNumbers() throws IOException {
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

        /** New variants use the existing section/table pagination, leaving established report pages unchanged. */
        private void additionalReport() throws IOException {
            newPage(false);
            section(new Section(title(report), List.of(report.analysisType(), report.household(),
                    "Original run started: " + report.runStarted(), "PDF exported: " + report.exportedAt())));
            section(new Section("Result status and provenance", report.context()));
            section(new Section(report.visualization() instanceof SocialSecurityGrid
                    ? "Recommendation and Export-Time Selection" : "Comparison Summary", report.selectedSummary()));
            if (report.visualization() instanceof SocialSecurityGrid grid) {
                newPage(true);
                socialSecurityGrid(grid);
            } else if (report.visualization() instanceof TablesOnly tables) {
                section(new Section("Comparison Method", List.of(tables.explanation())));
            }
            for (var assumptions : report.assumptions()) section(assumptions);
            for (var table : report.rankedTables()) { newPage(true); table(table); }
            y -= 24;
            for (var result : report.results()) section(result);
        }

        private void socialSecurityGrid(SocialSecurityGrid grid) throws IOException {
            float cellWidth = (CONTENT_WIDTH - 36) / grid.primaryAges().size();
            ssAxes(grid, cellWidth);
            for (int spouse : grid.spouseAges()) {
                var row = grid.primaryAges().stream().map(primary -> grid.cells().stream()
                        .filter(c -> c.primaryAge() == primary && c.spouseAge() == spouse).findFirst().orElseThrow()).toList();
                var lines = new ArrayList<List<String>>();
                int maximumLines = 1;
                for (var cell : row) {
                    var wrapped = wrap(cell.value(), 8.5f, cellWidth - 12);
                    lines.add(wrapped); maximumLines = Math.max(maximumLines, wrapped.size());
                }
                float height = Math.max(34, maximumLines * 10 + 19);
                if (y - height < 100) { newPage(true); ssAxes(grid, cellWidth); }
                text(Integer.toString(spouse), MARGIN + 6, y - 20, 9, true, INK);
                for (int column = 0; column < row.size(); column++) {
                    var cell = row.get(column);
                    float x = MARGIN + 36 + column * cellWidth, bottom = y - height;
                    rectangle(x, bottom + 2, cellWidth - 3, height - 3, Color.decode(cell.tier().color));
                    if (cell.optimal() || cell.selected()) {
                        content.setStrokingColor(Color.decode(cell.selected() ? ClaimingHeatMapPalette.SELECTED_BORDER : ClaimingHeatMapPalette.OPTIMAL_BORDER));
                        content.setLineWidth(cell.selected() ? 2 : 1);
                        content.addRect(x + 1, bottom + 3, cellWidth - 5, height - 5); content.stroke();
                    }
                    if (cell.optimal()) star(x + 8, y - 7, 3);
                    if (cell.retained()) {
                        content.setNonStrokingColor(INK);
                        float tx = x + (cell.optimal() ? 17 : 7);
                        content.moveTo(tx, y - 4); content.lineTo(tx, y - 10); content.lineTo(tx + 5, y - 7);
                        content.closePath(); content.fill();
                    }
                    float baseline = y - 21;
                    for (String line : lines.get(column)) { centered(line, x, baseline, cellWidth - 3, 8.5f, cell.optimal()); baseline -= 10; }
                }
                y -= height;
            }
            y -= 15;
            for (var tier : ClaimingHeatMapPalette.values()) {
                ensure(16);
                rectangle(MARGIN, y - 2, 10, 10, Color.decode(tier.color));
                text(tier.label + " — " + tier.help, MARGIN + 16, y, 8, false, INK); y -= 14;
            }
            section(new Section("Stage 1 Grid Explanation", List.of(
                    "Cells show original Stage 1 expected Social Security present value in dollars. Colors use unrounded percentages of the highest positive Stage 1 PV.",
                    "Star = exact Stage 1 optimum (including ties). Triangle = retained for Stage 2. Both markers may appear together. Blue outline = export-time selected pair.",
                    "Stage 2 expands survivor elections and has its own complete-strategy ranking. A Stage 1 optimum is not necessarily the best Stage 2 strategy. No positive optimum means unavailable percentages.")));
        }

        private void ssAxes(SocialSecurityGrid grid, float cellWidth) throws IOException {
            text("STAGE 1 CLAIMING-AGE GRID — Expected Social Security PV", MARGIN, y, 11, true, BLUE); y -= 20;
            text("Primary Claiming Age (horizontal); Spouse Claiming Age (vertical)", MARGIN, y, 9, false, INK); y -= 20;
            text("Spouse", MARGIN, y, 7, true, INK);
            for (int i = 0; i < grid.primaryAges().size(); i++) centered(grid.primaryAges().get(i).toString(), MARGIN + 36 + i * cellWidth, y, cellWidth - 3, 9, true);
            y -= 8;
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

        private void individualMap(float top, Individual map) throws IOException {
            text("PRIMARY CLAIMING-AGE ALTERNATIVES", MARGIN, top, 11, true, BLUE);
            text("Metric: " + map.metric(), MARGIN, top - 16, 9, true, INK);
            text("Age", MARGIN + 8, top - 37, 9, true, INK);
            text("Value", MARGIN + 66, top - 37, 9, true, INK);
            text("% of optimal", MARGIN + 210, top - 37, 9, true, INK);
            text("Markers", MARGIN + 312, top - 37, 9, true, INK);
            float rowTop = top - 45;
            for (var age : map.ages()) {
                float bottom = rowTop - 27;
                rectangle(MARGIN, bottom, 480, 25, Color.decode(age.tier().color));
                if (age.selected()) {
                    content.setStrokingColor(BLUE); content.setLineWidth(2);
                    content.addRect(MARGIN + 1, bottom + 1, 478, 23); content.stroke();
                }
                text(Integer.toString(age.age()), MARGIN + 8, bottom + 8, 9, true, INK);
                text(age.value(), MARGIN + 66, bottom + 8, 9, false, INK);
                text(age.percentOfOptimal(), MARGIN + 210, bottom + 8, 9, false, INK);
                text((age.optimal() ? "Optimal " : "") + (age.selected() ? "Selected " : "") + (age.current() ? "Current" : ""),
                        MARGIN + 312, bottom + 8, 9, true, INK);
                if (age.optimal()) star(MARGIN + 463, bottom + 12, 4);
                rowTop -= 27;
            }
            float legend = rowTop - 14;
            int i = 0;
            for (var tier : ClaimingHeatMapPalette.values()) {
                float x = MARGIN + i % 3 * 164;
                float baseline = legend - i / 3 * 15;
                rectangle(x, baseline - 1, 9, 9, Color.decode(tier.color));
                text(tier.label, x + 12, baseline, 7, false, INK); i++;
            }
            text("Colors use unrounded % of optimal. Stars mark exact ties; outline marks selection.", MARGIN, legend - 36, 7, false, MUTED);
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
                float valueSize = 7.5f;
                var lines = wrap(cell.value(), valueSize, cellWidth - 7);
                // Preserve the existing grid and ordinary text size; fit unusually large displayed amounts.
                while (valueSize > 5.5f && lines.size() * (valueSize + 1) > cellHeight - 6) {
                    valueSize -= 0.25f;
                    lines = wrap(cell.value(), valueSize, cellWidth - 7);
                }
                float lineHeight = valueSize + 1;
                float baseline = bottom + (cellHeight - 3 + lines.size() * lineHeight) / 2 - (valueSize - 0.5f);
                for (String line : lines) {
                    centered(line, x, baseline, cellWidth - 3, valueSize, cell.optimal());
                    baseline -= lineHeight;
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
