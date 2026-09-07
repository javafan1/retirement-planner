package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.AnalyzerLongevityAssumptions;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.HouseholdLongevityScenarioFactory;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityTables;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityCategory;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityPartialYearConvention;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class IntegratedSocialSecurityCompleteStrategySearchCalculatorTest {

    private final IntegratedSocialSecurityCompleteStrategySearchCalculator calculator =
            new IntegratedSocialSecurityCompleteStrategySearchCalculator();
    private final IntegratedSocialSecurityStrategyEvaluator evaluator =
            new IntegratedSocialSecurityStrategyEvaluator();

    @Test
    void sharedLongevityPreparationDoesNotAffectDeterministicSearch() throws Exception {
        RetirementPlan plan = plan(DeathScenario.SPOUSE_DIES);
        String before = json(plan);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(62, 70), List.of(62),
                standard.primarySurvivorCandidates().subList(0, 1),
                standard.spouseSurvivorCandidates().subList(0, 1),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 2);
        var baseline = calculator.calculate(request);
        var table = SocialSecurityMortalityTables.ssaPeriod2022();
        for (String factor : List.of("0.80", "1.00", "1.50")) {
            var assumptions = new AnalyzerLongevityAssumptions(
                    SocialSecurityMortalityCategory.MALE,
                    SocialSecurityMortalityAdjustment.of(new BigDecimal(factor)),
                    SocialSecurityMortalityCategory.FEMALE,
                    SocialSecurityMortalityAdjustment.standard(),
                    LocalDate.of(2026, 7, 1), table.metadata(),
                    SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
            new HouseholdLongevityScenarioFactory(table).create(
                    plan.getHousehold().getPrimaryPerson().getBirthDate(),
                    plan.getHousehold().getSpouse().getBirthDate(), assumptions);
            var actual = calculator.calculate(request);
            assertEquals(baseline.entries().stream().map(entry -> entry.metrics()).toList(),
                    actual.entries().stream().map(entry -> entry.metrics()).toList());
            assertEquals(baseline.entries().stream().map(entry -> entry.strategy()).toList(),
                    actual.entries().stream().map(entry -> entry.strategy()).toList());
            assertEquals(before, json(plan));
        }
    }
    @Test
    void progressIsMonotonicEndsAtTotalAndDoesNotChangeResults() {
        RetirementPlan plan = plan(DeathScenario.SPOUSE_DIES);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var small = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(62, 63), List.of(62),
                standard.primarySurvivorCandidates().subList(0, 2),
                standard.spouseSurvivorCandidates().subList(0, 2),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 2);
        List<AnalysisProgress> updates = new ArrayList<>();

        var withProgress = calculator.calculate(small, updates::add, () -> false);
        var withoutProgress = calculator.calculate(small);

        List<AnalysisProgress> strategyUpdates = updates.stream()
                .filter(update -> update.phase()
                        == AnalysisPhase.EXHAUSTIVE_INTEGRATED_STRATEGIES)
                .toList();
        assertEquals(0, strategyUpdates.getFirst().completedWork());
        assertEquals(8, strategyUpdates.getLast().completedWork());
        assertEquals(8, strategyUpdates.getLast().totalWork());
        for (int index = 1; index < strategyUpdates.size(); index++) {
            assertTrue(strategyUpdates.get(index).completedWork()
                    >= strategyUpdates.get(index - 1).completedWork());
        }
        assertEquals(withoutProgress.entries().stream().map(entry -> entry.metrics()).toList(),
                withProgress.entries().stream().map(entry -> entry.metrics()).toList());
    }

    @Test
    void cooperativeCancellationStopsBeforeLaterStrategiesAndPublishesNoResult()
            throws Exception {
        RetirementPlan plan = plan(DeathScenario.SPOUSE_DIES);
        String before = json(plan);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(62, 63, 64), List.of(62),
                standard.primarySurvivorCandidates().subList(0, 2),
                standard.spouseSurvivorCandidates().subList(0, 2),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 0);
        AtomicBoolean cancelled = new AtomicBoolean();
        List<AnalysisProgress> updates = new ArrayList<>();

        assertThrows(AnalysisCancelledException.class, () -> calculator.calculate(
                request,
                update -> {
                    updates.add(update);
                    if (update.phase() == AnalysisPhase.EXHAUSTIVE_INTEGRATED_STRATEGIES
                            && update.completedWork() == 3) {
                        cancelled.set(true);
                    }
                },
                cancelled::get));

        assertEquals(3, updates.stream()
                .filter(update -> update.phase()
                        == AnalysisPhase.EXHAUSTIVE_INTEGRATED_STRATEGIES)
                .mapToInt(AnalysisProgress::completedWork).max().orElseThrow());
        assertEquals(before, json(plan));
    }

    @Test
    void fullSearchEvaluatesIndependentUniverseRanksAndRetainsOnlyTopDetails()
            throws Exception {
        RetirementPlan plan = plan(DeathScenario.SPOUSE_DIES);
        String before = json(plan);
        IntegratedSocialSecurityCompleteStrategySearchRequest standard =
                IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        assertEquals(8, standard.primarySurvivorCandidates().size());
        assertEquals(8, standard.spouseSurvivorCandidates().size());
        assertEquals(5184, standard.strategyCount());

        long memoryBefore = usedMemory();
        TimedResult full = timed(standard);
        long memoryAfter = usedMemory();
        IntegratedSocialSecurityCompleteStrategySearchResult result = full.result();

        assertEquals(5184, result.totalStrategyCount());
        assertEquals(5184, result.successfulStrategyCount());
        assertEquals(0, result.failedStrategyCount());
        assertEquals(20, result.entries().stream()
                .filter(entry -> entry.retainedDetail().isPresent()).count());
        assertEquals(81, result.entries().stream()
                .map(entry -> entry.strategy().primaryRetirementAge() + "/"
                        + entry.strategy().spouseRetirementAge())
                .distinct().count());
        long survivorPairsForRetirementPair = result.entries().stream()
                .filter(entry -> entry.strategy().primaryRetirementAge() == 62
                        && entry.strategy().spouseRetirementAge() == 62)
                .count();
        assertEquals(64, survivorPairsForRetirementPair);

        for (int index = 1; index < result.rankedSuccessfulEntries().size(); index++) {
            var previous = result.rankedSuccessfulEntries().get(index - 1);
            var current = result.rankedSuccessfulEntries().get(index);
            int comparison = previous.metrics().orElseThrow().afterTaxEstate()
                    .compareTo(current.metrics().orElseThrow().afterTaxEstate());
            assertTrue(comparison >= 0);
            if (comparison == 0) {
                assertEquals(previous.afterTaxEstateRank(), current.afterTaxEstateRank());
                assertTrue(previous.generationOrder() < current.generationOrder());
            }
        }

        for (var entry : List.of(
                result.entries().getFirst(),
                result.entries().get(2592),
                result.entries().getLast())) {
            ProjectionMetrics direct = evaluator.evaluate(plan, entry.strategy()).metrics();
            assertEquals(direct, entry.metrics().orElseThrow());
        }
        var first = result.entries().getFirst();
        ProjectionMetrics baseline = result.currentPlanBaseline().metrics();
        assertEquals(first.metrics().orElseThrow().afterTaxEstate()
                        .subtract(baseline.afterTaxEstate()),
                first.differencesFromCurrentPlan().orElseThrow().afterTaxEstate());
        assertTrue(result.currentPlanRank().isPresent());
        assertTrue(result.rankOf(result.currentPlanBaseline().evaluatedStrategy()).isEmpty(),
                "Persisted age-67 survivor policy is outside this cohort's candidate set.");
        assertEquals(before, json(plan));

        TimedResult oneSurvivorPair = timed(request(
                standard, standard.primarySurvivorCandidates().subList(0, 1),
                standard.spouseSurvivorCandidates().subList(0, 1), 0));
        TimedResult approximatelyFiveHundred = timed(request(
                standard, standard.primarySurvivorCandidates().subList(0, 2),
                standard.spouseSurvivorCandidates().subList(0, 3), 0));
        TimedResult approximatelyOneThousand = timed(request(
                standard, standard.primarySurvivorCandidates().subList(0, 3),
                standard.spouseSurvivorCandidates().subList(0, 4), 0));

        printPerformance("81", oneSurvivorPair);
        printPerformance("486", approximatelyFiveHundred);
        printPerformance("972", approximatelyOneThousand);
        printPerformance("5184", full);
        System.out.println("Complete search observed heap delta bytes: "
                + Math.max(memoryAfter - memoryBefore, 0));
        printTopTen(result);
    }

    @Test
    void generationOrderIsRetirementThenPrimarySurvivorThenSpouseSurvivor() {
        RetirementPlan plan = plan(DeathScenario.SPOUSE_DIES);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(62, 63), List.of(64),
                standard.primarySurvivorCandidates().subList(0, 2),
                standard.spouseSurvivorCandidates().subList(0, 2),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 2);

        var result = calculator.calculate(request);

        assertEquals(8, result.entries().size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8),
                result.entries().stream().map(
                        IntegratedSocialSecurityCompleteStrategySearchEntry::generationOrder)
                        .toList());
        assertEquals(62, result.entries().get(3).strategy().primaryRetirementAge());
        assertEquals(63, result.entries().get(4).strategy().primaryRetirementAge());
        assertEquals(request.primarySurvivorCandidates().get(1),
                result.entries().get(2).strategy().primarySurvivorElection());
        assertEquals(request.spouseSurvivorCandidates().get(1),
                result.entries().get(1).strategy().spouseSurvivorElection());
    }

    @Test
    void exactEstateTiesShareRankAndPreserveGenerationOrder() {
        RetirementPlan plan = plan(DeathScenario.BOTH_SURVIVE);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(67), List.of(67),
                standard.primarySurvivorCandidates().subList(0, 2),
                standard.spouseSurvivorCandidates().subList(0, 2),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 4);

        var result = calculator.calculate(request);

        assertEquals(4, result.totalStrategyCount());
        assertEquals(Set.of(1), result.rankedSuccessfulEntries().stream()
                .map(entry -> entry.afterTaxEstateRank().orElseThrow())
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(1, 2, 3, 4), result.rankedSuccessfulEntries().stream()
                .map(IntegratedSocialSecurityCompleteStrategySearchEntry::generationOrder)
                .toList());
    }

    @Test
    void invalidStrategyIsRetainedAsFailureAndLaterStrategyStillSucceeds() {
        RetirementPlan plan = plan(DeathScenario.SPOUSE_DIES);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        SocialSecuritySurvivorClaimingCandidate invalid =
                new SocialSecuritySurvivorClaimingCandidate(
                        plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(59),
                        60, 0, "Invalid pre-age-60 date");
        SocialSecuritySurvivorClaimingCandidate valid =
                standard.primarySurvivorCandidates().getFirst();
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(67), List.of(67), List.of(invalid, valid),
                List.of(standard.spouseSurvivorCandidates().getFirst()),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 1);

        var result = calculator.calculate(request);

        assertEquals(2, result.totalStrategyCount());
        assertEquals(1, result.failedStrategyCount());
        assertFalse(result.entries().getFirst().successful());
        assertTrue(result.entries().getFirst().failure().orElseThrow().message()
                .contains("cannot be before age 60"));
        assertTrue(result.entries().get(1).successful());
    }

    @Test
    void representativeSearchIsRepeatableAndFixedPairReconcilesToRetirementGrid() {
        RetirementPlan plan = plan(DeathScenario.SPOUSE_DIES);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var small = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(62, 70), List.of(62, 70),
                standard.primarySurvivorCandidates().subList(0, 2),
                standard.spouseSurvivorCandidates().subList(0, 2),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 3);
        var first = calculator.calculate(small);
        var second = calculator.calculate(small);
        assertEquals(first.entries().stream().map(entry -> List.of(
                        entry.strategy(), entry.metrics(), entry.afterTaxEstateRank())).toList(),
                second.entries().stream().map(entry -> List.of(
                        entry.strategy(), entry.metrics(), entry.afterTaxEstateRank())).toList());

        IntegratedRetirementClaimingGridCalculator gridCalculator =
                new IntegratedRetirementClaimingGridCalculator();
        IntegratedRetirementClaimingGridSurvivorPolicy fixed =
                gridCalculator.survivorPolicy(plan);
        var completeFixed = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList(),
                java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList(),
                List.of(fixed.primaryElection()), List.of(fixed.spouseElection()),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 0);
        var search = calculator.calculate(completeFixed);
        var grid = gridCalculator.calculate(plan);
        for (var cell : grid.cells()) {
            SocialSecurityHouseholdClaimingStrategy strategy = cell.strategy();
            assertEquals(cell.integratedResult().orElseThrow().metrics(),
                    search.entryFor(strategy).orElseThrow().metrics().orElseThrow());
        }
    }

    private IntegratedSocialSecurityCompleteStrategySearchRequest request(
            IntegratedSocialSecurityCompleteStrategySearchRequest standard,
            List<SocialSecuritySurvivorClaimingCandidate> primary,
            List<SocialSecuritySurvivorClaimingCandidate> spouse,
            int details) {
        return new IntegratedSocialSecurityCompleteStrategySearchRequest(
                standard.plan(), standard.primaryRetirementAges(),
                standard.spouseRetirementAges(), primary, spouse,
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, details);
    }

    private TimedResult timed(IntegratedSocialSecurityCompleteStrategySearchRequest request) {
        Instant start = Instant.now();
        var result = calculator.calculate(request);
        return new TimedResult(result, Duration.between(start, Instant.now()).toMillis());
    }

    private void printPerformance(String label, TimedResult timed) {
        double seconds = timed.millis() / 1000.0;
        double perSecond = timed.result().totalStrategyCount() / seconds;
        System.out.printf("Complete integrated search %s strategies: %d ms, %.1f evaluations/second%n",
                label, timed.millis(), perSecond);
    }

    private void printTopTen(IntegratedSocialSecurityCompleteStrategySearchResult result) {
        System.out.println("Deterministic After-Tax Estate Ranking (top 10)");
        for (var entry : result.top(10)) {
            var strategy = entry.strategy();
            var metrics = entry.metrics().orElseThrow();
            var difference = entry.differencesFromCurrentPlan().orElseThrow();
            System.out.printf("%d | %d | %d | %s | %s | %s | %s | %s | %s | %s%n",
                    entry.afterTaxEstateRank().orElseThrow(),
                    strategy.primaryRetirementAge(), strategy.spouseRetirementAge(),
                    strategy.primarySurvivorElection().label(),
                    strategy.spouseSurvivorElection().label(),
                    metrics.lifetimeHouseholdSocialSecurity(), metrics.totalTaxes(),
                    metrics.lifetimePortfolioWithdrawals(), metrics.endingInvestableAssets(),
                    difference.afterTaxEstate());
        }
        var highest = result.rankedSuccessfulEntries().getFirst().metrics().orElseThrow()
                .afterTaxEstate();
        var lowest = result.rankedSuccessfulEntries().getLast().metrics().orElseThrow()
                .afterTaxEstate();
        System.out.println("Estate range: highest=" + highest + ", lowest=" + lowest
                + ", spread=" + highest.subtract(lowest));
        System.out.println("Current estate=" + result.currentPlanBaseline().metrics().afterTaxEstate()
                + ", rank=" + result.currentPlanRank().orElseThrow());
        System.out.println("First five ranked distinct retirement pairs:");
        Set<String> seen = new HashSet<>();
        result.rankedSuccessfulEntries().stream()
                .filter(entry -> seen.add(entry.strategy().primaryRetirementAge()
                        + "/" + entry.strategy().spouseRetirementAge()))
                .limit(5)
                .forEach(entry -> System.out.println(
                        entry.afterTaxEstateRank().orElseThrow() + " | "
                                + entry.strategy().primaryRetirementAge() + "/"
                                + entry.strategy().spouseRetirementAge() + " | "
                                + entry.metrics().orElseThrow().afterTaxEstate()));
    }

    private long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private RetirementPlan plan(DeathScenario scenario) {
        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 1, 1));
        Person spouse = new Person("Spouse", "Planner", LocalDate.of(1964, 2, 29));
        primary.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY,
                LocalDate.of(2026, 1, 1), null, new BigDecimal("5000"), BigDecimal.ZERO));
        primary.addIncomeSource(source(primary, AccountOwnership.PRIMARY, "3000", 67));
        spouse.addIncomeSource(source(spouse, AccountOwnership.SPOUSE, "1200", 62));
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("80000")));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount(
                "Brokerage", AccountOwnership.PRIMARY, new BigDecimal("750000")));
        DeathScenarioAssumptions death = scenario == DeathScenario.BOTH_SURVIVE
                ? new DeathScenarioAssumptions(scenario, null)
                : new DeathScenarioAssumptions(scenario, 2040, 67);
        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(new BigDecimal("0.03"), new BigDecimal("0.02"),
                        BigDecimal.ZERO, new BigDecimal("0.02")),
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST), death,
                25, LocalDate.of(2026, 7, 1));
        return new RetirementPlan(household, portfolio, assumptions);
    }

    private SocialSecurityIncome source(
            Person person, AccountOwnership owner, String benefit, int age) {
        return new SocialSecurityIncome("Social Security", owner,
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                        person.getBirthDate(), age),
                null, new BigDecimal(benefit), age, BigDecimal.ZERO, 2025);
    }

    private String json(RetirementPlan plan) throws Exception {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(plan);
    }

    private record TimedResult(
            IntegratedSocialSecurityCompleteStrategySearchResult result,
            long millis) {
    }
}
