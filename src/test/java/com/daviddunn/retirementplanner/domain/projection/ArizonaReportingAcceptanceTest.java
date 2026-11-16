package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.ArizonaProductionTaxCalculator;
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

class ArizonaReportingAcceptanceTest {

    @Test
    void annualCsvReportsArizonaTaxWithoutMichiganDetails(@TempDir Path directory) throws Exception {
        var projection = new ProjectionEngine().project(ArizonaProjectionAcceptanceTest.plan(false));
        var file = directory.resolve("arizona.csv");
        new ProjectionCsvExporter().export(projection, List.of(), file);
        var lines = Files.readAllLines(file);
        var header = Arrays.asList(lines.getFirst().split(","));
        assertEquals(projection.getYears().size() + 1, lines.size());
        for (int index = 0; index < projection.getYears().size(); index++) {
            var row = lines.get(index + 1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            assertEquals(header.size(), row.length);
            assertEquals("AZ", row[header.indexOf("State of Residence")]);
            assertEquals("SIMPLIFIED_STATE_ESTIMATE", row[header.indexOf("State Tax Calculation")]);
            assertEquals(0, new BigDecimal(row[header.indexOf("State Income Tax")])
                    .compareTo(projection.getYearAt(index).getStateIncomeTax()));
            assertEquals("", row[header.indexOf("Michigan Retirement Deduction")]);
            var disclosure = row[header.indexOf("Arizona Planning Limitations")];
            for (var note : ArizonaProductionTaxCalculator.planningLimitations(2027, 2029)) {
                assertTrue(disclosure.contains(note.replace("\"", "\"\"")));
            }
        }
    }

    @Test
    void projectionPdfDisclosesArizonaAndFrozen2026Parameters(@TempDir Path directory) throws Exception {
        var plan = ArizonaProjectionAcceptanceTest.plan(false);
        var projection = new ProjectionEngine().project(plan);
        var charts = com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.from(
                projection.getYears(), List.of(),
                com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold()),
                plan.getAccountPortfolio().getAccounts());
        var report = new ProjectionPdfReport("Arizona acceptance", charts, BigDecimal.ZERO,
                projection.getYears().getLast().getAfterTaxEstateValue(), null);
        var file = directory.resolve("arizona.pdf");
        new ProjectionPdfExporter().export(plan, projection, List.of(), report, file);
        try (var document = Loader.loadPDF(file.toFile())) {
            var text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Arizona"));
            assertTrue(text.contains("2026"));
            assertTrue(text.contains("2028"));
            assertTrue(text.contains("2029"));
            assertTrue(text.contains("Simplified estimate"));
            assertTrue(text.contains("Arizona Planning Limitations"));
            assertTrue(text.contains("AZ-2026-1"));
            assertTrue(text.contains("Enhanced senior subtraction expires after 2028"));
            assertTrue(text.contains("brokerage withdrawal proceeds are not automatically taxable income"));
            assertTrue(text.contains("Unmodeled realized gains are not included in the estimated tax base"));
            assertTrue(text.contains("may cause actual taxes to be higher"));
            assertTrue(text.contains("potentially overestimating tax when applicable"));
            assertFalse(text.contains("Michigan Income Tax"));
        }
    }

    @Test
    void completedAnalysisAndPdfKeepArizonaDisclosureAfterPlanChangesState(@TempDir Path directory)
            throws Exception {
        var plan = ArizonaProjectionAcceptanceTest.plan(true);
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO);
        var run = new MonteCarloRunService().run(plan, settings, null,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var frozenContext = MonteCarloPdfReportAdapter.from(run).context().toString();
        assertTrue(frozenContext.contains("State of residence: Arizona"));
        assertTrue(frozenContext.contains("2026"));
        assertTrue(frozenContext.contains("2028"));
        for (var note : ArizonaProductionTaxCalculator.planningLimitations(2027, 2029)) {
            assertTrue(frozenContext.contains(note));
        }
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var report = MonteCarloPdfReportAdapter.from(run);
        assertEquals(frozenContext, report.context().toString());
        assertFalse(report.context().toString().contains("State of residence: Florida"));
        var file = directory.resolve("frozen-arizona-analysis.pdf");
        new MonteCarloPdfExporter().export(report, file);
        try (var document = Loader.loadPDF(file.toFile())) {
            var text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("State of residence: Arizona"));
            assertTrue(text.contains("2026"));
            assertTrue(text.contains("2028"));
            assertTrue(text.contains("AZ-2026-1"));
            assertTrue(text.contains("Enhanced senior subtraction expires after 2028"));
            assertFalse(text.contains("State of residence: Florida"));
        }
    }

    @Test
    void retainedHistoricalDisclosuresAreNotReplacedByCurrentBrokeragePolicy(@TempDir Path directory) throws Exception {
        var original = new ProjectionEngine().project(ArizonaProjectionAcceptanceTest.plan(false));
        var snapshot = com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot.capture(original);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var tree = mapper.valueToTree(snapshot);
        String legacy = "Prior saved Arizona estimate: original result-time limitations.";
        for (var year : tree.path("years")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) year.path("state"))
                    .putArray("limitations").add(legacy);
        }
        var restored = mapper.treeToValue(tree,
                com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot.class).restoreProjection();
        assertEquals(original.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList(),
                restored.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList());
        restored.getYears().forEach(year -> assertEquals(List.of(legacy), year.stateTaxCalculation().orElseThrow().limitations()));
        var csv = directory.resolve("legacy-arizona.csv");
        new ProjectionCsvExporter().export(restored, List.of(), csv);
        var text = Files.readString(csv);
        assertTrue(text.contains(legacy));
        assertFalse(text.contains("Unmodeled realized gains"));
        assertTrue(String.join(" ", ArizonaProductionTaxCalculator.planningLimitations(2027, 2029))
                .contains("Unmodeled realized gains"));
    }
}
