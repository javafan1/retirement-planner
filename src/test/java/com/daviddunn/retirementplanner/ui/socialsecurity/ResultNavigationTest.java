package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;

class ResultNavigationTest {
    @TempDir Path dir;
    @BeforeAll static void prepare() throws Exception { SocialSecurityParameterPersistenceTest.init(); }

    @Test void manualChoiceAfterAdmissionWinsAndEachNewIdentityInitializesOnce() throws Exception {
        fx(() -> {
            var binding = new AtomicReference<Object>("Plan A");
            var guard = new ResultNavigationGuard(binding::get);
            var ranked = new Tab("Ranked"); var heat = new Tab("Heat");
            var tabs = new IntegratedAnalysisResultTabs(ranked, heat); guard.watch(tabs);
            var first = UUID.randomUUID(); var ticket = guard.capture();
            tabs.getSelectionModel().select(heat); tabs.getSelectionModel().select(ranked);
            guard.display("mode", first, ticket, tabs, heat, () -> true);
            assertSame(ranked, tabs.getSelectionModel().getSelectedItem());
            guard.display("mode", first, guard.capture(), tabs, heat, () -> true);
            assertSame(ranked, tabs.getSelectionModel().getSelectedItem(), "Same result must not get another initialization");
            var second = UUID.randomUUID(); guard.display("mode", second, guard.capture(), tabs, heat, () -> true);
            assertSame(heat, tabs.getSelectionModel().getSelectedItem());
            tabs.getSelectionModel().select(ranked);
            guard.display("mode", second, guard.capture(), tabs, heat, () -> true);
            assertSame(ranked, tabs.getSelectionModel().getSelectedItem());
            var old = guard.capture(); binding.set("Plan B");
            guard.display("mode", UUID.randomUUID(), old, tabs, heat, () -> true);
            assertSame(ranked, tabs.getSelectionModel().getSelectedItem());
            guard.display("mode", second, guard.capture(), tabs, heat, () -> true);
            assertSame(heat, tabs.getSelectionModel().getSelectedItem(), "Cloned run ID on another binding initializes independently");
            tabs.getSelectionModel().select(ranked); var finalTicket = guard.capture(); guard.close();
            guard.display("mode", UUID.randomUUID(), finalTicket, tabs, heat, () -> true);
            assertSame(ranked, tabs.getSelectionModel().getSelectedItem());
            return null;
        });
    }

    @Test void deferredRevealChecksLaterTabWorkspaceAndClosedBinding() throws Exception {
        var calls = new int[1];
        fx(() -> {
            var guard = new ResultNavigationGuard(() -> "Plan A");
            var tabs = new IntegratedAnalysisResultTabs(new Tab("Ranked"), new Tab("Heat")); guard.watch(tabs);
            guard.display("mode", UUID.randomUUID(), guard.capture(), tabs, tabs.getTabs().get(1), () -> { calls[0]++; return true; });
            tabs.getSelectionModel().selectFirst();
            return null;
        });
        fx(() -> { assertEquals(0, calls[0], "Deferred callback must reject newer selection before scrolling"); return null; });
    }

    private SocialSecurityParameterPersistenceTest helper() {
        var helper = new SocialSecurityParameterPersistenceTest(); helper.dir = dir; return helper;
    }

    private static final class BlockedSsRepository implements AnalysisSnapshotRepository {
        final JsonAnalysisSnapshotRepository delegate;
        final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicBoolean blocked = new java.util.concurrent.atomic.AtomicBoolean();
        BlockedSsRepository(Path root) { delegate = new JsonAnalysisSnapshotRepository(root); }
        public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) throws java.io.IOException {
            if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY && blocked.compareAndSet(false, true)) {
                entered.countDown();
                try {
                    if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("Blocked SS load timed out");
                } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new java.io.IOException(failure); }
            }
            return delegate.load(id, kind);
        }
        public void save(AnalysisSnapshot snapshot) throws java.io.IOException { delegate.save(snapshot); }
        public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) throws java.io.IOException { return delegate.loadBackup(id, kind); }
        public void claimPlan(UUID id, Path file) throws java.io.IOException { delegate.claimPlan(id, file); }
    }

    @ParameterizedTest @ValueSource(strings = {"tab", "workspace", "close", "plan"})
    void pendingSsHistoricalLoadRespectsNewerNavigationAndOwnership(String action) throws Exception {
        var source = helper().source(AnalysisKind.COUPLE_SOCIAL_SECURITY, new JsonRetirementPlanRepository());
        var accepted = CoupleSocialSecurityPersistenceTest.completion(source);
        source.getAnalysisResults().accept(accepted); settle(source.getAnalysisResults(), accepted.planId(), accepted.kind());
        var file = source.getCurrentFile(); SocialSecurityParameterPersistenceTest.stop(source);
        var repository = new BlockedSsRepository(dir.resolve("results"));
        var restarted = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(dir.resolve("reload.settings")), new AnalysisResultStore(repository));
        fx(() -> { restarted.open(file); return null; });
        try (var session = CoupleSocialSecurityPersistenceTest.session(restarted)) {
            assertTrue(repository.entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            fx(() -> {
                var resultTabs = field(session.dialog(), "socialSecurityTabs", TabPane.class);
                assertEquals(0, resultTabs.getSelectionModel().getSelectedIndex(), "No completed attachment must not select Claiming Grid");
                assertNull(field(session.dialog(), "presentation", SocialSecurityStrategyAnalyzerPresentation.class));
                switch (action) {
                    case "tab" -> resultTabs.getSelectionModel().select(2);
                    case "workspace" -> field(session.dialog(), "navigation", AnalyzerNavigationView.class).select(SocialSecurityAnalyzerJobController.Mode.QUICK);
                    case "close" -> ExhaustivePersistenceTest.invoke(session.dialog(), "close");
                    case "plan" -> restarted.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan());
                }
                return null;
            });
            repository.release.countDown();
            if (action.equals("tab") || action.equals("workspace")) await(session.persistence().notice, "Current Results");
            // Drain both repository-load and subsequent pure reconstruction work, then the FX attachment queue.
            for (int i = 0; i < 2; i++) restarted.getAnalysisResults().background(() -> null).toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
            fx(() -> {
                var resultTabs = field(session.dialog(), "socialSecurityTabs", TabPane.class);
                assertEquals(action.equals("tab") ? 2 : 0, resultTabs.getSelectionModel().getSelectedIndex());
                if (action.equals("workspace")) assertEquals(SocialSecurityAnalyzerJobController.Mode.QUICK,
                        field(session.dialog(), "navigation", AnalyzerNavigationView.class).selected().mode());
                if (action.equals("plan") || action.equals("close")) assertNull(field(session.dialog(), "presentation", SocialSecurityStrategyAnalyzerPresentation.class));
                // Closing the controller invalidates its generation without admitting financial work.
                assertEquals(action.equals("close") ? 1 : 0, session.jobs().generation());
                if (action.equals("close")) assertEquals(SocialSecurityAnalyzerJobController.State.CLOSED, session.jobs().state());
                return null;
            });
        } finally { repository.release.countDown(); SocialSecurityParameterPersistenceTest.stop(restarted); }
    }
    private static TabPane tabs(SocialSecurityParameterPersistenceTest.Rig rig, AnalysisKind kind) throws Exception {
        return switch (kind) {
            case COUPLE_SOCIAL_SECURITY -> field(rig.dialog, "socialSecurityTabs", TabPane.class);
            case COUPLE_EXHAUSTIVE -> field(rig.dialog, "deterministicResultTabs", TabPane.class);
            default -> field(rig.dialog, "weightedView", LongevityWeightedIntegratedView.class).resultTabs;
        };
    }
    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = {"COUPLE_SOCIAL_SECURITY", "COUPLE_EXHAUSTIVE", "COUPLE_WEIGHTED"})
    void eligibleSuccessDefaultsOnceAndCancelFailureObsoleteAndRejectedRunsDoNotNavigate(AnalysisKind kind) throws Exception {
        var source = helper().source(kind, new JsonRetirementPlanRepository());
        try (var rig = fx(() -> new SocialSecurityParameterPersistenceTest.Rig(source))) {
            fx(() -> { rig.select(kind); rig.run(kind); return null; }); rig.execute();
            fx(() -> {
                assertEquals(1, tabs(rig, kind).getSelectionModel().getSelectedIndex());
                tabs(rig, kind).getSelectionModel().selectFirst();
                return null;
            });
            long revision = source.getSourcePlanRevision();
            fx(() -> { rig.run(kind); rig.jobs.cancel(); return null; }); rig.execute();
            fx(() -> {
                assertEquals(0, tabs(rig, kind).getSelectionModel().getSelectedIndex());
                rig.fail(kind); rig.run(kind); return null;
            }); rig.execute();
            fx(() -> {
                assertEquals(0, tabs(rig, kind).getSelectionModel().getSelectedIndex());
                rig.run(kind); // Deliberately obsolete before the worker completes.
                rig.jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
                rig.run(kind); // Rejected while the previous lease is still held.
                return null;
            }); rig.execute();
            fx(() -> {
                assertEquals(0, tabs(rig, kind).getSelectionModel().getSelectedIndex());
                assertEquals(revision, source.getSourcePlanRevision());
                assertFalse(source.isModified());
                return null;
            });
        } finally { SocialSecurityParameterPersistenceTest.stop(source); }
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = {"COUPLE_SOCIAL_SECURITY", "COUPLE_EXHAUSTIVE", "COUPLE_WEIGHTED"})
    void manualTabOrWorkspaceChangeDuringAdmittedRunWins(AnalysisKind kind) throws Exception {
        var source = helper().source(kind, new JsonRetirementPlanRepository());
        try (var rig = fx(() -> new SocialSecurityParameterPersistenceTest.Rig(source))) {
            fx(() -> {
                rig.select(kind); rig.run(kind);
                tabs(rig, kind).getSelectionModel().selectLast();
                tabs(rig, kind).getSelectionModel().selectFirst();
                return null;
            }); rig.execute();
            fx(() -> {
                assertEquals(0, tabs(rig, kind).getSelectionModel().getSelectedIndex());
                rig.run(kind);
                field(rig.dialog, "navigation", AnalyzerNavigationView.class).select(SocialSecurityAnalyzerJobController.Mode.QUICK);
                return null;
            }); rig.execute();
            fx(() -> {
                assertEquals(SocialSecurityAnalyzerJobController.Mode.QUICK, field(rig.dialog, "navigation", AnalyzerNavigationView.class).selected().mode());
                assertEquals(0, tabs(rig, kind).getSelectionModel().getSelectedIndex());
                return null;
            });
        } finally { SocialSecurityParameterPersistenceTest.stop(source); }
    }

    @ParameterizedTest @CsvSource({"COUPLE_EXHAUSTIVE,false", "COUPLE_EXHAUSTIVE,true", "COUPLE_WEIGHTED,false", "COUPLE_WEIGHTED,true",
            "COUPLE_SOCIAL_SECURITY,false", "COUPLE_SOCIAL_SECURITY,true"})
    void restartDefaultsHistoricalResultsToClaimingView(AnalysisKind kind, boolean outdated) throws Exception {
        var source = helper().source(kind, new JsonRetirementPlanRepository());
        AcceptedAnalysisCompletion original = kind == AnalysisKind.COUPLE_EXHAUSTIVE
                ? ExhaustivePersistenceTest.completion(source) : kind == AnalysisKind.COUPLE_SOCIAL_SECURITY
                ? CoupleSocialSecurityPersistenceTest.completion(source) : coupleCompletion(source);
        AnalysisPayload payload = original.payload();
        if (payload instanceof ExhaustiveSnapshot p) {
            var selected = p.selection();
            payload = new ExhaustiveSnapshot(p.baseline(), p.rankingMeasure(), p.entries(), p.rankedOrders(), p.currentPlanRank(),
                    p.runtime(), p.displayedGroupLimit(), p.displayedGroups(), p.report(), p.socialSecurityReference(), p.completedAt(),
                    new ExhaustiveSnapshot.Selection(selected.occurrence(), selected.strategy(), selected.metric(), "ranked"));
        }
        var c = original.context(); var prefs = new HashMap<>(c.preferences());
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY) prefs.put("tab", "ranked");
        var context = new SnapshotContext(c.household(), c.primary(), c.spouse(), c.startedAt(), c.assumptions(), c.request(), c.comparisons(), prefs,
                c.baseline(), c.historicalComparisons());
        var completion = new AcceptedAnalysisCompletion(original.planId(), original.planFile(), kind, original.runId(), original.completedAt(),
                original.inputFingerprint(), original.contextFingerprint(), original.algorithms(), original.references(), payload, context);
        source.getAnalysisResults().accept(completion); settle(source.getAnalysisResults(), original.planId(), kind);
        var file = source.getCurrentFile(); SocialSecurityParameterPersistenceTest.stop(source);
        var restarted = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("reopened.settings")), new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
        fx(() -> { restarted.open(file); return null; });
        // A financial edit, rather than a renamed display context, establishes Outdated below.
        if (outdated) fx(() -> {
            if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY) {
                var primary = restarted.getCurrentPlan().getHousehold().getPrimaryPerson();
                primary.setBirthDate(primary.getBirthDate().plusDays(1));
            } else restarted.getCurrentPlan().getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Synthetic", new java.math.BigDecimal("123")));
            restarted.markModified(); return null;
        });
        try (var session = kind == AnalysisKind.COUPLE_EXHAUSTIVE ? ExhaustivePersistenceTest.session(restarted) : null;
             var weighted = kind == AnalysisKind.COUPLE_WEIGHTED ? coupleView(restarted) : null;
             var socialSecurity = kind == AnalysisKind.COUPLE_SOCIAL_SECURITY ? CoupleSocialSecurityPersistenceTest.session(restarted) : null) {
            var persistence = session != null ? session.persistence() : weighted != null ? weighted.persistence() : socialSecurity.persistence();
            var dialog = session != null ? session.dialog() : weighted != null ? weighted.dialog() : socialSecurity.dialog();
            await(persistence.notice, outdated ? "Outdated" : "Current Results");
            fx(() -> {
                var resultTabs = kind == AnalysisKind.COUPLE_SOCIAL_SECURITY ? field(dialog, "socialSecurityTabs", TabPane.class)
                        : kind == AnalysisKind.COUPLE_EXHAUSTIVE ? field(dialog, "deterministicResultTabs", TabPane.class)
                        : field(dialog, "weightedView", LongevityWeightedIntegratedView.class).resultTabs;
                assertEquals(1, resultTabs.getSelectionModel().getSelectedIndex());
                resultTabs.getSelectionModel().selectFirst();
                persistence.load(); persistence.reassess();
                assertEquals(0, resultTabs.getSelectionModel().getSelectedIndex());
                assertEquals(0, field(dialog, "jobs", SocialSecurityAnalyzerJobController.class).generation());
                return null;
            });
        } finally { SocialSecurityParameterPersistenceTest.stop(restarted); }
    }
}
