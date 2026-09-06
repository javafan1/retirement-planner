package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecuritySurvivorStrategyCalculatorTest {

    private final SocialSecurityStrategyCalculator calculator =
            new SocialSecurityStrategyCalculator();

    @Test
    void survivorFirstThenHigherOwnAt70SwitchesMonthByMonth() {

        SocialSecurityLifetimeResult result = calculate(
                election(AccountOwnership.PRIMARY, 1960, 1, 2,
                        3000, 2027, LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE, 1962, 1, 2,
                        2500, 2032, LocalDate.of(2032, 1, 2)),
                null,
                LocalDate.of(2022, 1, 2),
                LocalDate.of(2024, 1, 15),
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2032, 1, 31));

        SocialSecurityMonthlyResult survivorOnly = month(result, 2024, 1);
        assertMoney(BigDecimal.ZERO, survivorOnly.spouseOwnBenefit());
        assertMoney(new BigDecimal("2389.29"),
                survivorOnly.spouseSurvivorBenefit());
        assertEquals(Set.of(SocialSecurityBenefitType.SURVIVOR),
                survivorOnly.spouseActiveBenefitTypes());

        SocialSecurityMonthlyResult ownAt70 = month(result, 2032, 1);
        assertMoney(new BigDecimal("3100.00"), ownAt70.spouseOwnBenefit());
        assertMoney(BigDecimal.ZERO, ownAt70.spouseSurvivorBenefit());
        assertMoney(new BigDecimal("3100.00"),
                ownAt70.spouseSelectedBenefit());
    }

    @Test
    void ownFirstThenHigherSurvivorAddsOnlySurvivorExcess() {

        SocialSecurityLifetimeResult result = calculate(
                election(AccountOwnership.PRIMARY, 1960, 1, 2,
                        3000, 2027, LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE, 1962, 1, 2,
                        2000, 2030, LocalDate.of(2024, 1, 2)),
                null,
                LocalDate.of(2029, 1, 2),
                LocalDate.of(2030, 1, 15),
                null,
                LocalDate.of(2029, 12, 1),
                LocalDate.of(2030, 1, 31));

        assertMoney(new BigDecimal("1400.00"),
                month(result, 2029, 12).spouseSelectedBenefit());

        SocialSecurityMonthlyResult deathMonth = month(result, 2030, 1);
        assertMoney(new BigDecimal("1400.00"), deathMonth.spouseOwnBenefit());
        assertMoney(new BigDecimal("1600.00"),
                deathMonth.spouseSurvivorBenefit());
        assertMoney(new BigDecimal("3000.00"),
                deathMonth.spouseSelectedBenefit());
        assertEquals(Set.of(
                        SocialSecurityBenefitType.OWN_RETIREMENT,
                        SocialSecurityBenefitType.SURVIVOR),
                deathMonth.spouseActiveBenefitTypes());
    }

    @Test
    void lowerSurvivorBenefitDoesNotReduceActiveOwnBenefit() {

        SocialSecurityMonthlyResult result = calculate(
                election(AccountOwnership.PRIMARY, 1960, 1, 2,
                        1000, 2027, LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE, 1962, 1, 2,
                        3000, 2029, LocalDate.of(2029, 1, 2)),
                null,
                LocalDate.of(2029, 1, 2),
                LocalDate.of(2030, 1, 15),
                null,
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31))
                .monthlyResults().getFirst();

        assertMoney(new BigDecimal("3000.00"), result.spouseOwnBenefit());
        assertMoney(BigDecimal.ZERO, result.spouseSurvivorBenefit());
        assertMoney(new BigDecimal("3000.00"),
                result.spouseSelectedBenefit());
    }

    @Test
    void normalSpousalExcessStopsAndSurvivorBeginsInDeathMonth() {

        SocialSecurityLifetimeResult result = calculate(
                election(AccountOwnership.PRIMARY, 1960, 1, 2,
                        3000, 2029, LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE, 1962, 1, 2,
                        1000, 2029, LocalDate.of(2029, 1, 2)),
                null,
                LocalDate.of(2029, 1, 2),
                LocalDate.of(2030, 6, 15),
                null,
                LocalDate.of(2030, 5, 1),
                LocalDate.of(2030, 6, 30));

        assertMoney(new BigDecimal("500.00"),
                month(result, 2030, 5).spouseSpousalExcessBenefit());
        SocialSecurityMonthlyResult deathMonth = month(result, 2030, 6);
        assertMoney(BigDecimal.ZERO,
                deathMonth.spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("2000.00"),
                deathMonth.spouseSurvivorBenefit());
        assertMoney(new BigDecimal("3000.00"),
                deathMonth.spouseSelectedBenefit());
    }

    @Test
    void secondDeathAndSameMonthDeathsProduceZero() {

        SocialSecurityLifetimeResult result = calculate(
                election(AccountOwnership.PRIMARY, 1960, 1, 2,
                        3000, 2027, LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE, 1962, 1, 2,
                        1000, 2029, LocalDate.of(2029, 1, 2)),
                null,
                LocalDate.of(2029, 1, 2),
                LocalDate.of(2030, 1, 15),
                LocalDate.of(2031, 1, 15),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2031, 1, 31));

        assertMoney(new BigDecimal("3000.00"),
                month(result, 2030, 1).householdBenefit());
        assertMoney(BigDecimal.ZERO,
                month(result, 2031, 1).householdBenefit());

        SocialSecurityLifetimeResult sameMonth = calculate(
                election(AccountOwnership.PRIMARY, 1960, 1, 2,
                        3000, 2027, LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE, 1962, 1, 2,
                        1000, 2029, LocalDate.of(2029, 1, 2)),
                null,
                LocalDate.of(2029, 1, 2),
                LocalDate.of(2030, 1, 15),
                LocalDate.of(2030, 1, 20),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31));
        assertMoney(BigDecimal.ZERO,
                sameMonth.monthlyResults().getFirst().householdBenefit());
    }

    @Test
    void survivorComponentsReconcileMonthlyAnnualAndLifetime() {

        SocialSecurityLifetimeResult result = calculate(
                election(AccountOwnership.PRIMARY, 1960, 1, 2,
                        3000, 2027, LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE, 1962, 1, 2,
                        1000, 2029, LocalDate.of(2029, 1, 2)),
                null,
                LocalDate.of(2029, 1, 2),
                LocalDate.of(2030, 1, 15),
                LocalDate.of(2031, 1, 15),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2031, 1, 31));

        BigDecimal monthlySurvivor = result.monthlyResults().stream()
                .map(SocialSecurityMonthlyResult::spouseSurvivorBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal annualSurvivor = result.annualResults().stream()
                .map(SocialSecurityAnnualResult::spouseSurvivorBenefits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        result.monthlyResults().forEach(value -> {
            assertMoney(value.primaryOwnBenefit()
                            .add(value.primarySpousalExcessBenefit())
                            .add(value.primarySurvivorBenefit()),
                    value.primarySelectedBenefit());
            assertMoney(value.spouseOwnBenefit()
                            .add(value.spouseSpousalExcessBenefit())
                            .add(value.spouseSurvivorBenefit()),
                    value.spouseSelectedBenefit());
        });
        assertMoney(monthlySurvivor, annualSurvivor);
        assertMoney(monthlySurvivor,
                result.spouseTotalSurvivorBenefits());
    }

    @Test
    void survivorInputValidationIsActionable() {

        SocialSecurityClaimingElection primary = election(
                AccountOwnership.PRIMARY, 1960, 1, 2,
                3000, 2027, LocalDate.of(2027, 1, 2));
        SocialSecurityClaimingElection spouse = election(
                AccountOwnership.SPOUSE, 1962, 1, 2,
                1000, 2029, LocalDate.of(2029, 1, 2));

        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityStrategyRequest(
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2030, 1, 31),
                        primary,
                        spouse,
                        null,
                        LocalDate.of(2021, 1, 2),
                        null,
                        LocalDate.of(2030, 1, 15),
                        BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityStrategyRequest(
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2030, 1, 31),
                        primary,
                        spouse,
                        null,
                        LocalDate.of(2029, 1, 2),
                        null,
                        null,
                        BigDecimal.ZERO));
    }

    private SocialSecurityLifetimeResult calculate(
            SocialSecurityClaimingElection primary,
            SocialSecurityClaimingElection spouse,
            LocalDate primarySurvivorClaimDate,
            LocalDate spouseSurvivorClaimDate,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate,
            LocalDate analysisDate,
            LocalDate analysisEndDate) {

        return calculator.calculate(new SocialSecurityStrategyRequest(
                analysisDate,
                analysisEndDate,
                primary,
                spouse,
                primarySurvivorClaimDate,
                spouseSurvivorClaimDate,
                primaryDeathDate,
                spouseDeathDate,
                BigDecimal.ZERO));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            int year,
            int month,
            int day,
            int fraBenefit,
            int valuationYear,
            LocalDate retirementClaimDate) {

        return new SocialSecurityClaimingElection(
                owner,
                LocalDate.of(year, month, day),
                BigDecimal.valueOf(fraBenefit),
                valuationYear,
                retirementClaimDate);
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

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
