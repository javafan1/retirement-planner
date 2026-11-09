package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.projection.ConditionalBenefitExecutionSemantics;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import static org.junit.jupiter.api.Assertions.*;

class NonqualifiedAnnuityHistoryTest {
    @TempDir Path directory;
    ApplicationController controller() {
        return new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings-"+UUID.randomUUID())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analyses"))));
    }
    static void stop(ApplicationController c) throws Exception {
        c.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
        c.getSnapshotCopyCompletion().toCompletableFuture().get(20,TimeUnit.SECONDS);
        assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());c.getAnalysisParameters().shutdown();
        assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit());c.getAnalysisResults().shutdownAfterSuccessfulDrain();
    }
    @Test void frozenBasisNiitAndLegacyResultsSurviveSaveAsRestartSwapAndPdfWithoutExecution() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true,2030,new java.math.BigDecimal("294240"),
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ONE_TIME));
        var definitions=plan.getConditionalBenefits();plan.setConditionalBenefits(List.of());
        var c=controller(); ApplicationController restarted=null;
        var output=Path.of("target/phase23d/reporting");Files.createDirectories(output);
        try {
            c.newPlan(plan);c.getCurrentProjection();var old=c.getAcceptedProjectionSnapshot();
            c.saveCurrentAsBaseline("Original no-annuity result");
            plan.setConditionalBenefits(definitions);c.markModified();c.invalidateProjection();c.getCurrentProjection();
            var current=c.getAcceptedProjectionSnapshot();
            assertTrue(current.provenance().contains(ConditionalBenefitExecutionSemantics.ANNUITY_MODEL));
            assertTrue(current.calculationSemanticsCurrent());assertTrue(old.calculationSemanticsCurrent());
            var frozen=new RetirementPlanScenarioCopyService().copy(plan);
            assertThrows(IllegalArgumentException.class,()->ConditionalBenefitExecutionSemantics.validate(current.inputsDocument(),current.annual(),old.provenance()));
            c.saveAs(directory.resolve("original.json"));c.saveAs(directory.resolve("copy.json"));
            restarted=controller();
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(restarted,"projectionEngine",new ProjectionEngine(){
                @Override public Projection project(com.daviddunn.retirementplanner.domain.model.RetirementPlan ignored){throw new AssertionError("History must not project");}
                @Override public ProjectionExecutionResult projectWithOutcome(com.daviddunn.retirementplanner.domain.model.RetirementPlan ignored){throw new AssertionError("History must not execute");}
            });
            restarted.open(directory.resolve("copy.json"));restarted.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertEquals(current.id(),restarted.getAcceptedProjectionSnapshot().id());
            var swap=restarted.prepareProjectionSwap().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap));assertEquals(old.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            restarted.save();
            swap=restarted.prepareProjectionSwap().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap));assertEquals(current.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            var projection=current.annual().restoreProjection();
            new ProjectionPdfExporter().export(frozen,projection,List.of(),ConditionalBenefitReportingTest.report(frozen,projection),output.resolve("annuity-current.pdf"));
            new ProjectionPdfExporter().export(frozen,current.annual().restoreProjection(),List.of(),ConditionalBenefitReportingTest.report(frozen,projection),output.resolve("annuity-historical.pdf"));
            try(var pdf=Loader.loadPDF(output.resolve("annuity-current.pdf").toFile());var historical=Loader.loadPDF(output.resolve("annuity-historical.pdf").toFile())) {
                var text=new PDFTextStripper().getText(pdf);assertEquals(text,new PDFTextStripper().getText(historical));
                assertTrue(text.contains("closing recoverable investment"));assertTrue(text.contains("Federal NIIT"));assertTrue(text.contains("8,640.00"));assertTrue(text.contains("218.88"));
                var renderer=new PDFRenderer(pdf);
                for(int page=0;page<pdf.getNumberOfPages();page++) javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page,96),"png",output.resolve("annuity-page-"+page+".png").toFile());
            }
        } finally {if(restarted!=null)stop(restarted);stop(c);}
    }
}
