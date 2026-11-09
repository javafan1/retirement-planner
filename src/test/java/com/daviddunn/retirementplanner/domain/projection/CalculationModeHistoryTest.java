package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.junit.jupiter.api.Assertions.*;

class CalculationModeHistoryTest {
    @TempDir Path directory;
    @Test void modeOnlyResultCannotMasqueradeAsAnUntaggedLegacyCalculation() {
        var plan=plan();plan.setConditionalBenefits(List.of());
        var annual=RetainedProjectionSnapshot.capture(new ProjectionEngine().project(plan));
        assertThrows(IllegalArgumentException.class,()->ConditionalBenefitExecutionSemantics.validate(
                CompleteProjectionSnapshot.freeze(plan),annual,PensionSelectionSemantics.PROVENANCE));
    }
    @Test void completedCommonComparisonRejectsChangedInputsEvenWithoutARevisionNotification() throws Exception {
        var c=controller();try {
            var plan=plan();c.newPlan(plan);c.getCurrentProjection();c.saveCurrentAsBaseline("Frozen Basic");
            plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);c.markModified();c.invalidateProjection();c.getCurrentProjection();
            var accepted=c.getAcceptedProjectionSnapshot();
            var completed=c.calculateCommonModeComparison().toCompletableFuture().get(20,TimeUnit.SECONDS);
            plan.setConditionalBenefits(List.of(com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.change(
                    plan.getConditionalBenefits().getFirst(),"grossAmount",new BigDecimal("1500"))));
            assertFalse(c.acceptCommonModeComparison(completed));assertEquals(accepted.id(),c.getAcceptedProjectionSnapshot().id());
            assertEquals("",c.commonModeComparisonDisclosure());
        }finally{NonqualifiedAnnuityHistoryTest.stop(c);}
    }
    ApplicationController controller() {
        return new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings-"+UUID.randomUUID())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analyses"))));
    }
    static RetirementPlan plan() {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.setRothConversionRequest(new RothConversionRequest(true,2030,new BigDecimal("294240"),RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ONE_TIME));
        return plan;
    }
    static RetirementPlan original(CompleteProjectionSnapshot result) {
        var roots=result.restoreInputs();var plan=new RetirementPlan(roots.getHousehold(),roots.getAccountPortfolio(),roots.getPlanningAssumptions(),
                roots.getRothConversionRequest(),roots.getNonInvestableAssets());plan.setConditionalBenefits(roots.getConditionalBenefits());
        if(roots.getFinancialCalculationMode()!=null)plan.setFinancialCalculationMode(roots.getFinancialCalculationMode());return plan;
    }
    @Test void bothOriginalModesSurviveSaveAsRestartSwapAndPdfWithoutHistoricalExecution() throws Exception {
        var c=controller(); ApplicationController restarted=null;
        try {
            var plan=plan();c.newPlan(plan);c.getCurrentProjection();var basic=c.getAcceptedProjectionSnapshot();
            assertTrue(basic.provenance().contains(ConditionalBenefitExecutionSemantics.BASIC_MODEL));
            assertEquals(0,new BigDecimal("49468").compareTo(basic.annual().restoreProjection().getYearAt(0).getFederalIncomeTax()));
            c.saveCurrentAsBaseline("Original Basic result");
            plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);c.markModified();c.invalidateProjection();c.getCurrentProjection();
            var advanced=c.getAcceptedProjectionSnapshot();assertTrue(advanced.provenance().contains(ConditionalBenefitExecutionSemantics.ADVANCED_MODEL));
            assertEquals(0,new BigDecimal("49686.88").compareTo(advanced.annual().restoreProjection().getYearAt(0).getFederalIncomeTax()));
            c.getBaselineProjection();assertThrows(UnsupportedOperationException.class,c::compareProjections);
            var common=c.calculateCommonModeComparison().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(c.acceptCommonModeComparison(common));var comparison=c.compareProjections();
            assertEquals(FinancialCalculationMode.ADVANCED,comparison.calculationMode());assertEquals(0,comparison.getTotalTaxesChange().signum());
            assertEquals(FinancialCalculationMode.BASIC,plan.getBaseline().getSnapshot().getFinancialCalculationMode());
            assertEquals(basic.id(),plan.getBaseline().getProjectionSnapshotId());
            var breakEven=c.requestBreakEvenAnalysis();assertNotNull(breakEven);
            assertEquals(FinancialCalculationMode.ADVANCED,breakEven.currentAssumptions().calculationMode());
            assertEquals(FinancialCalculationMode.ADVANCED,breakEven.baselineAssumptions().calculationMode());
            var reportDirectory=Path.of("target/phase23e/reporting");Files.createDirectories(reportDirectory);
            var breakEvenPath=reportDirectory.resolve("advanced-common-break-even.pdf");
            new com.daviddunn.retirementplanner.app.export.BreakEvenPdfExporter().export(
                    new com.daviddunn.retirementplanner.app.export.BreakEvenPdfReport(breakEven,
                            com.daviddunn.retirementplanner.domain.breakeven.BreakEvenContext.unavailable(),
                            new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService().prepare(breakEven)),breakEvenPath);
            try(var document=Loader.loadPDF(breakEvenPath.toFile())) {
                assertTrue(new PDFTextStripper().getText(document).contains("Financial Calculation Mode: Advanced"));
                javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0,110),"png",reportDirectory.resolve("advanced-common-break-even.png").toFile());
            }
            assertEquals(basic.annual(),plan.getProjectionComponents().get(basic.id()).annual());
            assertThrows(IllegalArgumentException.class,()->ConditionalBenefitExecutionSemantics.validate(
                    advanced.inputsDocument(),basic.annual(),advanced.provenance()));
            c.saveAs(directory.resolve("original.json"));c.saveAs(directory.resolve("copy.json"));
            restarted=controller();
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(restarted,"projectionEngine",new ProjectionEngine() {
                @Override public Projection project(RetirementPlan ignored) { throw new AssertionError("History must not project"); }
                @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan ignored) { throw new AssertionError("History must not execute"); }
            });
            restarted.open(directory.resolve("copy.json"));restarted.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertEquals(FinancialCalculationMode.ADVANCED,restarted.getCurrentPlan().getFinancialCalculationMode());
            assertEquals(advanced.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            var swap=restarted.prepareProjectionSwap().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap));assertEquals(FinancialCalculationMode.BASIC,restarted.getCurrentPlan().getFinancialCalculationMode());
            assertEquals(basic.annual(),restarted.getAcceptedProjectionSnapshot().annual());restarted.save();
            swap=restarted.prepareProjectionSwap().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap));assertEquals(advanced.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            var output=Path.of("target/phase23e/reporting");Files.createDirectories(output);
            for(var result:List.of(basic,advanced)) {
                var frozen=original(result);var projection=result.annual().restoreProjection();
                var name=frozen.getFinancialCalculationMode()==FinancialCalculationMode.BASIC?"basic-historical":"advanced-current";
                var path=output.resolve(name+".pdf");
                new ProjectionPdfExporter().export(frozen,projection,List.of(),ConditionalBenefitReportingTest.report(frozen,projection),path);
                try(var pdf=Loader.loadPDF(path.toFile())) {
                    var text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("FINANCIAL CALCULATION MODE"));
                    assertTrue(text.contains(frozen.getFinancialCalculationMode().label()));
                    if(frozen.getFinancialCalculationMode()==FinancialCalculationMode.BASIC) assertTrue(text.contains("NIIT Not Modeled"));
                    else assertTrue(text.contains("218.88"));
                    var renderer=new PDFRenderer(pdf);javax.imageio.ImageIO.write(renderer.renderImageWithDPI(pdf.getNumberOfPages()-1,110),"png",output.resolve(name+"-last.png").toFile());
                }
            }
        } finally {if(restarted!=null)NonqualifiedAnnuityHistoryTest.stop(restarted);NonqualifiedAnnuityHistoryTest.stop(c);}
    }
}
