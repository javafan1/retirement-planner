package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityClaimingGridCalculatorTest {

    private final SocialSecurityClaimingGridCalculator calculator =
            new SocialSecurityClaimingGridCalculator();

    @Test
    void oneByOneCellEqualsDirectStrategyCalculationAndValuation() {
        SocialSecurityClaimingGridRequest request = request(
                standardStrategy(),
                List.of(67),
                List.of(64));
        SocialSecurityClaimingGridCell cell = calculator.calculate(request)
                .cells().getFirst();

        LocalDate primaryDate = SocialSecurityRetirementDateCalculator
                .calculateRetirementClaimDate(
                        request.baseStrategy().primaryElection().birthDate(),
                        67);
        LocalDate spouseDate = SocialSecurityRetirementDateCalculator
                .calculateRetirementClaimDate(
                        request.baseStrategy().spouseElection().birthDate(),
                        64);
        SocialSecurityStrategyRequest directRequest =
                SocialSecurityClaimingGridCalculator.deriveStrategy(
                        request.baseStrategy(),
                        primaryDate,
                        spouseDate);
        SocialSecurityLifetimeResult directLifetime =
                new SocialSecurityStrategyCalculator().calculate(directRequest);
        SocialSecurityStrategyValuation directValuation =
                new SocialSecurityStrategyValuationCalculator().calculate(
                        directLifetime,
                        request.presentValueBaseDate(),
                        request.realDiscountRate());

        assertEquals(67, cell.primaryClaimAge());
        assertEquals(64, cell.spouseClaimAge());
        assertEquals(primaryDate, cell.primaryClaimDate());
        assertEquals(spouseDate, cell.spouseClaimDate());
        assertEquals(directValuation, cell.valuation());
        assertMoney(cell.nominalLifetimeBenefits(),
                cell.primaryLifetimeBenefits().add(
                        cell.spouseLifetimeBenefits()));
    }

    @Test
    void twoByThreeGridUsesPrimaryRowsAndSpouseColumns() {
        SocialSecurityClaimingGridResult result = calculator.calculate(
                request(
                        standardStrategy(),
                        List.of(62, 70),
                        List.of(62, 67, 70)));

        assertEquals(List.of("62/62", "62/67", "62/70",
                        "70/62", "70/67", "70/70"),
                result.cells().stream()
                        .map(cell -> cell.primaryClaimAge()
                                + "/" + cell.spouseClaimAge())
                        .toList());
        assertTrue(result.cellFor(70, 67).isPresent());
        assertFalse(result.cellFor(65, 67).isPresent());
    }

    @Test
    void fullWholeYearGridContainsEightyOneUniqueCoordinates() {
        List<Integer> ages = List.of(62, 63, 64, 65, 66, 67, 68, 69, 70);
        SocialSecurityClaimingGridResult result = calculator.calculate(
                request(standardStrategy(), ages, ages));

        assertEquals(81, result.cells().size());
        assertEquals(81, result.cells().stream()
                .map(cell -> cell.primaryClaimAge()
                        + "/" + cell.spouseClaimAge())
                .distinct()
                .count());
    }

    @Test
    void januaryFirstBirthUsesEstablishedSocialSecurityConvention() {
        SocialSecurityStrategyRequest base = strategy(
                LocalDate.of(1963, 1, 1),
                LocalDate.of(1965, 1, 1),
                3000,
                1200,
                LocalDate.of(2048, 1, 1),
                LocalDate.of(2060, 1, 1));
        SocialSecurityClaimingGridCell cell = calculator.calculate(
                        request(base, List.of(67), List.of(62)))
                .cells().getFirst();

        assertEquals(LocalDate.of(2029, 12, 31),
                cell.primaryClaimDate());
        assertEquals(LocalDate.of(2026, 12, 31),
                cell.spouseClaimDate());
    }

    @Test
    void zeroBenefitHouseholdRetainsEveryExactTie() {
        SocialSecurityStrategyRequest zero = strategy(
                LocalDate.of(1963, 6, 4),
                LocalDate.of(1965, 2, 28),
                0,
                0,
                LocalDate.of(2048, 6, 4),
                LocalDate.of(2060, 2, 28));
        SocialSecurityClaimingGridResult result = calculator.calculate(
                request(zero, List.of(62, 70), List.of(62, 70)));

        assertEquals(4, result.highestNominal().highestCells().size());
        assertEquals(4, result.highestReal().highestCells().size());
        assertEquals(4,
                result.highestPresentValue().highestCells().size());
        assertMoney(BigDecimal.ZERO,
                result.highestPresentValue().highestValue());
    }

    @Test
    void rankingsExactlyMatchCellMaximaAndAllTies() {
        SocialSecurityClaimingGridResult result = calculator.calculate(
                request(
                        standardStrategy(),
                        List.of(62, 67, 70),
                        List.of(62, 67, 70)));

        verifyRanking(result.cells(), result.highestNominal());
        verifyRanking(result.cells(), result.highestReal());
        verifyRanking(result.cells(), result.highestPresentValue());
    }

    @Test
    void claimAt70AfterDeathNeverCreatesOwnPayments() {
        SocialSecurityStrategyRequest earlyDeath = strategy(
                LocalDate.of(1963, 6, 4),
                LocalDate.of(1965, 2, 28),
                3000,
                1200,
                LocalDate.of(2031, 6, 4),
                LocalDate.of(2065, 2, 28));
        SocialSecurityClaimingGridResult result = calculator.calculate(
                request(earlyDeath, List.of(62, 67, 70), List.of(67)));

        SocialSecurityClaimingGridCell age70 =
                result.cellFor(70, 67).orElseThrow();
        assertTrue(age70.valuation().strategyResult().monthlyResults().stream()
                .allMatch(month -> month.primaryOwnBenefit().signum() == 0));
        assertTrue(result.cellFor(62, 67).orElseThrow()
                .primaryLifetimeBenefits().signum() > 0);
    }

    @Test
    void delayedWorkerEntitlementAndSurvivorPolicyRemainAuditableAndFixed() {
        SocialSecurityStrategyRequest base = standardStrategy();
        SocialSecurityClaimingGridResult result = calculator.calculate(
                request(base, List.of(67, 70), List.of(62)));

        SocialSecurityClaimingGridCell delayed =
                result.cellFor(70, 62).orElseThrow();
        SocialSecurityLifetimeResult lifetime = delayed.valuation()
                .strategyResult();
        YearMonth workerClaimMonth = YearMonth.from(delayed.primaryClaimDate());

        assertTrue(lifetime.monthlyResults().stream()
                .filter(month -> month.month().isBefore(workerClaimMonth))
                .anyMatch(month -> month.spouseOwnBenefit().signum() > 0
                        && month.spouseSpousalExcessBenefit().signum() == 0));
        assertTrue(lifetime.monthlyResults().stream()
                .filter(month -> !month.month().isBefore(workerClaimMonth))
                .anyMatch(month -> month.spouseSpousalExcessBenefit().signum() > 0));

        result.cells().forEach(cell -> {
            SocialSecurityStrategyRequest candidate = cell.valuation()
                    .strategyResult().request();
            assertEquals(base.primarySurvivorClaimDate(),
                    candidate.primarySurvivorClaimDate());
            assertEquals(base.spouseSurvivorClaimDate(),
                    candidate.spouseSurvivorClaimDate());
            assertEquals(base.primaryDeathDate(), candidate.primaryDeathDate());
            assertEquals(base.spouseDeathDate(), candidate.spouseDeathDate());
        });
    }

    @Test
    void survivorBenefitsFlowThroughValuationForEitherDeathOrder() {
        SocialSecurityClaimingGridResult spouseSurvives = calculator.calculate(
                request(
                        strategy(
                                LocalDate.of(1963, 6, 4),
                                LocalDate.of(1965, 2, 28),
                                3000, 1200,
                                LocalDate.of(2040, 6, 4),
                                LocalDate.of(2065, 2, 28)),
                        List.of(67, 70),
                        List.of(62)));
        assertTrue(spouseSurvives.cells().stream()
                .anyMatch(cell -> cell.valuation().strategyResult()
                        .spouseTotalSurvivorBenefits().signum() > 0));

        SocialSecurityClaimingGridResult primarySurvives = calculator.calculate(
                request(
                        strategy(
                                LocalDate.of(1963, 6, 4),
                                LocalDate.of(1965, 2, 28),
                                1200, 3000,
                                LocalDate.of(2065, 6, 4),
                                LocalDate.of(2040, 2, 28)),
                        List.of(62),
                        List.of(67, 70)));
        assertTrue(primarySurvives.cells().stream()
                .anyMatch(cell -> cell.valuation().strategyResult()
                        .primaryTotalSurvivorBenefits().signum() > 0));
    }

    @Test
    void longHorizonValuationsRemainFiniteAndNonNegative() {
        SocialSecurityStrategyRequest longLife = strategy(
                LocalDate.of(1963, 6, 4),
                LocalDate.of(1965, 2, 28),
                3000,
                1200,
                LocalDate.of(2073, 6, 4),
                LocalDate.of(2075, 2, 28));
        SocialSecurityClaimingGridResult result = calculator.calculate(
                request(longLife, List.of(62, 70), List.of(62, 70)));

        result.cells().forEach(cell -> {
            assertTrue(cell.nominalLifetimeBenefits().signum() >= 0);
            assertTrue(cell.realLifetimeBenefits().signum() >= 0);
            assertTrue(cell.presentValue().signum() >= 0);
        });
    }

    private void verifyRanking(
            List<SocialSecurityClaimingGridCell> cells,
            SocialSecurityClaimingGridRanking ranking) {
        BigDecimal actualMaximum = cells.stream()
                .map(cell -> SocialSecurityClaimingGridRanking.value(
                        cell,
                        ranking.measure()))
                .max(BigDecimal::compareTo)
                .orElseThrow();
        assertMoney(actualMaximum, ranking.highestValue());
        List<SocialSecurityClaimingGridCell> actualTies = cells.stream()
                .filter(cell -> SocialSecurityClaimingGridRanking.value(
                                cell,
                                ranking.measure())
                        .compareTo(actualMaximum) == 0)
                .toList();
        assertEquals(actualTies, ranking.highestCells());
    }

    private SocialSecurityClaimingGridRequest request(
            SocialSecurityStrategyRequest base,
            List<Integer> primaryAges,
            List<Integer> spouseAges) {
        return new SocialSecurityClaimingGridRequest(
                base,
                primaryAges,
                spouseAges,
                base.analysisDate(),
                new BigDecimal("0.01"));
    }

    private SocialSecurityStrategyRequest standardStrategy() {
        return strategy(
                LocalDate.of(1963, 6, 4),
                LocalDate.of(1965, 2, 28),
                3000,
                1000,
                LocalDate.of(2048, 6, 4),
                LocalDate.of(2060, 2, 28));
    }

    private SocialSecurityStrategyRequest strategy(
            LocalDate primaryBirthDate,
            LocalDate spouseBirthDate,
            int primaryBenefit,
            int spouseBenefit,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1),
                null,
                election(AccountOwnership.PRIMARY,
                        primaryBirthDate, primaryBenefit,
                        SocialSecurityRetirementDateCalculator
                                .calculateRetirementClaimDate(
                                        primaryBirthDate, 67)),
                election(AccountOwnership.SPOUSE,
                        spouseBirthDate, spouseBenefit,
                        SocialSecurityRetirementDateCalculator
                                .calculateRetirementClaimDate(
                                        spouseBirthDate, 67)),
                primaryBirthDate.plusYears(60),
                spouseBirthDate.plusYears(60),
                primaryDeathDate,
                spouseDeathDate,
                new BigDecimal("0.02"));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            int benefit,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(
                owner,
                birthDate,
                BigDecimal.valueOf(benefit),
                2025,
                claimDate);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
