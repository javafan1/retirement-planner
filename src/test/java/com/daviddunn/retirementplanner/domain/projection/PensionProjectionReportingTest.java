package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;
import com.daviddunn.retirementplanner.ui.charts.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PensionProjectionReportingTest {
    @Test void frozenAnnualValuesAndChartsExportIdenticallyAfterLivePensionEditsWithoutRecalculation() throws Exception {
        var plan = PensionCashTaxConsistencyTest.survivorOnlyPlan(DeathScenario.PRIMARY_DIES);
        var projection = new ProjectionEngine().project(plan);
        var frozen = new RetirementPlanScenarioCopyService().copy(plan);
        var retained = RetainedProjectionSnapshot.capture(projection);
        var non = new NonInvestableAssetProjectionService().project(frozen.getNonInvestableAssets(), 2030, 2034);
        var chart = ProjectionChartModel.from(projection.getYears(), non, BreakEvenPlanSummary.from(frozen),
                frozen.getAccountPortfolio().getAccounts());
        for (int index = 0; index < projection.size(); index++) {
            assertEquals(projection.getYearAt(index).getTotalIncomeTax(), chart.years().get(index).values().get(ProjectionChartMetric.TOTAL_TAXES));
            PensionCashTaxConsistencyTest.money(index == 0 ? BigDecimal.ZERO : new BigDecimal("30000"),
                    chart.years().get(index).values().get(ProjectionChartMetric.TOTAL_INCOME));
        }
        var average = projection.getYears().stream().map(ProjectionYear::getCombinedEffectiveTaxRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(projection.size()), 6, RoundingMode.HALF_UP);
        var report = new ProjectionPdfReport("Synthetic B0 pension acceptance", chart, average,
                projection.getYearAt(projection.size() - 1).getAfterTaxEstateValue(), null);
        Path folder = Path.of("target", "phase22b0", "reporting");
        Files.createDirectories(folder);
        Path current = folder.resolve("corrected-pension.pdf"), historical = folder.resolve("frozen-pension.pdf");
        new ProjectionPdfExporter().export(frozen, projection, non, report, current);
        // Reporting is supplied only completed frozen inputs/results. Live plan changes are irrelevant.
        PensionCashTaxConsistencyTest.removePensions(plan);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Later edit", AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("8000"), BigDecimal.ZERO, new BigDecimal("9000")));
        new ProjectionPdfExporter().export(frozen, retained.restoreProjection(), non, report, historical);
        try (var first = Loader.loadPDF(current.toFile()); var second = Loader.loadPDF(historical.toFile())) {
            var text = new PDFTextStripper().getText(first);
            assertEquals(text, new PDFTextStripper().getText(second));
            assertEquals(first.getNumberOfPages(), second.getNumberOfPages());
            assertTrue(text.contains("30,000"));
            assertFalse(text.contains("Later edit"));
            var renderer = new PDFRenderer(first);
            int table = first.getNumberOfPages() - 2;
            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(table, 96), "png", folder.resolve("annual-table.png").toFile());
            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(first.getNumberOfPages() - 1, 96), "png", folder.resolve("assumptions.png").toFile());
        }
        assertEquals(retained, RetainedProjectionSnapshot.capture(retained.restoreProjection()));
    }
}
