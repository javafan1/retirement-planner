package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import java.nio.file.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloHistoricalPdfFidelityTest {
    @BeforeAll static void fixtures() { MonteCarloResultLifecycleTest.calculateIndependentFixturesOnce(); }

    @Test void allEightHistoricalReportsPreserveFrozenDataAndPixels() throws Exception {
        for (var frozen : MonteCarloResultLifecycleTest.FIXTURES.values()) {
            var codec = new AnalysisSnapshotCodec();
            var binding = new MonteCarloResultLifecycle.Binding(java.util.UUID.randomUUID(), Path.of("target/frozen-plan.json"), frozen.kind());
            var original = MonteCarloResultLifecycle.completion(binding, frozen).presentationSnapshot(codec);
            var decoded = (MonteCarloSnapshot) codec.decode(codec.encode(original)).payload();
            var a = report(frozen); var b = report(decoded);
            assertEquals(a.context(), b.context()); assertEquals(a.chart(), b.chart()); assertEquals(a.sections(), b.sections());
            var root = Path.of("target/mc-2d-pdf"); Files.createDirectories(root);
            var current = dated(MonteCarloHistoricalPdf.from(a, frozen, MonteCarloHistoricalPdf.Currency.CURRENT, "Saved", false));
            var restored = dated(MonteCarloHistoricalPdf.from(b, decoded, MonteCarloHistoricalPdf.Currency.CURRENT, "Saved", false));
            var historical = dated(MonteCarloHistoricalPdf.from(b, decoded, MonteCarloHistoricalPdf.Currency.OUTDATED, "Saved", false));
            assertEquals(current, restored);
            assertEquals(current.chart(), historical.chart()); assertEquals(current.sections(), historical.sections());
            assertTrue(historical.context().containsAll(b.context()));
            var ap = root.resolve(frozen.kind() + "-original.pdf");
            var bp = root.resolve(frozen.kind() + "-restored.pdf");
            var hp = root.resolve(frozen.kind() + "-outdated.pdf");
            var exporter = new MonteCarloPdfExporter(); exporter.export(current, ap); exporter.export(restored, bp); exporter.export(historical, hp);
            try (var da = Loader.loadPDF(ap.toFile()); var db = Loader.loadPDF(bp.toFile()); var dh = Loader.loadPDF(hp.toFile())) {
                assertEquals(new PDFTextStripper().getText(da), new PDFTextStripper().getText(db));
                assertEquals(da.getNumberOfPages(), db.getNumberOfPages());
                var ra = new PDFRenderer(da); var rb = new PDFRenderer(db);
                for (int page = 0; page < da.getNumberOfPages(); page++) {
                    var ia = ra.renderImageWithDPI(page, 50); var ib = rb.renderImageWithDPI(page, 50);
                    assertArrayEquals(ia.getRGB(0, 0, ia.getWidth(), ia.getHeight(), null, 0, ia.getWidth()),
                            ib.getRGB(0, 0, ib.getWidth(), ib.getHeight(), null, 0, ib.getWidth()));
                }
                var text = new PDFTextStripper().getText(dh);
                assertTrue(text.contains("Historical Analysis"));
                assertTrue(text.contains("Original analysis completed: " + frozen.completedAt()));
                var renderer = new PDFRenderer(dh);
                for (int page = 0; page < Math.min(3, dh.getNumberOfPages()); page++)
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 90), "png", root.resolve(frozen.kind() + "-" + page + ".png").toFile());
            }
        }
    }

    @Test @EnabledIfSystemProperty(named = "mc.pdf.historical.large", matches = "true")
    void realCachedTenThousandPairedTrialsExportWithoutCalculation() throws Exception {
        var path = Path.of("target/mc-codec-capacity-true-false-true-10000.json.gz");
        assertTrue(Files.exists(path), "Requires independently calculated Phase 2B capacity evidence");
        var bytes = Files.readAllBytes(path);
        var frozen = (MonteCarloSnapshot) new AnalysisSnapshotCodec().decode(bytes).payload();
        assertEquals(10000, frozen.paired().outcomes().size());
        var report = MonteCarloHistoricalPdf.from(report(frozen), frozen, MonteCarloHistoricalPdf.Currency.OUTDATED, "Saved", false);
        var output = Path.of("target/mc-2d-pdf/large-10000-outdated.pdf"); Files.createDirectories(output.getParent());
        new MonteCarloPdfExporter().export(report, output);
        try (var doc = Loader.loadPDF(output.toFile())) {
            assertTrue(new PDFTextStripper().getText(doc).contains("10,000"));
            var renderer = new PDFRenderer(doc);
            for (int page = 0; page < Math.min(3, doc.getNumberOfPages()); page++)
                javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 90), "png", output.resolveSibling("large-" + page + ".png").toFile());
        }
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }

    private static MonteCarloPdfReport report(MonteCarloSnapshot s) {
        return s.paired() == null ? MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreRun(s))
                : MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreComparison(s));
    }
    private static MonteCarloPdfReport dated(MonteCarloPdfReport r) {
        return new MonteCarloPdfReport(r.title(), r.type(), r.filename(), Instant.parse("2026-10-21T12:00:00Z"), r.context(), r.chart(), r.sections());
    }
}
