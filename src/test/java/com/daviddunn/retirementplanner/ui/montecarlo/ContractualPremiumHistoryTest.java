package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;
import static com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium;
import static org.junit.jupiter.api.Assertions.*;

class ContractualPremiumHistoryTest {
    @TempDir Path directory;
    @BeforeAll static void start() throws Exception { startFx(); }
    @ParameterizedTest @EnumSource(value=AnalysisKind.class,names={"COUPLE_MONTE_CARLO_FIXED","COUPLE_MONTE_CARLO_LONGEVITY",
            "COUPLE_MONTE_CARLO_PAIRED_FIXED","COUPLE_MONTE_CARLO_PAIRED_LONGEVITY"})
    void originalPremiumsSurviveLiveEditsSaveAsRestartAndHistoricalPdfWithoutRecalculation(AnalysisKind kind) throws Exception {
        var fixture=new MonteCarloParameterPersistenceTest(); fixture.dir=directory;
        var source=fixture.source(kind,"premium-original",new JsonRetirementPlanRepository()); AnalysisSnapshot original;
        var view=fx(()-> {
            var p=source.getCurrentPlan();
            p.getHousehold().addExpense(premium("2000",ContractualPremiumPolicy.Insured.PRIMARY,p.getPlanningAssumptions().getProjectionStartDate(),null));
            source.markModified(); source.invalidateProjection(); source.getCurrentProjection(); source.save();
            return new View(source,kind,false);
        });
        try {
            fx(()->{ view.configure(kind); view.button("run").fire(); return null; }); view.execute(); await(view.label("parameters-status"),"SAVED");
            var completion=source.getAnalysisResults().inMemory(new AnalysisResultStore.Slot(source.getAnalysisSessionId(),kind)).orElseThrow();
            original=completion.presentationSnapshot(new AnalysisSnapshotCodec()); assertEquals(3,((MonteCarloSnapshot)original.payload()).benefitExecution().version());
            pdf((MonteCarloSnapshot)original.payload(),false);
            fx(()->{
                var p=source.getCurrentPlan(); var old=p.getHousehold().getExpenses().stream().filter(Expense::isContractualInsurancePremium).findFirst().orElseThrow();
                p.getHousehold().replaceExpense(old,premium("3000",ContractualPremiumPolicy.Insured.PRIMARY,old.getStartDate(),old.getEndDate()));
                source.markModified(); source.invalidateProjection(); source.saveAs(directory.resolve("premium-copy.json")); return null;
            });
        } finally { fx(()->{view.close(); return null;}); stop(source); }
        var restored=new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("restart-settings")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        try {
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(restored,"projectionEngine",new ProjectionEngine() {
                @Override public Projection project(RetirementPlan p) { throw new AssertionError("History must not project"); }
                @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw new AssertionError("History must not execute"); }
            });
            fx(()->{restored.open(directory.resolve("premium-copy.json")); return null;});
            restored.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
            var association=restored.getAnalyzerAssociations(restored.getAcceptedProjectionSnapshot().id()).getFirst();
            var history=restored.loadAssociatedAnalyzer(association).toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertEquals(original.payload(),history.payload()); assertEquals(original.inputFingerprint(),history.inputFingerprint()); assertEquals(original.runId(),history.runId());
            assertFalse(restored.currentProjectionDeterministicallyApplicable());
            var originalPlan=MonteCarloPlanDocument.read(((MonteCarloSnapshot)history.payload()).request().planA());
            assertEquals(new BigDecimal("2000"),originalPlan.getHousehold().getExpenses().stream().filter(Expense::isContractualInsurancePremium).findFirst().orElseThrow().getAnnualAmount());
            pdf((MonteCarloSnapshot)history.payload(),true);
        } finally { stop(restored); }
    }

    static void pdf(MonteCarloSnapshot payload,boolean historical) throws Exception {
        var original=fx(()->payload.paired()==null?MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreRun(payload))
                :MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreComparison(payload)));
        var report=historical?MonteCarloHistoricalPdf.from(original,payload,MonteCarloHistoricalPdf.Currency.OUTDATED,"Saved",true):original;
        var folder=Path.of("target/phase23c/reporting"); Files.createDirectories(folder);
        String name=payload.kind().name()+(historical?"-history":"-current"); var path=folder.resolve(name+".pdf");
        new com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter().export(report,path);
        try(var document=org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+"," ");
            assertTrue(text.contains("Contractual insurance premium")); assertTrue(text.contains("annual amount 2000")); assertFalse(text.contains("annual amount 3000"));
            assertTrue(text.contains("not reduced by household survivor-spending assumptions"));
            if(historical) assertTrue(text.contains("Historical Analysis"));
            if(payload.paired()!=null) { assertTrue(text.contains("Current")); assertTrue(text.contains("Baseline")); }
            for(int page=0;page<document.getNumberOfPages();page++) {
                var stripper=new org.apache.pdfbox.text.PDFTextStripper(); stripper.setStartPage(page+1); stripper.setEndPage(page+1);
                if(page==0 || stripper.getText(document).contains("Contractual insurance premium"))
                    javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(page,84),"png",folder.resolve(name+"-page-"+page+".png").toFile());
            }
        }
    }
}
