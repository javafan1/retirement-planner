package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LifeInsuranceReportingHistoryTest {
    static final Path OUTPUT=Path.of("target/phase23b/reporting");
    @TempDir Path directory;
    ApplicationController controller() {
        return new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings-"+UUID.randomUUID())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analyses"))));
    }
    static void stop(ApplicationController c) throws Exception {
        c.getProjectionRestoration().toCompletableFuture().get(15,TimeUnit.SECONDS);
        c.getSnapshotCopyCompletion().toCompletableFuture().get(15,TimeUnit.SECONDS);
        assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete()); c.getAnalysisParameters().shutdown();
        assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit()); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
    }
    @Test void oldTaxableAndNewExcludedResultsStayFrozenThroughSaveAsRestartSwapsAndPdf() throws Exception {
        Files.createDirectories(OUTPUT);
        var plan=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
        var node=LifeInsurancePrincipalTest.document(); node.remove("lifeInsuranceQualification"); node.put("taxCharacter","FULLY_TAXABLE_ORDINARY_NON_PENSION");
        var taxable=BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class); plan.setConditionalBenefits(List.of(taxable));
        var frozenTaxable=new RetirementPlanScenarioCopyService().copy(plan);
        var c=controller(); ApplicationController restarted=null;
        try {
            c.newPlan(plan); c.getCurrentProjection(); var old=c.getAcceptedProjectionSnapshot();
            assertTrue(old.provenance().contains("conditional-benefits=deterministic-annual-v1;"));
            c.saveCurrentAsBaseline("Original fully taxable death benefit");
            plan.setConditionalBenefits(List.of(LifeInsurancePrincipalTest.principal())); c.markModified(); c.invalidateProjection();
            c.getCurrentProjection(); var current=c.getAcceptedProjectionSnapshot();
            assertTrue(current.provenance().contains("conditional-benefits=deterministic-life-principal-v2;"));
            var frozenExcluded=new RetirementPlanScenarioCopyService().copy(plan);
            assertTrue(current.calculationSemanticsCurrent()); assertTrue(old.calculationSemanticsCurrent());
            assertNotEquals(old.annual().years().get(1).agi(),current.annual().years().get(1).agi());
            // An old tag cannot authenticate new excluded inputs/evidence, even if other metadata is retained.
            assertThrows(IllegalArgumentException.class,()->com.daviddunn.retirementplanner.app.projection.ConditionalBenefitExecutionSemantics.validate(
                    current.inputsDocument(),current.annual(),old.provenance()));
            c.saveAs(directory.resolve("original.json")); c.saveAs(directory.resolve("copy.json"));
            restarted=controller();
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(restarted,"projectionEngine",new ProjectionEngine() {
                @Override public Projection project(RetirementPlan ignored) { throw new AssertionError("History must not project"); }
                @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan ignored) { throw new AssertionError("History must not execute"); }
            });
            restarted.open(directory.resolve("copy.json")); restarted.getProjectionRestoration().toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertEquals(current.id(),restarted.getAcceptedProjectionSnapshot().id());
            var swap=restarted.prepareProjectionSwap().toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap)); assertEquals(old.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            assertFalse(restarted.getCurrentPlan().getConditionalBenefits().getFirst().excludesLifePrincipal());
            restarted.save();
            swap=restarted.prepareProjectionSwap().toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap)); assertEquals(current.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            assertTrue(restarted.getCurrentPlan().getConditionalBenefits().getFirst().excludesLifePrincipal());
            for(var label:List.of("taxable-original","excluded-original","excluded-history")) {
                boolean excluded=!label.startsWith("taxable"); var frozen=excluded?frozenExcluded:frozenTaxable;
                var projection=(excluded?current:old).annual().restoreProjection();
                new ProjectionPdfExporter().export(frozen,projection,List.of(),ConditionalBenefitReportingTest.report(frozen,projection),OUTPUT.resolve(label+".pdf"));
            }
            try(var currentPdf=Loader.loadPDF(OUTPUT.resolve("excluded-original.pdf").toFile());
                var historicPdf=Loader.loadPDF(OUTPUT.resolve("excluded-history.pdf").toFile());
                var taxablePdf=Loader.loadPDF(OUTPUT.resolve("taxable-original.pdf").toFile())) {
                var text=new PDFTextStripper().getText(currentPdf);
                assertEquals(text,new PDFTextStripper().getText(historicPdf));
                assertTrue(text.contains("excluded principal")); assertTrue(text.contains("400,000"));
                assertTrue(text.replaceAll("\\s+"," ").contains("Future investment earnings may be taxable"));
                assertFalse(new PDFTextStripper().getText(taxablePdf).contains("Original qualification assertion"));
                for(int page=0;page<currentPdf.getNumberOfPages();page++)
                    javax.imageio.ImageIO.write(new PDFRenderer(currentPdf).renderImageWithDPI(page,96),"png",OUTPUT.resolve("excluded-page-"+page+".png").toFile());
            }
            var repo=new JsonRetirementPlanRepository();
            var acceptance=Path.of("target/phase23b/qualified-personal-life.json");
            var contrast=Path.of("target/phase23b/fully-taxable-control.json");
            if(!Files.exists(acceptance)) repo.save(frozenExcluded,acceptance);
            if(!Files.exists(contrast)) repo.save(frozenTaxable,contrast);
            assertEquals(frozenExcluded.getConditionalBenefits(),repo.load(acceptance).getConditionalBenefits());
            assertFalse(repo.load(contrast).getConditionalBenefits().getFirst().excludesLifePrincipal());
        } finally { if(restarted!=null) stop(restarted); stop(c); }
    }
}
