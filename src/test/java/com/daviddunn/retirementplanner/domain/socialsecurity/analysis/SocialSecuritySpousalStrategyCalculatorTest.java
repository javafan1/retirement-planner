package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecuritySpousalStrategyCalculatorTest {

    private final SocialSecurityStrategyCalculator calculator =
            new SocialSecurityStrategyCalculator();

    @Test
    void strongOwnBenefitsProduceNoSpousalExcess() {

        SocialSecurityMonthlyResult month = calculateMonth(
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1960, 1, 2), 3000, 2029,
                        LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1962, 1, 2), 2000, 2029,
                        LocalDate.of(2029, 1, 2)),
                YearMonth.of(2029, 1));

        assertMoney(BigDecimal.ZERO,
                month.primarySpousalExcessBenefit());
        assertMoney(BigDecimal.ZERO,
                month.spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("5000.00"),
                month.householdBenefit());
    }

    @Test
    void positiveSpousalExcessAtFraRetainsOwnAndAuxiliaryComponents() {

        SocialSecurityMonthlyResult month = calculateMonth(
                highPrimaryAtFra(),
                lowSpouseAtFra(),
                YearMonth.of(2029, 1));

        assertMoney(new BigDecimal("1000.00"),
                month.spouseOwnBenefit());
        assertMoney(new BigDecimal("500.00"),
                month.spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("1500.00"),
                month.spouseSelectedBenefit());
        assertEquals(
                Set.of(
                        SocialSecurityBenefitType.OWN_RETIREMENT,
                        SocialSecurityBenefitType.SPOUSAL),
                month.spouseActiveBenefitTypes());
    }

    @Test
    void workerDelayTo70DoesNotIncreaseHalfPiaSpousalBasis() {

        SocialSecurityClaimingElection delayedPrimary = election(
                AccountOwnership.PRIMARY,
                LocalDate.of(1960, 1, 2), 3000, 2030,
                LocalDate.of(2030, 1, 2));

        SocialSecurityMonthlyResult month = calculateMonth(
                delayedPrimary,
                lowSpouseAtFra(),
                YearMonth.of(2030, 1));

        assertMoney(new BigDecimal("3720.00"),
                month.primaryOwnBenefit());
        assertMoney(new BigDecimal("500.00"),
                month.spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("1500.00"),
                month.spouseSelectedBenefit());
    }

    @Test
    void earlySpouseOwnAndExcessUseSeparateReductionRules() {

        SocialSecurityMonthlyResult month = calculateMonth(
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1960, 1, 2), 3000, 2024,
                        LocalDate.of(2022, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1962, 1, 2), 1000, 2024,
                        LocalDate.of(2024, 1, 2)),
                YearMonth.of(2024, 1));

        assertMoney(new BigDecimal("700.00"),
                month.spouseOwnBenefit());
        assertMoney(new BigDecimal("325.00"),
                month.spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("1025.00"),
                month.spouseSelectedBenefit());
    }

    @Test
    void lowerEarnerGetsOwnOnlyUntilWorkerClaimsThenUnreducedExcess() {

        SocialSecurityClaimingElection delayedPrimary = election(
                AccountOwnership.PRIMARY,
                LocalDate.of(1960, 1, 2), 3000, 2030,
                LocalDate.of(2030, 1, 2));
        SocialSecurityClaimingElection earlySpouse = election(
                AccountOwnership.SPOUSE,
                LocalDate.of(1962, 1, 2), 1000, 2030,
                LocalDate.of(2024, 1, 2));

        SocialSecurityLifetimeResult result = calculate(
                delayedPrimary,
                earlySpouse,
                LocalDate.of(2029, 12, 1),
                LocalDate.of(2030, 1, 31),
                null,
                null,
                BigDecimal.ZERO);

        assertMoney(BigDecimal.ZERO,
                month(result, 2029, 12).spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("700.00"),
                month(result, 2029, 12).spouseSelectedBenefit());
        assertMoney(new BigDecimal("500.00"),
                month(result, 2030, 1).spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("1200.00"),
                month(result, 2030, 1).spouseSelectedBenefit());
    }

    @Test
    void modernDeemedFilingDoesNotPermitSpouseOnlyBeforeOwnClaimDate() {

        SocialSecurityClaimingElection primaryWorker = highPrimaryAtFra();
        SocialSecurityClaimingElection spouseClaimsAt70 = election(
                AccountOwnership.SPOUSE,
                LocalDate.of(1962, 1, 2), 1000, 2032,
                LocalDate.of(2032, 1, 2));

        SocialSecurityLifetimeResult result = calculate(
                primaryWorker,
                spouseClaimsAt70,
                LocalDate.of(2031, 12, 1),
                LocalDate.of(2032, 1, 31),
                null,
                null,
                BigDecimal.ZERO);

        assertMoney(BigDecimal.ZERO,
                month(result, 2031, 12).spouseSelectedBenefit());
        assertMoney(new BigDecimal("1240.00"),
                month(result, 2032, 1).spouseOwnBenefit());
        assertMoney(new BigDecimal("500.00"),
                month(result, 2032, 1).spouseSpousalExcessBenefit());
    }

    @Test
    void bothOwnerDirectionsAreEvaluatedFromBenefitValues() {

        SocialSecurityMonthlyResult primaryAuxiliary = calculateMonth(
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1960, 1, 2), 1000, 2029,
                        LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1962, 1, 2), 3000, 2029,
                        LocalDate.of(2029, 1, 2)),
                YearMonth.of(2029, 1));

        assertMoney(new BigDecimal("500.00"),
                primaryAuxiliary.primarySpousalExcessBenefit());
        assertMoney(BigDecimal.ZERO,
                primaryAuxiliary.spouseSpousalExcessBenefit());

        SocialSecurityMonthlyResult spouseAuxiliary = calculateMonth(
                highPrimaryAtFra(),
                lowSpouseAtFra(),
                YearMonth.of(2029, 1));

        assertMoney(BigDecimal.ZERO,
                spouseAuxiliary.primarySpousalExcessBenefit());
        assertMoney(new BigDecimal("500.00"),
                spouseAuxiliary.spouseSpousalExcessBenefit());
    }

    @Test
    void workerDeathStopsNormalAuxiliaryButSurvivorOwnContinues() {

        SocialSecurityLifetimeResult result = calculate(
                highPrimaryAtFra(),
                lowSpouseAtFra(),
                LocalDate.of(2030, 5, 1),
                LocalDate.of(2030, 6, 30),
                LocalDate.of(2030, 6, 15),
                null,
                BigDecimal.ZERO);

        assertMoney(new BigDecimal("500.00"),
                month(result, 2030, 5).spouseSpousalExcessBenefit());
        assertMoney(BigDecimal.ZERO,
                month(result, 2030, 6).spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("1000.00"),
                month(result, 2030, 6).spouseSelectedBenefit());
        assertEquals(SocialSecurityHouseholdLifeState.SPOUSE_ONLY,
                month(result, 2030, 6).lifeState());
    }

    @Test
    void auxiliaryClaimantDeathStopsAllClaimantComponents() {

        SocialSecurityLifetimeResult result = calculate(
                highPrimaryAtFra(),
                lowSpouseAtFra(),
                LocalDate.of(2030, 5, 1),
                LocalDate.of(2030, 6, 30),
                null,
                LocalDate.of(2030, 6, 15),
                BigDecimal.ZERO);

        SocialSecurityMonthlyResult deathMonth = month(result, 2030, 6);
        assertMoney(BigDecimal.ZERO, deathMonth.spouseOwnBenefit());
        assertMoney(BigDecimal.ZERO,
                deathMonth.spouseSpousalExcessBenefit());
        assertMoney(BigDecimal.ZERO,
                deathMonth.spouseSelectedBenefit());
    }

    @Test
    void differentValuationYearsAreComparedInPaymentMonthDollars() {

        SocialSecurityMonthlyResult month = calculateMonth(
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1960, 1, 2), 3000, 2030,
                        LocalDate.of(2027, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1962, 1, 2), 1000, 2029,
                        LocalDate.of(2029, 1, 2)),
                YearMonth.of(2031, 1),
                new BigDecimal("0.10"));

        assertMoney(new BigDecimal("1210.00"),
                month.spouseOwnBenefit());
        assertMoney(new BigDecimal("440.00"),
                month.spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("1650.00"),
                month.spouseSelectedBenefit());
    }

    @Test
    void componentRoundingReconcilesWithoutPennyDrift() {

        SocialSecurityMonthlyResult month = calculateMonth(
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1960, 1, 2), 3001, 2024,
                        LocalDate.of(2022, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1962, 1, 2), 1001, 2024,
                        LocalDate.of(2024, 1, 2)),
                YearMonth.of(2024, 1));

        assertMoney(new BigDecimal("700.70"),
                month.spouseOwnBenefit());
        assertMoney(new BigDecimal("324.68"),
                month.spouseSpousalExcessBenefit());
        assertMoney(new BigDecimal("1025.38"),
                month.spouseSelectedBenefit());
    }

    @Test
    void claimantDeathBeforeWorkerClaimPreventsFutureAuxiliaryBenefit() {

        SocialSecurityLifetimeResult result = calculate(
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1960, 1, 2), 3000, 2030,
                        LocalDate.of(2030, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1962, 1, 2), 1000, 2030,
                        LocalDate.of(2024, 1, 2)),
                LocalDate.of(2029, 12, 1),
                LocalDate.of(2030, 1, 31),
                null,
                LocalDate.of(2029, 12, 15),
                BigDecimal.ZERO);

        assertMoney(BigDecimal.ZERO,
                month(result, 2030, 1).spouseSpousalExcessBenefit());
        assertMoney(BigDecimal.ZERO,
                month(result, 2030, 1).spouseSelectedBenefit());
    }

    @Test
    void spousalComponentsReconcileAcrossMonthlyAnnualAndLifetimeResults() {

        SocialSecurityLifetimeResult result = calculate(
                highPrimaryAtFra(),
                lowSpouseAtFra(),
                LocalDate.of(2029, 1, 1),
                LocalDate.of(2030, 12, 31),
                null,
                null,
                new BigDecimal("0.02"));

        BigDecimal monthlySpousal = result.monthlyResults().stream()
                .map(SocialSecurityMonthlyResult::spouseSpousalExcessBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal annualSpousal = result.annualResults().stream()
                .map(SocialSecurityAnnualResult::spouseSpousalExcessBenefits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        result.monthlyResults().forEach(value -> {
            assertMoney(
                    value.primaryOwnBenefit().add(
                            value.primarySpousalExcessBenefit()),
                    value.primarySelectedBenefit());
            assertMoney(
                    value.spouseOwnBenefit().add(
                            value.spouseSpousalExcessBenefit()),
                    value.spouseSelectedBenefit());
        });

        assertMoney(monthlySpousal, annualSpousal);
        assertMoney(monthlySpousal,
                result.spouseTotalSpousalExcessBenefits());
    }

    @Test
    void historicalRestrictedApplicationCohortsAreExplicitlyUnsupported() {

        assertThrows(
                IllegalArgumentException.class,
                () -> calculate(
                        election(AccountOwnership.PRIMARY,
                                LocalDate.of(1953, 1, 1), 3000, 2020,
                                LocalDate.of(2019, 12, 31)),
                        lowSpouseAtFra(),
                        LocalDate.of(2029, 1, 1),
                        LocalDate.of(2029, 1, 31),
                        null,
                        null,
                        BigDecimal.ZERO));
    }

    private SocialSecurityClaimingElection highPrimaryAtFra() {

        return election(AccountOwnership.PRIMARY,
                LocalDate.of(1960, 1, 2), 3000, 2029,
                LocalDate.of(2027, 1, 2));
    }

    private SocialSecurityClaimingElection lowSpouseAtFra() {

        return election(AccountOwnership.SPOUSE,
                LocalDate.of(1962, 1, 2), 1000, 2029,
                LocalDate.of(2029, 1, 2));
    }

    private SocialSecurityMonthlyResult calculateMonth(
            SocialSecurityClaimingElection primary,
            SocialSecurityClaimingElection spouse,
            YearMonth month) {

        return calculateMonth(primary, spouse, month, BigDecimal.ZERO);
    }

    private SocialSecurityMonthlyResult calculateMonth(
            SocialSecurityClaimingElection primary,
            SocialSecurityClaimingElection spouse,
            YearMonth month,
            BigDecimal cola) {

        return calculate(
                        primary,
                        spouse,
                        month.atDay(1),
                        month.atEndOfMonth(),
                        null,
                        null,
                        cola)
                .monthlyResults()
                .getFirst();
    }

    private SocialSecurityLifetimeResult calculate(
            SocialSecurityClaimingElection primary,
            SocialSecurityClaimingElection spouse,
            LocalDate analysisDate,
            LocalDate analysisEndDate,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate,
            BigDecimal cola) {

        return calculator.calculate(
                new SocialSecurityStrategyRequest(
                        analysisDate,
                        analysisEndDate,
                        primary,
                        spouse,
                        primaryDeathDate,
                        spouseDeathDate,
                        cola));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            int fraBenefit,
            int valuationYear,
            LocalDate claimDate) {

        return new SocialSecurityClaimingElection(
                owner,
                birthDate,
                BigDecimal.valueOf(fraBenefit),
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

    private void assertMoney(
            BigDecimal expected,
            BigDecimal actual) {

        assertEquals(0, expected.compareTo(actual));
    }
}
