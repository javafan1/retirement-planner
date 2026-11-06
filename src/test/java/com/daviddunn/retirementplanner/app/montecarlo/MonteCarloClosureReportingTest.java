package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StandaloneExpenseCapabilityTest.*;
import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloClosureReportingTest {
    static Stream<Arguments> configurations() {
        return Stream.of(false, true).flatMap(c -> Stream.of(false, true).flatMap(l ->
                Stream.of(false, true).flatMap(p -> Stream.of(false, true).map(e -> Arguments.of(c, l, p, e)))));
    }

    public static MonteCarloSnapshot capture(boolean couple, boolean longevity, boolean paired, boolean enabled) {
        var a = plan(couple);
        if (enabled) enable(a, ".25");
        var settings = MonteCarloSettings.forPlan(a, 2, 417, new BigDecimal(".12"))
                .withInflation(MonteCarloInflationTest.inflation(".0175"));
        if (paired) {
            var b = plan(couple);
            if (enabled) enable(b, ".17");
            a.setBaseline(ProjectionBaselineFactory.create(b, "Independent frozen baseline"));
            var service = new MonteCarloStrategyComparisonRunService();
            var prepared = MonteCarloStrategyComparisonRunService.capture(a, settings,
                    longevity ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN,
                    "1", "1", "67", "67");
            return MonteCarloSnapshotPresentationMapper.capture(service.run(prepared, PROGRESS, NEVER), START, START.plusSeconds(1));
        }
        var service = new MonteCarloRunService();
        var run = longevity ? service.runMortality(a, mortality(a, settings), PROGRESS, NEVER)
                : service.run(a, settings, null, PROGRESS, NEVER);
        return MonteCarloSnapshotPresentationMapper.capture(run, a, START, START.plusSeconds(1));
    }

    public static MonteCarloPdfReport report(MonteCarloSnapshot snapshot) {
        return snapshot.paired() == null
                ? MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreRun(snapshot))
                : MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreComparison(snapshot));
    }

    static String text(MonteCarloPdfReport report) {
        return Stream.concat(report.context().stream(), report.sections().stream().flatMap(s -> s.paragraphs().stream()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @ParameterizedTest @MethodSource("configurations")
    void eightFamiliesKeepOriginalReportingAndPdfAfterLiveExpenseEdits(boolean couple, boolean longevity, boolean paired, boolean enabled) throws Exception {
        var snapshot = capture(couple, longevity, paired, enabled);
        var codec = new AnalysisSnapshotCodec();
        var saved = envelope(snapshot);
        byte[] original = codec.raw(saved);
        var decoded = (MonteCarloSnapshot) codec.decode(codec.encode(saved)).payload();
        var current = report(snapshot);
        var restored = report(decoded);
        assertEquals(current.context(), restored.context());
        assertEquals(current.chart(), restored.chart());
        assertEquals(current.sections(), restored.sections());
        assertTrue(current.context().contains("Household: " + (couple ? "Couple" : "Single person")));
        String details = text(restored);
        assertTrue(details.contains("Seed: 417"));
        assertTrue(details.contains("Simulations: 2"));
        assertTrue(details.contains("inflation"));
        if (enabled) {
            assertTrue(details.contains("expense 1 = 25%"));
            if (paired) assertTrue(details.contains("expense 1 = 17%"));
            assertTrue(details.contains("expected escalated amount"));
            assertTrue(details.contains("unbounded positive tail"));
            assertTrue(details.contains("annual expense shocks do not compound"));
            assertTrue(details.contains("One-time expenses"));
            assertTrue(details.contains("INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1"));
        } else {
            assertFalse(details.contains("INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1"));
            assertFalse(details.contains("unbounded positive tail"));
            assertEquals(1, snapshot.version());
        }
        // Editing a detached live plan, including definition and collection changes, must not alter the saved report.
        var live = MonteCarloPlanDocument.read(snapshot.request().planA());
        enable(live, ".99");
        for (var expense : List.copyOf(live.getHousehold().getExpenses())) live.getHousehold().removeExpense(expense);
        live.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense(
                "Changed live expense", new BigDecimal("123")));
        live.setBaseline(null);
        var historical = MonteCarloHistoricalPdf.from(report(decoded), decoded, MonteCarloHistoricalPdf.Currency.OUTDATED, "Saved", true);
        assertEquals(current.chart(), historical.chart());
        assertEquals(current.sections(), historical.sections());
        assertTrue(historical.context().containsAll(current.context()));
        assertArrayEquals(original, codec.raw(saved));
        assertEquals(saved.inputFingerprint(), new MonteCarloFingerprintService().storedFingerprint(decoded).fingerprint());
        var folder = Path.of("target/phase21b4/reports"); Files.createDirectories(folder);
        var path = folder.resolve(snapshot.kind() + "-" + enabled + ".pdf");
        new MonteCarloPdfExporter().export(historical, path);
        try (var pdf = Loader.loadPDF(path.toFile())) {
            String extracted = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertTrue(extracted.contains("Household: " + (couple ? "Couple" : "Single person")));
            assertEquals(enabled, extracted.contains("unbounded positive tail"));
            assertTrue(extracted.contains("Investable Assets"));
            assertTrue(pdf.getNumberOfPages() > 0);
        }
    }

    @ParameterizedTest @CsvSource({"true,false,false", "true,true,false", "false,false,false", "false,true,false",
            "true,false,true", "true,true,true", "false,false,true", "false,true,true"})
    void olderEnabledArtifactsGainExplanationWithoutChangingSnapshotOrFingerprint(boolean couple, boolean longevity, boolean paired) throws Exception {
        var captured = capture(couple, longevity, paired, true);
        var d = captured.display();
        String oldDetails = d.details().replace("\n" + ExpenseExecution.LEVEL_INTERPRETATION, "");
        assertFalse(oldDetails.contains("unbounded positive tail"));
        var oldDisplay = new MonteCarloSnapshot.Display(d.people(), d.primaryName(), d.spouseName(), d.years(), d.chart(),
                d.firstYear(), d.lastYear(), d.referenceIncomplete(), d.elapsedNanos(), oldDetails);
        var older = new MonteCarloSnapshot(captured.version(), captured.mode(), captured.hasSpouse(), captured.settings(), captured.request(),
                captured.provenance(), captured.fixed(), captured.mortality(), captured.paired(), oldDisplay,
                captured.startedAt(), captured.completedAt(), captured.expenseExecution(), captured.pairedExpenseExecution());
        var codec = new AnalysisSnapshotCodec(); var saved = envelope(older);
        byte[] bytes = codec.raw(saved);
        var decoded = (MonteCarloSnapshot) codec.decode(codec.encode(saved)).payload();
        assertEquals(oldDetails, decoded.display().details());
        String presented = text(report(decoded));
        assertEquals(1, presented.split(java.util.regex.Pattern.quote(ExpenseExecution.LEVEL_INTERPRETATION), -1).length - 1);
        assertArrayEquals(bytes, codec.raw(saved));
        assertEquals(saved.inputFingerprint(), new MonteCarloFingerprintService().storedFingerprint(decoded).fingerprint());
        assertEquals(captured.paired(), decoded.paired());
        assertEquals(captured.fixed(), decoded.fixed());
        assertEquals(captured.mortality(), decoded.mortality());
    }

    @Test void unsupportedPathFeedbackPreservesGuardAndDoesNotRecommendZeroingConfiguration() {
        var plan = plan(false); enable(plan, ".25");
        String before = MonteCarloPlanDocument.capture(plan);
        var failure = assertThrows(IllegalArgumentException.class, () -> ExpenseVolatilityAdmission.requireSupported(plan));
        assertTrue(failure.getMessage().contains("supported generated Monte Carlo execution"));
        assertFalse(failure.getMessage().contains("Set annual expense volatility to 0%"));
        assertEquals(before, MonteCarloPlanDocument.capture(plan));
    }
}
