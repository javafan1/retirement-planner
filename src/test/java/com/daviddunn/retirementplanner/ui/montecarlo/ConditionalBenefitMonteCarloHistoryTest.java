package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitMonteCarloHistoryTest {
    static final Path REPORTS = Path.of("target/phase22b3/reporting");
    @TempDir Path directory;
    @BeforeAll static void init() throws Exception { startFx(); }

    @ParameterizedTest @EnumSource(value=AnalysisKind.class,names=".*MONTE_CARLO.*",mode=EnumSource.Mode.MATCH_ALL)
    void actualEightUiCompletionPathsKeepCapabilityThroughSaveAsRestartAndHistoricalLoad(AnalysisKind kind) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var source = fixture.source(kind, "original", new JsonRetirementPlanRepository());
        fx(() -> {
            var plan = source.getCurrentPlan();
            var benefit = BenefitFixtures.change(BenefitFixtures.monthly(), "trigger",
                    new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.Trigger(
                            com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.TriggerType.FIXED_DATE,
                            plan.getPlanningAssumptions().getProjectionStartDate(), null, null));
            plan.setConditionalBenefits(List.of(benefit));
            plan.getHousehold().addExpense(new Expense("Independent stochastic spending", new BigDecimal("500"),
                    GrowthCategory.GENERAL, null, null, ExpenseType.RECURRING, new BigDecimal(".17")));
            source.markModified(); source.invalidateProjection(); source.getCurrentProjection(); source.save(); return null;
        });
        var projection = source.getAcceptedProjectionSnapshot();
        var view = fx(() -> new View(source, kind, false));
        AnalysisSnapshot original;
        try {
            fx(() -> { view.configure(kind); view.button("run").fire(); return null; }); view.execute();
            await(view.label("parameters-status"), "SAVED");
            var slot = new AnalysisResultStore.Slot(source.getAnalysisSessionId(), kind);
            var completion = source.getAnalysisResults().inMemory(slot).orElseThrow();
            original = completion.presentationSnapshot(new AnalysisSnapshotCodec());
            pdf(original,kind.name()+"-current");
            assertEquals(kind.name().contains("PAIRED") ? 6 : 5, original.fingerprintVersion());
            var refs = source.getAnalyzerAssociations(projection.id());
            assertEquals(1, refs.size(), source.getAnalyzerAssociationNotice(kind));
            assertEquals(original.payload(), source.loadAssociatedAnalyzer(refs.getFirst()).toCompletableFuture().get(20, TimeUnit.SECONDS).payload());
            // A replacement failure and cancellation preserve the last successful object and artifact.
            var previous = fx(view::result); view.fail.set(true);
            fx(() -> { view.text("seed").setText("999"); view.button("run").fire(); return null; }); view.execute(); await(view.label("status"), "failed");
            assertSame(previous, fx(view::result)); view.fail.set(false);
            fx(() -> { view.button("run").fire(); view.button("cancel").fire(); return null; }); view.execute(); await(view.label("status"), "cancelled");
            assertSame(previous, fx(view::result));
            fx(() -> { source.save(); source.saveAs(directory.resolve("copy.json")); return null; });
        } finally { fx(() -> { view.close(); return null; }); stop(source); }
        var restored = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(directory.resolve("restart.settings")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        fx(() -> { restored.open(directory.resolve("copy.json")); return null; });
        restored.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
        var reopened = fx(() -> new View(restored, kind, true));
        try {
            fx(() -> { reopened.mode(kind.name().endsWith("LONGEVITY")); return null; });
            await(reopened.label("saved-status"), "Current Results");
            assertEquals(0, reopened.calls.get()); assertTrue(reopened.tasks.isEmpty());
            var association = restored.getAnalyzerAssociations(restored.getAcceptedProjectionSnapshot().id()).getFirst();
            var saved = restored.loadAssociatedAnalyzer(association).toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(original.payload(), saved.payload()); assertEquals(original.inputFingerprint(), saved.inputFingerprint());
            assertEquals(original.runId(), saved.runId());
            fx(() -> {
                var p = restored.getCurrentPlan(); var b = p.getConditionalBenefits().getFirst();
                p.setConditionalBenefits(List.of(BenefitFixtures.change(b, "grossAmount", new BigDecimal("99999"))));
                restored.markModified(); restored.invalidateProjection(); return null;
            });
            assertEquals(original.payload(), restored.loadAssociatedAnalyzer(association).toCompletableFuture().get(20, TimeUnit.SECONDS).payload());
            pdf(saved,kind.name()+"-history");
            assertEquals(0, reopened.calls.get());
        } finally { fx(() -> { reopened.close(); return null; }); stop(restored); }
    }

    static void pdf(AnalysisSnapshot saved,String name) throws Exception {
        var payload = (com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot)saved.payload();
        var report = fx(() -> payload.paired()==null
                ? MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreRun(payload))
                : MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreComparison(payload)));
        var disclosure = payload.benefitExecution().disclosure();
        String originalText = String.join("\n",report.context()) + "\n" + report.sections();
        for(String line:disclosure) assertTrue(originalText.contains(line));
        Files.createDirectories(REPORTS); var file = REPORTS.resolve(name+".pdf");
        new com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter().export(report,file);
        try(var document = org.apache.pdfbox.Loader.loadPDF(file.toFile())) {
            var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.replaceAll("\\s+"," ").contains("fully taxable ordinary non-pension"));
            Files.writeString(REPORTS.resolve(name+".txt"),text);
            var renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            for(int page=0;page<document.getNumberOfPages();page++) {
                var stripper = new org.apache.pdfbox.text.PDFTextStripper(); stripper.setStartPage(page+1);stripper.setEndPage(page+1);
                if(page==0 || stripper.getText(document).contains("fully taxable"))
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page,84),"png",REPORTS.resolve(name+"-page-"+page+".png").toFile());
            }
        }
    }

    @Test void pairedBenefitAssociationsSurviveBothProjectionSwapDirectionsWithoutRecalculation() throws Exception {
        var kind=AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_LONGEVITY;
        var fixture=new MonteCarloParameterPersistenceTest();fixture.dir=directory;
        var source=fixture.source(kind,"swaps",new JsonRetirementPlanRepository());
        var view=fx(()->new View(source,kind,false));
        try {
            fx(()-> {
                source.getCurrentPlan().setConditionalBenefits(List.of(BenefitFixtures.monthly()));
                source.markModified();source.invalidateProjection();source.getCurrentProjection();source.saveCurrentAsBaseline("Original benefit baseline");source.save();
                view.configure(kind);view.button("run").fire();return null;
            });view.execute();await(view.label("parameters-status"),"SAVED");
            var projectionA=source.getAcceptedProjectionSnapshot();var refA=source.getAnalyzerAssociations(projectionA.id()).getFirst();
            var resultA=source.loadAssociatedAnalyzer(refA).toCompletableFuture().get(20,TimeUnit.SECONDS);
            fx(()-> {
                source.getCurrentPlan().setConditionalBenefits(List.of(BenefitFixtures.change(BenefitFixtures.monthly(),"grossAmount",new BigDecimal("4000"))));
                source.markModified();source.invalidateProjection();source.getCurrentProjection();source.save();
                view.configure(kind);view.button("run").fire();return null;
            });view.execute();await(view.label("parameters-status"),"SAVED");
            var projectionB=source.getAcceptedProjectionSnapshot();var refB=source.getAnalyzerAssociations(projectionB.id()).getFirst();
            var resultB=source.loadAssociatedAnalyzer(refB).toCompletableFuture().get(20,TimeUnit.SECONDS);
            fx(()-> {view.close();source.save();return null;});
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(source,"projectionEngine",new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine(){
                @Override public com.daviddunn.retirementplanner.domain.projection.Projection project(com.daviddunn.retirementplanner.domain.model.RetirementPlan p){throw new AssertionError("Swap cannot recalculate");}
            });
            var first=source.prepareProjectionSwap().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(source.commitProjectionSwap(first));assertEquals(projectionA.id(),source.getAcceptedProjectionSnapshot().id());source.save();
            assertEquals(resultA.payload(),associated(source,refA).payload());
            assertEquals(resultB.payload(),associated(source,refB).payload());
            var second=source.prepareProjectionSwap().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(source.commitProjectionSwap(second));assertEquals(projectionB.id(),source.getAcceptedProjectionSnapshot().id());source.save();
            assertEquals(resultA.payload(),associated(source,refA).payload());
            assertEquals(resultB.payload(),associated(source,refB).payload());
        } finally {fx(()-> {view.close();return null;});stop(source);}
    }
    static AnalysisSnapshot associated(ApplicationController source,com.daviddunn.retirementplanner.app.projection.AnalyzerAssociation original) throws Exception {
        var durable=source.getAnalyzerAssociations(original.projectionId()).stream().filter(a->a.runId().equals(original.runId())).findFirst().orElseThrow();
        return source.loadAssociatedAnalyzer(durable).toCompletableFuture().get(20,TimeUnit.SECONDS);
    }
}
