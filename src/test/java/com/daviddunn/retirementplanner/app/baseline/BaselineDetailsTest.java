package com.daviddunn.retirementplanner.app.baseline;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineParameterComparison.*;

public class BaselineDetailsTest {
    @TempDir Path dir;
    public static RetirementPlan plan(boolean couple) {
        var primary = new Person("Primary", "Fixture", LocalDate.of(1960, 1, 1));
        primary.addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.PRIMARY, LocalDate.of(2027, 1, 1), null, new BigDecimal("2000.00100"), 67, BigDecimal.ZERO, 2026));
        var spouse = couple ? new Person("Spouse", "Fixture", LocalDate.of(1962, 1, 1)) : null;
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount("Account", AccountOwnership.PRIMARY, new BigDecimal("1000000.0012300")));
        var household = new Household(primary, spouse);
        household.addExpense(new Expense("Spending", new BigDecimal("30000.00")));
        return new RetirementPlan(household, portfolio, new PlanningAssumptions(new BigDecimal("0.03"), new BigDecimal("0.0250"), 3, LocalDate.of(2026, 1, 1)));
    }
    public static void set(Object object, String field, Object value) throws Exception {
        var f = object.getClass().getDeclaredField(field); f.setAccessible(true); f.set(object, value);
    }
    public static <T> T get(Object object, String field, Class<T> type) throws Exception {
        var f = object.getClass().getDeclaredField(field); f.setAccessible(true); return type.cast(f.get(object));
    }
    ApplicationController controller() throws Exception {
        return new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("settings")), new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
    }
    void stop(ApplicationController c) { c.getAnalysisParameters().shutdown(); c.getAnalysisResults().shutdownIfIdle(); }
    static ProjectionInputs inputs(String key, Value value) { return new ProjectionInputs(new Fields(Map.of(key, value))); }
    static Fields account(String name, String owner, String amount) {
        return new Fields(Map.of("name", new TextValue(name), "ownership", new TextValue(owner), "accountType", new TextValue("traditional"), "currentBalance", new NumberValue(new BigDecimal(amount))));
    }
    @Test void exactScalesNumericDatesBooleansMissingAndMutationIsolation() {
        var p = plan(true); var original = ProjectionInputs.capture(p);
        p.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1000001.0023400"));
        var rows = compare(original, ProjectionInputs.capture(p));
        var row = rows.stream().filter(r -> r.key().endsWith("currentBalance") && r.state() == State.CHANGED).findFirst().orElseThrow();
        assertEquals(new BigDecimal("0.0011100").add(BigDecimal.ONE), row.difference());
        assertEquals(new BigDecimal("1000000.0012300"), ((NumberValue)row.baseline()).value());
        assertThrows(UnsupportedOperationException.class, () -> original.root().values().clear());
        assertTrue(compare(original, null).stream().allMatch(r -> r.state() == State.UNKNOWN && r.difference() == null));
        assertTrue(compare(inputs("rate", new NumberValue(new BigDecimal("0.0250"))), inputs("rate", new NumberValue(new BigDecimal("0.02500")))).stream().allMatch(r -> r.state() == State.UNCHANGED));
        var date = compare(inputs("startDate", new TextValue("2026-01-01")), inputs("startDate", new TextValue("2026-01-03"))).getFirst();
        assertEquals(new BigDecimal("2"), date.difference()); assertEquals(Units.DAYS, date.units());
        assertEquals(State.CHANGED, compare(inputs("enabled", new BooleanValue(false)), inputs("enabled", new BooleanValue(true))).getFirst().state());
        var optional = compare(inputs("rothConversionRequest", Empty.NULL),
                inputs("rothConversionRequest", new Fields(Map.of("enabled", new BooleanValue(true))))).getFirst();
        assertEquals(Empty.NULL, optional.baseline());
        assertEquals(State.CHANGED, optional.state(), "Recorded null must not become unrecorded history");
        assertNull(optional.difference());
        assertEquals(State.CHANGED, compare(inputs("election", new TextValue("PRIMARY_DIES")), inputs("election", new TextValue("BOTH_SURVIVE"))).getFirst().state());
    }
    @Test void duplicateNamesDoNotEstablishCorrespondenceAndCertainPresenceIsDistinctFromMissingHistory() {
        var a = inputs("accounts", new Items(List.of(account("Duplicate", "PRIMARY", "100"), account("Duplicate", "PRIMARY", "200"))));
        var b = inputs("accounts", new Items(List.of(account("Duplicate", "PRIMARY", "110"), account("Duplicate", "PRIMARY", "210"))));
        assertTrue(compare(a, b).stream().allMatch(r -> r.state() == State.UNKNOWN && r.difference() == null));
        var one = inputs("accounts", new Items(List.of(account("Renamed", "SPOUSE", "150"))));
        var old = inputs("accounts", new Items(List.of(account("Old", "SPOUSE", "100"))));
        assertTrue(compare(old, one).stream().anyMatch(r -> r.difference() != null && r.difference().equals(new BigDecimal("50")) && r.confidence() == Confidence.UNIQUE_STRUCTURE));
        assertTrue(compare(inputs("accounts", new Items(List.of())), one).stream().allMatch(r -> r.state() == State.ADDED && r.baseline() == Empty.ABSENT));
        assertTrue(compare(one, inputs("accounts", new Items(List.of()))).stream().allMatch(r -> r.state() == State.REMOVED));
        assertTrue(compare(null, one).stream().allMatch(r -> r.state() == State.UNKNOWN));
        var reordered = inputs("accounts", new Items(List.of(account("Duplicate", "PRIMARY", "200"), account("Duplicate", "PRIMARY", "100"))));
        assertTrue(compare(a, reordered).stream().allMatch(r -> r.state() == State.UNCHANGED));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptedInputsAndCachedChartNeverReadLaterEditsAndMissingChartStillAllowsComparison(boolean couple) throws Exception {
        var c = controller(); c.newPlan(plan(couple));
        try {
            c.getCurrentProjection(); c.saveCurrentAsBaseline("Original");
            c.compareProjections(); var same = c.peekCurrentProjection();
            assertFalse(c.canViewBaselineDetails(same));
            c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1000100.0012300"));
            c.markModified();
            assertFalse(c.canViewBaselineDetails(same), "An edit alone cannot change prepared comparison eligibility");
            c.invalidateProjection(); c.compareProjections(); var accepted = c.peekCurrentProjection();
            var session = c.prepareBaselineDetails(accepted); assertNotNull(session); assertNotNull(session.baselineChart());
            var originalBaseline = c.getBaselineProjection();
            for (int i = 0; i < originalBaseline.getYears().size(); i++) assertEquals(originalBaseline.getYearAt(i).getEndingInvestableAssets(), session.baselineChart().years().get(i).values().get(com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric.INVESTABLE_ASSETS));
            c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("7777777")); c.markModified();
            assertEquals(session.rows(), c.prepareBaselineDetails(accepted).rows());
            set(c, "baselineProjection", null); set(c, "baselineDetailsChart", null);
            set(c, "projectionEngine", new ProjectionEngine() {
                @Override public Projection project(RetirementPlan p) { throw new AssertionError("No calculation on open"); }
                @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw new AssertionError("No calculation on open"); }
            });
            assertTrue(c.canViewBaselineDetails(accepted)); assertNull(c.prepareBaselineDetails(accepted).baselineChart());
            assertEquals(session.rows(), c.prepareBaselineDetails(accepted).rows());
            set(c, "currentDetailsContext", null);
            assertTrue(c.prepareBaselineDetails(accepted).rows().stream().allMatch(r -> r.state() == State.UNKNOWN));
            c.newPlan(plan(!couple)); assertNull(c.prepareBaselineDetails(accepted));
        } finally { stop(c); }
    }
    @Test void saveAsAndRestartPreserveBaselineAndAcceptedCurrentContext() throws Exception {
        var c = controller(); c.newPlan(plan(false));
        try {
            c.getCurrentProjection(); c.saveCurrentAsBaseline("Saved");
            c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1200000"));
            c.invalidateProjection();
            c.compareProjections(); var session = c.prepareBaselineDetails(c.peekCurrentProjection());
            c.saveAs(dir.resolve("one.json")); String original = c.getCurrentPlan().getPlanId();
            c.saveAs(dir.resolve("two.json")); assertNotEquals(original, c.getCurrentPlan().getPlanId());
            assertEquals(session.rows(), c.prepareBaselineDetails(c.peekCurrentProjection()).rows());
            String json = java.nio.file.Files.readString(dir.resolve("two.json")); assertFalse(json.contains("currentDetailsContext"));
            c.open(dir.resolve("one.json")); c.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertNotNull(c.peekCurrentProjection());
            c.compareProjections(); assertNotNull(c.prepareBaselineDetails(c.peekCurrentProjection()));
            assertEquals("Saved", c.getCurrentPlan().getBaseline().getDescription());
        } finally { stop(c); }
    }
    @Test void obsoleteAndPlanSwitchedExecutionCannotPublishContextAndCancellationDoesNotInventOne() throws Exception {
        var c = controller(); c.newPlan(plan(false));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var ready = new ProjectionEngine().projectWithOutcome(c.getCurrentPlan());
        set(c, "projectionEngine", new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) {
                entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
                return ready;
            }
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var old = executor.submit(c::getCurrentProjection); assertTrue(entered.await(5, TimeUnit.SECONDS));
            c.invalidateProjection();
            set(c, "projectionEngine", new ProjectionEngine());
            var newer = c.getCurrentProjection(); var context = get(c, "currentDetailsContext", BaselineDetailsSession.Accepted.class);
            release.countDown(); old.get(5, TimeUnit.SECONDS);
            assertSame(newer, c.peekCurrentProjection()); assertSame(context, get(c, "currentDetailsContext", BaselineDetailsSession.Accepted.class));
            c.invalidateProjection();
            set(c, "projectionEngine", new ProjectionEngine() {
                @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { c.newPlan(plan(true)); return ready; }
            });
            assertNull(c.getCurrentProjection()); assertNull(get(c, "currentDetailsContext", BaselineDetailsSession.Accepted.class));
            for (RuntimeException failure : List.of(new CancellationException(), new IllegalStateException("Failed"))) {
                set(c, "projectionEngine", new ProjectionEngine() { @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw failure; } });
                assertThrows(failure.getClass(), c::getCurrentProjection); assertNull(get(c, "currentDetailsContext", BaselineDetailsSession.Accepted.class));
            }
        } finally { release.countDown(); stop(c); }
    }
}
