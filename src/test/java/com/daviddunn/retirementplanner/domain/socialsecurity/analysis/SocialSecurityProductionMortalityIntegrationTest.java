package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityProductionMortalityIntegrationTest {

    @Test
    void authoritativeTableSupportsOneByOneMortalityWeightedClaimingGrid() {
        SocialSecurityMortalityWeightedClaimingGridResult result = productionGrid(
                List.of(67));

        assertEquals(1, result.cells().size());
        assertEquals(2500, result.jointMortalityScenarios().size());
        assertEquals(2500, result.strategyEvaluationCount());
    }

    @Test
    void authoritativeTableSupportsThreeByThreeMortalityWeightedClaimingGrid() {
        SocialSecurityMortalityWeightedClaimingGridResult result = productionGrid(
                List.of(62, 67, 70));

        assertEquals(9, result.cells().size());
        assertEquals(22500, result.strategyEvaluationCount());
    }

    @Test
    @Disabled("Explicit multi-minute production performance benchmark; run manually.")
    void authoritativeTableSupportsFullMortalityWeightedNineByNineClaimingGrid() {
        SocialSecurityMortalityWeightedClaimingGridResult result = productionGrid(
                List.of(62, 63, 64, 65, 66, 67, 68, 69, 70));

        assertEquals(81, result.cells().size());
        assertEquals(2500, result.jointMortalityScenarios().size());
        assertEquals(202500, result.strategyEvaluationCount());
        assertTrue(result.cells().stream()
                .allMatch(cell -> cell.expectedPresentValue().signum() >= 0));
        assertFalse(result.highestExpectedPresentValue().highestCells().isEmpty());
    }

    private SocialSecurityMortalityWeightedClaimingGridResult productionGrid(
            List<Integer> ages) {
        SocialSecurityMortalityDistributionProvider provider =
                new SocialSecurityMortalityDistributionProvider(
                        SocialSecurityMortalityTables.ssaPeriod2022());
        LocalDate birthDate = LocalDate.of(1955, 1, 2);
        LocalDate baseDate = LocalDate.of(2025, 1, 2);
        SocialSecurityMortalityDistribution primary = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        birthDate, baseDate, SocialSecurityMortalityCategory.MALE))
                .distribution();
        SocialSecurityMortalityDistribution spouse = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        birthDate, baseDate, SocialSecurityMortalityCategory.FEMALE))
                .distribution();
        return new SocialSecurityMortalityWeightedClaimingGridCalculator().calculate(
                        new SocialSecurityMortalityWeightedClaimingGridRequest(
                                strategy(birthDate, LocalDate.of(2022, 1, 2)),
                                ages,
                                ages,
                                primary,
                                spouse,
                                baseDate,
                                baseDate,
                                new BigDecimal("0.01")));
    }

    @Test
    void authoritativeTableFeedsCompleteMortalityWeightedAuditTrail() {
        SocialSecurityMortalityDistributionProvider provider =
                new SocialSecurityMortalityDistributionProvider(
                        SocialSecurityMortalityTables.ssaPeriod2022());
        LocalDate birthDate = LocalDate.of(1955, 1, 2);
        LocalDate baseDate = LocalDate.of(2025, 1, 2);
        SocialSecurityMortalityDistribution primary = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        birthDate,
                        baseDate,
                        SocialSecurityMortalityCategory.MALE))
                .distribution();
        SocialSecurityMortalityDistribution spouse = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        birthDate,
                        baseDate,
                        SocialSecurityMortalityCategory.FEMALE))
                .distribution();

        SocialSecurityMortalityWeightedComparisonResult result =
                new SocialSecurityMortalityWeightedComparisonCalculator().calculate(
                        new SocialSecurityMortalityWeightedComparisonRequest(
                                strategy(birthDate, LocalDate.of(2025, 1, 2)),
                                strategy(birthDate, LocalDate.of(2022, 1, 2)),
                                primary,
                                spouse,
                                baseDate,
                                baseDate,
                                new BigDecimal("0.01")));

        assertEquals(50, primary.probabilities().size());
        assertEquals(50, spouse.probabilities().size());
        assertEquals(2500, result.scenarios().size());
        assertEquals(2500, result.deterministicMatrix().cells().size());
        assertEquals(0, BigDecimal.ONE.compareTo(result.totalJointProbability()));
        assertFalse(result.scenarios().isEmpty());
        assertTrue(result.expectedNominalStrategyA().signum() > 0);
        assertTrue(result.expectedNominalStrategyB().signum() > 0);
        assertTrue(result.expectedRealStrategyA().signum() > 0);
        assertTrue(result.expectedPresentValueStrategyA().signum() > 0);
        assertEquals(0, BigDecimal.ONE.compareTo(
                result.probabilityStrategyAWinsPresentValue()
                        .add(result.probabilityStrategyBWinsPresentValue())
                        .add(result.probabilityPresentValueTie())));
        assertFalse(result.deterministicMatrix().cells().getFirst()
                .comparison().monthlyComparisons().isEmpty());
    }

    private SocialSecurityStrategyRequest strategy(
            LocalDate birthDate,
            LocalDate primaryClaimDate) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 2),
                null,
                election(AccountOwnership.PRIMARY,
                        birthDate, 3000, primaryClaimDate),
                election(AccountOwnership.SPOUSE,
                        birthDate, 1200, LocalDate.of(2017, 1, 2)),
                LocalDate.of(2015, 1, 2),
                LocalDate.of(2015, 1, 2),
                LocalDate.of(2035, 1, 2),
                LocalDate.of(2040, 1, 2),
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
}
