package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StochasticExpenseMonteCarloTest {
    static final AnalysisProgressListener PROGRESS = AnalysisProgressListener.none();
    static final AnalysisCancellationToken NEVER = AnalysisCancellationToken.none();
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    static RetirementPlan plan(boolean couple) {
        var plan = couple ? MonteCarloMortalityExecutionTest.plan() : SinglePersonMortalityAnalysisTest.plan();
        for (var expense : List.copyOf(plan.getHousehold().getExpenses())) plan.getHousehold().removeExpense(expense);
        plan.getHousehold().addExpense(new Expense("Living", new BigDecimal("10000.0000"), GrowthCategory.GENERAL));
        plan.getHousehold().addExpense(new Expense("Health", new BigDecimal("1000.00"), GrowthCategory.HEALTHCARE));
        plan.setRothConversionRequest(null);
        return plan;
    }

    static InternalExpenseRun configuration(RetirementPlan plan, String volatility) {
        return InternalExpenseRun.capture(plan, Collections.nCopies(plan.getHousehold().getExpenses().size(), new BigDecimal(volatility)));
    }

    static MonteCarloMortalityRequest mortality(RetirementPlan plan, MonteCarloSettings settings) {
        return plan.getHousehold().hasSpouse() ? MonteCarloWorldGeneratorTest.request(plan, settings)
                : MonteCarloMortalityRequest.individual(plan, settings, SocialSecurityMortalityAdjustment.standard());
    }

    static MonteCarloStrategyComparisonRequest comparison(RetirementPlan a, RetirementPlan b,
            MonteCarloSettings settings, boolean longevity, int last) {
        MonteCarloStrategyComparisonRequest.Assumptions assumptions = longevity
                ? new MonteCarloStrategyComparisonRequest.Longevity(mortality(a, settings))
                : new MonteCarloStrategyComparisonRequest.Fixed(settings, a.getPlanningAssumptions().getProjectionStartDate(), last,
                a.getHousehold().hasSpouse() ? MonteCarloLifetime.couple(HouseholdLifetimeScenario.bothSurvive())
                        : new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty())));
        return new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("A", a), new MonteCarloStrategyCandidate("B", b), assumptions);
    }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void standaloneBothHouseholdsAndHorizonsMatchDirectFullEngineOracles(boolean couple, boolean longevity) throws Exception {
        var plan = plan(couple);
        var settings = MonteCarloSettings.forPlan(plan, 4, 417, new BigDecimal(".12"))
                .withInflation(MonteCarloInflationTest.inflation(".0175"));
        var expenses = configuration(plan, ".5");
        String original = JSON.writeValueAsString(plan);
        int first = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        var analyzer = new MonteCarloAnalyzer();
        if (!longevity) {
            var actual = analyzer.analyzeInternalExpenses(plan, settings, expenses, PROGRESS, NEVER);
            var repeated = analyzer.analyzeInternalExpenses(plan, settings, expenses, PROGRESS, NEVER);
            assertEquals(actual.outcomes(), repeated.outcomes()); assertEquals(actual.annual(), repeated.annual());
            int last = first + plan.getPlanningAssumptions().getProjectionLengthYears() - 1;
            for (int index = settings.simulationCount() - 1; index >= 0; index--) {
                var economic = new MonteCarloScenarioGenerator().generate(first, last, settings, index);
                var context = ProjectionEvaluationContext.empty().withInflationPath(new MonteCarloInflationGenerator()
                        .generate(first, last, settings, index).orElseThrow());
                context = expenses.apply(plan, context, settings.seed(), index, first, last);
                var result = new ProjectionEngine().projectWithOutcome(plan, context, economic);
                var observed = actual.outcomes().get(index);
                if (result instanceof ProjectionExecutionResult.Completed completed) {
                    assertEquals(new ProjectionMetricsCalculator().calculate(plan, completed.projection()), observed.metrics().orElseThrow());
                } else assertEquals(((ProjectionExecutionResult.InsufficientFunds) result).fundingFailure(), observed.fundingFailure().orElseThrow());
            }
            assertEquals(new MonteCarloAnalyzer().analyze(plan, settings).deterministicReference(), actual.reference());
        } else {
            var request = mortality(plan, settings);
            var actual = analyzer.analyzeMortalityInternalExpenses(plan, request, expenses, PROGRESS, NEVER);
            assertEquals(actual.outcomes(), analyzer.analyzeMortalityInternalExpenses(plan, request, expenses, PROGRESS, NEVER).outcomes());
            var worlds = new MonteCarloWorldGenerator(request);
            for (int index = settings.simulationCount() - 1; index >= 0; index--) {
                var world = worlds.generate(index); int last = world.lifetime().terminalDeathYear() - 1;
                assertEquals(world.lifetime(), actual.outcomes().get(index).lifetime());
                if (last < first) continue; // Opening-death outcome has no annual execution or expense path.
                var context = ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(last);
                if (couple) context = context.withSurvivorClaimingAge(request.survivorClaimingAge().orElseThrow());
                if (world.inflationPath().isPresent()) context = context.withInflationPath(world.inflationPath().orElseThrow());
                context = expenses.apply(plan, context, settings.seed(), index, first, last);
                var result = new ProjectionEngine().projectWithOutcome(plan, context, world.economicPath());
                var annual = new TreeMap<Integer, BigDecimal>();
                result.completedYears().forEach(row -> annual.put(row.getCalendarYear(), row.getEndingInvestableAssets()));
                assertEquals(annual, actual.outcomes().get(index).annualInvestableAssets());
                if (result instanceof ProjectionExecutionResult.Completed completed) {
                    var metrics = new ProjectionMetricsCalculator().calculate(plan, completed.projection());
                    assertEquals(metrics.afterTaxEstate(), actual.outcomes().get(index).terminal().orElseThrow().afterTaxEstate());
                }
            }
        }
        assertEquals(original, JSON.writeValueAsString(plan));
    }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void pairedBothHouseholdsAndModesPreserveIdenticalFinancialResultsAndCommonShocks(boolean couple, boolean longevity) {
        var plan = plan(couple);
        var duplicate = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().addExpense(new Expense("Duplicate", duplicate.getAnnualAmount(), duplicate.getGrowthCategory()));
        var settings = MonteCarloSettings.forPlan(plan, 5, 417, new BigDecimal(".12"));
        var request = comparison(plan, plan, settings, longevity, 2029);
        var result = new MonteCarloStrategyComparisonAnalyzer().analyzeInternalExpenses(request,
                configuration(plan, ".5"), configuration(plan, ".5"), PROGRESS, NEVER);
        for (var pair : result.outcomes()) assertEquals(pair.outcomeA(), pair.outcomeB());
        assertEquals(5, result.outcomes().size());
        var repeat = new MonteCarloStrategyComparisonAnalyzer().analyzeInternalExpenses(request,
                configuration(plan, ".5"), configuration(plan, ".5"), PROGRESS, NEVER);
        assertEquals(result.outcomes(), repeat.outcomes());
    }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void disabledInternalInputsKeepExactLegacyOutputsForEveryIdentity(boolean couple, boolean longevity) {
        var plan = plan(couple); var settings = MonteCarloSettings.forPlan(plan, 3, 417, new BigDecimal(".12"));
        var disabled = configuration(plan, "0"); var analyzer = new MonteCarloAnalyzer();
        if (longevity) {
            var request = mortality(plan, settings);
            var original = analyzer.analyzeMortality(plan, request);
            var actual = analyzer.analyzeMortalityInternalExpenses(plan, request, disabled, PROGRESS, NEVER);
            assertEquals(original.outcomes(), actual.outcomes()); assertEquals(original.annualResults(), actual.annual());
        } else {
            var original = analyzer.analyze(plan, settings);
            var actual = analyzer.analyzeInternalExpenses(plan, settings, disabled, PROGRESS, NEVER);
            assertEquals(original.outcomes(), actual.outcomes()); assertEquals(original.annualInvestableAssets(), actual.annual());
            assertEquals(original.deterministicReference(), actual.reference());
        }
        var request = comparison(plan, plan, settings, longevity, 2029);
        var comparison = new MonteCarloStrategyComparisonAnalyzer();
        assertEquals(comparison.analyzeComparison(request).outcomes(), comparison.analyzeInternalExpenses(request,
                disabled, disabled, PROGRESS, NEVER).outcomes());
    }

    @Test void noEnabledPublicEntryOrSupportedSnapshotPdfConversionExists() {
        for (var type : List.of(MonteCarloAnalyzer.class, MonteCarloStrategyComparisonAnalyzer.class)) {
            for (var method : type.getDeclaredMethods()) {
                if (Arrays.asList(method.getParameterTypes()).contains(InternalExpenseRun.class)) {
                    assertFalse(Modifier.isPublic(method.getModifiers()));
                }
            }
        }
        for (var type : List.of(InternalExpenseResults.Fixed.class, InternalExpenseResults.Longevity.class, InternalExpenseResults.Comparison.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            assertFalse(MonteCarloAnalysisResult.class.isAssignableFrom(type));
            for (var method : MonteCarloSnapshotMapping.class.getMethods()) {
                if (method.getDeclaringClass() != MonteCarloSnapshotMapping.class) continue;
                for (var parameter : method.getParameterTypes()) assertFalse(parameter.isAssignableFrom(type));
            }
            for (var method : com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloPdfReportAdapter.class.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                for (var parameter : method.getParameterTypes()) assertFalse(parameter.isAssignableFrom(type));
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> InternalExpenseRun.requireGeneratedWorlds(new Object()));
        InternalExpenseRun.requireGeneratedWorlds(null);
    }

    @Test void enabledSuppliedWorldsFailBeforeAnySupplierOrProjectionCanExecute() throws Exception {
        var plan = plan(true); var settings = MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO);
        var input = configuration(plan, ".5");
        var analyzer = new MonteCarloAnalyzer();
        var fixed = MonteCarloAnalyzer.class.getDeclaredMethod("analyze", RetirementPlan.class, MonteCarloSettings.class,
                ProjectionExecutionResult.class, java.util.function.IntFunction.class, String.class,
                AnalysisProgressListener.class, AnalysisCancellationToken.class, InternalExpenseRun.class);
        fixed.setAccessible(true);
        java.util.function.IntFunction<ProjectionEconomicPath> market = index -> { fail("No enabled supplied market may execute"); return null; };
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> fixed.invoke(analyzer,
                plan, settings, null, market, "CALLER_SUPPLIED_PATHS", PROGRESS, NEVER, input));
        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
        var mortality = MonteCarloAnalyzer.class.getDeclaredMethod("analyzeMortality", RetirementPlan.class,
                MonteCarloMortalityRequest.class, java.util.function.IntFunction.class, String.class, String.class,
                AnalysisProgressListener.class, AnalysisCancellationToken.class, InternalExpenseRun.class);
        mortality.setAccessible(true);
        java.util.function.IntFunction<MonteCarloWorld> worlds = index -> { fail("No enabled supplied world may execute"); return null; };
        failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> mortality.invoke(analyzer,
                plan, mortality(plan, settings), worlds, "CALLER_SUPPLIED_WORLDS", "CALLER_SUPPLIED_WORLDS", PROGRESS, NEVER, input));
        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
        var paired = MonteCarloStrategyComparisonAnalyzer.class.getDeclaredMethod("run", MonteCarloStrategyComparisonRequest.class,
                java.util.function.IntFunction.class, AnalysisProgressListener.class, AnalysisCancellationToken.class,
                InternalExpenseRun.class, InternalExpenseRun.class);
        paired.setAccessible(true);
        java.util.function.IntFunction<MonteCarloComparisonWorld> pairedWorlds = index -> { fail("No enabled supplied pair may execute"); return null; };
        failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> paired.invoke(new MonteCarloStrategyComparisonAnalyzer(),
                comparison(plan, plan, settings, false, 2029), pairedWorlds, PROGRESS, NEVER, input, input));
        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }

    @Test void staleFrozenDefinitionsCancellationAndPartialFundingFailuresRemainSafe() {
        var plan = plan(true); var input = configuration(plan, ".5");
        var old = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().replaceExpense(old, new Expense(old.getDescription(), old.getAnnualAmount().add(BigDecimal.ONE)));
        var settings = MonteCarloSettings.forPlan(plan, 5, 417, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer().analyzeInternalExpenses(plan, settings, input, PROGRESS, NEVER));
        var fresh = configuration(plan, ".5"); var cancelled = new AtomicBoolean(); var calls = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext context, ProjectionEconomicPath market) {
                if (context.expensePath().isPresent() && calls.incrementAndGet() == 1) cancelled.set(true);
                return super.projectWithOutcome(p, context, market);
            }
        };
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloAnalyzer(engine).analyzeInternalExpenses(plan, settings, fresh, PROGRESS, cancelled::get));
        assertEquals(1, calls.get());
        var funding = MonteCarloFundingTest.plan("1000", "1000000", 2);
        var failed = new MonteCarloAnalyzer().analyzeInternalExpenses(funding,
                MonteCarloSettings.forPlan(funding, 3, 417, BigDecimal.ZERO), configuration(funding, "1"), PROGRESS, NEVER);
        assertTrue(failed.outcomes().stream().allMatch(o -> o.fundingFailure().isPresent()));
    }

    @Test void pairedUnrelatedDefinitionsOrderAndHorizonDoNotChangeMatchedMultipliers() {
        var a = plan(true); var b = plan(true);
        var entries = List.copyOf(b.getHousehold().getExpenses()); entries.forEach(b.getHousehold()::removeExpense);
        b.getHousehold().addExpense(new Expense("Unrelated", new BigDecimal("1"), GrowthCategory.NONE));
        b.getHousehold().addExpense(entries.get(1)); b.getHousehold().addExpense(entries.get(0));
        var ca = configuration(a, ".5").apply(a, ProjectionEvaluationContext.empty(), 417, 7, 2027, 2056).expensePath().orElseThrow();
        var cb = configuration(b, ".5").apply(b, ProjectionEvaluationContext.empty(), 417, 7, 2027, 2086).expensePath().orElseThrow();
        for (int year = 2027; year <= 2056; year++) {
            assertEquals(ca.multiplier(ca.bindingForExpense(0).orElseThrow(), year), cb.multiplier(cb.bindingForExpense(2).orElseThrow(), year));
            assertEquals(ca.multiplier(ca.bindingForExpense(1).orElseThrow(), year), cb.multiplier(cb.bindingForExpense(1).orElseThrow(), year));
        }
    }

    @Test void changedPairedDefinitionsReachEachSidesFinancialEngineAndKeepTheSameMarketWorld() {
        var a = plan(true); var b = plan(true);
        var old = b.getHousehold().getExpenses().getFirst();
        b.getHousehold().replaceExpense(old, new Expense("Same label is not correspondence", old.getAnnualAmount().multiply(new BigDecimal("2"))));
        var settings = MonteCarloSettings.forPlan(a, 3, 417, new BigDecimal(".12"));
        var request = comparison(a, b, settings, false, 2029);
        var inputA = configuration(a, ".5"); var inputB = configuration(b, ".5");
        var markets = new ArrayList<ProjectionEconomicPath>();
        var engine = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext context, ProjectionEconomicPath market) {
                markets.add(market);
                assertTrue(context.expensePath().isPresent());
                return super.projectWithOutcome(p, context, market);
            }
        };
        var result = new MonteCarloStrategyComparisonAnalyzer(engine).analyzeInternalExpenses(request, inputA, inputB, PROGRESS, NEVER);
        for (int index = 0; index < 3; index++) {
            assertSame(markets.get(index * 2), markets.get(index * 2 + 1));
            var world = request.worldSource().apply(index);
            int side = 0;
            for (var p : List.of(a, b)) {
                var input = side == 0 ? inputA : inputB;
                var context = input.apply(p, ProjectionEvaluationContext.empty().withExactEndingYear(2029), 417, index, 2027, 2029);
                var direct = new ProjectionEngine().project(p, context, world.economicPath());
                var observed = side++ == 0 ? result.outcomes().get(index).outcomeA() : result.outcomes().get(index).outcomeB();
                assertEquals(new ProjectionMetricsCalculator().calculate(p, direct).afterTaxEstate(), observed.terminal().orElseThrow().afterTaxEstate());
            }
        }
        assertNotEquals(result.outcomes().getFirst().outcomeA(), result.outcomes().getFirst().outcomeB());
    }

    @Test void pairedCancellationBetweenSidesAndSoftwareErrorsNeverPublishPartialResults() {
        var plan = plan(true); var settings = MonteCarloSettings.forPlan(plan, 3, 417, BigDecimal.ZERO);
        var request = comparison(plan, plan, settings, false, 2029); var input = configuration(plan, ".5");
        var cancelled = new AtomicBoolean(); var calls = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext context, ProjectionEconomicPath market) {
                calls.incrementAndGet(); cancelled.set(true);
                return super.projectWithOutcome(p, context, market);
            }
        };
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloStrategyComparisonAnalyzer(engine)
                .analyzeInternalExpenses(request, input, input, PROGRESS, cancelled::get));
        assertEquals(1, calls.get());
        var throwing = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext context, ProjectionEconomicPath market) {
                throw new IllegalStateException("Synthetic software failure");
            }
        };
        var failure = assertThrows(MonteCarloStrategyComparisonAnalyzer.ExecutionException.class, () ->
                new MonteCarloStrategyComparisonAnalyzer(throwing).analyzeInternalExpenses(request, input, input, PROGRESS, NEVER));
        assertEquals(0, failure.scenarioIndex());
        assertEquals(MonteCarloStrategyComparisonAnalyzer.Stage.STRATEGY_A, failure.stage());
    }
}
