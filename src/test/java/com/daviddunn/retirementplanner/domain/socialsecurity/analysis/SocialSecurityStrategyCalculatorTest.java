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
import com.daviddunn.retirementplanner.domain.socialsecurity.SocialSecurityBenefitReductionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityStrategyCalculatorTest {

    private final SocialSecurityStrategyCalculator calculator =
            new SocialSecurityStrategyCalculator();

    @Test
    void effectiveYearUsesPaymentYearAndPermanentReductionDoesNotCompoundWithCola() {
        var base = election(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4),
                new BigDecimal("1000"), 2030, LocalDate.of(2030, 6, 4));
        var request = new SocialSecurityStrategyRequest(
                LocalDate.of(2030, 1, 1), LocalDate.of(2032, 12, 31),
                base, zeroSpouseElection(), null, null, null, null,
                new BigDecimal("0.02"),
                new SocialSecurityBenefitReductionPolicy(new BigDecimal("22"), 2031));
        var result = calculator.calculate(request);

        assertMoney(BigDecimal.ZERO, month(result, 2030, 1).primaryOwnBenefit());
        assertMoney(new BigDecimal("1000.00"), month(result, 2030, 6).primaryOwnBenefit());
        assertMoney(new BigDecimal("795.60"), month(result, 2031, 1).primaryOwnBenefit());
        // 2032 scheduled amount is 1040.40 * .78; the 22% is not compounded on 795.60.
        assertMoney(new BigDecimal("811.51"), month(result, 2032, 1).primaryOwnBenefit());
        assertMoney(month(result, 2032, 1).primarySelectedBenefit(),
                month(result, 2032, 1).householdBenefit());
    }

    @Test
    void reductionReconcilesSpousalAndSurvivorComponentsAfterSelection() {
        var primary = election(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4),
                new BigDecimal("1000"), 2030, LocalDate.of(2030, 1, 1));
        var spouse = election(AccountOwnership.SPOUSE, LocalDate.of(1965, 2, 28),
                new BigDecimal("5000"), 2030, LocalDate.of(2030, 1, 1));
        var reduction = new SocialSecurityBenefitReductionPolicy(new BigDecimal("20"), 2031);
        var bothAlive = calculator.calculate(new SocialSecurityStrategyRequest(
                LocalDate.of(2030, 1, 1), LocalDate.of(2031, 12, 31), primary, spouse,
                null, null, null, null, BigDecimal.ZERO, reduction)).monthlyResults();
        var baselineResult = bothAlive.getFirst();
        assertTrue(baselineResult.primarySpousalExcessBenefit().signum() > 0);
        assertEquals(0, baselineResult.householdBenefit().compareTo(
                baselineResult.primarySelectedBenefit().add(baselineResult.spouseSelectedBenefit())));
        var reducedCoupleResult = bothAlive.get(12);
        assertMoney(baselineResult.primaryOwnBenefit().multiply(new BigDecimal("0.80"))
                        .setScale(2, java.math.RoundingMode.HALF_UP),
                reducedCoupleResult.primaryOwnBenefit());
        assertMoney(reducedCoupleResult.primarySelectedBenefit().add(reducedCoupleResult.spouseSelectedBenefit()),
                reducedCoupleResult.householdBenefit());

        var survivorWorker = election(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4),
                new BigDecimal("5000"), 2030, LocalDate.of(2030, 1, 1));
        var survivorClaimant = election(AccountOwnership.SPOUSE, LocalDate.of(1965, 2, 28),
                new BigDecimal("1000"), 2030, LocalDate.of(2030, 1, 1));
        var survivor = calculator.calculate(new SocialSecurityStrategyRequest(
                LocalDate.of(2030, 1, 1), LocalDate.of(2031, 12, 31), survivorWorker, survivorClaimant,
                null, LocalDate.of(2030, 6, 1), LocalDate.of(2030, 5, 15), null,
                BigDecimal.ZERO, reduction)).monthlyResults();
        var transition = survivor.stream().filter(value -> value.month().equals(YearMonth.of(2030, 6)))
                .findFirst().orElseThrow();
        assertTrue(transition.spouseSurvivorBenefit().signum() > 0);
        var reducedSurvivor = survivor.stream().filter(value -> value.month().equals(YearMonth.of(2031, 6)))
                .findFirst().orElseThrow();
        assertEquals(0, reducedSurvivor.householdBenefit().compareTo(
                reducedSurvivor.primarySelectedBenefit().add(reducedSurvivor.spouseSelectedBenefit())));
        assertTrue(reducedSurvivor.spouseSurvivorBenefit().signum() > 0);
    }

    @Test
    void reductionCacheKeySeparatesRatesAndYearsButNormalizesScale() {
        var primary = activePrimaryElection();
        var spouse = activeSpouseElection();
        SocialSecurityStrategyRequest request = requestWithPolicy(primary, spouse,
                new BigDecimal("17.0"), 2040);
        assertEquals(SocialSecurityStrategyCalculator.scheduleKey(request),
                SocialSecurityStrategyCalculator.scheduleKey(requestWithPolicy(primary, spouse,
                        new BigDecimal("17.00"), 2040)));
        assertNotEquals(SocialSecurityStrategyCalculator.scheduleKey(request),
                SocialSecurityStrategyCalculator.scheduleKey(requestWithPolicy(primary, spouse,
                        new BigDecimal("22"), 2040)));
        assertNotEquals(SocialSecurityStrategyCalculator.scheduleKey(request),
                SocialSecurityStrategyCalculator.scheduleKey(requestWithPolicy(primary, spouse,
                        new BigDecimal("17"), 2041)));
    }

    @Test
    void ownOnlyMonthlyAndAnnualOverloadsApplyPolicyAndOldOverloadsRemainNoReduction() {
        var election = election(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4),
                new BigDecimal("1000"), 2030, LocalDate.of(2030, 6, 4));
        var policy = new SocialSecurityBenefitReductionPolicy(new BigDecimal("10"), 2031);
        var monthly = calculator.calculateOwnRetirementMonthly(election,
                YearMonth.of(2030, 1), YearMonth.of(2031, 12), BigDecimal.ZERO, policy);
        assertMoney(new BigDecimal("1000.00"), monthly.get(YearMonth.of(2030, 12)));
        assertMoney(new BigDecimal("900.00"), monthly.get(YearMonth.of(2031, 1)));
        var annual = calculator.calculateOwnRetirement(election, 2030, 2031, BigDecimal.ZERO, null, policy);
        assertMoney(new BigDecimal("7000.00"), annual.get(2030));
        assertMoney(new BigDecimal("10800.00"), annual.get(2031));
        assertMoney(new BigDecimal("7000.00"), calculator
                .calculateOwnRetirement(election, 2030, 2030, BigDecimal.ZERO).get(2030));
    }

    private SocialSecurityStrategyRequest requestWithPolicy(
            SocialSecurityClaimingElection primary,
            SocialSecurityClaimingElection spouse,
            BigDecimal percent,
            int effectiveYear) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2030, 1, 1), LocalDate.of(2045, 12, 31), primary, spouse,
                null, null, null, null, BigDecimal.ZERO,
                new SocialSecurityBenefitReductionPolicy(percent, effectiveYear));
    }

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
