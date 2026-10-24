package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ExhaustivePdfFidelitySupport;
import com.daviddunn.retirementplanner.persistence.*;
import javafx.scene.control.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ExhaustiveHistoricalPdfFidelityTest {
    static final AnalysisKind KIND = AnalysisKind.COUPLE_EXHAUSTIVE;
    static final Path EVIDENCE = Path.of("target", "gate4-exhaustive-pdf");
    @TempDir Path directory;
    @BeforeAll static void fxStartup() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); Files.createDirectories(EVIDENCE); }

    static IntegratedAnalyzerReport stable(IntegratedAnalyzerReport r) {
        return new IntegratedAnalyzerReport(r.analysisType(), r.household(), r.runStarted(), Instant.parse("2026-10-20T18:00:00Z"),
                r.context(), r.selectedSummary(), r.visualization(), r.assumptions(), r.rankedTables(), r.results());
    }
    static void sameFinancialReport(IntegratedAnalyzerReport a, IntegratedAnalyzerReport b) {
        assertEquals(a.household(), b.household()); assertEquals(a.runStarted(), b.runStarted());
        assertEquals(a.assumptions(), b.assumptions()); assertEquals(a.selectedSummary(), b.selectedSummary());
        assertEquals(a.rankedTables(), b.rankedTables()); assertEquals(a.results(), b.results());
        assertEquals(a.visualization(), b.visualization());
    }
    static Map<ClaimingStrategyHeatMapMetric, IntegratedAnalyzerReport> reports(ExhaustivePersistenceTest.Session session, int selected) throws Exception {
        return fx(() -> {
            var model = field(session.dialog(), "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class);
            var entry = model.result().entries().get(selected - 1);
            var heat = field(session.dialog(), "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
            heat.restoreStrategy(entry.strategy());
            field(session.dialog(), "deterministicResultTabs", TabPane.class).getSelectionModel().select(0);
            var table = field(session.dialog(), "exhaustiveTable", TableView.class);
            table.getSelectionModel().select(model.groups().stream().filter(g -> g.representative().generationOrder() == selected).findFirst().orElseThrow());
            var result = new EnumMap<ClaimingStrategyHeatMapMetric, IntegratedAnalyzerReport>(ClaimingStrategyHeatMapMetric.class);
            for (var metric : ClaimingStrategyHeatMapProfile.deterministic().displayMetrics()) {
                heat.metric.setValue(metric); result.put(metric, stable(ExhaustivePersistenceTest.report(session)));
            }
            return result;
        });
    }

    @Test void freshRestoredAndOutdatedExportsRetainExactFinancialSourcesAndEveryMetric() throws Exception {
        var helper = new ExhaustivePersistenceTest(); helper.dir = directory;
        var root = directory.resolve("snapshots"); var repository = new JsonAnalysisSnapshotRepository(root);
        var store = new AnalysisResultStore(repository); var source = helper.saved(store);
        var financialCalls = new AtomicInteger(); var calculated = new AtomicReference<IntegratedSocialSecurityCompleteStrategySearchResult>();
        Map<ClaimingStrategyHeatMapMetric, IntegratedAnalyzerReport> original;
        int selected;
        AnalysisSnapshot snapshot;
        try {
            try (var live = ExhaustivePersistenceTest.session(source)) {
                fx(() -> {
                    live.dialog().exhaustiveCalculation = request -> (progress, cancel) -> {
                        financialCalls.incrementAndGet();
                        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(request.plan());
                        var ages = java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList();
                        var result = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(
                                new IntegratedSocialSecurityCompleteStrategySearchRequest(request.plan(), ages, ages,
                                        standard.primarySurvivorCandidates().subList(0, 2), standard.spouseSurvivorCandidates().subList(0, 2),
                                        IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 20), progress, cancel);
                        calculated.set(result); return result;
                    };
                    ExhaustivePersistenceTest.invoke(live.dialog(), "runExhaustiveSearch"); return null;
                });
                await(live.persistence().notice, "Results saved.");
                // Disk commit and the independent frozen-metadata task can finish in either order.
                await(live.persistence().notice, "Current Results");
                snapshot = repository.load(source.getAnalysisSessionId(), KIND).orElseThrow();
                var model = fx(() -> field(live.dialog(), "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
                selected = model.groups().stream().map(ExhaustiveIntegratedSearchPresentation.Group::representative)
                        .filter(e -> e.retainedDetail().isPresent()).skip(1).findFirst().orElseThrow().generationOrder();
                original = reports(live, selected);
                assertEquals(324, calculated.get().totalStrategyCount());
                assertEquals(20, calculated.get().entries().stream().filter(e -> e.retainedDetail().isPresent()).count());
                assertTrue(calculated.get().rankedSuccessfulEntries().stream().map(e -> e.afterTaxEstateRank().orElseThrow()).distinct().count() < 324);
                assertTrue(original.values().stream().allMatch(r -> r.heatMap().cells().size() == 81));
            }
        } finally { stop(store); }
        byte[] planBefore = Files.readAllBytes(source.getCurrentFile());
        byte[] snapshotBefore = Files.readAllBytes(repository.path(snapshot.planId(), KIND));
        var restarted = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(root));
        var reopened = helper.source(restarted, "restart"); reopened.open(source.getCurrentFile());
        try (var restored = ExhaustivePersistenceTest.session(reopened)) {
            await(restored.persistence().notice, "Current Results");
            var result = fx(() -> field(restored.dialog(), "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class).result());
            ExhaustivePdfFidelitySupport.assertExact(calculated.get(), result);
            var copied = reports(restored, selected);
            for (var metric : original.keySet()) {
                assertEquals(original.get(metric), copied.get(metric));
                comparePdf(original.get(metric), copied.get(metric), metric.name().toLowerCase(Locale.ROOT));
            }
            var oldAssumptions = reopened.getCurrentPlan().getPlanningAssumptions();
            fx(() -> {
                reopened.getCurrentPlan().setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(
                        new BigDecimal("0.1499"), new BigDecimal("0.0998"), new BigDecimal("0.0887"), new BigDecimal("0.0776")),
                        oldAssumptions.getTaxAssumptions(), oldAssumptions.getWithdrawalAssumptions(), oldAssumptions.getDeathScenarioAssumptions(),
                        oldAssumptions.getProjectionLengthYears(), oldAssumptions.getProjectionStartDate(), oldAssumptions.getStateTaxAssumptions()));
                reopened.getCurrentPlan().getHousehold().getPrimaryPerson().setFirstName("CHANGED_LIVE_NAME");
                reopened.markModified(); return null;
            });
            await(restored.persistence().notice, "Outdated");
            var historical = reports(restored, selected);
            for (var metric : original.keySet()) {
                var report = historical.get(metric); sameFinancialReport(original.get(metric), report);
                assertTrue(report.analysisType().contains("Outdated"));
                assertTrue(report.context().toString().contains(snapshot.completedAt().toString()));
                assertFalse(report.toString().contains("CHANGED_LIVE_NAME"));
                exportAndRender(report, "outdated-" + metric.name().toLowerCase(Locale.ROOT));
            }
            assertEquals(1, financialCalls.get());
            assertEquals(0L, fx(restored.jobs()::generation).longValue());
            assertArrayEquals(planBefore, Files.readAllBytes(source.getCurrentFile()));
            assertArrayEquals(snapshotBefore, Files.readAllBytes(repository.path(snapshot.planId(), KIND)));
        } finally { stop(restarted); }
    }

    @Test void originalBaselineWarningsMustBeIncludedInPdfData() {
        var plan = ExhaustivePersistenceTest.plan();
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var result = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(
                new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(67), List.of(67),
                        standard.primarySurvivorCandidates().subList(0, 1), standard.spouseSurvivorCandidates().subList(0, 1),
                        IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 1));
        var model = ExhaustiveIntegratedSearchPresentation.from(result, Duration.ofMillis(1), 20);
        var map = DeterministicHeatMapAdapter.from(model);
        var report = IntegratedAnalyzerReportAdapter.deterministic(IntegratedAnalyzerPdfReportTest.context(false), model, map,
                ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE, map.cell(67, 67), result.entries().getFirst(), model.groups(), "Unavailable originally");
        var baseline = result.currentPlanBaseline();
        assertFalse(baseline.warnings().isEmpty());
        for (String warning : baseline.warnings()) assertTrue(report.results().toString().contains(warning), warning);
    }

    @Test void availableSocialSecurityReferenceStaysFrozenAndIncompatibleExportIsBlocked() throws Exception {
        ExhaustivePersistenceTest.fixture();
        var helper = new ExhaustivePersistenceTest(); helper.dir = directory;
        var repository = new JsonAnalysisSnapshotRepository(directory.resolve("reference-results"));
        var store = new AnalysisResultStore(repository); var source = helper.saved(store);
        var completion = ExhaustivePersistenceTest.completion(source);
        store.accept(completion); settle(store, completion.planId(), KIND);
        var saved = repository.load(completion.planId(), KIND).orElseThrow();
        var expectedReference = ((ExhaustiveSnapshot) completion.payload()).socialSecurityReference();
        assertEquals(new BigDecimal("123456.0012300"), expectedReference.expectedPresentValue());
        assertEquals(expectedReference, ((ExhaustiveSnapshot) saved.payload()).socialSecurityReference());
        try (var session = ExhaustivePersistenceTest.session(source)) {
            await(session.persistence().notice, "Current Results");
            var original = fx(() -> ExhaustivePersistenceTest.report(session));
            assertTrue(original.results().toString().contains("Original SS-only recommendation cross-reference"));
            assertTrue(original.results().toString().contains(com.daviddunn.retirementplanner.ui.util.UIFormatters.money(expectedReference.expectedPresentValue())));
            fx(() -> {
                var person = source.getCurrentPlan().getHousehold().getPrimaryPerson();
                var ss = person.getIncomeSources().stream().filter(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::isInstance)
                        .map(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::cast).findFirst().orElseThrow();
                person.replaceIncomeSource(ss, new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(ss.getName(), ss.getOwnership(),
                        ss.getStartDate(), ss.getEndDate(), new BigDecimal("7777.7777"), ss.getClaimingAge(), ss.getAnnualColaRate(), 2044));
                source.markModified(); return null;
            });
            await(session.persistence().notice, "Outdated");
            var historical = fx(() -> ExhaustivePersistenceTest.report(session));
            sameFinancialReport(original, historical);
            assertEquals(expectedReference, fx(() -> field(session.dialog(), "historicalExhaustiveReference", ExhaustiveSnapshot.CrossReference.class)));
            assertEquals(0L, fx(session.jobs()::generation).longValue());
        } finally { stop(store); }
        var incompatible = new AnalysisSnapshot(saved.schemaVersion(), saved.fingerprintVersion(), saved.planId(), saved.kind(), saved.shape(),
                saved.runId(), saved.completedAt(), saved.applicationBuild(), Map.of("projection", "v999"), saved.references(),
                saved.inputFingerprint(), saved.contextFingerprint(), saved.payloadDigest(), saved.payload(), saved.context(), saved.originPlanId());
        repository.save(incompatible);
        byte[] intact = Files.readAllBytes(repository.path(saved.planId(), KIND));
        var restart = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("reference-results")));
        var reopened = helper.source(restart, "incompatible"); reopened.open(source.getCurrentFile());
        try (var session = ExhaustivePersistenceTest.session(reopened)) {
            await(session.persistence().notice, "Incompatible or Unavailable");
            fx(() -> {
                assertTrue(field(session.dialog(), "deterministicExportPdf", Button.class).isDisabled());
                assertThrows(java.lang.reflect.InvocationTargetException.class, () -> ExhaustivePersistenceTest.report(session));
                return null;
            });
            assertArrayEquals(intact, Files.readAllBytes(repository.path(saved.planId(), KIND)));
        } finally { stop(restart); }
    }

    @Test void longTablesAndLargeDisplayValuesRenderWithRepeatedHeaders() throws Exception {
        var original = IntegratedAnalyzerPdfReportTest.deterministicReport();
        String large = "$123,456,789,012,345.67";
        var tables = original.rankedTables().stream().map(t -> new IntegratedAnalyzerReport.Table(t.title(), t.columns(),
                t.rows().stream().map(row -> row.stream().map(s -> s.contains("$") ? large : s).toList()).toList())).toList();
        var cells = original.heatMap().cells().stream().map(c -> new IntegratedAnalyzerReport.Cell(c.primaryAge(), c.spouseAge(),
                large, c.tier(), c.optimal(), c.selected())).toList();
        var map = new IntegratedAnalyzerReport.HeatMap(original.heatMap().metric(), original.heatMap().explanation(),
                original.heatMap().primaryAges(), original.heatMap().spouseAges(), cells);
        var report = new IntegratedAnalyzerReport(original.analysisType(), original.household(), original.runStarted(), original.exportedAt(),
                original.context(), original.selectedSummary(), map, original.assumptions(), tables, original.results());
        var file = EVIDENCE.resolve("large-values-long-tables.pdf"); new IntegratedAnalyzerPdfExporter().export(report, file);
        try (var document = Loader.loadPDF(file.toFile())) {
            var stripper = new PDFTextStripper();
            assertTrue(document.getNumberOfPages() >= 8);
            int tablePages = 0;
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i); stripper.setEndPage(i); String text = stripper.getText(document);
                if (text.contains("Ranked Strategies - Complete Elections")) {
                    assertTrue(text.contains("Primary Retirement")); assertTrue(text.contains("Claiming Age")); tablePages++;
                }
            }
            assertTrue(tablePages > 1);
            var fitted = new AtomicBoolean();
            var check = new PDFTextStripper() {
                @Override protected void processTextPosition(org.apache.pdfbox.text.TextPosition p) {
                    if (p.getUnicode().contains("$") && p.getXDirAdj() < 510 && p.getFontSizeInPt() < 7.5f) fitted.set(true);
                    super.processTextPosition(p);
                }
            };
            check.setEndPage(1); check.getText(document);
            assertTrue(fitted.get(), "Long amounts must fit inside the unchanged heat-map cells");
            for (int page : new int[]{0, 2, 4, Math.min(10, document.getNumberOfPages() - 1), document.getNumberOfPages() - 1})
                javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(page, 96), "png",
                        EVIDENCE.resolve("large-values-long-tables-page-" + (page + 1) + ".png").toFile());
        }
    }

    static void comparePdf(IntegratedAnalyzerReport original, IntegratedAnalyzerReport restored, String name) throws Exception {
        Path first = exportAndRender(original, "original-" + name);
        Path second = exportAndRender(restored, "restored-" + name);
        try (var a = Loader.loadPDF(first.toFile()); var b = Loader.loadPDF(second.toFile())) {
            assertEquals(new PDFTextStripper().getText(a), new PDFTextStripper().getText(b));
            assertEquals(a.getNumberOfPages(), b.getNumberOfPages());
            for (int page = 0; page < a.getNumberOfPages(); page++) {
                var left = new PDFRenderer(a).renderImageWithDPI(page, 72);
                var right = new PDFRenderer(b).renderImageWithDPI(page, 72);
                assertArrayEquals(left.getRGB(0, 0, left.getWidth(), left.getHeight(), null, 0, left.getWidth()),
                        right.getRGB(0, 0, right.getWidth(), right.getHeight(), null, 0, right.getWidth()));
            }
        }
    }
    static Path exportAndRender(IntegratedAnalyzerReport report, String name) throws Exception {
        Path file = EVIDENCE.resolve(name + ".pdf"); new IntegratedAnalyzerPdfExporter().export(report, file);
        try (var document = Loader.loadPDF(file.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Ranked Strategies")); assertTrue(text.contains("Projection detail retained: true"));
            if (report.analysisType().contains("Outdated")) assertTrue(text.contains("Outdated"));
            var previewPages = name.contains("future_dollar_estate") ? java.util.stream.IntStream.range(0, document.getNumberOfPages()).toArray()
                    : new int[]{0, 1, Math.min(2, document.getNumberOfPages() - 1), document.getNumberOfPages() - 1};
            for (int page : previewPages) {
                var preview = new PDFRenderer(document).renderImageWithDPI(page, 96);
                if (page == 0) for (var tier : com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette.values()) {
                    int color = java.awt.Color.decode(tier.color).getRGB(), count = 0;
                    for (int y = 0; y < preview.getHeight(); y++) for (int x = 0; x < preview.getWidth(); x++)
                        if (preview.getRGB(x, y) == color) count++;
                    assertTrue(count > 20, "Original palette color remains present: " + tier.name());
                }
                javax.imageio.ImageIO.write(preview, "png", EVIDENCE.resolve(name + "-page-" + (page + 1) + ".png").toFile());
            }
        }
        return file;
    }
}
