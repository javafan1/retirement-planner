package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HouseholdLifetimeProjectionTest {
    private final ProjectionEngine engine = new ProjectionEngine();

    private RetirementPlan plan() {
        return LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2031, 67, new BigDecimal("0.75")));
    }

    private HouseholdLifetimeScenario lifetime(Integer primary, Integer spouse) {
        return new HouseholdLifetimeScenario(Optional.ofNullable(primary).map(Year::of),
                Optional.ofNullable(spouse).map(Year::of));
    }

    private Projection run(RetirementPlan plan, Integer primary, Integer spouse) {
        return engine.project(plan, ProjectionEvaluationContext.withLifetimeScenario(lifetime(primary, spouse)));
    }

    @Test
    void scenariosCannotContaminateDeterministicRunsOrPersistedPlan() throws Exception {
        var plan = plan();
        String originalPlan = LifetimeProjectionTestSupport.json(plan);
        var account = plan.getAccountPortfolio().getAccounts().getFirst();
        BigDecimal balance = account.getCurrentBalance();
        String deterministic = LifetimeProjectionTestSupport.json(engine.project(plan));
        String first = LifetimeProjectionTestSupport.json(run(plan, 2031, 2033));
        String second = LifetimeProjectionTestSupport.json(run(plan, 2033, 2031));
        assertNotEquals(first, second);
        assertEquals(first, LifetimeProjectionTestSupport.json(run(plan, 2031, 2033)));
        assertEquals(deterministic, LifetimeProjectionTestSupport.json(engine.project(plan)));
        assertEquals(originalPlan, LifetimeProjectionTestSupport.json(plan));
        assertEquals(balance, account.getCurrentBalance());
    }

    @Test
    void explicitSurvivalOverridesPersistedDeathAndMatchesPersistedSurvival() throws Exception {
        var survived = run(plan(), null, null);
        var persisted = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null, 67, new BigDecimal("0.75")));
        assertEquals(LifetimeProjectionTestSupport.json(engine.project(persisted)),
                LifetimeProjectionTestSupport.json(survived));
        assertTrue(survived.getYearAt(2).getSocialSecurityResult().primaryOwnBenefit().signum() > 0);
    }

    @ParameterizedTest
    @CsvSource({"2031,2033", "2033,2031"})
    void independentDeathsDriveSocialSecurityMedicareExpensesAndFilingStatus(int primary, int spouse) {
        var result = run(plan(), primary, spouse);
        assertEquals(5, result.size());
        int[] participants = {2, 1, 1, 0, 0};
        String[] expenses = {"20000", "30000", "30000", "5000", "0"};
        for (int i = 0; i < result.size(); i++) {
            var year = result.getYearAt(i);
            assertEquals(participants[i], year.getMedicarePremiumCalculation().coveredMedicareParticipants());
            money(expenses[i], year.getAnnualExpenses());
            if (year.getCalendarYear() >= primary) {
                money("0", year.getSocialSecurityResult().primarySelectedBenefit());
            }
            if (year.getCalendarYear() >= spouse) {
                money("0", year.getSocialSecurityResult().spouseSelectedBenefit());
            }
        }
        // Compare to the existing deterministic first-death convention, including annual rule projection.
        var persisted = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(
                primary < spouse ? DeathScenario.PRIMARY_DIES : DeathScenario.SPOUSE_DIES,
                2031, 67, new BigDecimal("0.75")));
        var expected = engine.project(persisted);
        for (int i = 0; i < result.size(); i++) {
            assertEquals(expected.getYearAt(i).getFederalStandardDeduction(),
                    result.getYearAt(i).getFederalStandardDeduction());
        }
        assertTrue(result.getYearAt(2).getFederalStandardDeduction()
                .compareTo(result.getYearAt(1).getFederalStandardDeduction()) < 0);
    }

    @Test
    void survivorSocialSecurityAndPensionStopAtSecondDeath() {
        var result = run(plan(), 2031, 2033);
        assertTrue(result.getYearAt(1).getSocialSecurityResult().spouseSurvivorCandidate().signum() > 0);
        money("6600", nonSocialSecurity(result.getYearAt(1)));
        money("7260", nonSocialSecurity(result.getYearAt(2)));
        money("0", result.getYearAt(3).getGuaranteedIncome());
        money("0", result.getYearAt(4).getGuaranteedIncome());
    }

    @Test
    void survivorPensionKeepsColaAndSourceEndDate() {
        var result = run(plan(), 2031, null);
        money("12000", nonSocialSecurity(result.getYearAt(0)));
        money("6600", nonSocialSecurity(result.getYearAt(1)));
        money("7260", nonSocialSecurity(result.getYearAt(2)));
        money("3993", nonSocialSecurity(result.getYearAt(3)));
        money("0", nonSocialSecurity(result.getYearAt(4)));
    }

    @Test
    void survivorPensionDoesNotStartBeforePensionCommencement() {
        var plan = plan();
        var person = plan.getHousehold().getPrimaryPerson();
        var original = person.getIncomeSources().stream().filter(Pension.class::isInstance).findFirst().orElseThrow();
        person.replaceIncomeSource(original, new Pension("Later", AccountOwnership.PRIMARY,
                LocalDate.of(2032, 7, 1), null, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("500")));
        var result = run(plan, 2031, null);
        money("0", nonSocialSecurity(result.getYearAt(1)));
        money("3000", nonSocialSecurity(result.getYearAt(2)));
        money("6000", nonSocialSecurity(result.getYearAt(3)));
    }

    @ParameterizedTest
    @CsvSource({"2031,2031", "2028,2029", "2030,2030"})
    void bothDeadYearsHaveNoHouseholdCashIncomeOrRecurringCosts(int primary, int spouse) {
        var result = run(plan(), primary, spouse);
        for (var year : result.getYears()) {
            if (year.getCalendarYear() >= Math.max(primary, spouse)) {
                money("0", year.getGuaranteedIncome());
                money(year.getCalendarYear() == 2033 ? "5000" : "0", year.getAnnualExpenses());
                assertEquals(0, year.getMedicarePremiumCalculation().coveredMedicareParticipants());
            }
        }
        assertEquals(5, result.size());
    }

    @Test
    void firstYearDeathUsesFullDeceasedYearAndStillProratesRecurringExpenses() {
        var result = run(plan(), 2030, null);
        money("15000", result.getYearAt(0).getAnnualExpenses());
        money("0", result.getYearAt(0).getSocialSecurityResult().primarySelectedBenefit());
        assertEquals(1, result.getYearAt(0).getMedicarePremiumCalculation().coveredMedicareParticipants());
    }

    @Test
    void deathsBeyondHorizonAreAbsentOnlyForFiniteRequest() throws Exception {
        var timing = lifetime(2040, 2045);
        var result = engine.project(plan(), ProjectionEvaluationContext.withLifetimeScenario(timing));
        assertEquals(LifetimeProjectionTestSupport.json(run(plan(), null, null)),
                LifetimeProjectionTestSupport.json(result));
        assertEquals(LocalDate.of(2040, 1, 1), timing.primaryDeathDate().orElseThrow());
        // Only one of the two deaths is beyond the horizon.
        assertDoesNotThrow(() -> run(plan(), 2031, 2045));
        assertDoesNotThrow(() -> run(plan(), 2045, 2031));
    }

    @Test
    void missingSurvivorPolicyFailsOnlyWhenSurvivorBehaviorIsRelevant() {
        var plan = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null));
        var error = assertThrows(IllegalArgumentException.class, () -> run(plan, 2031, null));
        assertTrue(error.getMessage().contains("survivor claiming age"));
        assertDoesNotThrow(() -> run(plan, null, null));
        assertDoesNotThrow(() -> run(plan, 2031, 2031));
        assertDoesNotThrow(() -> run(plan, 2028, 2029));
        assertDoesNotThrow(() -> run(plan, 2040, 2045));
        assertDoesNotThrow(() -> engine.project(plan, ProjectionEvaluationContext.withSocialSecurityStrategy(
                strategy(plan, 67), lifetime(2031, null))));
    }

    @Test
    void explicitAndPersistedSurvivorElectionsAfterClaimantDeathAreOmitted() {
        var plan = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, 70));
        var result = engine.project(plan, ProjectionEvaluationContext.withSocialSecurityStrategy(
                strategy(plan, 70), lifetime(2030, 2032)));
        money("0", result.getYearAt(1).getSocialSecurityResult().spouseSurvivorCandidate());
        assertDoesNotThrow(() -> run(plan, 2030, 2032));
    }

    @Test
    void legacyLifetimeFailsClearlyButOrdinaryFallbackRemainsUnchanged() throws Exception {
        var plan = plan();
        var spouse = plan.getHousehold().getSpouse();
        spouse.removeIncomeSource(spouse.getIncomeSources().getFirst());
        String ordinary = LifetimeProjectionTestSupport.json(engine.project(plan));
        var error = assertThrows(IllegalArgumentException.class, () -> run(plan, null, null));
        assertTrue(error.getMessage().contains("legacy compatibility fallback"));
        assertEquals(ordinary, LifetimeProjectionTestSupport.json(engine.project(plan, ProjectionEvaluationContext.empty())));
    }

    @ParameterizedTest
    @CsvSource({"2031,PRIMARY_DIES", "2031,SPOUSE_DIES"})
    void lifetimeOnlyPensionTaxAndConversionCorrectionsLeavePersistedRunUnchanged(
            int deathYear, DeathScenario direction) throws Exception {
        var plan = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(
                direction, deathYear, 67, new BigDecimal("0.75")));
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                "IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA(
                "Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                true, 2030, BigDecimal.ZERO,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FILL_12_PERCENT_BRACKET,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(assumptions.getEconomicAssumptions(),
                assumptions.getTaxAssumptions(), assumptions.getWithdrawalAssumptions(),
                assumptions.getDeathScenarioAssumptions(), 8, assumptions.getProjectionStartDate()));
        var expected = engine.project(plan);
        var actual = run(plan, direction == DeathScenario.PRIMARY_DIES ? deathYear : null,
                direction == DeathScenario.SPOUSE_DIES ? deathYear : null);
        assertEquals(LifetimeProjectionTestSupport.json(expected),
                LifetimeProjectionTestSupport.json(engine.project(plan)));
        if (direction == DeathScenario.PRIMARY_DIES) {
            money("0", actual.getYearAt(5).getRequiredMinimumDistribution());
            assertTrue(expected.getYearAt(5).getRequiredMinimumDistribution().signum() > 0);
            money("0", actual.getYearAt(1).getPrimaryRothConversion());
            assertTrue(expected.getYearAt(1).getPrimaryRothConversion().signum() > 0);
            assertTrue(actual.getYearAt(1).getAdjustedGrossIncome().compareTo(expected.getYearAt(1).getAdjustedGrossIncome()) < 0);
        }
        assertTrue(actual.getYears().stream().anyMatch(year -> year.getRothConversion().signum() > 0));
    }

    @Test
    void independentDeathResultsReconcileToAuthoritativeMonthlySocialSecurity() {
        var plan = plan();
        var result = run(plan, 2031, 2033);
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        var primarySource = (SocialSecurityIncome) primary.getIncomeSources().getFirst();
        var spouseSource = (SocialSecurityIncome) spouse.getIncomeSources().getFirst();
        var direct = new SocialSecurityStrategyCalculator().calculate(new SocialSecurityStrategyRequest(
                LocalDate.of(2030, 1, 1), LocalDate.of(2034, 12, 31),
                new SocialSecurityClaimingElection(AccountOwnership.PRIMARY, primary.getBirthDate(),
                        primarySource.getFullRetirementMonthlyBenefit(), 2030, primarySource.getStartDate()),
                new SocialSecurityClaimingElection(AccountOwnership.SPOUSE, spouse.getBirthDate(),
                        spouseSource.getFullRetirementMonthlyBenefit(), 2030, spouseSource.getStartDate()),
                null, spouse.getBirthDate().plusYears(67),
                LocalDate.of(2031, 1, 1), LocalDate.of(2033, 1, 1), BigDecimal.ZERO));
        for (int i = 0; i < result.size(); i++) {
            money(direct.annualResults().get(i).householdBenefits().toPlainString(),
                    result.getYearAt(i).getSocialSecurityResult().householdBenefit());
        }
    }

    private SocialSecurityHouseholdClaimingStrategy strategy(RetirementPlan plan, int survivorAge) {
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        return new SocialSecurityHouseholdClaimingStrategy(67, 67,
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(primary.getBirthDate(), 67),
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(spouse.getBirthDate(), 67),
                new SocialSecuritySurvivorClaimingCandidate(primary.getBirthDate().plusYears(survivorAge), survivorAge, 0, "Test"),
                new SocialSecuritySurvivorClaimingCandidate(spouse.getBirthDate().plusYears(survivorAge), survivorAge, 0, "Test"));
    }

    private BigDecimal nonSocialSecurity(ProjectionYear year) {
        return year.getGuaranteedIncome().subtract(year.getSocialSecurityResult().householdBenefit());
    }

    private void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + ", got " + actual);
    }
}
