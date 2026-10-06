package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class SinglePersonDeterministicStrategyTest {
    public static RetirementPlan plan() {
        var pair = Stage4TestPlans.plan();
        var person = new Person("Primary", "Single", LocalDate.of(1965, 2, 1));
        person.addIncomeSource(new SocialSecurityIncome("Own SS", AccountOwnership.PRIMARY,
                person.getBirthDate().plusYears(67), null, new BigDecimal("3000"), 67,
                new BigDecimal("0.99"), 2027)); // legacy COLA must remain inert
        person.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY,
                LocalDate.of(2027, 1, 1), null, new BigDecimal("1000"), new BigDecimal("0.02")));
        var household = new Household(person);
        household.addExpense(new Expense("Living", new BigDecimal("40000")));
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount("Cash", AccountOwnership.PRIMARY, new BigDecimal("400000")));
        portfolio.addAccount(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("700000")));
        portfolio.addAccount(new RothIRA("Roth", AccountOwnership.PRIMARY, new BigDecimal("100000")));
        var old = pair.getPlanningAssumptions();
        var plan = new RetirementPlan(household, portfolio, new PlanningAssumptions(
                old.getEconomicAssumptions(), old.getTaxAssumptions(), old.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), 20, LocalDate.of(2027, 1, 1)));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                true, 2027, BigDecimal.ZERO,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL, new BigDecimal("75000")));
        return plan;
    }

    @Test void nineRealStrategiesUseSameEngineAndPreservePlan() throws Exception {
        var plan = plan();
        var before = Stage4TestPlans.json(plan);
        var request = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        assertEquals(9, request.strategyCount());
        var candidates = request.strategies();
        assertEquals(java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList(),
                candidates.stream().map(SocialSecurityHouseholdClaimingStrategy::primaryRetirementAge).toList());
        assertEquals(9, new HashSet<>(candidates).size());
        candidates.forEach(s -> {
            assertFalse(s.hasSpouse());
            assertTrue(s.spouseRetirementAgeIfPresent().isEmpty());
            assertNull(s.spouseRetirementClaimDate());
            assertNull(s.primarySurvivorElection());
            assertEquals(SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                    plan.getHousehold().getPrimaryPerson().getBirthDate(), s.primaryRetirementAge()), s.primaryRetirementClaimDate());
        });
        var progress = new ArrayList<com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress>();
        var result = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request,
                progress::add, com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        assertEquals(9, result.entries().size());
        var candidateProgress = progress.stream().filter(p -> p.phase()
                == com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase.EXHAUSTIVE_INTEGRATED_STRATEGIES).toList();
        assertEquals(10, candidateProgress.size()); // initial zero, then one event per actual candidate
        assertEquals(9, candidateProgress.getLast().completedWork());
        assertEquals(9, candidateProgress.getLast().totalWork());
        assertEquals(9, result.rankedSuccessfulEntries().size());
        assertEquals(1, result.rankedSuccessfulEntries().getFirst().afterTaxEstateRank().orElseThrow());
        assertEquals(67, result.currentPlanBaseline().evaluatedStrategy().primaryRetirementAge());
        assertTrue(result.entryFor(result.currentPlanBaseline().evaluatedStrategy()).isPresent());
        assertEquals(result.currentPlanRank(), result.rankOf(result.currentPlanBaseline().evaluatedStrategy()));
        for (var entry : result.entries()) {
            assertEquals(new IntegratedSocialSecurityStrategyEvaluator().evaluate(plan, entry.strategy()).metrics(),
                    entry.metrics().orElseThrow());
            assertTrue(entry.metrics().orElseThrow().spouseSocialSecurity().isEmpty());
        }
        var early = result.entries().getFirst().metrics().orElseThrow();
        var late = result.entries().getLast().metrics().orElseThrow();
        assertNotEquals(early.lifetimeHouseholdSocialSecurity(), late.lifetimeHouseholdSocialSecurity());
        assertNotEquals(early.totalTaxes(), late.totalTaxes());
        assertNotEquals(early.lifetimePortfolioWithdrawals(), late.lifetimePortfolioWithdrawals());
        assertNotEquals(early.lifetimeRothConversions(), late.lifetimeRothConversions());
        assertNotEquals(early.afterTaxEstate(), late.afterTaxEstate());
        assertEquals(before, Stage4TestPlans.json(plan));
    }

    @Test void primaryDeathEndsOwnBenefitWithoutSurvivor() {
        var plan = plan();
        var context = ProjectionEvaluationContext.withLifetimeScenario(
                HouseholdLifetimeScenario.primaryOnly(Optional.of(Year.of(2034))));
        var years = new ProjectionEngine().project(plan, context).getYears();
        var withoutDeath = new ProjectionEngine().project(plan).getYears();
        for (var y : years) {
            var ss = y.getSocialSecurityResult();
            assertFalse(ss.hasSpouse());
            assertNull(ss.primarySurvivorCandidate());
            assertEquals(ss.primaryOwnBenefit(), ss.householdBenefit());
            if (y.getCalendarYear() >= 2034) assertEquals(0, ss.householdBenefit().signum());
            // No surviving filer exists; composition must not silently overwrite configured filing status.
            assertEquals(withoutDeath.get(y.getCalendarYear() - 2027).getFederalStandardDeduction(),
                    y.getFederalStandardDeduction());
        }
        assertTrue(years.stream().anyMatch(y -> y.getSocialSecurityResult().householdBenefit().signum() > 0));
    }

    @Test void ageAuthorityColaAndDeathBeforeClaimAreExplicit() {
        for (int age : List.of(62, 67, 70)) {
            var plan = plan();
            var person = plan.getHousehold().getPrimaryPerson();
            var source = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).findFirst().orElseThrow();
            person.removeIncomeSource(source);
            person.addIncomeSource(new SocialSecurityIncome("Own", AccountOwnership.PRIMARY,
                    LocalDate.of(2099, 1, 1), null, new BigDecimal("3000"), age, new BigDecimal("0.99"), 2027));
            var derived = SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(person.getBirthDate(), age);
            var annual = new SocialSecurityProjectionIncomeProvider().calculate(plan, 2027, 2040);
            var expected = new SocialSecurityStrategyCalculator().calculateOwnRetirement(
                    new SocialSecurityClaimingElection(AccountOwnership.PRIMARY, person.getBirthDate(),
                            new BigDecimal("3000"), 2027, derived), 2027, 2040,
                    plan.getPlanningAssumptions().getSocialSecurityColaRate());
            annual.forEach((year, value) -> assertEquals(expected.get(year), value.householdBenefit()));
            assertTrue(annual.get(derived.getYear()).householdBenefit().signum() > 0);
            var context = ProjectionEvaluationContext.withLifetimeScenario(HouseholdLifetimeScenario.primaryOnly(
                    Optional.of(Year.of(2027))));
            new SocialSecurityProjectionIncomeProvider().calculate(plan, 2027, 2040, context).values()
                    .forEach(v -> { assertEquals(0, v.householdBenefit().signum()); assertNull(v.spouseOwnBenefit()); });
        }
    }

    @Test void completedBreakEvenUsesRealPeopleAndExistingMetrics() {
        var plan = plan();
        plan.addNonInvestableAsset(new com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset(
                "Home", new BigDecimal("300000"), new BigDecimal("0.02")));
        var run = SinglePersonIntegratedAnalysis.calculate(IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        assertEquals(9, run.breakEvenByClaimingAge().size());
        var identical = run.breakEvenByClaimingAge().get(67);
        identical.metrics().values().forEach(m -> assertEquals(BreakEvenStatus.IDENTICAL, m.status()));
        var early = run.breakEvenByClaimingAge().get(62);
        assertFalse(early.currentAssumptions().hasSpouse());
        early.metrics().values().forEach(m -> m.years().forEach(y -> assertNull(y.spouseAge())));
        var entry = run.result().entries().getFirst();
        var ss = early.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).years().getLast();
        assertEquals(0, ss.currentValue().compareTo(entry.metrics().orElseThrow().lifetimeHouseholdSocialSecurity()));
        var netWorth = early.metrics().get(BreakEvenMetric.TOTAL_NET_WORTH).years().getLast();
        assertEquals(0, netWorth.currentValue().compareTo(entry.metrics().orElseThrow().endingNetWorth()));
        var context = new com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory().create(early,
                LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)));
        assertTrue(context.survivalExplanation().contains("deferred"));
    }

    @Test void mortalityDependentOptimizersRejectClearlyAndInvalidStrategiesFail() {
        var plan = plan();
        var ex = assertThrows(UnsupportedOperationException.class, () ->
                new com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityStrategyAnalysisRequestFactory().create(
                        plan, SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(),
                        new BigDecimal("0.01"), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1)));
        assertTrue(ex.getMessage().contains("mortality-weighted"));
        assertThrows(UnsupportedOperationException.class, () -> new LongevityWeightedIntegratedStrategyRequest(
                plan, new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan), null,
                LocalDate.of(2027, 1, 1), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SocialSecurityHouseholdClaimingStrategy(
                67, null, LocalDate.of(2032, 2, 1), LocalDate.of(2032, 1, 1), null, null));
        assertThrows(IllegalArgumentException.class, () -> new IntegratedSocialSecurityStrategyEvaluator().evaluate(plan,
                SocialSecurityHouseholdClaimingStrategy.primaryOnly(67, LocalDate.of(2030, 1, 1))));
    }
}
