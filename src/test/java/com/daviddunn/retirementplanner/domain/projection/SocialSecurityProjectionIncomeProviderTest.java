package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.TaxIncomeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityProjectionIncomeProviderTest {

    @Test
    void reconcilesEveryYearToDirectAdvancedCalculationIncludingSpousalExcess() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), true);
        Map<Integer, HouseholdSocialSecurityResult> projected =
                new SocialSecurityProjectionIncomeProvider().calculate(plan, 2030, 2033);
        SocialSecurityLifetimeResult direct = new SocialSecurityStrategyCalculator()
                .calculate(directRequest(plan, 2030, 2033));

        for (SocialSecurityAnnualResult annual : direct.annualResults()) {
            HouseholdSocialSecurityResult actual = projected.get(annual.calendarYear());
            assertMoney(annual.primaryOwnBenefits(), actual.primaryOwnBenefit());
            assertMoney(annual.spouseOwnBenefits(), actual.spouseOwnBenefit());
            assertMoney(annual.primarySpousalExcessBenefits(),
                    actual.primarySpousalExcessBenefit());
            assertMoney(annual.spouseSpousalExcessBenefits(),
                    actual.spouseSpousalExcessBenefit());
            assertMoney(annual.householdBenefits(), actual.householdBenefit());
        }
        assertTrue(projected.get(2031).spouseSpousalExcessBenefit().signum() > 0);
    }

    @Test
    void projectionUsesAuthoritativeAmountOnceForCashAndTaxInput() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), true);
        Projection projection = new ProjectionEngine().project(plan);
        Projection repeated = new ProjectionEngine().project(plan);

        for (int index = 0; index < projection.size(); index++) {
            ProjectionYear year = projection.getYearAt(index);
            HouseholdSocialSecurityResult result = year.getSocialSecurityResult();
            assertMoney(result.householdBenefit(), year.getGuaranteedIncome());
            TaxIncome taxIncome = new TaxIncomeCalculator().calculate(
                    plan.getHousehold(), LocalDate.of(year.getCalendarYear(), 12, 31),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    plan.getPlanningAssumptions().getSocialSecurityColaRate(),
                    plan.getPlanningAssumptions().getDeathScenarioAssumptions(), result);
            assertMoney(result.householdBenefit(), taxIncome.getSocialSecurityIncome());
            assertMoney(result.householdBenefit(), repeated.getYearAt(index)
                    .getSocialSecurityResult().householdBenefit());
        }
    }

    @Test
    void januaryFirstDeathBridgeProducesMonthlyDeathYearTransitionAndSurvivor() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2032, 62), true);
        Map<Integer, HouseholdSocialSecurityResult> results =
                new SocialSecurityProjectionIncomeProvider().calculate(plan, 2030, 2033);

        assertTrue(results.get(2031).primaryOwnBenefit().signum() > 0);
        assertMoney(BigDecimal.ZERO, results.get(2032).primaryOwnBenefit());
        assertTrue(results.get(2032).spouseSurvivorCandidate().signum() > 0);
        assertMoney(BigDecimal.ZERO, results.get(2033).primaryOwnBenefit());
    }

    @Test
    void missingSpouseSourceRemainsSafeThroughIsolatedLegacyFallback() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), false);
        Map<Integer, HouseholdSocialSecurityResult> results =
                new SocialSecurityProjectionIncomeProvider().calculate(plan, 2030, 2031);

        assertTrue(results.get(2031).primaryOwnBenefit().signum() > 0);
        assertMoney(BigDecimal.ZERO, results.get(2031).spouseOwnBenefit());
    }

    private RetirementPlan plan(
            DeathScenarioAssumptions death,
            boolean includeSpouseSource) {
        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 6, 4));
        Person spouse = new Person("Spouse", "Planner", LocalDate.of(1965, 2, 28));
        primary.addIncomeSource(source(AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4), "3000", 67));
        if (includeSpouseSource) {
            spouse.addIncomeSource(source(AccountOwnership.SPOUSE,
                    LocalDate.of(2027, 2, 28), "800", 62));
        }
        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("0.02")),
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                death, 4, LocalDate.of(2030, 7, 1));
        return new RetirementPlan(new Household(primary, spouse),
                new AccountPortfolio(), assumptions);
    }

    private SocialSecurityIncome source(
            AccountOwnership owner,
            LocalDate start,
            String benefit,
            int age) {
        return new SocialSecurityIncome("Social Security", owner, start, null,
                new BigDecimal(benefit), age, BigDecimal.ZERO, 2030);
    }

    private SocialSecurityStrategyRequest directRequest(
            RetirementPlan plan,
            int firstYear,
            int lastYear) {
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        SocialSecurityIncome primarySource = (SocialSecurityIncome)
                primary.getIncomeSources().getFirst();
        SocialSecurityIncome spouseSource = (SocialSecurityIncome)
                spouse.getIncomeSources().getFirst();
        return new SocialSecurityStrategyRequest(
                LocalDate.of(firstYear, 1, 1), LocalDate.of(lastYear, 12, 31),
                election(primary, primarySource, AccountOwnership.PRIMARY),
                election(spouse, spouseSource, AccountOwnership.SPOUSE),
                null, null, null, null,
                plan.getPlanningAssumptions().getSocialSecurityColaRate());
    }

    private SocialSecurityClaimingElection election(
            Person person,
            SocialSecurityIncome source,
            AccountOwnership owner) {
        return new SocialSecurityClaimingElection(owner, person.getBirthDate(),
                source.getFullRetirementMonthlyBenefit(),
                source.getBenefitValuationYear(), source.getStartDate());
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
