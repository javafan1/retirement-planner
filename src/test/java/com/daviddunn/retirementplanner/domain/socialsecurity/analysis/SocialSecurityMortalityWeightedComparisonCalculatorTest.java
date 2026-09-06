package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityMortalityWeightedComparisonCalculatorTest {

    private final SocialSecurityMortalityWeightedComparisonCalculator calculator =
            new SocialSecurityMortalityWeightedComparisonCalculator();

    @Test
    void certainDeathDistributionExactlyMatchesDeterministicCell() {
        SocialSecurityMortalityWeightedComparisonResult result = calculator.calculate(
                request(
                        strategy(LocalDate.of(2033, 1, 2)),
                        strategy(LocalDate.of(2030, 1, 2)),
                        distribution(entry(85, "1.0")),
                        distribution(entry(92, "1.0")),
                        new BigDecimal("0.015")));
        SocialSecurityStrategyComparisonResult cell = result.deterministicMatrix()
                .cellFor(85, 92).orElseThrow().comparison();

        assertMoney(cell.strategyA().nominalLifetimeBenefits(),
                result.expectedNominalStrategyA());
        assertMoney(cell.strategyB().nominalLifetimeBenefits(),
                result.expectedNominalStrategyB());
        assertMoney(cell.strategyA().realLifetimeBenefits(),
                result.expectedRealStrategyA());
        assertMoney(cell.strategyB().realLifetimeBenefits(),
                result.expectedRealStrategyB());
        assertMoney(cell.strategyA().presentValue(),
                result.expectedPresentValueStrategyA());
        assertMoney(cell.strategyB().presentValue(),
                result.expectedPresentValueStrategyB());
        assertEquals(cell.presentValueWinner(), result.expectedPresentValueWinner());
        assertDecimal(BigDecimal.ONE, probabilityForWinner(
                result,
                cell.presentValueWinner()));
        assertEquals(1, result.scenarios().size());
    }

    @Test
    void twoPointDistributionsProduceExactJointProbabilitiesAndWeightedTotals() {
        SocialSecurityMortalityWeightedComparisonResult result = calculator.calculate(
                request(
                        strategy(LocalDate.of(2033, 1, 2)),
                        strategy(LocalDate.of(2030, 1, 2)),
                        distribution(entry(80, "0.25"), entry(90, "0.75")),
                        distribution(entry(85, "0.40"), entry(95, "0.60")),
                        new BigDecimal("0.01")));

        assertEquals(List.of(
                        new BigDecimal("0.1000"),
                        new BigDecimal("0.1500"),
                        new BigDecimal("0.3000"),
                        new BigDecimal("0.4500")),
                result.scenarios().stream()
                        .map(SocialSecurityMortalityWeightedScenario::jointProbability)
                        .toList());
        assertDecimal(BigDecimal.ONE, result.totalJointProbability());

        BigDecimal manuallyWeightedPvA = result.scenarios().stream()
                .map(scenario -> scenario.jointProbability().multiply(
                        scenario.matrixCell().comparison().strategyA().presentValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        assertMoney(manuallyWeightedPvA, result.expectedPresentValueStrategyA());
        assertMoney(result.expectedPresentValueStrategyA()
                        .subtract(result.expectedPresentValueStrategyB()),
                result.expectedPresentValueDifference());

        BigDecimal weightedDifference = result.scenarios().stream()
                .map(SocialSecurityMortalityWeightedScenario::weightedPresentValueDifference)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        assertMoney(result.expectedPresentValueDifference(), weightedDifference);
        assertMoney(result.expectedNominalStrategyA()
                        .subtract(result.expectedNominalStrategyB()),
                result.expectedNominalDifference());
        assertMoney(result.expectedRealStrategyA()
                        .subtract(result.expectedRealStrategyB()),
                result.expectedRealDifference());

        BigDecimal outcomeProbability = result.probabilityStrategyAWinsPresentValue()
                .add(result.probabilityStrategyBWinsPresentValue())
                .add(result.probabilityPresentValueTie());
        assertDecimal(BigDecimal.ONE, outcomeProbability);
    }

    @Test
    void identicalStrategiesTieAcrossAllProbabilityMass() {
        SocialSecurityStrategyRequest strategy = strategy(LocalDate.of(2030, 1, 2));
        SocialSecurityMortalityWeightedComparisonResult result = calculator.calculate(
                request(
                        strategy,
                        strategy,
                        distribution(entry(80, "0.25"), entry(90, "0.75")),
                        distribution(entry(85, "0.40"), entry(95, "0.60")),
                        BigDecimal.ZERO));

        assertMoney(BigDecimal.ZERO, result.expectedNominalDifference());
        assertMoney(BigDecimal.ZERO, result.expectedRealDifference());
        assertMoney(BigDecimal.ZERO, result.expectedPresentValueDifference());
        assertEquals(StrategyComparisonWinner.TIE, result.expectedNominalWinner());
        assertEquals(StrategyComparisonWinner.TIE, result.expectedRealWinner());
        assertEquals(StrategyComparisonWinner.TIE,
                result.expectedPresentValueWinner());
        assertDecimal(BigDecimal.ZERO,
                result.probabilityStrategyAWinsPresentValue());
        assertDecimal(BigDecimal.ZERO,
                result.probabilityStrategyBWinsPresentValue());
        assertDecimal(BigDecimal.ONE, result.probabilityPresentValueTie());
    }

    @Test
    void zeroProbabilityScenarioIsRetainedButContributesNothing() {
        SocialSecurityMortalityWeightedComparisonResult result = calculator.calculate(
                request(
                        strategy(LocalDate.of(2033, 1, 2)),
                        strategy(LocalDate.of(2030, 1, 2)),
                        distribution(entry(68, "0.00"), entry(90, "1.00")),
                        distribution(entry(100, "1.00")),
                        new BigDecimal("-0.01")));

        SocialSecurityMortalityWeightedScenario zero = result.scenarioFor(68, 100)
                .orElseThrow();
        assertDecimal(BigDecimal.ZERO, zero.jointProbability());
        assertDecimal(BigDecimal.ZERO, zero.weightedPresentValueStrategyA());
        assertTrue(result.expectedPresentValueStrategyA().signum() > 0);
    }

    @Test
    void retainedMatrixMatchesDirectMatrixAndCapturesBothDeathOrders() {
        SocialSecurityMortalityWeightedComparisonRequest request = request(
                strategy(LocalDate.of(2033, 1, 2)),
                strategy(LocalDate.of(2030, 1, 2)),
                distribution(entry(68, "0.50"), entry(100, "0.50")),
                distribution(entry(68, "0.40"), entry(100, "0.60")),
                new BigDecimal("0.01"));
        SocialSecurityMortalityWeightedComparisonResult result = calculator.calculate(request);
        SocialSecurityDeathAgeMatrixResult direct =
                new SocialSecurityDeathAgeMatrixCalculator().calculate(
                        new SocialSecurityDeathAgeMatrixRequest(
                                request.strategyA(),
                                request.strategyB(),
                                request.primaryMortality().deathAges(),
                                request.spouseMortality().deathAges(),
                                request.presentValueBaseDate(),
                                request.realDiscountRate()));

        assertEquals(direct, result.deterministicMatrix());
        assertEquals(direct.cells().size(), result.scenarios().size());
        assertTrue(result.scenarioFor(68, 100).orElseThrow().matrixCell()
                .comparison().strategyA().strategyResult().monthlyResults().stream()
                .anyMatch(month -> month.spouseSurvivorBenefit().signum() > 0));
        assertTrue(result.scenarioFor(100, 68).orElseThrow().matrixCell()
                .comparison().strategyA().strategyResult().monthlyResults().stream()
                .anyMatch(month -> month.lifeState()
                        == SocialSecurityHouseholdLifeState.PRIMARY_ONLY));
    }

    @Test
    void moderateFiveByFiveSupportProducesTwentyFiveAuditableScenarios() {
        SocialSecurityMortalityDistribution primary = equalDistribution(
                70, 75, 80, 85, 90);
        SocialSecurityMortalityDistribution spouse = equalDistribution(
                70, 80, 90, 100, 110);

        SocialSecurityMortalityWeightedComparisonResult result = calculator.calculate(
                request(
                        strategy(LocalDate.of(2033, 1, 2)),
                        strategy(LocalDate.of(2030, 1, 2)),
                        primary,
                        spouse,
                        new BigDecimal("0.01")));

        assertEquals(25, result.scenarios().size());
        assertDecimal(BigDecimal.ONE, result.totalJointProbability());
    }

    private BigDecimal probabilityForWinner(
            SocialSecurityMortalityWeightedComparisonResult result,
            StrategyComparisonWinner winner) {
        return switch (winner) {
            case STRATEGY_A -> result.probabilityStrategyAWinsPresentValue();
            case STRATEGY_B -> result.probabilityStrategyBWinsPresentValue();
            case TIE -> result.probabilityPresentValueTie();
        };
    }

    private SocialSecurityMortalityWeightedComparisonRequest request(
            SocialSecurityStrategyRequest strategyA,
            SocialSecurityStrategyRequest strategyB,
            SocialSecurityMortalityDistribution primary,
            SocialSecurityMortalityDistribution spouse,
            BigDecimal rate) {
        return new SocialSecurityMortalityWeightedComparisonRequest(
                strategyA,
                strategyB,
                primary,
                spouse,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1),
                rate);
    }

    private SocialSecurityMortalityDistribution equalDistribution(int... ages) {
        return new SocialSecurityMortalityDistribution(
                java.util.Arrays.stream(ages)
                        .mapToObj(age -> entry(age, "0.20"))
                        .toList());
    }

    private SocialSecurityMortalityDistribution distribution(
            SocialSecurityMortalityProbability... entries) {
        return new SocialSecurityMortalityDistribution(List.of(entries));
    }

    private SocialSecurityMortalityProbability entry(int age, String probability) {
        return new SocialSecurityMortalityProbability(
                age,
                new BigDecimal(probability));
    }

    private SocialSecurityStrategyRequest strategy(LocalDate primaryClaimDate) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1),
                null,
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 1, 2), 3000,
                        primaryClaimDate),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 1, 2), 1200,
                        LocalDate.of(2032, 1, 2)),
                LocalDate.of(2023, 1, 2),
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2053, 1, 2),
                LocalDate.of(2060, 1, 2),
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

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
