package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.OklahomaProductionTaxCalculator;
import com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloPdfReportAdapter;
import com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRunService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OklahomaReportingAcceptanceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void annualCsvReportsOklahomaTaxWithoutMichiganDetails(boolean couple, @TempDir Path directory) throws Exception {
        var projection = new ProjectionEngine().project(OklahomaProjectionAcceptanceTest.plan(couple));
        var file = directory.resolve("oklahoma.csv");
        var nonInvestable = projection.getYears().stream().map(year ->
                new com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection(
                        year.getCalendarYear(), List.of(new com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetValue(
                                "Home", new BigDecimal("300000"))), new BigDecimal("300000"))).toList();
        new ProjectionCsvExporter().export(projection, nonInvestable, file);
        var lines = Files.readAllLines(file);
        var header = Arrays.asList(lines.getFirst().split(","));
        assertEquals(projection.getYears().size() + 1, lines.size());
        for (int index = 0; index < projection.getYears().size(); index++) {
            var row = lines.get(index + 1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            assertEquals(header.size(), row.length);
            assertEquals("OK", row[header.indexOf("State of Residence")]);
            assertEquals("SIMPLIFIED_STATE_ESTIMATE", row[header.indexOf("State Tax Calculation")]);
            assertEquals(0, new BigDecimal(row[header.indexOf("State Income Tax")])
                    .compareTo(projection.getYearAt(index).getStateIncomeTax()));
            assertEquals("", row[header.indexOf("Michigan Retirement Deduction")]);
            var disclosure = row[header.indexOf("Oklahoma Planning Limitations")];
            for (var note : OklahomaProductionTaxCalculator.planningLimitations(2027, 2029)) {
                assertTrue(disclosure.contains(note.replace("\"", "\"\"")));
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void projectionPdfDisclosesOklahomaAndFrozen2026Parameters(boolean couple, @TempDir Path directory) throws Exception {
        var plan = OklahomaProjectionAcceptanceTest.plan(couple);
        var projection = new ProjectionEngine().project(plan);
        var charts = com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.from(
                projection.getYears(), List.of(),
                com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold()),
                plan.getAccountPortfolio().getAccounts());
        var report = new ProjectionPdfReport("Oklahoma acceptance", charts, BigDecimal.ZERO,
                projection.getYears().getLast().getAfterTaxEstateValue(), null);
        var file = directory.resolve("oklahoma.pdf");
        new ProjectionPdfExporter().export(plan, projection, List.of(), report, file);
        try (var document = Loader.loadPDF(file.toFile())) {
            var text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Oklahoma"));
            assertTrue(text.contains("2026"));
            assertTrue(text.contains("2027"));
            assertTrue(text.contains("2029"));
            assertTrue(text.contains("Simplified estimate"));
            assertTrue(text.contains("Oklahoma Planning Limitations"));
            assertTrue(text.contains("OK-2026-1"));
            assertTrue(text.contains("government/CSRS"));
            assertTrue(text.contains("not automatically taxable income"));
            assertTrue(text.contains("Unmodeled realized gains may cause actual tax to be higher"));
            assertTrue(text.contains("may cause actual tax to be higher"));
            assertTrue(text.contains("Standard-deduction-only model"));
            assertFalse(text.contains("Michigan Income Tax"));
        }
    }

    @Test
    void completedAnalysisAndPdfKeepOklahomaDisclosureAfterPlanChangesState(@TempDir Path directory)
            throws Exception {
        var plan = OklahomaProjectionAcceptanceTest.plan(true);
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO);
        var run = new MonteCarloRunService().run(plan, settings, null,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var payload = com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloSnapshotPresentationMapper.capture(
                run, plan, java.time.Instant.now(), java.time.Instant.now());
        var completion = com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloResultLifecycle.completion(
                new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloResultLifecycle.Binding(
                        java.util.UUID.randomUUID(), null, payload.kind()), payload);
        var snapshot = completion.presentationSnapshot(new com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec());
        assertEquals("OK-2026-1", snapshot.references().get(
                com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaTaxRules.REFERENCE_KEY));
        var codec = new com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec();
        var decoded = codec.decode(codec.encode(snapshot));
        assertEquals(snapshot.references(), decoded.references());
        var inputs = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService.Inputs(
                payload.kind(), payload.settings(), payload.request());
        assertEquals(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisCompatibilityPolicy.State.CURRENT,
                new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService().compatibility(decoded, inputs).state());
        var frozenContext = MonteCarloPdfReportAdapter.from(run).context().toString();
        assertTrue(frozenContext.contains("State of residence: Oklahoma"));
        assertTrue(frozenContext.contains("2026"));
        assertTrue(frozenContext.contains("2027"));
        for (var note : OklahomaProductionTaxCalculator.planningLimitations(2027, 2029)) {
            assertTrue(frozenContext.contains(note));
        }
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var report = MonteCarloPdfReportAdapter.from(run);
        assertEquals(frozenContext, report.context().toString());
        assertFalse(report.context().toString().contains("State of residence: Florida"));
        var file = directory.resolve("frozen-oklahoma-analysis.pdf");
        new MonteCarloPdfExporter().export(report, file);
        try (var document = Loader.loadPDF(file.toFile())) {
            var text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("State of residence: Oklahoma"));
            assertTrue(text.contains("2026"));
            assertTrue(text.contains("2027"));
            assertTrue(text.contains("OK-2026-1"));
            assertTrue(text.contains("government/CSRS"));
            assertFalse(text.contains("State of residence: Florida"));
        }
    }

    @Test
    void retainedHistoricalDisclosuresAreNotReplacedByCurrentBrokeragePolicy(@TempDir Path directory) throws Exception {
        var original = new ProjectionEngine().project(OklahomaProjectionAcceptanceTest.plan(false));
        var snapshot = com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot.capture(original);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
        var tree = mapper.valueToTree(snapshot);
        String legacy = "Prior saved Oklahoma estimate: original result-time limitations.";
        for (var year : tree.path("years")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) year.path("state"))
                    .putArray("limitations").add(legacy);
        }
        var restored = mapper.treeToValue(tree,
                com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot.class).restoreProjection();
        assertEquals(original.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList(),
                restored.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList());
        restored.getYears().forEach(year -> assertEquals(List.of(legacy), year.stateTaxCalculation().orElseThrow().limitations()));
        var csv = directory.resolve("legacy-oklahoma.csv");
        new ProjectionCsvExporter().export(restored, List.of(), csv);
        var text = Files.readString(csv);
        assertTrue(text.contains(legacy));
        assertFalse(text.contains("Unmodeled realized gains"));
        assertTrue(String.join(" ", OklahomaProductionTaxCalculator.planningLimitations(2027, 2029))
                .contains("Unmodeled realized gains"));
        var plan = OklahomaProjectionAcceptanceTest.plan(false);
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var charts = com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.from(
                restored.getYears(), List.of(),
                com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold()),
                plan.getAccountPortfolio().getAccounts());
        var pdf = directory.resolve("historical-ky.pdf");
        new ProjectionPdfExporter().export(plan, restored, List.of(),
                new ProjectionPdfReport("Frozen KY", charts, BigDecimal.ZERO,
                        restored.getYears().getLast().getAfterTaxEstateValue(), null), pdf);
        try (var document = Loader.loadPDF(pdf.toFile())) {
            var pdfText = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(pdfText.contains(legacy));
            assertTrue(pdfText.contains("Oklahoma"));
            assertFalse(pdfText.contains("Florida"));
            assertFalse(pdfText.contains("Unmodeled realized gains"));
        }
    }
}
