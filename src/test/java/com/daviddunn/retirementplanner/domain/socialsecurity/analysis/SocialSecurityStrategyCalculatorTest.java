package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityStrategyCalculatorTest {

    private final SocialSecurityStrategyCalculator calculator =
            new SocialSecurityStrategyCalculator();

    @Test
    void claimAt62UsesMonthlyEarlyRetirementReduction() {

        SocialSecurityMonthlyResult result = calculateOneMonth(
                election(
                        AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 6, 4),
                        new BigDecimal("3000"),
                        2030,
                        LocalDate.of(2025, 6, 4)),
                YearMonth.of(2025, 6));

        assertMoney(new BigDecimal("2100.00"), result.primaryOwnBenefit());
    }

    @Test
    void claimAtFraUsesFullRetirementBenefit() {

        SocialSecurityMonthlyResult result = calculateOneMonth(
                election(
                        AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 6, 4),
                        new BigDecimal("3000"),
                        2030,
                        LocalDate.of(2030, 6, 4)),
                YearMonth.of(2030, 6));

        assertMoney(new BigDecimal("3000.00"), result.primaryOwnBenefit());
    }

    @Test
    void claimAt70UsesMonthlyDelayedRetirementCredits() {

        SocialSecurityMonthlyResult result = calculateOneMonth(
                election(
                        AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 6, 4),
                        new BigDecimal("3000"),
                        2033,
                        LocalDate.of(2033, 6, 4)),
                YearMonth.of(2033, 6));

        assertMoney(new BigDecimal("3720.00"), result.primaryOwnBenefit());
    }

    @Test
    void claimDateSupportsFraWithMonthComponent() {

        SocialSecurityMonthlyResult result = calculateOneMonth(
                election(
                        AccountOwnership.PRIMARY,
                        LocalDate.of(1958, 6, 15),
                        new BigDecimal("3000"),
                        2025,
                        LocalDate.of(2025, 2, 15)),
                YearMonth.of(2025, 2));

        assertMoney(new BigDecimal("3000.00"), result.primaryOwnBenefit());
    }

    @Test
    void juneClaimIncludesJuneThroughDecemberEntitlementMonths() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                request(
                        LocalDate.of(2030, 1, 1),
                        LocalDate.of(2030, 12, 31),
                        election(AccountOwnership.PRIMARY,
                                LocalDate.of(1963, 6, 4),
                                new BigDecimal("3000"), 2030,
                                LocalDate.of(2030, 6, 4)),
                        zeroSpouseElection(),
                        null,
                        null,
                        BigDecimal.ZERO));

        assertEquals(12, result.monthlyResults().size());
        assertMoney(BigDecimal.ZERO,
                month(result, 2030, 5).primaryOwnBenefit());
        assertMoney(new BigDecimal("3000.00"),
                month(result, 2030, 6).primaryOwnBenefit());
        assertMoney(new BigDecimal("21000.00"),
                result.annualResults().getFirst().primaryOwnBenefits());
    }

    @Test
    void januaryAndDecemberClaimsAggregateFromMonthlyEntitlement() {

        SocialSecurityLifetimeResult january = calculator.calculate(
                requestForSingleYearClaimMonth(1));
        SocialSecurityLifetimeResult december = calculator.calculate(
                requestForSingleYearClaimMonth(12));

        assertMoney(new BigDecimal("36000.00"),
                january.householdLifetimeNominalBenefits());
        assertMoney(new BigDecimal("3000.00"),
                december.householdLifetimeNominalBenefits());
    }

    @Test
    void annualColaChangesBenefitsInJanuaryNotEveryMonth() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                request(
                        LocalDate.of(2030, 12, 1),
                        LocalDate.of(2031, 2, 28),
                        election(AccountOwnership.PRIMARY,
                                LocalDate.of(1963, 6, 4),
                                new BigDecimal("3000"), 2030,
                                LocalDate.of(2030, 6, 4)),
                        zeroSpouseElection(),
                        null,
                        null,
                        new BigDecimal("0.02")));

        assertMoney(new BigDecimal("3000.00"),
                month(result, 2030, 12).primaryOwnBenefit());
        assertMoney(new BigDecimal("3060.00"),
                month(result, 2031, 1).primaryOwnBenefit());
        assertMoney(new BigDecimal("3060.00"),
                month(result, 2031, 2).primaryOwnBenefit());
    }

    @Test
    void primaryDiesFirstThenSpouseAndSecondDeathAreRepresented() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                request(
                        LocalDate.of(2031, 5, 1),
                        null,
                        activePrimaryElection(),
                        activeSpouseElection(),
                        LocalDate.of(2031, 6, 15),
                        LocalDate.of(2032, 9, 20),
                        BigDecimal.ZERO));

        assertEquals(SocialSecurityHouseholdLifeState.BOTH_ALIVE,
                month(result, 2031, 5).lifeState());
        assertEquals(SocialSecurityHouseholdLifeState.SPOUSE_ONLY,
                month(result, 2031, 6).lifeState());
        assertEquals(SocialSecurityHouseholdLifeState.NEITHER_ALIVE,
                month(result, 2032, 9).lifeState());
        assertMoney(BigDecimal.ZERO,
                month(result, 2031, 6).primarySelectedBenefit());
        assertMoney(BigDecimal.ZERO,
                month(result, 2032, 9).householdBenefit());
    }

    @Test
    void spouseDiesFirstThenPrimaryContinuesOwnBenefit() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                request(
                        LocalDate.of(2031, 5, 1),
                        null,
                        activePrimaryElection(),
                        activeSpouseElection(),
                        LocalDate.of(2032, 9, 20),
                        LocalDate.of(2031, 6, 15),
                        BigDecimal.ZERO));

        SocialSecurityMonthlyResult deathMonth = month(result, 2031, 6);
        assertEquals(SocialSecurityHouseholdLifeState.PRIMARY_ONLY,
                deathMonth.lifeState());
        assertMoney(new BigDecimal("3000.00"),
                deathMonth.primarySelectedBenefit());
        assertMoney(BigDecimal.ZERO,
                deathMonth.spouseSelectedBenefit());
    }

    @Test
    void sameMonthDeathsProduceNeitherAliveForThatMonth() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                request(
                        LocalDate.of(2031, 5, 1),
                        null,
                        activePrimaryElection(),
                        activeSpouseElection(),
                        LocalDate.of(2031, 6, 1),
                        LocalDate.of(2031, 6, 30),
                        BigDecimal.ZERO));

        assertEquals(SocialSecurityHouseholdLifeState.NEITHER_ALIVE,
                month(result, 2031, 6).lifeState());
        assertMoney(BigDecimal.ZERO,
                month(result, 2031, 6).householdBenefit());
    }

    @Test
    void bothSurviveRequiresAndUsesExplicitFiniteHorizon() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                request(
                        LocalDate.of(2030, 3, 15),
                        LocalDate.of(2030, 5, 20),
                        activePrimaryElection(),
                        activeSpouseElection(),
                        null,
                        null,
                        BigDecimal.ZERO));

        assertEquals(3, result.monthlyResults().size());
        assertEquals(YearMonth.of(2030, 3),
                result.monthlyResults().getFirst().month());
        assertEquals(YearMonth.of(2030, 5),
                result.monthlyResults().getLast().month());
    }

    @Test
    void everyMonthlyAnnualAndLifetimeAmountReconciles() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                request(
                        LocalDate.of(2030, 1, 1),
                        null,
                        activePrimaryElection(),
                        activeSpouseElection(),
                        LocalDate.of(2031, 4, 10),
                        LocalDate.of(2032, 8, 20),
                        new BigDecimal("0.02")));

        result.monthlyResults().forEach(month ->
                assertMoney(
                        month.primarySelectedBenefit().add(
                                month.spouseSelectedBenefit()),
                        month.householdBenefit()));

        BigDecimal monthlyTotal = result.monthlyResults().stream()
                .map(SocialSecurityMonthlyResult::householdBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal annualTotal = result.annualResults().stream()
                .map(SocialSecurityAnnualResult::householdBenefits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertMoney(monthlyTotal, annualTotal);
        assertMoney(monthlyTotal,
                result.householdLifetimeNominalBenefits());
    }

    @Test
    void calculationDoesNotMutatePlanPersonOrSource() {

        Person primary = new Person(
                "Primary", "Planner", LocalDate.of(1963, 6, 4));
        Person spouse = new Person(
                "Spouse", "Planner", LocalDate.of(1965, 2, 28));
        SocialSecurityIncome source = new SocialSecurityIncome(
                "Primary Social Security",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4),
                null,
                new BigDecimal("3000"),
                67,
                BigDecimal.ZERO,
                2030);
        primary.addIncomeSource(source);
        RetirementPlan plan = plan(primary, spouse);

        SocialSecurityStrategyRequest request = request(
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 12, 31),
                election(AccountOwnership.PRIMARY,
                        primary.getBirthDate(),
                        source.getFullRetirementMonthlyBenefit(),
                        source.getBenefitValuationYear(),
                        LocalDate.of(2033, 6, 4)),
                zeroSpouseElection(),
                null,
                null,
                BigDecimal.ZERO);

        calculator.calculate(request);

        assertEquals(1, primary.getIncomeSources().size());
        assertNotSame(source, request.primaryElection());
        assertEquals(LocalDate.of(2030, 6, 4), source.getStartDate());
        assertEquals(67, source.getClaimingAge());
        assertEquals(plan.getHousehold().getPrimaryPerson(), primary);
    }

    @Test
    void invalidRequestsFailWithActionableValidation() {

        assertThrows(IllegalArgumentException.class,
                () -> election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 6, 4),
                        new BigDecimal("3000"), 2030,
                        LocalDate.of(2025, 6, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 6, 4),
                        new BigDecimal("3000"), 2030,
                        LocalDate.of(2033, 6, 5)));
        assertThrows(IllegalArgumentException.class,
                () -> request(LocalDate.of(2030, 1, 1), null,
                        activePrimaryElection(), activeSpouseElection(),
                        null, null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> request(LocalDate.of(2030, 1, 1),
                        LocalDate.of(2029, 12, 31),
                        activePrimaryElection(), activeSpouseElection(),
                        null, null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> request(LocalDate.of(2030, 1, 1),
                        LocalDate.of(2031, 12, 31),
                        activePrimaryElection(), activeSpouseElection(),
                        LocalDate.of(1960, 1, 1), null,
                        BigDecimal.ZERO));
    }

    @Test
    void resultListsAreImmutable() {

        SocialSecurityLifetimeResult result = calculator.calculate(
                requestForSingleYearClaimMonth(6));

        assertThrows(UnsupportedOperationException.class,
                () -> result.monthlyResults().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.annualResults().clear());
    }

    private SocialSecurityMonthlyResult calculateOneMonth(
            SocialSecurityClaimingElection primary,
            YearMonth month) {

        LocalDate date = month.atDay(1);

        return calculator.calculate(
                        request(
                                date,
                                month.atEndOfMonth(),
                                primary,
                                zeroSpouseElection(),
                                null,
                                null,
                                BigDecimal.ZERO))
                .monthlyResults()
                .getFirst();
    }

    private SocialSecurityStrategyRequest requestForSingleYearClaimMonth(
            int claimMonth) {

        return request(
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 12, 31),
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, claimMonth, 4),
                        new BigDecimal("3000"), 2030,
                        LocalDate.of(2030, claimMonth, 4)),
                zeroSpouseElection(),
                null,
                null,
                BigDecimal.ZERO);
    }

    private SocialSecurityStrategyRequest request(
            LocalDate analysisDate,
            LocalDate analysisEndDate,
            SocialSecurityClaimingElection primary,
            SocialSecurityClaimingElection spouse,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate,
            BigDecimal cola) {

        return new SocialSecurityStrategyRequest(
                analysisDate,
                analysisEndDate,
                primary,
                spouse,
                primaryDeathDate,
                spouseDeathDate,
                cola);
    }

    private SocialSecurityClaimingElection activePrimaryElection() {

        return election(AccountOwnership.PRIMARY,
                LocalDate.of(1963, 6, 4),
                new BigDecimal("3000"), 2030,
                LocalDate.of(2030, 6, 4));
    }

    private SocialSecurityClaimingElection activeSpouseElection() {

        return election(AccountOwnership.SPOUSE,
                LocalDate.of(1965, 2, 28),
                new BigDecimal("2000"), 2030,
                LocalDate.of(2030, 2, 28));
    }

    private SocialSecurityClaimingElection zeroSpouseElection() {

        return election(AccountOwnership.SPOUSE,
                LocalDate.of(1965, 2, 28),
                BigDecimal.ZERO, 2030,
                LocalDate.of(2032, 2, 28));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            BigDecimal monthlyBenefit,
            int valuationYear,
            LocalDate claimDate) {

        return new SocialSecurityClaimingElection(
                owner,
                birthDate,
                monthlyBenefit,
                valuationYear,
                claimDate);
    }

    private SocialSecurityMonthlyResult month(
            SocialSecurityLifetimeResult result,
            int year,
            int month) {

        YearMonth expected = YearMonth.of(year, month);

        return result.monthlyResults().stream()
                .filter(value -> value.month().equals(expected))
                .findFirst()
                .orElseThrow();
    }

    private RetirementPlan plan(
            Person primary,
            Person spouse) {

        return new RetirementPlan(
                new Household(primary, spouse),
                new AccountPortfolio(),
                new PlanningAssumptions(
                        new EconomicAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        new TaxAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        2,
                        LocalDate.of(2030, 1, 1)));
    }

    private void assertMoney(
            BigDecimal expected,
            BigDecimal actual) {

        assertEquals(0, expected.compareTo(actual));
    }
}
