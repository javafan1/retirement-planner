package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityMortalityWeightedClaimingGridCalculatorTest {

    private final SocialSecurityMortalityWeightedClaimingGridCalculator calculator =
            new SocialSecurityMortalityWeightedClaimingGridCalculator();

    @Test
    void cancellationAtGridBoundaryPublishesNoResult() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var request = request(List.of(67, 68), List.of(64), twoPoint(), twoPoint());
        assertThrows(com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException.class,
                () -> calculator.calculate(request, update -> cancelled.set(true), cancelled::get));
    }
    @Test
    void oneByOneMatchesDirectMortalityWeightedCalculationAndOwnerTotalsReconcile() {
        SocialSecurityMortalityWeightedClaimingGridRequest request = request(
                List.of(67), List.of(64), twoPoint(), twoPoint());
        SocialSecurityMortalityWeightedClaimingGridResult result =
                calculator.calculate(request);
        SocialSecurityMortalityWeightedClaimingGridCell cell = result.cells().getFirst();

        SocialSecurityStrategyRequest candidate =
                SocialSecurityClaimingGridCalculator.deriveStrategy(
                        request.baseStrategy(),
                        cell.primaryClaimDate(),
                        cell.spouseClaimDate());
        SocialSecurityMortalityWeightedComparisonResult direct =
                new SocialSecurityMortalityWeightedComparisonCalculator().calculate(
                        new SocialSecurityMortalityWeightedComparisonRequest(
                                candidate,
                                candidate,
                                request.primaryMortality(),
                                request.spouseMortality(),
                                request.mortalityBaseDate(),
                                request.presentValueBaseDate(),
                                request.realDiscountRate()));

        assertMoney(direct.expectedNominalStrategyA(), cell.expectedNominalBenefits());
        assertMoney(direct.expectedRealStrategyA(), cell.expectedRealBenefits());
        assertMoney(direct.expectedPresentValueStrategyA(), cell.expectedPresentValue());
        assertMoney(cell.expectedNominalBenefits(),
                cell.expectedPrimarySelectedBenefits()
                        .add(cell.expectedSpouseSelectedBenefits()));
        assertEquals(4, result.jointMortalityScenarios().size());
        assertEquals(4, result.strategyEvaluationCount());
    }

    @Test
    void twoByThreeIsRowMajorAndRankingsExactlyMatchCells() {
        SocialSecurityMortalityWeightedClaimingGridResult result = calculator.calculate(
                request(List.of(62, 70), List.of(62, 67, 70),
                        certain(90), certain(95)));

        assertEquals(List.of("62/62", "62/67", "62/70",
                        "70/62", "70/67", "70/70"),
                result.cells().stream()
                        .map(cell -> cell.primaryClaimAge() + "/" + cell.spouseClaimAge())
                        .toList());
        assertTrue(result.cellFor(70, 67).isPresent());
        assertFalse(result.cellFor(65, 67).isPresent());
        verifyRanking(result.cells(), result.highestExpectedNominal());
        verifyRanking(result.cells(), result.highestExpectedReal());
        verifyRanking(result.cells(), result.highestExpectedPresentValue());
    }

    @Test
    void zeroBenefitsRetainEveryTieAndNineByNineHasEightyOneCells() {
        List<Integer> ages = List.of(62, 63, 64, 65, 66, 67, 68, 69, 70);
        SocialSecurityStrategyRequest zero = baseStrategy(BigDecimal.ZERO, BigDecimal.ZERO);
        SocialSecurityMortalityWeightedClaimingGridRequest request = new SocialSecurityMortalityWeightedClaimingGridRequest(
                zero, ages, ages, certain(90), certain(95),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), BigDecimal.ZERO);
        SocialSecurityMortalityWeightedClaimingGridResult result = calculator.calculate(request);

        assertEquals(81, result.cells().size());
        assertEquals(81, result.highestExpectedNominal().highestCells().size());
        assertEquals(81, result.highestExpectedReal().highestCells().size());
        assertEquals(81, result.highestExpectedPresentValue().highestCells().size());
    }

    @Test
    void sequentialAndParallelExecutionAreExactlyEquivalent() {
        SocialSecurityMortalityWeightedClaimingGridRequest request = request(
                List.of(62, 67, 70), List.of(62, 70), twoPoint(), twoPoint());
        SocialSecurityMortalityWeightedClaimingGridResult sequential =
                new SocialSecurityMortalityWeightedClaimingGridCalculator(
                        SocialSecurityMortalityWeightedClaimingGridCalculator.ExecutionMode.SEQUENTIAL,
                        1).calculate(request);
        SocialSecurityMortalityWeightedClaimingGridResult parallel =
                new SocialSecurityMortalityWeightedClaimingGridCalculator(
                        SocialSecurityMortalityWeightedClaimingGridCalculator.ExecutionMode.PARALLEL,
                        2).calculate(request);

        assertEquals(sequential, parallel);
    }

    @Test
    void requestReusesWholeYearClaimingValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(61), List.of(62), certain(90), certain(95)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(62, 62), List.of(62), certain(90), certain(95)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(), List.of(62), certain(90), certain(95)));
    }

    @Test
    void extractedScenariosPreserveEveryGridValueAndRankingWithSeparateValuationDate() {
        var original = request(List.of(62, 67, 70), List.of(62, 70), twoPoint(), twoPoint());
        var request = new SocialSecurityMortalityWeightedClaimingGridRequest(
                original.baseStrategy(), original.primaryClaimAges(), original.spouseClaimAges(),
                original.primaryMortality(), original.spouseMortality(), original.mortalityBaseDate(),
                LocalDate.of(2024, 1, 1), new BigDecimal("0.025"));
        var legacyScenarios = HouseholdLongevityScenarioFactoryTest.legacyScenarios(
                request.baseStrategy().primaryElection().birthDate(),
                request.baseStrategy().spouseElection().birthDate(),
                request.primaryMortality(), request.spouseMortality());
        var result = calculator.calculate(request);
        assertEquals(legacyScenarios, result.jointMortalityScenarios());
        for (var cell : result.cells()) {
            var strategy = SocialSecurityClaimingGridCalculator.deriveStrategy(
                    request.baseStrategy(), cell.primaryClaimDate(), cell.spouseClaimDate());
            var legacyValue = new SocialSecurityMortalityWeightedStrategyCalculator().calculate(
                    strategy, legacyScenarios, request.presentValueBaseDate(), request.realDiscountRate());
            assertEquals(new SocialSecurityMortalityWeightedClaimingGridCell(
                    cell.primaryClaimAge(), cell.spouseClaimAge(), cell.primaryClaimDate(),
                    cell.spouseClaimDate(), legacyValue), cell);
        }
        verifyRanking(result.cells(), result.highestExpectedNominal());
        verifyRanking(result.cells(), result.highestExpectedReal());
        verifyRanking(result.cells(), result.highestExpectedPresentValue());
    }
    private void verifyRanking(
            List<SocialSecurityMortalityWeightedClaimingGridCell> cells,
            SocialSecurityMortalityWeightedClaimingGridRanking ranking) {
        BigDecimal maximum = cells.stream()
                .map(cell -> SocialSecurityMortalityWeightedClaimingGridRanking
                        .value(cell, ranking.measure()))
                .max(BigDecimal::compareTo)
                .orElseThrow();
        assertMoney(maximum, ranking.highestValue());
        assertEquals(cells.stream()
                        .filter(cell -> SocialSecurityMortalityWeightedClaimingGridRanking
                                .value(cell, ranking.measure()).compareTo(maximum) == 0)
                        .toList(),
                ranking.highestCells());
    }

    private SocialSecurityMortalityWeightedClaimingGridRequest request(
            List<Integer> primaryAges,
            List<Integer> spouseAges,
            SocialSecurityMortalityDistribution primaryMortality,
            SocialSecurityMortalityDistribution spouseMortality) {
        return new SocialSecurityMortalityWeightedClaimingGridRequest(
                baseStrategy(new BigDecimal("3000"), new BigDecimal("1200")),
                primaryAges,
                spouseAges,
                primaryMortality,
                spouseMortality,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1),
                new BigDecimal("0.015"));
    }

    private SocialSecurityStrategyRequest baseStrategy(
            BigDecimal primaryBenefit,
            BigDecimal spouseBenefit) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1), null,
                election(AccountOwnership.PRIMARY, LocalDate.of(1963, 1, 2),
                        primaryBenefit, LocalDate.of(2030, 1, 2)),
                election(AccountOwnership.SPOUSE, LocalDate.of(1965, 1, 2),
                        spouseBenefit, LocalDate.of(2032, 1, 2)),
                LocalDate.of(2023, 1, 2), LocalDate.of(2025, 1, 2),
                LocalDate.of(2053, 1, 2), LocalDate.of(2060, 1, 2),
                new BigDecimal("0.02"));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            BigDecimal benefit,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(owner, birthDate, benefit, 2025, claimDate);
    }

    private SocialSecurityMortalityDistribution certain(int age) {
        return new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(age, BigDecimal.ONE)));
    }

    private SocialSecurityMortalityDistribution twoPoint() {
        return new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(80, new BigDecimal("0.25")),
                new SocialSecurityMortalityProbability(95, new BigDecimal("0.75"))));
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
