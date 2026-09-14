package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class LongevityWeightedAnalysisRequestFactoryTest {
    static RetirementPlan plan() {
        Person primary = person(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4));
        Person spouse = person(AccountOwnership.SPOUSE, LocalDate.of(1965, 2, 28));
        return new RetirementPlan(new Household(primary, spouse), new AccountPortfolio(),
                new PlanningAssumptions(new BigDecimal("0.03"), new BigDecimal("0.02"), 2, LocalDate.of(2026, 7, 1)));
    }
    private static Person person(AccountOwnership owner, LocalDate birth) {
        Person person = new Person(owner.name(), "Planner", birth);
        person.setMortalityCategory(owner == AccountOwnership.PRIMARY ? MortalityCategory.MALE : MortalityCategory.FEMALE);
        person.addIncomeSource(new SocialSecurityIncome("SS", owner, birth.plusYears(67), null,
                new BigDecimal("2000"), 67, BigDecimal.ZERO, 2025));
        return person;
    }
    static LongevityWeightedAnalysisRequestFactory.Snapshot capture(RetirementPlan plan) {
        return new LongevityWeightedAnalysisRequestFactory().capture(plan, SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityCategory.FEMALE, SocialSecurityMortalityAdjustment.of(new BigDecimal("0.80")),
                SocialSecurityMortalityAdjustment.standard(), LocalDate.of(2026, 7, 1), new BigDecimal("0.01"), 7, 9);
    }
    static LongevityWeightedIntegratedStrategyComparisonRequest request(RetirementPlan plan) {
        return new LongevityWeightedAnalysisRequestFactory().create(capture(plan), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }
    @Test void freezesPlanAssumptionsAndCompleteUniverseWithoutSocialSecurityRun() {
        var plan = plan();
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var frozen = capture(plan);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1970, 1, 1));
        var request = new LongevityWeightedAnalysisRequestFactory().create(frozen, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(standard.strategyCount(), request.candidates().size());
        assertEquals(62, request.candidates().getFirst().primaryRetirementAge());
        assertEquals(70, request.candidates().getLast().spouseRetirementAge());
        assertEquals(LocalDate.of(1963, 6, 4), request.longevityScenarios().primary().request().dateOfBirth());
        assertEquals(new BigDecimal("0.80"), request.longevityScenarios().assumptions().primaryAdjustment().factor());
        assertEquals(new BigDecimal("0.01"), request.realDiscountRate());
        assertEquals(7, frozen.planRevision);
        assertEquals(9, frozen.assumptionsRevision);
        assertTrue(request.detailRetention().selectedCandidateOrders().isEmpty());
    }
    @Test void missingSurvivorPolicyOmitsBaselineWithoutFallback() {
        var plan = plan();
        policy(plan, null);
        assertTrue(request(plan).baselineStrategy().isEmpty());
    }
    @Test void dormantBothSurvivePolicyDoesNotSupplyEitherElection() {
        var plan = plan();
        policy(plan, 65);
        assertTrue(request(plan).baselineStrategy().isEmpty());
    }

    @Test void explicitBaselineIsFrozenAndDoesNotConstrainCandidates() {
        var plan = plan();
        var baseline = CurrentStrategyBaseline.capture(plan, "66", "70");
        var factory = new LongevityWeightedAnalysisRequestFactory();
        var snapshot = factory.capture(plan, SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(),
                LocalDate.of(2026, 7, 1), LocalDate.of(2025, 1, 1), new BigDecimal("0.01"), 7, 9, baseline);
        var expectedCandidates = request(plan).candidates();
        policy(plan, 62);
        var result = factory.create(snapshot, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(expectedCandidates, result.candidates());
        assertEquals(baseline.strategy(), result.baselineStrategy());
        assertEquals(66, result.baselineStrategy().orElseThrow().primarySurvivorElection().ageYears());
        assertEquals(70, result.baselineStrategy().orElseThrow().spouseSurvivorElection().ageYears());
        assertEquals(LocalDate.of(2025, 1, 1), result.valuationDate());
        assertEquals("Analyzer", snapshot.baseline.elections().get(2).source());
    }

    @Test void explicitBaselineFlowsThroughSmallFinancialComparisonWithExactPvDeltas() {
        var plan = plan();
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.SavingsAccount(
                "Test reserves", AccountOwnership.JOINT, new BigDecimal("1000000")));
        var baseline = CurrentStrategyBaseline.capture(plan, "66", "67").strategy().orElseThrow();
        var candidates = new java.util.ArrayList<SocialSecurityHouseholdClaimingStrategy>();
        candidates.add(baseline);
        candidates.add(request(plan).candidates().getFirst());
        candidates.add(request(plan).candidates().getLast());
        var scenarios = PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2026, 7, 1),
                java.util.List.of(new SocialSecurityMortalityProbability(80, BigDecimal.ONE)),
                java.util.List.of(new SocialSecurityMortalityProbability(82, BigDecimal.ONE)));
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios,
                LocalDate.of(2025, 1, 1), new BigDecimal("0.013"), java.util.Optional.of(baseline),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareExact(request);
        assertEquals(3, result.completedStrategyCount(), () -> result.failures().toString());
        var base = result.baseline().orElseThrow().aggregate().orElseThrow();
        assertEquals(0, result.orderedEntries().getFirst().pvDifferenceFromBaseline().orElseThrow().signum());
        for (var entry : result.orderedEntries()) {
            assertEquals(entry.aggregate().orElseThrow().expectedPvAfterTaxEstate().subtract(base.expectedPvAfterTaxEstate()),
                    entry.pvDifferenceFromBaseline().orElseThrow());
        }
        assertTrue(new LongevityWeightedIntegratedPresentation(result, 0, 0).currentHasCandidateRank());
        assertEquals(java.util.OptionalInt.empty(), result.baseline().orElseThrow().rank());
    }
    @Test void malformedHouseholdIsNotMissingPolicy() {
        var plan = plan();
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1940, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> request(plan));
    }
    @Test void cancelledPreparationDoesNotConstructRequest() {
        assertThrows(AnalysisCancelledException.class, () -> new LongevityWeightedAnalysisRequestFactory()
                .create(capture(plan()), AnalysisProgressListener.none(), () -> true));
    }
    static void policy(RetirementPlan plan, Integer age) {
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, age, BigDecimal.ONE),
                old.getProjectionLengthYears(), old.getProjectionStartDate()));
    }
}
