package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitAnalyzerReportingTest {
    static final Path OUTPUT = Path.of("target/phase22b3/reporting");
    static final Instant START = Instant.parse("2026-11-06T17:00:00Z");
    static final Instant DONE = START.plusSeconds(2);
    static IntegratedReportContext context(RetirementPlan plan, boolean weighted) {
        var captured = IntegratedReportContext.capture(plan, weighted,
                new SocialSecurityAnalyzerInputSummary.AnalyzerValues(BigDecimal.ONE, BigDecimal.ONE,
                        plan.getPlanningAssumptions().getProjectionStartDate(), plan.getPlanningAssumptions().getProjectionStartDate(), BigDecimal.ZERO),
                CurrentStrategyBaseline.fromPlan(plan));
        return new IntegratedReportContext(captured.household(), captured.primary(), captured.spouse(), START, captured.assumptions(), captured.baseline());
    }
    static AnalysisSnapshot saved(RetirementPlan plan, AnalysisKind kind, AnalysisPayload payload, SnapshotContext context) throws Exception {
        return saved(plan,kind,payload,context,UUID.randomUUID());
    }
    static AnalysisSnapshot saved(RetirementPlan plan, AnalysisKind kind, AnalysisPayload payload, SnapshotContext context,UUID runId) throws Exception {
        var fingerprints = new AnalysisFingerprintService(); var inputs = ConditionalBenefitAnalyzerValidation.inputs(payload, context);
        var completion = new AcceptedAnalysisCompletion(UUID.randomUUID(), null, kind, runId, DONE,
                fingerprints.fingerprint(kind, plan, inputs), fingerprints.effectiveContextFingerprint(kind, plan),
                AnalysisVersions.calculatedAlgorithms(kind, payload), Map.of(), payload, context).withConditionalBenefits(plan);
        var codec = new AnalysisSnapshotCodec(); var original = completion.presentationSnapshot(codec);
        var copy = codec.decode(codec.encode(original)); assertEquals(original, copy); return copy;
    }
    static SnapshotContext snapshotContext(IntegratedReportContext context, SnapshotContext.Baseline baseline) {
        return new SnapshotContext(context.household(), context.primary(), context.spouse(), START,
                context.assumptions().stream().map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(), List.of(), List.of(), Map.of(), baseline);
    }

    @Test void genuinelyCalculatedQuickRetainsReceiptsAndFrozenOriginalPdf() throws Exception {
        var plan = ConditionalBenefitIntegratedExecutionTest.plan();
        // Exact zero-SS oracle: the prerequisite values are zero for every supplied strategy.
        for (var person : List.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse())) {
            var ss = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).map(SocialSecurityIncome.class::cast).findFirst().orElseThrow();
            person.removeIncomeSource(ss);
            person.addIncomeSource(new SocialSecurityIncome(ss.getName(), ss.getOwnership(),
                    com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(person.getBirthDate(),67),
                    null, BigDecimal.ZERO,67,BigDecimal.ZERO,2026));
        }
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var strategies = new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(67,68), List.of(67),
                standard.primarySurvivorCandidates().subList(0,1),standard.spouseSurvivorCandidates().subList(0,1),IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,2).strategies();
        var zero = new SocialSecurityMortalityWeightedStrategyValue(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,1);
        var cells = strategies.stream().map(s -> new SocialSecuritySurvivorClaimingOptimizationCell(s,zero)).toList();
        var result = new IntegratedSocialSecurityStrategyComparisonService().compareAnalyzerCandidates(plan,cells);
        var model = new IntegratedSocialSecurityComparisonPresentation(result,result.entries().stream()
                .map(e -> new IntegratedSocialSecurityComparisonPresentation.Row(1,e)).toList());
        var financial = context(plan,false);
        var session = QuickReportSession.accepted(new QuickReportSession.Prepared(UUID.randomUUID(),UUID.randomUUID(),financial,
                "a".repeat(64),UUID.randomUUID(),2,strategies.stream().map(s -> new QuickReportSession.Candidate(1,s,zero)).toList(),List.of()),model,DONE);
        var payload = QuickSnapshotAdapter.capture(model,session);
        var restored = saved(plan,AnalysisKind.COUPLE_QUICK,payload,QuickSnapshotAdapter.context(session),session.prepared().runId());
        var before = QuickComparisonReportAdapter.capture(session,true,DONE.plusSeconds(1),"Synthetic saved fixture");
        plan.setConditionalBenefits(List.of());
        var after = QuickComparisonReportAdapter.capture(QuickSnapshotAdapter.report(restored),true,DONE.plusSeconds(1),"Synthetic saved fixture");
        assertEquals(before,after);
        pdf(before,"quick-current"); pdf(after,"quick-history");
        assertEquals(Files.readString(OUTPUT.resolve("quick-current.txt")),Files.readString(OUTPUT.resolve("quick-history.txt")));
        assertTrue(Files.readString(OUTPUT.resolve("quick-history.txt")).replaceAll("\\s+"," ").contains("fully taxable ordinary non-pension"));
        assertTrue(((QuickComparisonSnapshot)restored.payload()).baseline().years().stream().flatMap(y -> y.sources().stream()).anyMatch(s -> s.conditionalBenefit()!=null));
    }

    @Test void exhaustiveWeightedAndIndividualCapabilitiesRestoreAndReportOriginalDefinitions() throws Exception {
        var plan = ConditionalBenefitIntegratedExecutionTest.plan();
        var universe = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(plan,List.of(67,68),List.of(67),
                universe.primarySurvivorCandidates().subList(0,1),universe.spouseSurvivorCandidates().subList(0,1),IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,2);
        var search = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        var display = ExhaustiveIntegratedSearchPresentation.from(search,Duration.ofSeconds(1),20); var financial = context(plan,false);
        var payload = ExhaustiveResultSnapshots.capture(display,financial,null,DONE,"DIFFERENCE_FROM_CURRENT","ranked");
        var restored = saved(plan,AnalysisKind.COUPLE_EXHAUSTIVE,payload,snapshotContext(financial,null));
        var decoded = ExhaustiveResultSnapshots.restore((ExhaustiveSnapshot)restored.payload());
        var map = DeterministicHeatMapAdapter.from(decoded.model());
        pdf(IntegratedAnalyzerReportAdapter.deterministic(decoded.report(),decoded.model(),map,ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT,
                map.cells().getFirst(),search.rankedSuccessfulEntries().getFirst(),decoded.model().groups(),"No SS-only prerequisite required"),"exhaustive-history");
        var strategy = request.strategies().getFirst(); var opening = plan.getPlanningAssumptions().getProjectionStartDate();
        var weightedRequest = new LongevityWeightedIntegratedStrategyComparisonRequest(plan,List.of(strategy),
                ConditionalBenefitIntegratedExecutionTest.mortality(plan,2027,2029),opening,BigDecimal.ZERO);
        var weighted = new LongevityWeightedIntegratedStrategyComparisonService().compare(weightedRequest);
        var weightedPayload = new WeightedSnapshots.Payload(WeightedSnapshots.Couple.from(weighted),null);
        financial = context(plan,true);
        var baseline = CurrentStrategyBaseline.fromPlan(plan);
        var baselineContext = new SnapshotContext.Baseline(baseline.elections().stream().map(e -> new SnapshotContext.Election(e.name(),e.value(),e.source())).toList(),
                baseline.strategy().map(WeightedSnapshots.Strategy::from).orElse(null),baseline.problem());
        var weightedSaved = saved(plan,AnalysisKind.COUPLE_WEIGHTED,weightedPayload,snapshotContext(financial,baselineContext));
        var weightedDisplay = new LongevityWeightedIntegratedPresentation(weightedSaved.payload().couple().restore(),0,0);
        var weightedMap = LongevityWeightedHeatMapAdapter.from(weightedDisplay);
        pdf(IntegratedAnalyzerReportAdapter.weighted(financial,weightedDisplay,weightedMap,ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE,
                weightedMap.cells().getFirst(),weighted.orderedEntries().getFirst(),weighted.orderedEntries(),List.of(),null),"weighted-history");
        var single = SinglePersonMortalityAnalysisTest.plan(); single.setConditionalBenefits(List.of(BenefitFixtures.monthly()));
        var singleContext = IndividualReportContext.capture(single);
        var deterministic = SinglePersonIntegratedAnalysis.calculate(IntegratedSocialSecurityCompleteStrategySearchRequest.standard(single),AnalysisProgressListener.none(),AnalysisCancellationToken.none());
        var dPayload = IndividualDeterministicSnapshot.capture(deterministic);
        var sContext = new SnapshotContext(singleContext.household(),singleContext.primary(),null,START,singleContext.assumptions().stream()
                .map(s -> new SnapshotContext.Section(s.title(),s.lines())).toList(),List.of(),List.of(),Map.of());
        var singleSaved = saved(single,AnalysisKind.INDIVIDUAL_DETERMINISTIC,dPayload,sContext);
        pdf(IndividualAnalyzerReportAdapter.deterministic(singleContext,((IndividualDeterministicSnapshot)singleSaved.payload()).restore(),67),"individual-deterministic-history");
        var result = SinglePersonMortalityAnalysisTest.run(single,SinglePersonMortalityAnalysisTest.mortality(single,"1"),".01",SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        var wPayload = new WeightedSnapshots.Payload(null,WeightedSnapshots.Individual.from(result));
        var swSaved = saved(single,AnalysisKind.INDIVIDUAL_WEIGHTED,wPayload,sContext);
        pdf(IndividualAnalyzerReportAdapter.mortality(singleContext,swSaved.payload().individual().restore(),67),"individual-weighted-history");
        for(String name:List.of("exhaustive-history","weighted-history","individual-deterministic-history","individual-weighted-history"))
            assertTrue(Files.readString(OUTPUT.resolve(name+".txt")).contains("fully taxable ordinary"),name);
    }

    static void pdf(IntegratedAnalyzerReport report,String name) throws Exception {
        Files.createDirectories(OUTPUT); var path = OUTPUT.resolve(name+".pdf");
        new IntegratedAnalyzerPdfExporter().export(report,path);
        try(var document = Loader.loadPDF(path.toFile())) {
            Files.writeString(OUTPUT.resolve(name+".txt"),new PDFTextStripper().getText(document));
            for(int page=0;page<document.getNumberOfPages();page++) {
                var stripper = new PDFTextStripper(); stripper.setStartPage(page+1); stripper.setEndPage(page+1);
                String text = stripper.getText(document);
                if(page==0 || text.contains("fully taxable") || text.contains("400000"))
                    javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(page,84),"png",OUTPUT.resolve(name+"-page-"+page+".png").toFile());
            }
        }
    }

    @Test void actualWeightedJavaFxAssumptionsWrapInBothThemesAndWindowSizes() throws Exception {
        var plan=ConditionalBenefitIntegratedExecutionTest.plan();var request=IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var strategy=request.strategies().getFirst();var opening=plan.getPlanningAssumptions().getProjectionStartDate();
        var result=new LongevityWeightedIntegratedStrategyComparisonService().compare(new LongevityWeightedIntegratedStrategyComparisonRequest(
                plan,List.of(strategy),ConditionalBenefitIntegratedExecutionTest.mortality(plan,2027,2029),opening,BigDecimal.ZERO));
        var presentation=new LongevityWeightedIntegratedPresentation(result,0,0);var frozen=context(plan,true);
        com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.startFx();Files.createDirectories(OUTPUT);
        for(var theme:com.daviddunn.retirementplanner.application.settings.ApplicationTheme.values()) for(int width:new int[]{900,1180}) {
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.fx(()-> {
                var view=new LongevityWeightedIntegratedView();view.render(presentation,CurrentStrategyBaseline.fromPlan(plan));view.originalBenefitAssumptions(frozen.assumptions());
                var scroll=new javafx.scene.control.ScrollPane(view);scroll.setFitToWidth(true);
                var stage=new javafx.stage.Stage();var scene=new javafx.scene.Scene(scroll,width,width==900?650:820);
                try(var manager=com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager.start(theme)) {
                    stage.setScene(scene);stage.show();view.applyCss();view.layout();
                    var section=view.lookupAll(".titled-pane").stream().filter(javafx.scene.control.TitledPane.class::isInstance)
                            .map(javafx.scene.control.TitledPane.class::cast).filter(t->t.getText().equals("Methodology and Result-Time Assumptions")).findFirst().orElseThrow();
                    section.setAnimated(false);section.setExpanded(true);scroll.applyCss();scroll.layout();
                    scroll.setVvalue(1);scroll.layout();
                    assertTrue(((javafx.scene.control.Label)section.getContent()).getText().contains("400000 one-time lump sum"));
                    assertTrue(((javafx.scene.control.Label)section.getContent()).isWrapText());
                    var image=scroll.snapshot(null,null);var bitmap=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                    javax.imageio.ImageIO.write(bitmap,"png",OUTPUT.resolve("weighted-"+theme+"-"+width+".png").toFile());
                } finally {stage.close();}
            });
        }
    }
}
