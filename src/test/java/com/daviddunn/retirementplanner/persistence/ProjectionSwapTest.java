package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set;

class ProjectionSwapTest {
    @TempDir Path dir;
    final List<ApplicationController> controllers = new ArrayList<>();
    ApplicationController controller(JsonRetirementPlanRepository repo) throws Exception {
        var c = new ApplicationController(repo, new JsonApplicationSettingsRepository(dir.resolve("settings-" + controllers.size())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("slots-" + controllers.size()))));
        controllers.add(c); return c;
    }
    ApplicationController pair(boolean couple) throws Exception {
        var c = controller(new JsonRetirementPlanRepository()); c.newPlan(BaselineDetailsTest.plan(couple));
        c.getCurrentProjection(); c.saveCurrentAsBaseline("Original baseline");
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1200000.1234500"));
        c.markModified(); c.invalidateProjection(); c.getCurrentProjection();
        c.saveAs(dir.resolve("plan-" + controllers.size() + ".json"));
        c.getBaselineProjection(); return c;
    }
    static ProjectionEngine throwing() { return new ProjectionEngine() {
        @Override public Projection project(RetirementPlan p) { throw new AssertionError("Swap cannot project"); }
        @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw new AssertionError("Swap cannot project"); }
    }; }
    static void noCalculations(ApplicationController c) throws Exception {
        set(c, "projectionEngine", throwing());
        set(c, "breakEvenAnalyzerFactory", (java.util.function.Supplier<BreakEvenAnalyzer>) () -> { throw new AssertionError("Swap cannot analyze Break-Even"); });
    }
    static void swap(ApplicationController c) throws Exception {
        assertEquals("", c.projectionSwapUnavailableReason());
        var prepared = c.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(c.commitProjectionSwap(prepared));
    }
    @AfterEach void close() throws Exception {
        for (var c : controllers) {
            c.getPlanPreferenceWrite().toCompletableFuture().get(15, TimeUnit.SECONDS);
            c.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
            c.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete()); c.getAnalysisParameters().shutdown();
            assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit()); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void exactDoubleSwapRestoresInputsResultsAllThirteenChartsAndAnnotations(boolean couple) throws Exception {
        var c = pair(couple); var original = c.getCurrentPlan().getProjectionState();
        var current = c.getAcceptedProjectionSnapshot(); var baseline = c.getCurrentPlan().getProjectionComponents().get(original.baseline().id());
        var comparison = c.compareProjections(); var beforeBreakEven = c.requestBreakEvenAnalysis(); assertNotNull(beforeBreakEven);
        noCalculations(c); swap(c);
        assertEquals(baseline.id(), c.getAcceptedProjectionSnapshot().id());
        assertSame(baseline,c.getAcceptedProjectionSnapshot(),"Verified immutable component is reused rather than duplicated");
        assertEquals(current.id(), c.getCurrentPlan().getProjectionState().baseline().id());
        assertEquals(baseline.inputsDocument(), CompleteProjectionSnapshot.freeze(c.getCurrentPlan()));
        assertEquals(baseline.summary(), CompleteProjectionSnapshot.Summary.capture(c.getCurrentProjectionSummary()));
        assertEquals(baseline.annual(), RetainedProjectionSnapshot.capture(c.peekCurrentProjection()));
        assertEquals(current.annual(), RetainedProjectionSnapshot.capture(c.getBaselineProjection()));
        var chart = c.getAcceptedProjectionChart(c.peekCurrentProjection()); assertEquals(baseline.chart(), chart);
        assertEquals(13, ProjectionChartMetric.values().length);
        for (var point : chart.years()) assertEquals(13, point.values().size());
        assertEquals(baseline.chart().claims(), chart.claims()); assertEquals(baseline.chart().rmdPeriods(), chart.rmdPeriods()); assertEquals(baseline.chart().rothPeriods(), chart.rothPeriods());
        assertNull(c.getCachedBreakEvenAnalysis());
        assertTrue(c.currentProjectionInputsMatch()); assertTrue(c.isModified());
        var reversed = c.compareProjections();
        assertEquals(comparison.getEndingInvestableAssetsChange().negate(), reversed.getEndingInvestableAssetsChange());
        // Ordinary Save is required before the next dirty-state swap.
        c.save(); assertTrue(c.getProjectionSnapshotNotice().contains("roles saved")); swap(c);
        assertEquals(original, c.getCurrentPlan().getProjectionState());
        assertEquals(current, c.getAcceptedProjectionSnapshot());
        assertEquals(current.inputsDocument(), CompleteProjectionSnapshot.freeze(c.getCurrentPlan()));
        assertEquals(current.annual(), RetainedProjectionSnapshot.capture(c.peekCurrentProjection()));
        assertEquals(current.chart(), c.getAcceptedProjectionChart(c.peekCurrentProjection()));
        assertEquals(current.summary(), CompleteProjectionSnapshot.Summary.capture(c.getCurrentProjectionSummary()));
    }
    @Test void differentHorizonsKeepOwnTotalsEndYearsAndExplicitSharedYearBreakEven() throws Exception {
        var c = pair(true); var assumptions = c.getCurrentPlan().getPlanningAssumptions();
        set(assumptions, "projectionLengthYears", 5); c.markModified(); c.invalidateProjection(); c.getCurrentProjection(); c.save();
        var before = c.compareProjections(); int currentEnd = c.peekCurrentProjection().getEndYear(), baselineEnd = c.getBaselineProjection().getEndYear();
        assertNotEquals(currentEnd, baselineEnd); var oldBreakEven = c.requestBreakEvenAnalysis();
        noCalculations(c); swap(c); var after = c.compareProjections();
        assertEquals(baselineEnd, c.peekCurrentProjection().getEndYear()); assertEquals(currentEnd, c.getBaselineProjection().getEndYear());
        assertEquals(before.getBaselineTotalIncome(), after.getCurrentTotalIncome());
        assertEquals(before.getCurrentTotalIncome(), after.getBaselineTotalIncome());
        assertEquals(before.getBaselineEndingInvestableAssets(), after.getCurrentEndingInvestableAssets());
        assertEquals(before.getCurrentEndingInvestableAssets(), after.getBaselineEndingInvestableAssets());
        assertNull(c.getCachedBreakEvenAnalysis());
        set(c, "breakEvenAnalyzerFactory", (java.util.function.Supplier<BreakEvenAnalyzer>) BreakEvenAnalyzer::new);
        var explicit = c.requestBreakEvenAnalysis();
        assertEquals(oldBreakEven.comparableYearCount(), explicit.comparableYearCount());
        for (var metric : BreakEvenMetric.values()) {
            var a = oldBreakEven.metrics().get(metric).years(); var b = explicit.metrics().get(metric).years();
            for (int i=0;i<a.size();i++) assertEquals(a.get(i).difference().negate(), b.get(i).difference());
        }
    }
    @Test void dirtySaveContinueAndSaveAsRestartPreserveRolesAndIndependentOwnership() throws Exception {
        var c = pair(false); c.markModified();
        assertThrows(ExecutionException.class, () -> c.prepareProjectionSwap().toCompletableFuture().get());
        var receipt = c.saveBeforeProjectionSwap().toCompletableFuture().get(); c.finishProjectionSwapSave(receipt); swap(c);
        var roles = c.getCurrentPlan().getProjectionState(); var originalFile = c.getCurrentFile(); c.save();
        var durable = Files.readAllBytes(originalFile);
        var reloaded = controller(new JsonRetirementPlanRepository()); noCalculations(reloaded); reloaded.open(originalFile);
        reloaded.getProjectionRestoration().toCompletableFuture().get();
        assertEquals(roles, reloaded.getCurrentPlan().getProjectionState()); assertTrue(reloaded.currentProjectionInputsMatch());
        var savedCurrent = reloaded.getAcceptedProjectionSnapshot();
        reloaded.saveAs(dir.resolve("clone.json")); reloaded.getSnapshotCopyCompletion().toCompletableFuture().get();
        assertNotEquals(roles.owner(), reloaded.getCurrentPlan().getProjectionState().owner());
        assertEquals(roles.current().id(), reloaded.getCurrentPlan().getProjectionState().current().id());
        assertEquals(savedCurrent.annual(), reloaded.getAcceptedProjectionSnapshot().annual());
        assertArrayEquals(durable, Files.readAllBytes(originalFile));
    }
    @Test void noOpCancelStaleAndWrongPlanNeverMutate() throws Exception {
        var c = pair(false); var state = c.getCurrentPlan().getProjectionState();
        var prepared = c.prepareProjectionSwap().toCompletableFuture().get(); c.cancelProjectionSwap();
        var canceled = prepared;
        assertThrows(IllegalStateException.class, () -> c.commitProjectionSwap(canceled)); assertEquals(state,c.getCurrentPlan().getProjectionState());
        prepared = c.prepareProjectionSwap().toCompletableFuture().get(); var late = prepared;
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("7"));
        assertThrows(IllegalStateException.class, () -> c.commitProjectionSwap(late)); assertEquals(state,c.getCurrentPlan().getProjectionState());
        assertTrue(c.projectionSwapUnavailableReason().contains("matching"));
        c.newPlan(BaselineDetailsTest.plan(false)); c.getCurrentProjection(); c.saveCurrentAsBaseline("same"); c.saveAs(dir.resolve("same.json"));
        var same = c.getCurrentPlan(); var noOp = c.prepareProjectionSwap().toCompletableFuture().get();
        assertFalse(c.commitProjectionSwap(noOp)); assertSame(same,c.getCurrentPlan()); assertFalse(c.isModified());
        var old = c.prepareProjectionSwap().toCompletableFuture().get(); c.newPlan(BaselineDetailsTest.plan(true));
        assertThrows(IllegalStateException.class, () -> c.commitProjectionSwap(old)); assertTrue(c.getCurrentPlan().getHousehold().hasSpouse());
    }
    @Test void baselineReplacementOrNewProjectionInvalidatesPreparedTransaction() throws Exception {
        var c = pair(true); var a = c.prepareProjectionSwap().toCompletableFuture().get();
        var first = a;
        c.saveCurrentAsBaseline("replacement"); assertThrows(IllegalStateException.class, () -> c.commitProjectionSwap(first));
        c.save(); a = c.prepareProjectionSwap().toCompletableFuture().get(); var old = a;
        c.invalidateProjection(); c.getCurrentProjection();
        assertThrows(IllegalStateException.class, () -> c.commitProjectionSwap(old));
    }
    @Test void corruptMissingAndLegacySnapshotsRefuseWithoutMutation() throws Exception {
        var c = pair(true); var plan = c.getCurrentPlan(); var state = plan.getProjectionState();
        Path component = c.getCurrentFile().getParent().resolve(".retirement-planner-projections").resolve(state.owner().toString()).resolve(state.baseline().id()+".json.gz");
        Files.write(component, new byte[]{1,2,3});
        assertThrows(ExecutionException.class, () -> c.prepareProjectionSwap().toCompletableFuture().get()); assertSame(plan,c.getCurrentPlan()); assertEquals(state,plan.getProjectionState());
        Files.delete(component); // Test-owned temp artifact only; simulates externally missing component.
        assertThrows(ExecutionException.class, () -> c.prepareProjectionSwap().toCompletableFuture().get());
        plan.setProjectionState(new ProjectionRoleManifest(1,state.owner(),state.current(),null));
        assertTrue(c.projectionSwapUnavailableReason().contains("Legacy"));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionRoleManifest(3,state.owner(),state.current(),state.baseline()));
    }
    @Test void ordinarySaveFailureAndSwappedManifestFailureLeaveDurableRolesIntact() throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var repo = new JsonRetirementPlanRepository(new AtomicFileWriter((stage,path) -> {
            if (fail.get() && stage == AtomicFileWriter.Stage.REPLACE) throw new java.io.IOException("test injected replacement failure");
        }), new com.fasterxml.jackson.databind.ObjectMapper());
        var c = controller(repo); c.newPlan(BaselineDetailsTest.plan(true)); c.getCurrentProjection(); c.saveCurrentAsBaseline("baseline");
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1500000.0000")); c.invalidateProjection(); c.getCurrentProjection(); c.saveAs(dir.resolve("failure.json"));
        var original = c.getCurrentPlan().getProjectionState(); var bytes = Files.readAllBytes(c.getCurrentFile()); c.markModified(); fail.set(true);
        assertThrows(ExecutionException.class, () -> c.saveBeforeProjectionSwap().toCompletableFuture().get());
        assertEquals(original,c.getCurrentPlan().getProjectionState()); assertArrayEquals(bytes,Files.readAllBytes(c.getCurrentFile())); assertTrue(c.isModified());
        fail.set(false); c.finishProjectionSwapSave(c.saveBeforeProjectionSwap().toCompletableFuture().get()); swap(c); fail.set(true);
        assertThrows(java.io.IOException.class,c::save); assertTrue(c.isModified()); assertArrayEquals(bytes,Files.readAllBytes(c.getCurrentFile()));
        fail.set(false); c.save(); assertFalse(c.isModified()); assertNotEquals(original,c.getCurrentPlan().getProjectionState());
    }
    @Test void queuedPreparationRejectsNewProjectionAndOldCompletionCannotCancelNewerSwap() throws Exception {
        var c=pair(true); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var blocker=c.getAnalysisResults().background(() -> {entered.countDown(); assertTrue(release.await(10,TimeUnit.SECONDS)); return true;});
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        var old=c.prepareProjectionSwap();
        assertThrows(ExecutionException.class,()->c.prepareProjectionSwap().toCompletableFuture().get());
        c.invalidateProjection(); c.getCurrentProjection();
        release.countDown(); blocker.toCompletableFuture().get();
        var obsolete=old.toCompletableFuture().get();
        var newer=c.prepareProjectionSwap().toCompletableFuture().get();
        assertThrows(IllegalStateException.class,()->c.commitProjectionSwap(obsolete));
        assertTrue(c.commitProjectionSwap(newer));
    }
    @Test void clearingBaselineDuringWorkerPreparationRejectsCommit() throws Exception {
        var c=pair(false); var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var blocker=c.getAnalysisResults().background(()->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return true;});
        assertTrue(entered.await(5,TimeUnit.SECONDS));var pending=c.prepareProjectionSwap();
        var current=c.getAcceptedProjectionSnapshot();c.getCurrentPlan().setBaseline(null);
        release.countDown();blocker.toCompletableFuture().get();var prepared=pending.toCompletableFuture().get();
        assertThrows(IllegalStateException.class,()->c.commitProjectionSwap(prepared));
        assertEquals(current,c.getAcceptedProjectionSnapshot());assertNull(c.getCurrentPlan().getBaseline());
    }
    @Test void planSwitchDuringOrdinarySaveNeverPublishesIntoNewPlan() throws Exception {
        var block=new java.util.concurrent.atomic.AtomicBoolean();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var repo=new JsonRetirementPlanRepository(new AtomicFileWriter((stage,path)->{
            if(block.get()&&stage==AtomicFileWriter.Stage.VERIFY){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new java.io.IOException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}}
        }),new com.fasterxml.jackson.databind.ObjectMapper());
        var c=controller(repo);c.newPlan(BaselineDetailsTest.plan(true));c.getCurrentProjection();c.saveCurrentAsBaseline("source");c.saveAs(dir.resolve("source.json"));c.markModified();
        var originalOwner=c.getAnalysisSessionId();block.set(true);var saving=c.saveBeforeProjectionSwap();assertTrue(entered.await(5,TimeUnit.SECONDS));
        c.newPlan(BaselineDetailsTest.plan(false));var next=c.getCurrentPlan();release.countDown();var receipt=saving.toCompletableFuture().get();
        assertThrows(IllegalStateException.class,()->c.finishProjectionSwapSave(receipt));
        assertSame(next,c.getCurrentPlan());assertFalse(c.isModified());assertNull(c.getCurrentFile());assertNotEquals(originalOwner,c.getAnalysisSessionId());
    }
    @Test void allFifteenAcceptedConfigurationsAndFrozenFingerprintsSurviveRoleExchange() throws Exception {
        var c=pair(true);var configurations=com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfigurations.empty();
        for(var kind:AnalysisKind.values()) configurations=configurations.with(com.daviddunn.retirementplanner.app.analysis.parameters.ParameterFixtures.configuration(kind));
        c.getCurrentPlan().setAnalyzerConfigurations(configurations);c.save();var json=configurations.json();
        var settings=com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings.defaults(java.time.LocalDate.of(2031,2,3));
        c.setLongevitySessionSettings(settings);
        var current=c.getAcceptedProjectionSnapshot();swap(c);
        assertSame(settings,c.getLongevitySessionSettings(),"A role change is not a different plan-owned longevity session");
        assertEquals(json,c.getCurrentPlan().getAnalyzerConfigurations().json());
        assertEquals(current.inputDigest(),c.getCurrentPlan().getProjectionComponents().get(current.id()).inputDigest());
        c.save();var saved=new JsonRetirementPlanRepository().load(c.getCurrentFile());assertEquals(json,saved.getAnalyzerConfigurations().json());
    }
    @Test void actualRmdConversionAndClaimAnnotationsStayWithOriginalAnnualValues() throws Exception {
        var c=controller(new JsonRetirementPlanRepository());var plan=BaselineDetailsTest.plan(true);
        set(plan.getPlanningAssumptions(),"projectionLengthYears",20);
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA",AccountOwnership.PRIMARY,new BigDecimal("1500000.00000")));
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA("Roth",AccountOwnership.PRIMARY,new BigDecimal("200000.00100")));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true,2027,new BigDecimal("20000.00100"),
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        c.newPlan(plan);c.getCurrentProjection();var original=c.getAcceptedProjectionSnapshot();
        assertFalse(original.chart().claims().isEmpty());assertFalse(original.chart().rmdPeriods().isEmpty());assertFalse(original.chart().rothPeriods().isEmpty());
        c.saveCurrentAsBaseline("With annotations");plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1400000.0012300"));
        c.invalidateProjection();c.getCurrentProjection();c.saveAs(dir.resolve("annotations.json"));noCalculations(c);swap(c);
        assertEquals(original.chart(),c.getAcceptedProjectionChart(c.peekCurrentProjection()));
        assertEquals(original.annual(),RetainedProjectionSnapshot.capture(c.peekCurrentProjection()));
        var accounts=c.getCurrentPlan().getAccountPortfolio().getAccounts();assertEquals(3,accounts.size());
        assertEquals(AccountOwnership.PRIMARY,accounts.get(1).getOwnership());
        assertEquals(new BigDecimal("1500000.00000"),accounts.get(1).getCurrentBalance());
    }
    @Test void acceptedAssociationDuringOrdinarySaveIsNeverDiscardedBySaveFinalization() throws Exception {
        var block=new java.util.concurrent.atomic.AtomicBoolean();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var repo=new JsonRetirementPlanRepository(new AtomicFileWriter((stage,path)->{
            if(block.get()&&stage==AtomicFileWriter.Stage.VERIFY){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new java.io.IOException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}}
        }),new com.fasterxml.jackson.databind.ObjectMapper());
        var c=controller(repo);c.newPlan(BaselineDetailsTest.plan(true));c.getCurrentProjection();c.saveCurrentAsBaseline("original");c.saveAs(dir.resolve("analysis-during-save.json"));c.markModified();
        block.set(true);var saving=c.saveBeforeProjectionSwap();assertTrue(entered.await(5,TimeUnit.SECONDS));
        var token=c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_WEIGHTED,"a".repeat(64));
        var completion=new AcceptedAnalysisCompletion(c.getAnalysisSessionId(),c.getCurrentFile(),token.kind(),token.ticket(),java.time.Instant.now(),
                "a".repeat(64),"b".repeat(64),AnalysisVersions.algorithms(token.kind()),AnalysisVersions.references(token.kind()),
                com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures.couple(),com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures.context());
        assertTrue(c.associateAnalyzer(token,completion));var association=c.getAnalyzerAssociations(token.projectionId()).getFirst();
        release.countDown();var receipt=saving.toCompletableFuture().get();
        assertThrows(IllegalStateException.class,()->c.finishProjectionSwapSave(receipt));
        assertEquals(association,c.getAnalyzerAssociations(token.projectionId()).getFirst());
        assertTrue(c.getCurrentPlan().getPendingAnalyzerAssociations().containsKey(token.ticket()));assertTrue(c.isModified());
        var durable=repo.load(c.getCurrentFile());assertTrue(durable.getProjectionState().associations().isEmpty(),"Captured Save commits a coherent earlier document");
        block.set(false);c.save();assertEquals(token.ticket(),c.getAnalyzerAssociations(token.projectionId()).getFirst().runId());
        assertTrue(c.getCurrentPlan().getPendingAnalyzerAssociations().isEmpty());
    }
    @Test void changedPlanUuidDuringPreparationCannotPromoteWrongOwner() throws Exception {
        var c=pair(true);var owner=c.getCurrentPlan();var roles=owner.getProjectionState();
        var prepared=c.prepareProjectionSwap().toCompletableFuture().get();owner.assignPlanId(UUID.randomUUID());
        assertThrows(IllegalStateException.class,()->c.commitProjectionSwap(prepared));
        assertSame(owner,c.getCurrentPlan());assertEquals(roles,owner.getProjectionState());
    }
}
