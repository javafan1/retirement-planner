package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class CoupleSocialSecurityPersistenceTest {
    @TempDir Path dir;
    static SocialSecurityStrategyAnalysisContext context;
    static SocialSecuritySurvivorClaimingOptimizationResult result;
    static CoupleSocialSecuritySnapshot payload;
    static final AnalysisKind KIND = AnalysisKind.COUPLE_SOCIAL_SECURITY;
    @BeforeAll static void prepare() throws Exception {
        startFx();
        context = fixture(false, false);
        result = new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(context.request());
        payload = CoupleSocialSecuritySnapshotMapper.capture(context, result);
    }
    static SocialSecurityStrategyAnalysisContext fixture(boolean full, boolean zero) {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var original = new SocialSecurityStrategyAnalysisRequestFactory().create(plan, SocialSecurityMortalityAdjustment.standard(),
                SocialSecurityMortalityAdjustment.standard(), new BigDecimal("0.010"), plan.getPlanningAssumptions().getProjectionStartDate(), plan.getPlanningAssumptions().getProjectionStartDate());
        var base = original.request().retirementGridRequest().baseStrategy();
        if (zero) {
            var p = base.primaryElection(); var s = base.spouseElection();
            base = new SocialSecurityStrategyRequest(base.analysisDate(), base.analysisEndDate(),
                    new SocialSecurityClaimingElection(p.owner(), p.birthDate(), new BigDecimal("0.0000"), p.benefitValuationYear(), p.retirementClaimDate()),
                    new SocialSecurityClaimingElection(s.owner(), s.birthDate(), new BigDecimal("0.0000"), s.benefitValuationYear(), s.retirementClaimDate()),
                    base.primarySurvivorClaimDate(), base.spouseSurvivorClaimDate(), base.primaryDeathDate(), base.spouseDeathDate(), base.socialSecurityColaRate());
        }
        var mortality = new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(80, new BigDecimal("0.40")),
                new SocialSecurityMortalityProbability(95, new BigDecimal("0.60"))));
        var ages = full ? java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList() : List.of(67, 70);
        var request = new SocialSecuritySurvivorClaimingOptimizationRequest(new SocialSecurityMortalityWeightedClaimingGridRequest(
                base, ages, ages, mortality, mortality, base.analysisDate(), base.analysisDate(), new BigDecimal("0.010")),
                new SocialSecuritySurvivorOptimizationSettings(5));
        return new SocialSecurityStrategyAnalysisContext(request, original.primaryName(), original.spouseName(),
                base.primaryElection().fullRetirementMonthlyBenefit(), base.spouseElection().fullRetirementMonthlyBenefit(),
                original.primaryBenefitValuationYear(), original.spouseBenefitValuationYear(), original.primaryCurrentClaimDate(), original.spouseCurrentClaimDate(),
                original.primaryMortalityCategory(), original.spouseMortalityCategory(), original.primaryMortalityAdjustment(), original.spouseMortalityAdjustment(), original.mortalityMetadata());
    }
    static AcceptedAnalysisCompletion completion(ApplicationController source) {
        var plan = source.getCurrentPlan(); var fingerprints = new AnalysisFingerprintService();
        var inputs = new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE,
                context.request().retirementGridRequest().mortalityBaseDate(), context.request().retirementGridRequest().presentValueBaseDate(),
                new BigDecimal("0.010"), null, null, List.of(), List.of(), null);
        return new AcceptedAnalysisCompletion(source.getAnalysisSessionId(), source.getCurrentFile(), KIND, UUID.randomUUID(), COMPLETED,
                fingerprints.fingerprint(KIND, plan, inputs), fingerprints.effectiveContextFingerprint(KIND, plan), AnalysisVersions.algorithms(KIND), Map.of(),
                payload, new SnapshotContext("Couple", context.primaryName(), context.spouseName(), COMPLETED.minusSeconds(3),
                        List.of(), List.of(), List.of(), Map.of("occurrence", "2")));
    }
    ApplicationController controller(AnalysisResultStore store, String settings) throws Exception {
        return new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve(settings)), store);
    }
    record Session(SocialSecurityStrategyAnalyzerDialog dialog, SocialSecurityAnalyzerJobController jobs,
            SocialSecurityAnalysisJobCoordinator coordinator, WeightedResultPersistence persistence, long generation) implements AutoCloseable {
        public void close() throws Exception {
            fx(() -> { var close = dialog.getClass().getDeclaredMethod("close"); close.setAccessible(true); close.invoke(dialog); return null; }); coordinator.close();
        }
    }
    static Session session(ApplicationController source) throws Exception {
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        return fx(() -> {
            var jobs = new SocialSecurityAnalyzerJobController(coordinator);
            var dialog = new SocialSecurityStrategyAnalyzerDialog(null, source, jobs);
            dialog.socialSecurityCalculation = request -> (progress, cancel) -> { throw new AssertionError("Restoration must not calculate"); };
            return new Session(dialog, jobs, coordinator, field(dialog, "socialSecurityPersistence", WeightedResultPersistence.class), jobs.generation());
        });
    }
    @Test void exactRoundTripPreservesBothStagesTiesContextOrderingAndScale() throws Exception {
        var source = controller(new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))), "settings");
        source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        try {
            var snapshot = completion(source).snapshot(new AnalysisSnapshotCodec());
            var codec = new AnalysisSnapshotCodec(); var restored = codec.decode(codec.encode(snapshot));
            assertEquals(snapshot, restored);
            var copy = (CoupleSocialSecuritySnapshot) restored.payload();
            assertEquals(payload, copy);
            assertEquals(CoupleSocialSecuritySnapshot.Request.from(result.request()), CoupleSocialSecuritySnapshot.Request.from(copy.restore().request()));
            assertEquals(result.retirementGrid().cells(), copy.restore().retirementGrid().cells());
            assertEquals(result.retirementGrid().jointMortalityScenarios(), copy.scenarios());
            assertEquals(result.evaluatedStrategies(), copy.restore().evaluatedStrategies());
            assertEquals(result.highestExpectedPresentValueStrategies(), copy.restore().highestExpectedPresentValueStrategies());
            var restoredContext = CoupleSocialSecuritySnapshotMapper.context(copy);
            for (var component : SocialSecurityStrategyAnalysisContext.class.getRecordComponents()) {
                if (!component.getName().equals("request"))
                    assertEquals(component.getAccessor().invoke(context), component.getAccessor().invoke(restoredContext), component.getName());
            }
            assertEquals(SocialSecurityStrategyAnalyzerPresentation.from(result).rankedStrategies(), SocialSecurityStrategyAnalyzerPresentation.from(copy.restore()).rankedStrategies());
            assertEquals(2, copy.request().primaryMortality().getFirst().probability().scale());
        } finally { stop(source.getAnalysisResults()); }
    }
    @Test void restartRestoresWithoutJobAndCurrencyReturnsToCurrent() throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var source = controller(store, "settings"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var accepted = completion(source); store.accept(accepted); settle(store, accepted.planId(), KIND); stop(store);
        var restarted = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var reopened = controller(restarted, "reopened"); reopened.open(source.getCurrentFile());
        byte[] planBytes = Files.readAllBytes(source.getCurrentFile());
        try (var view = session(reopened)) {
            await(view.persistence.notice, "Current Results");
            fx(() -> {
                var tabs = field(view.dialog, "socialSecurityTabs", TabPane.class);
                assertEquals(1, tabs.getSelectionModel().getSelectedIndex(), "Historical SS initially selects Claiming Grid");
                tabs.getSelectionModel().selectFirst();
                view.persistence.load(); view.persistence.reassess();
                var render = view.dialog.getClass().getDeclaredMethod("render", SocialSecurityStrategyAnalysisContext.class,
                        SocialSecurityStrategyAnalyzerPresentation.class);
                render.setAccessible(true);
                render.invoke(view.dialog, context, field(view.dialog, "presentation", SocialSecurityStrategyAnalyzerPresentation.class));
                assertEquals(0, tabs.getSelectionModel().getSelectedIndex(), "Refresh/re-render preserves manual Recommendation");
                return null;
            });
            fx(() -> {
                assertEquals(view.generation, view.jobs.generation()); assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, view.jobs.state());
                var actual = field(view.dialog, "presentation", SocialSecurityStrategyAnalyzerPresentation.class);
                assertEquals(result.evaluatedStrategies(), actual.result().evaluatedStrategies());
                assertFalse(field(view.dialog, "integratedRunButton", Button.class).isDisabled());
                var factor = field(view.dialog, "primaryMortalityAdjustment", TextField.class);
                factor.setText("0.8"); assertFalse(view.persistence.current());
                factor.setText("invalid"); assertTrue(view.persistence.notice.getText().contains("Inputs Need Attention"));
                factor.setText("1"); assertTrue(view.persistence.current());
                var navigation = field(view.dialog, "navigation", AnalyzerNavigationView.class);
                navigation.select(SocialSecurityAnalyzerJobController.Mode.QUICK);
                navigation.select(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY);
                assertEquals(0, field(view.dialog, "socialSecurityTabs", TabPane.class).getSelectionModel().getSelectedIndex());
                assertEquals(actual, field(view.dialog, "presentation", SocialSecurityStrategyAnalyzerPresentation.class));
                return null;
            });
            assertArrayEquals(planBytes, Files.readAllBytes(source.getCurrentFile()));
        }
        try (var reopenedDialog = session(reopened)) {
            await(reopenedDialog.persistence.notice, "Current Results");
            fx(() -> { assertEquals(1, field(reopenedDialog.dialog, "socialSecurityTabs", TabPane.class).getSelectionModel().getSelectedIndex()); return null; });
        } finally { stop(restarted); }
    }
    @Test void fullAndTieHeavyCapacityRoundTrip() throws Exception {
        var codec = new AnalysisSnapshotCodec();
        for (boolean zero : List.of(false, true)) {
            var c = fixture(true, zero);
            var r = new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(c.request());
            var p = CoupleSocialSecuritySnapshotMapper.capture(c, r);
            var frozen = new SnapshotContext("Couple", c.primaryName(), c.spouseName(), COMPLETED, List.of(), List.of(), List.of(), Map.of());
            var s = new AnalysisSnapshot(1, 1, UUID.randomUUID(), KIND, KIND.shape(), UUID.randomUUID(), COMPLETED, "test",
                    AnalysisVersions.algorithms(KIND), AnalysisVersions.references(KIND), SnapshotFixtures.HASH, SnapshotFixtures.HASH, codec.digest(p, frozen), p, frozen, null);
            long start = System.nanoTime(); var encoded = codec.encodeMeasured(s); long written = System.nanoTime();
            assertEquals(s, codec.decode(encoded.bytes())); long read = System.nanoTime();
            assertEquals(81, p.gridCells().size());
            if (zero) { assertEquals(5184, p.strategies().size()); assertEquals(5184, p.highestStrategies().size()); }
            System.out.printf("Couple SS capacity zero=%s strategies=%d scenarios=%d raw=%d gzip=%d encodeMs=%.2f decodeMs=%.2f%n",
                    zero, p.strategies().size(), p.scenarios().size(), encoded.rawBytes(), encoded.bytes().length,
                    (written-start)/1e6, (read-written)/1e6);
        }
    }
    @Test void saveFailurePreservesPreviousBytesAndRetryDoesNotCalculate() throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var repository = AnalysisSnapshotFaults.failVerification(dir.resolve("results"), fail::get);
        var store = new AnalysisResultStore(repository); var source = controller(store, "settings");
        source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var old = completion(source); store.accept(old); settle(store, old.planId(), KIND);
        var path = repository.path(old.planId(), KIND); byte[] previous = Files.readAllBytes(path);
        try (var view = session(source)) {
            await(view.persistence.notice, "Current Results"); fail.set(true);
            var newer = completion(source);
            fx(() -> { view.persistence.accept(newer); return null; });
            await(view.persistence.notice, "could not be saved");
            assertArrayEquals(previous, Files.readAllBytes(path));
            assertEquals(old.runId(), repository.load(old.planId(), KIND).orElseThrow().runId());
            fail.set(false); fx(() -> { view.persistence.retry.fire(); return null; });
            settle(store, newer.planId(), KIND);
            assertEquals(newer.runId(), repository.load(old.planId(), KIND).orElseThrow().runId());
            assertEquals(old.runId(), repository.loadBackup(old.planId(), KIND).orElseThrow().runId());
            assertEquals(view.generation, fx(view.jobs::generation));
        } finally { fail.set(false); store.retryFailed(); stop(store); }
    }
    @Test void saveAsClonesIndependentlyAndRestoresHistoricalMetadata() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repository);
        var source = controller(store, "settings"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("original.json"));
        var original = completion(source); store.accept(original); settle(store, original.planId(), KIND);
        var originalSnapshot = repository.load(original.planId(), KIND).orElseThrow();
        source.saveAs(dir.resolve("copy.json"));
        try (var view = session(source)) {
            await(view.persistence.notice, "Current Results");
            await(view.persistence.notice, "Historical copy");
            var clone = repository.load(source.getAnalysisSessionId(), KIND).orElseThrow();
            assertNotEquals(original.planId(), clone.planId());
            assertEquals(original.planId(), clone.originPlanId()); assertEquals(original.completedAt(), clone.completedAt());
            assertEquals(original.payload(), clone.payload()); assertEquals(original.context(), clone.context());
            assertEquals(originalSnapshot, repository.load(original.planId(), KIND).orElseThrow());
        } finally { stop(store); }
    }
    @Test void incompatibleSnapshotIsUnavailableAndRetained() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repository);
        var source = controller(store, "settings"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var original = completion(source).snapshot(new AnalysisSnapshotCodec()); repository.claimPlan(original.planId(), source.getCurrentFile()); repository.save(original);
        var path = repository.path(original.planId(), KIND);
        var raw = new String(new AnalysisSnapshotCodec().raw(original), java.nio.charset.StandardCharsets.UTF_8).replace("\"schemaVersion\":1", "\"schemaVersion\":99");
        Files.write(path, AnalysisSnapshotCodecTest.gzip(raw)); byte[] bytes = Files.readAllBytes(path);
        try (var view = session(source)) {
            await(view.persistence.notice, "Incompatible or Unavailable");
            assertNull(fx(() -> field(view.dialog, "presentation", SocialSecurityStrategyAnalyzerPresentation.class)));
            assertTrue(fx(() -> field(view.dialog, "integratedRunButton", Button.class).isDisabled()));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        } finally { stop(store); }
    }
    @Test void lateRestoreCannotPublishIntoAnotherPlan() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repository);
        var source = controller(store, "settings"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("first.json"));
        var original = completion(source); store.accept(original); settle(store, original.planId(), KIND);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        store.background(() -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return null; });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try (var view = session(source)) {
            fx(() -> { source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); return null; });
            release.countDown();
            store.background(() -> null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            fx(() -> {
                assertNull(field(view.dialog, "presentation", SocialSecurityStrategyAnalyzerPresentation.class));
                assertFalse(view.persistence.current()); return null;
            });
        } finally { release.countDown(); stop(store); }
    }
    @Test void actualRunButtonAcceptsAndPersistsOnlyEligibleCompletion() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repository);
        var source = controller(store, "settings"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        try (var view = session(source)) {
            var runError = new java.util.concurrent.atomic.AtomicReference<String>();
            fx(() -> {
                javafx.collections.ListChangeListener<javafx.stage.Window> errors = change -> {
                    while (change.next()) for (var window : change.getAddedSubList()) {
                        if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane pane) {
                            runError.set(pane.getContentText());
                            javafx.application.Platform.runLater(window::hide);
                        }
                    }
                };
                javafx.stage.Window.getWindows().addListener(errors);
                // Terminal conditioning keeps the actual optimizer fixture bounded, while using the real button and callback.
                var date = field(view.dialog, "mortalityDate", DatePicker.class);
                date.setValue(LocalDate.of(2082, 1, 1));
                date.getEditor().setText(date.getConverter().toString(date.getValue()));
                view.dialog.socialSecurityCalculation = request -> (progress, cancel) -> new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(request, progress, cancel);
                try { field(view.dialog, "runButton", Button.class).fire(); }
                finally { javafx.stage.Window.getWindows().removeListener(errors); }
                return null;
            });
            assertNull(runError.get(), "Actual Run error: " + runError.get());
            await(view.persistence.notice, "Results saved");
            var saved = repository.load(source.getAnalysisSessionId(), KIND).orElseThrow();
            assertTrue(saved.payload() instanceof CoupleSocialSecuritySnapshot);
            assertEquals(fx(() -> field(view.dialog, "presentation", SocialSecurityStrategyAnalyzerPresentation.class).result().evaluatedStrategies()),
                    ((CoupleSocialSecuritySnapshot) saved.payload()).strategies());
            // A canceled or obsolete replacement uses the unchanged job gate and never reaches persistence acceptance.
            for (String outcome : List.of("cancel", "obsolete", "failure")) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                var finished = new CompletableFuture<Void>();
                var published = new java.util.concurrent.atomic.AtomicBoolean();
                fx(() -> {
                    var originalChanged = field(view.jobs, "changed", Runnable.class);
                    view.jobs.onChanged(() -> {
                        originalChanged.run();
                        if (view.jobs.state() == SocialSecurityAnalyzerJobController.State.IDLE) {
                            view.jobs.onChanged(originalChanged);
                            javafx.application.Platform.runLater(() -> finished.complete(null));
                        }
                    });
                    view.jobs.start(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY, (progress, cancellation) -> {
                        entered.countDown();
                        try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                        if (outcome.equals("failure")) throw new IllegalStateException("injected calculation failure");
                        return result;
                    }, ignored -> published.set(true), failure -> published.set(!outcome.equals("failure")));
                    return null;
                });
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                fx(() -> {
                    if (outcome.equals("cancel")) view.jobs.cancel();
                    else if (outcome.equals("obsolete")) view.jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
                    return null;
                });
                release.countDown();
                finished.get(10, TimeUnit.SECONDS);
                assertFalse(published.get());
                assertEquals(saved.runId(), repository.load(source.getAnalysisSessionId(), KIND).orElseThrow().runId());
            }
        } finally { stop(store); }
    }
    @Test void checkedReferencesRejectDuplicatesOutOfRangeAndWrongFamilies() throws Exception {
        var components = CoupleSocialSecuritySnapshot.class.getRecordComponents();
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) values[i] = components[i].getAccessor().invoke(payload);
        var constructor = CoupleSocialSecuritySnapshot.class.getDeclaredConstructor(Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));
        for (String name : List.of("retainedCells", "highestStrategies")) {
            int index = java.util.stream.IntStream.range(0, components.length).filter(i -> components[i].getName().equals(name)).findFirst().orElseThrow();
            for (var invalid : List.of(List.of(-1), List.of(100000), List.of(0, 0))) {
                var changed = values.clone(); changed[index] = invalid;
                var corrupt = constructor.newInstance(changed);
                assertThrows(IllegalArgumentException.class, () -> corrupt.validate(KIND));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> payload.validate(AnalysisKind.COUPLE_WEIGHTED));
        assertThrows(IllegalArgumentException.class, () -> payload.validate(AnalysisKind.COUPLE_QUICK));
    }
    @Test void separateSlotsAndRelevantVersusIrrelevantPlanEdits() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repository);
        var source = controller(store, "settings"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var weighted = coupleCompletion(source); store.accept(weighted); settle(store, weighted.planId(), weighted.kind());
        var accepted = completion(source); store.accept(accepted); settle(store, accepted.planId(), KIND);
        try (var view = session(source)) {
            await(view.persistence.notice, "Current Results");
            var expense = new com.daviddunn.retirementplanner.domain.financial.Expense("Synthetic", new BigDecimal("12.3400"));
            fx(() -> { source.getCurrentPlan().getHousehold().addExpense(expense); source.markModified(); return null; });
            await(view.persistence.notice, "Current Results");
            fx(() -> {
                assertTrue(view.persistence.current());
                var person = source.getCurrentPlan().getHousehold().getPrimaryPerson();
                person.setMortalityCategory(com.daviddunn.retirementplanner.domain.model.MortalityCategory.FEMALE); source.markModified();
                return null;
            });
            await(view.persistence.notice, "Outdated");
            fx(() -> { source.getCurrentPlan().getHousehold().getPrimaryPerson().setMortalityCategory(com.daviddunn.retirementplanner.domain.model.MortalityCategory.MALE); source.markModified(); return null; });
            await(view.persistence.notice, "Current Results");
            assertEquals(weighted.payload(), repository.load(weighted.planId(), weighted.kind()).orElseThrow().payload());
            assertEquals(accepted.payload(), repository.load(accepted.planId(), KIND).orElseThrow().payload());
        } finally { stop(store); }
    }
}
