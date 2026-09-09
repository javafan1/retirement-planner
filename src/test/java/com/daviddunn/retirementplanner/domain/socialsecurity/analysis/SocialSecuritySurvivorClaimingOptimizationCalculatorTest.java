package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecuritySurvivorClaimingOptimizationCalculatorTest {

    private final SocialSecuritySurvivorClaimingOptimizationCalculator calculator =
            new SocialSecuritySurvivorClaimingOptimizationCalculator();

    @Test
    void cancellationAtSurvivorBoundaryPublishesNoResult() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var request = request(List.of(67), List.of(64), 1, standardStrategy());
        org.junit.jupiter.api.Assertions.assertThrows(
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException.class,
                () -> calculator.calculate(request, update -> {
                    if (update.phase() == AnalysisPhase.SOCIAL_SECURITY_SURVIVOR_STRATEGIES) {
                        cancelled.set(true);
                    }
                }, cancelled::get));
    }
    @Test
    void reportsRealStageOneAndStageTwoWorkAndPreservesResult() {
        var request = request(List.of(62, 70), List.of(62, 70), 1, standardStrategy());
        List<AnalysisProgress> updates = new ArrayList<>();

        var withProgress = calculator.calculate(request, updates::add);
        var withoutProgress = calculator.calculate(request);

        assertEquals(withoutProgress, withProgress);
        var stageOne = updates.stream().filter(update -> update.phase()
                == AnalysisPhase.SOCIAL_SECURITY_RETIREMENT_GRID).toList();
        var stageTwo = updates.stream().filter(update -> update.phase()
                == AnalysisPhase.SOCIAL_SECURITY_SURVIVOR_STRATEGIES).toList();
        assertEquals(new AnalysisProgress(
                AnalysisPhase.SOCIAL_SECURITY_RETIREMENT_GRID, 0, 4), stageOne.getFirst());
        assertEquals(4, stageOne.getLast().completedWork());
        assertEquals(0, stageTwo.getFirst().completedWork());
        assertEquals(withProgress.stageTwoStrategyCount(), stageTwo.getLast().completedWork());
        assertEquals(withProgress.stageTwoStrategyCount(), stageTwo.getLast().totalWork());
    }

    @Test
    void oneByOneSearchMatchesDirectCompleteStrategyValuation() {
        SocialSecuritySurvivorClaimingOptimizationRequest request = request(
                List.of(67), List.of(64), 1, standardStrategy());
        SocialSecuritySurvivorClaimingOptimizationResult result =
                calculator.calculate(request);
        SocialSecuritySurvivorClaimingOptimizationCell cell =
                result.evaluatedStrategies().get(17);
        SocialSecurityHouseholdClaimingStrategy strategy = cell.strategy();
        SocialSecurityStrategyRequest retirementStrategy =
                SocialSecurityClaimingGridCalculator.deriveStrategy(
                        request.retirementGridRequest().baseStrategy(),
                        strategy.primaryRetirementClaimDate(),
                        strategy.spouseRetirementClaimDate());
        SocialSecurityMortalityWeightedStrategyValue direct =
                new SocialSecurityMortalityWeightedStrategyCalculator().calculate(
                        retirementStrategy,
                        strategy.primarySurvivorElection().claimDate(),
                        strategy.spouseSurvivorElection().claimDate(),
                        HouseholdLongevityScenarioFactoryTest.legacyScenarios(
                                request.retirementGridRequest().baseStrategy().primaryElection().birthDate(),
                                request.retirementGridRequest().baseStrategy().spouseElection().birthDate(),
                                request.retirementGridRequest().primaryMortality(),
                                request.retirementGridRequest().spouseMortality()),
                        request.retirementGridRequest().presentValueBaseDate(),
                        request.retirementGridRequest().realDiscountRate());

        assertEquals(direct, cell.expectedValue());
        assertEquals(1, result.retainedRetirementCells().size());
        assertEquals(8, result.primarySurvivorCandidates().size());
        assertEquals(8, result.spouseSurvivorCandidates().size());
        assertEquals(64, result.stageTwoStrategyCount());
        assertEquals(256, result.stageTwoDeterministicEvaluationCount());
        assertMoney(cell.expectedNominalBenefits(),
                cell.expectedPrimarySelectedBenefits()
                        .add(cell.expectedSpouseSelectedBenefits()));
    }

    @Test
    void topNRetainsEveryExactCutoffTieAndCountsFullStageTwoProduct() {
        SocialSecurityStrategyRequest zero = strategy(BigDecimal.ZERO, BigDecimal.ZERO);
        SocialSecuritySurvivorClaimingOptimizationResult result = calculator.calculate(
                request(List.of(62, 70), List.of(62, 70), 1, zero));

        assertEquals(4, result.retainedRetirementCells().size());
        assertEquals(4 * 8 * 8, result.stageTwoStrategyCount());
        assertEquals(result.stageTwoStrategyCount() * 4,
                result.stageTwoDeterministicEvaluationCount());
        assertEquals(result.stageTwoStrategyCount(),
                result.highestExpectedPresentValueStrategies().size());
    }

    @Test
    void resultIsDeterministicAndRanksOnlyByExpectedPresentValue() {
        SocialSecuritySurvivorClaimingOptimizationRequest request = request(
                List.of(62, 70), List.of(62), 1, standardStrategy());
        SocialSecuritySurvivorClaimingOptimizationResult first =
                calculator.calculate(request);
        SocialSecuritySurvivorClaimingOptimizationResult second =
                calculator.calculate(request);

        assertEquals(first, second);
        BigDecimal maximum = first.evaluatedStrategies().stream()
                .map(SocialSecuritySurvivorClaimingOptimizationCell::expectedPresentValue)
                .max(BigDecimal::compareTo).orElseThrow();
        assertMoney(maximum, first.highestExpectedPresentValue());
        assertTrue(first.highestExpectedPresentValueStrategies().stream()
                .allMatch(cell -> cell.expectedPresentValue().compareTo(maximum) == 0));
    }

    @Test
    void earlyAndFraSurvivorElectionsRemainIndependentFromRetirement() {
        SocialSecuritySurvivorClaimingOptimizationResult result = calculator.calculate(
                request(List.of(70), List.of(62), 1, standardStrategy()));

        assertTrue(result.evaluatedStrategies().stream().anyMatch(cell ->
                cell.strategy().primaryRetirementAge() == 70
                        && cell.strategy().primarySurvivorElection()
                                .wholeYearAge().orElse(-1) == 60));
        assertTrue(result.evaluatedStrategies().stream().anyMatch(cell ->
                cell.strategy().spouseRetirementAge() == 62
                        && cell.strategy().spouseSurvivorElection().claimDate()
                                .equals(result.spouseSurvivorCandidates()
                                        .getLast().claimDate())));
    }

    @Test
    void sequentialAndParallelStageTwoResultsAreExactlyEqual() {
        SocialSecuritySurvivorClaimingOptimizationRequest request = request(
                List.of(62, 70), List.of(62), 1, standardStrategy());
        SocialSecuritySurvivorClaimingOptimizationResult sequential =
                new SocialSecuritySurvivorClaimingOptimizationCalculator(
                        new SocialSecurityMortalityWeightedClaimingGridCalculator(
                                SocialSecurityMortalityWeightedClaimingGridCalculator
                                        .ExecutionMode.SEQUENTIAL,
                                1),
                        new SocialSecuritySurvivorClaimingCandidateGenerator(),
                        SocialSecuritySurvivorClaimingOptimizationCalculator
                                .ExecutionMode.SEQUENTIAL,
                        1).calculate(request);
        SocialSecuritySurvivorClaimingOptimizationResult parallel =
                new SocialSecuritySurvivorClaimingOptimizationCalculator(
                        new SocialSecurityMortalityWeightedClaimingGridCalculator(
                                SocialSecurityMortalityWeightedClaimingGridCalculator
                                        .ExecutionMode.PARALLEL,
                                2),
                        new SocialSecuritySurvivorClaimingCandidateGenerator(),
                        SocialSecuritySurvivorClaimingOptimizationCalculator
                                .ExecutionMode.PARALLEL,
                        2).calculate(request);

        assertEquals(sequential, parallel);
    }

    private SocialSecuritySurvivorClaimingOptimizationRequest request(
            List<Integer> primaryAges,
            List<Integer> spouseAges,
            int topN,
            SocialSecurityStrategyRequest strategy) {
        SocialSecurityMortalityDistribution mortality =
                new SocialSecurityMortalityDistribution(List.of(
                        new SocialSecurityMortalityProbability(
                                80, new BigDecimal("0.40")),
                        new SocialSecurityMortalityProbability(
                                95, new BigDecimal("0.60"))));
        return new SocialSecuritySurvivorClaimingOptimizationRequest(
                new SocialSecurityMortalityWeightedClaimingGridRequest(
                        strategy,
                        primaryAges,
                        spouseAges,
                        mortality,
                        mortality,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        new BigDecimal("0.015")),
                new SocialSecuritySurvivorOptimizationSettings(topN));
    }

    private SocialSecurityStrategyRequest standardStrategy() {
        return strategy(new BigDecimal("3000"), new BigDecimal("1200"));
    }

    private SocialSecurityStrategyRequest strategy(
            BigDecimal primaryBenefit,
            BigDecimal spouseBenefit) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1), null,
                election(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4),
                        primaryBenefit, LocalDate.of(2030, 6, 4)),
                election(AccountOwnership.SPOUSE, LocalDate.of(1965, 2, 28),
                        spouseBenefit, LocalDate.of(2032, 2, 28)),
                LocalDate.of(2023, 6, 4), LocalDate.of(2025, 2, 28),
                LocalDate.of(2053, 6, 4), LocalDate.of(2060, 2, 28),
                new BigDecimal("0.02"));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            BigDecimal benefit,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(owner, birthDate, benefit, 2025, claimDate);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
