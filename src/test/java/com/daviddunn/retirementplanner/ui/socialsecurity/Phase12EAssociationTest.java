package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.montecarlo.Phase12EHistoryFixtures;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set;
import static com.daviddunn.retirementplanner.ui.socialsecurity.IndividualCompletionPersistenceTest.fx;

class Phase12EAssociationTest {
    @TempDir Path directory;
    static final Map<AnalysisKind, AnalysisPayload> payloads = new EnumMap<>(AnalysisKind.class);
    static final Map<AnalysisKind, SnapshotContext> contexts = new EnumMap<>(AnalysisKind.class);
    final List<ApplicationController> controllers = new ArrayList<>();

    @BeforeAll static void fixtures() throws Exception {
        CoupleSocialSecurityPersistenceTest.prepare(); ExhaustivePersistenceTest.fixture();
        payloads.put(AnalysisKind.COUPLE_SOCIAL_SECURITY, CoupleSocialSecurityPersistenceTest.payload);
        contexts.put(AnalysisKind.COUPLE_SOCIAL_SECURITY, new SnapshotContext("Original household", "Primary", "Spouse", Instant.now().minusSeconds(60), List.of(), List.of(), List.of(), Map.of()));
        var model = ExhaustiveIntegratedSearchPresentation.from(ExhaustivePersistenceTest.result, Duration.ofSeconds(1), 3);
        var report = IntegratedAnalyzerPdfReportTest.context(true);
        payloads.put(AnalysisKind.COUPLE_EXHAUSTIVE, ExhaustiveResultSnapshots.capture(model, report, null, Instant.now(), "PERCENT_OF_OPTIMAL", "heat-map"));
        payloads.put(AnalysisKind.COUPLE_WEIGHTED, SnapshotFixtures.couple());
        contexts.put(AnalysisKind.COUPLE_WEIGHTED, SnapshotFixtures.context());
        contexts.put(AnalysisKind.COUPLE_EXHAUSTIVE, SnapshotFixtures.context());
        var selected = SocialSecurityStrategyAnalyzerPresentation.from(CoupleSocialSecurityPersistenceTest.result).rankedStrategies().stream().limit(3).toList();
        var quickModel = SocialSecurityAndQuickPdfTest.comparison(selected);
        var quick = QuickReportSession.accepted(new QuickReportSession.Prepared(UUID.randomUUID(), UUID.randomUUID(), report,
                "a".repeat(64), UUID.randomUUID(), 3, selected.stream().map(s -> new QuickReportSession.Candidate(s.rank(), s.cell().strategy(), s.cell().expectedValue())).toList(), List.of()), quickModel, Instant.now());
        payloads.put(AnalysisKind.COUPLE_QUICK, QuickSnapshotAdapter.capture(quickModel, quick)); contexts.put(AnalysisKind.COUPLE_QUICK, QuickSnapshotAdapter.context(quick));
        var single = SinglePersonMortalityAnalysisTest.plan();
        var mortality = SinglePersonMortalityAnalysisTest.mortality(single, "1.00");
        payloads.put(AnalysisKind.INDIVIDUAL_DETERMINISTIC, IndividualDeterministicSnapshot.capture(SinglePersonIntegratedAnalysis.calculate(
                IntegratedSocialSecurityCompleteStrategySearchRequest.standard(single), AnalysisProgressListener.none(), AnalysisCancellationToken.none())));
        for (var kind : List.of(AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY, AnalysisKind.INDIVIDUAL_WEIGHTED))
            payloads.put(kind, new WeightedSnapshots.Payload(null, WeightedSnapshots.Individual.from(SinglePersonMortalityAnalysisTest.run(single, mortality, "0.0100",
                    kind == AnalysisKind.INDIVIDUAL_WEIGHTED ? SinglePersonMortalityAnalysis.Mode.INTEGRATED : SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY))));
        for (var kind : List.of(AnalysisKind.INDIVIDUAL_DETERMINISTIC, AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY, AnalysisKind.INDIVIDUAL_WEIGHTED))
            contexts.put(kind, SnapshotFixtures.individualContext(single));
        payloads.putAll(Phase12EHistoryFixtures.calculate());
        for (var kind : AnalysisKind.values()) if (kind.isMonteCarlo()) contexts.put(kind, new SnapshotContext("Original MC household", "Primary",
                kind.shape() == AnalysisKind.Shape.COUPLE ? "Spouse" : null, Instant.now().minusSeconds(60), List.of(), List.of(), List.of(), Map.of()));
    }
    ApplicationController controller(boolean couple) throws Exception {
        var c = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(directory.resolve("settings-" + controllers.size())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("slots-" + controllers.size()))));
        controllers.add(c); c.newPlan(BaselineDetailsTest.plan(couple)); return c;
    }
    @AfterEach void close() throws Exception {
        for (var c : controllers) {
            c.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            c.getSnapshotCopyCompletion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(15)).complete()); c.getAnalysisParameters().shutdown();
            assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(15)).safeToExit()); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
        }
    }
    AcceptedAnalysisCompletion completion(ApplicationController c, AnalyzerAdmission token) {
        var payload = payloads.get(token.kind());
        var time = payload instanceof ExhaustiveSnapshot e ? e.completedAt() : payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot m ? m.completedAt() : Instant.now();
        var context = contexts.get(token.kind());
        if (payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot m)
            context = new SnapshotContext(context.household(), context.primary(), context.spouse(), m.startedAt(), context.assumptions(), context.request(), context.comparisons(), context.preferences());
        return new AcceptedAnalysisCompletion(c.getAnalysisSessionId(), c.getCurrentFile(), token.kind(), token.ticket(), time,
                "a".repeat(64), "b".repeat(64), AnalysisVersions.algorithms(token.kind()), AnalysisVersions.references(token.kind()),
                payload, context).associatedWith(token);
    }
    static ProjectionEngine throwing() { return new ProjectionEngine() {
        @Override public Projection project(com.daviddunn.retirementplanner.domain.model.RetirementPlan p) { throw new AssertionError("Historical navigation cannot project"); }
        @Override public ProjectionExecutionResult projectWithOutcome(com.daviddunn.retirementplanner.domain.model.RetirementPlan p) { throw new AssertionError("Historical navigation cannot project"); }
    }; }

    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void allFifteenImmutableComponentsSurviveRestartAndIndependentSaveAsWithoutEngines(AnalysisKind kind) throws Exception {
        var source = controller(kind.shape() == AnalysisKind.Shape.COUPLE); source.getCurrentProjection();
        var file = directory.resolve("original.json"); source.saveAs(file);
        var projection = source.getAcceptedProjectionSnapshot();
        var token = source.prepareAnalyzerAssociation(kind, "a".repeat(64)); assertNotNull(token);
        var accepted = completion(source, token); assertTrue(source.associateAnalyzer(token, accepted));
        assertFalse(source.isModified());
        source.saveCurrentAsBaseline("Exact projection");
        var before = source.loadAssociatedAnalyzer(source.getAnalyzerAssociations(projection.id()).getFirst()).toCompletableFuture().get(20, TimeUnit.SECONDS);
        set(source, "projectionEngine", throwing()); source.save();
        assertTrue(source.getAnalyzerAssociationNotice(kind).contains("saved"));
        var ref = source.getAnalyzerAssociations(projection.id()).getFirst(); assertNotEquals("0".repeat(64), ref.componentDigest());
        assertEquals(source.getCurrentPlan().getProjectionState().current().id(), source.getCurrentPlan().getProjectionState().baseline().id());
        var reloaded = controller(kind.shape() == AnalysisKind.Shape.COUPLE); set(reloaded, "projectionEngine", throwing()); reloaded.open(file);
        reloaded.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
        var actual = reloaded.loadAssociatedAnalyzer(ref).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(before.payload(), actual.payload()); assertEquals(before.context(), actual.context()); assertEquals(before.payloadDigest(), actual.payloadDigest());
        assertEquals(before.originPlanId(), actual.originPlanId());
        assertEquals(accepted.runId(), actual.runId()); assertEquals(accepted.completedAt(), actual.completedAt());
        verifyPresentation(actual, kind);
        var originalBytes = Files.readAllBytes(file); var cloned = directory.resolve("clone.json"); reloaded.saveAs(cloned);
        var copiedRef = reloaded.getAnalyzerAssociations(projection.id()).getFirst();
        assertNotEquals(ref.owner(), copiedRef.owner()); assertEquals(ref.runId(), copiedRef.runId());
        var copied = reloaded.loadAssociatedAnalyzer(copiedRef).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(actual.payload(), copied.payload()); assertEquals(actual.context(), copied.context()); assertEquals(actual.payloadDigest(), copied.payloadDigest());
        assertArrayEquals(originalBytes, Files.readAllBytes(file));
        assertThrows(ExecutionException.class, () -> reloaded.loadAssociatedAnalyzer(ref).toCompletableFuture().get());
        assertFalse(reloaded.isModified());
    }
    static void verifyPresentation(AnalysisSnapshot snapshot, AnalysisKind kind) throws Exception {
        var prepared = ProjectionAnalyzerHistoryDialog.prepare(snapshot); // no financial factories are available to this adapter
        var output = Path.of("target/phase12e-previews"); Files.createDirectories(output);
        var work = fx(() -> {
            var view = prepared.view().get();
            var scroll = new javafx.scene.control.ScrollPane(view); scroll.setFitToWidth(true);
            boolean dark = kind.shape() == AnalysisKind.Shape.SINGLE;
            if (dark) scroll.setStyle("-fx-base:#202328;-fx-background:#202328;-fx-control-inner-background:#252830;-fx-text-inner-color:#f1f1f1;-fx-text-base-color:#f1f1f1;");
            var root = new StackPane(scroll); new Scene(root, dark ? 900 : 1180, dark ? 650 : 820); root.applyCss(); root.layout();
            assertFalse(root.lookupAll(".label").isEmpty());
            var image = root.snapshot(null, null);
            var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y=0;y<pixels.getHeight();y++) for (int x=0;x<pixels.getWidth();x++) pixels.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            javax.imageio.ImageIO.write(pixels,"png",output.resolve(kind.name()+".png").toFile());
            return prepared.export().apply(output.resolve(kind.name()+".pdf"));
        });
        work.run();
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(output.resolve(kind.name()+".pdf").toFile())) {
            assertTrue(pdf.getNumberOfPages() > 0);
            var text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Original saved")); assertTrue(text.contains(snapshot.completedAt().toString()));
            var page = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0, 100);
            javax.imageio.ImageIO.write(page,"png",output.resolve(kind.name()+"-pdf.png").toFile());
        }
    }
    @Test void supersededAndWrongRunTokensCannotPinAndLegacyVisibilityCannotInferAnAssociation() throws Exception {
        var c = controller(true); c.getCurrentProjection();
        var old = c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_SOCIAL_SECURITY, "a".repeat(64));
        var newer = c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_SOCIAL_SECURITY, "a".repeat(64));
        assertFalse(c.associateAnalyzer(old, completion(c, old)));
        assertFalse(c.associateAnalyzer(newer, completion(c, newer).forRun(UUID.randomUUID())));
        var value=completion(c,newer);
        var mismatched=new AcceptedAnalysisCompletion(value.planId(),value.planFile(),value.kind(),value.runId(),value.completedAt(),"f".repeat(64),value.contextFingerprint(),
                value.algorithms(),value.references(),value.payload(),value.context());
        assertFalse(c.associateAnalyzer(newer,mismatched));
        assertTrue(c.getAnalyzerAssociations(newer.projectionId()).isEmpty());
        assertTrue(c.associateAnalyzer(newer, completion(c, newer)));
        var accepted = c.getAnalyzerAssociations(newer.projectionId());
        var canceled = c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_SOCIAL_SECURITY, "a".repeat(64)); // canceled/failed work never publishes
        assertEquals(accepted, c.getAnalyzerAssociations(newer.projectionId()));
        c.markModified(); assertFalse(c.associateAnalyzer(canceled, completion(c, canceled)));
        var planA = c.getCurrentPlan(); c.newPlan(BaselineDetailsTest.plan(true)); c.getCurrentProjection();
        assertFalse(c.associateAnalyzer(canceled, completion(c, canceled))); assertTrue(c.getCurrentPlan().getProjectionState().associations().isEmpty());
        assertEquals(accepted, planA.getProjectionState().associations());
    }
    @Test void recalculationAndBaselineClearingRetainOriginalPinsAndOriginalProjectionComponents() throws Exception {
        var c = controller(true); c.getCurrentProjection();
        var token = c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_QUICK, "a".repeat(64)); assertTrue(c.associateAnalyzer(token, completion(c, token)));
        var original = c.getAcceptedProjectionSnapshot();
        c.saveCurrentAsBaseline("First"); c.saveAs(directory.resolve("plan.json"));
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("2000000.0012300"));
        c.invalidateProjection(); c.getCurrentProjection(); assertNotEquals(original.id(), c.getAcceptedProjectionSnapshot().id());
        assertEquals(1, c.getAnalyzerAssociations(original.id()).size());
        c.getCurrentPlan().setBaseline(null); c.save();
        assertNull(c.getCurrentPlan().getProjectionState().baseline());
        assertEquals(1, c.getAnalyzerAssociations(original.id()).size());
        var reload = controller(true); set(reload, "projectionEngine", throwing()); reload.open(c.getCurrentFile());
        reload.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(payloads.get(AnalysisKind.COUPLE_QUICK), reload.loadAssociatedAnalyzer(reload.getAnalyzerAssociations(original.id()).getFirst()).toCompletableFuture().get().payload());
    }
    @Test void malformedReferencesAndCorruptComponentsAreRejectedWithoutReplacingDurableManifest() throws Exception {
        var c = controller(true); c.getCurrentProjection(); var token = c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_QUICK, "a".repeat(64));
        assertTrue(c.associateAnalyzer(token, completion(c, token))); var file = directory.resolve("plan.json"); c.saveAs(file);
        var ref = c.getAnalyzerAssociations(token.projectionId()).getFirst(); var state = c.getCurrentPlan().getProjectionState();
        assertThrows(IllegalArgumentException.class, () -> new ProjectionRoleManifest(2, UUID.randomUUID(), state.current(), null, List.of(ref)));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionRoleManifest(2, state.owner(), state.current(), null, List.of(ref, ref)));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionRoleManifest(2, state.owner(), state.current(),
                new ProjectionRoleManifest.Reference(state.current().id(),"f".repeat(64)), List.of(ref)));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionRoleManifest(2, state.owner(), null, null, List.of(ref)));
        var snapshot = c.loadAssociatedAnalyzer(ref).toCompletableFuture().get();
        var wrong = new AnalyzerAssociation(1, ref.owner(), ref.projectionId(), ref.projectionInputDigest(), ref.kind(), ref.runId(),
                "f".repeat(64), ref.contextFingerprint(), ref.payloadDigest(), ref.componentDigest(), ref.acceptedAt());
        assertThrows(IllegalArgumentException.class, () -> wrong.validate(snapshot));
        var component = directory.resolve(".retirement-planner-projections").resolve(ref.owner().toString()).resolve("analysis-" + ref.runId() + ".json.gz");
        var bytes = Files.readAllBytes(file); Files.write(component, new byte[]{1,2,3});
        assertThrows(ExecutionException.class, () -> c.loadAssociatedAnalyzer(ref).toCompletableFuture().get());
        assertThrows(java.io.IOException.class, c::save); assertArrayEquals(bytes, Files.readAllBytes(file));
    }
    @ParameterizedTest @ValueSource(strings={"manual-navigation","plan-switch","close"})
    void delayedHistoryRestorationCannotReplaceLaterNavigationOrAnotherPlansView(String action) throws Exception {
        var c=controller(true); c.getCurrentProjection(); var token=c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_SOCIAL_SECURITY, "a".repeat(64));
        assertTrue(c.associateAnalyzer(token,completion(c,token))); c.saveAs(directory.resolve("plan.json"));
        set(c,"projectionEngine",throwing());
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var barrier=c.getAnalysisResults().background(()->{entered.countDown(); assertTrue(release.await(15,TimeUnit.SECONDS));return null;});
        assertTrue(entered.await(10,TimeUnit.SECONDS));
        var dialog=fx(()->{var d=new ProjectionAnalyzerHistoryDialog(c,token.projectionId());d.show();return d;});
        try {
            fx(()->{
                if(action.equals("manual-navigation")) {
                    var f=ProjectionAnalyzerHistoryDialog.class.getDeclaredField("kinds");f.setAccessible(true);
                    @SuppressWarnings("unchecked") var kinds=(javafx.scene.control.ComboBox<AnalysisKind>)f.get(dialog);
                    kinds.setValue(AnalysisKind.COUPLE_QUICK);
                } else if(action.equals("plan-switch")) c.newPlan(BaselineDetailsTest.plan(false)); else dialog.close();
                return null;
            });
            release.countDown(); barrier.toCompletableFuture().get(20,TimeUnit.SECONDS);
            c.getAnalysisResults().background(()->null).toCompletableFuture().get(20,TimeUnit.SECONDS);
            fx(()->null);
            fx(()->{
                if(action.equals("manual-navigation")) {
                    var f=ProjectionAnalyzerHistoryDialog.class.getDeclaredField("notice");f.setAccessible(true);
                    assertTrue(((javafx.scene.control.Label)f.get(dialog)).getText().contains("not captured"));
                } else assertFalse(dialog.isShowing());
                return null;
            });
        } finally {release.countDown();fx(()->{dialog.close();return null;});}
    }
}
