package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport.*;
import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real 5,000-world reports. Report-only snapshots permit layout reruns without simulations. */
@EnabledIfSystemProperty(named = "montecarlo.pdf.preview", matches = "true")
class MonteCarloPdfPreviewTest {
    static final Path ROOT = Path.of("target/phase4d-pdf-preview");
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    @BeforeAll static void init() throws Exception { startFx(); Files.createDirectories(ROOT); }

    // Test-only cache of already formatted frozen results, not persisted application plan/schema.
    record CachedPoint(int year, MonteCarloPercentiles percentiles, BigDecimal reference) { }
    record CachedChart(String title, String explanation, boolean difference, List<CachedPoint> points, List<Marker> markers, List<Period> periods) { }
    record Cached(String title, String type, String filename, Instant exportedAt, List<String> context, CachedChart chart, List<Section> sections) {
        static Cached of(MonteCarloPdfReport r) {
            var c = r.chart();
            return new Cached(r.title(), r.type(), r.filename(), r.exportedAt(), r.context(), new CachedChart(c.title(), c.explanation(), c.difference(),
                    c.points().stream().map(p -> new CachedPoint(p.year(), p.percentiles().orElse(null), p.reference().orElse(null))).toList(), c.markers(), c.periods()), r.sections());
        }
        MonteCarloPdfReport report() {
            return new MonteCarloPdfReport(title, type, filename, exportedAt, context,
                    new Chart(chart.title(), chart.explanation(), chart.difference(), chart.points().stream().map(p -> new Point(p.year(), Optional.ofNullable(p.percentiles()), Optional.ofNullable(p.reference()))).toList(), chart.markers(), chart.periods()), sections);
        }
    }

    @Test void fiveReportPreviewsFromCompletedResults() throws Exception {
        var reports = new LinkedHashMap<String, MonteCarloPdfReport>();
        for (var name : List.of("fixed", "longevity", "comparison-fixed", "comparison-longevity")) {
            Path cache = ROOT.resolve(name + "-completed.json");
            MonteCarloPdfReport report;
            if (Files.exists(cache) && !Boolean.getBoolean("montecarlo.pdf.preview.regenerate")) report = JSON.readValue(cache.toFile(), Cached.class).report();
            else {
                var plan = MonteCarloComparisonFixtures.plan();
                var settings = MonteCarloSettings.forPlan(plan, 5000, 417, new BigDecimal("0.12"))
                        .withInflation(new MonteCarloInflationSettings(new BigDecimal("0.02"), new BigDecimal("0.0175"), new BigDecimal("-0.02")));
                if (name.startsWith("comparison")) {
                    var mode = name.endsWith("longevity") ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN;
                    var prepared = fx(() -> MonteCarloStrategyComparisonRunService.capture(plan, settings, mode, "1", "1", "67", "67"));
                    var completed = new MonteCarloStrategyComparisonRunService().run(prepared, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
                    report = fx(() -> MonteCarloPdfReportAdapter.from(completed));
                } else {
                    var service = new MonteCarloRunService();
                    var completed = name.equals("fixed") ? service.run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none())
                            : service.runMortality(plan, new MonteCarloMortalityRequest(plan, settings, LongevitySessionSettings.defaults(plan.getPlanningAssumptions().getProjectionStartDate()), 67),
                            AnalysisProgressListener.none(), AnalysisCancellationToken.none());
                    report = fx(() -> MonteCarloPdfReportAdapter.from(completed));
                }
                JSON.writerWithDefaultPrettyPrinter().writeValue(cache.toFile(), Cached.of(report));
            }
            reports.put(name, report);
        }
        var longevity = reports.get("longevity");
        var population = longevity.sections().stream().filter(s -> s.title().equals("Annual living population")).findFirst().orElseThrow().tables().getFirst();
        var smallRows = population.rows().stream().filter(row -> row.getLast().equals("Small")).toList();
        assertFalse(smallRows.isEmpty());
        var sections = new ArrayList<>(longevity.sections());
        sections.add(1, new Section("Late-life population focus", List.of("The following unchanged rows from the completed result have a small surviving sample. Percentiles may be unstable; these households are not omitted."),
                List.of(new Table(population.headings(), smallRows, population.widths()))));
        reports.put("longevity-late", new MonteCarloPdfReport(longevity.title(), longevity.type(), longevity.filename(), longevity.exportedAt(), longevity.context(), longevity.chart(), sections));
        StringBuilder timings = new StringBuilder("Export times exclude all analysis execution and page-image rendering.\n");
        for (var entry : reports.entrySet()) {
            Path path = ROOT.resolve(entry.getKey() + ".pdf");
            try (var previous = Files.list(ROOT)) {
                for (var image : previous.filter(p -> p.getFileName().toString().matches(entry.getKey() + "-page-\\d+\\.png")).toList()) Files.delete(image);
            }
            long start = System.nanoTime(); new MonteCarloPdfExporter().export(entry.getValue(), path);
            timings.append(entry.getKey()).append(": ").append((System.nanoTime() - start) / 1e6).append(" ms\n");
            try (var document = Loader.loadPDF(path.toFile())) {
                var renderer = new PDFRenderer(document);
                var stripper = new BoundsStripper();
                String text = stripper.getText(document);
                assertFalse(text.contains("?"), "No missing glyph substitutions");
                assertTrue(text.contains("Seed: 417")); assertTrue(text.contains("5,000"));
                assertEquals("Retirement Planner", document.getDocumentInformation().getCreator());
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    var image = renderer.renderImageWithDPI(page, 120);
                    javax.imageio.ImageIO.write(image, "png", ROOT.resolve(entry.getKey() + "-page-" + (page + 1) + ".png").toFile());
                    stripper.setStartPage(page + 1); stripper.setEndPage(page + 1);
                    var pageText = stripper.getText(document);
                    assertTrue(pageText.contains("Page " + (page + 1) + " of " + document.getNumberOfPages()));
                    assertTrue(pageText.length() > 120, "No accidental blank page");
                }
                timings.append("  ").append(document.getNumberOfPages()).append(" pages; all text bounds and footers passed.\n");
                javax.imageio.ImageIO.write(renderer.renderImageWithDPI(0, 120, org.apache.pdfbox.rendering.ImageType.GRAY), "png", ROOT.resolve(entry.getKey() + "-grayscale.png").toFile());
            }
        }
        Files.writeString(ROOT.resolve("export-timings.txt"), timings);
    }

    static final class BoundsStripper extends PDFTextStripper {
        BoundsStripper() throws java.io.IOException { }
        @Override protected void writeString(String text, List<TextPosition> positions) throws java.io.IOException {
            for (var position : positions) {
                assertTrue(position.getXDirAdj() >= 20, "Text outside left margin: " + text);
                assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= 592, "Text outside right margin: " + text);
                assertTrue(position.getYDirAdj() > 10 && position.getYDirAdj() < 784, "Text outside page: " + text);
            }
            super.writeString(text, positions);
        }
    }
}
