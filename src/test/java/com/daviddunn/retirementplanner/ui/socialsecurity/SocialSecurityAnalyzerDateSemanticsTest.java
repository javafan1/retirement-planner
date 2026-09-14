package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityAnalyzerDateSemanticsTest {
    @Test
    void valuationOnlyRequestChangePreservesCashFlowsMortalityAndRanking() {
        var conditioning = LocalDate.of(2050, 1, 1);
        var before = SocialSecurityAnalyzerDateRequestTest.ss(conditioning, LocalDate.of(2049, 1, 1));
        var after = SocialSecurityAnalyzerDateRequestTest.ss(conditioning, LocalDate.of(2048, 1, 1));
        var b = before.request().retirementGridRequest();
        var a = after.request().retirementGridRequest();
        assertEquals(b.baseStrategy(), a.baseStrategy());
        assertEquals(b.primaryMortality().probabilities(), a.primaryMortality().probabilities());
        assertEquals(b.spouseMortality().probabilities(), a.spouseMortality().probabilities());
        var resultBefore = reduced(b);
        var resultAfter = reduced(a);
        assertEquals(order(resultBefore), order(resultAfter));
        var forwardFactor = BigDecimal.ONE.add(b.realDiscountRate())
                .multiply(BigDecimal.ONE.add(b.baseStrategy().socialSecurityColaRate()));
        for (int index = 0; index < resultBefore.cells().size(); index++) {
            var original = resultBefore.cells().get(index);
            var changed = resultAfter.cells().get(index);
            assertEquals(original.expectedNominalBenefits(), changed.expectedNominalBenefits());
            var error = changed.expectedPresentValue().multiply(forwardFactor).subtract(original.expectedPresentValue()).abs();
            assertTrue(error.compareTo(original.expectedPresentValue().abs().scaleByPowerOfTen(-30)) < 0);
        }
    }

    @Test
    void conditioningChangesValidDistributionsAndExpectedValuesButNotValuationOrPlanStart() {
        var valuation = LocalDate.of(2049, 1, 1);
        var before = SocialSecurityAnalyzerDateRequestTest.ss(LocalDate.of(2050, 1, 1), valuation).request().retirementGridRequest();
        var after = SocialSecurityAnalyzerDateRequestTest.ss(LocalDate.of(2052, 1, 1), valuation).request().retirementGridRequest();
        assertNotEquals(before.primaryMortality().probabilities(), after.primaryMortality().probabilities());
        assertNotEquals(before.spouseMortality().probabilities(), after.spouseMortality().probabilities());
        for (var distribution : List.of(before.primaryMortality().probabilities(), after.primaryMortality().probabilities(), before.spouseMortality().probabilities(), after.spouseMortality().probabilities())) {
            assertEquals(0, BigDecimal.ONE.compareTo(distribution.stream()
                    .map(SocialSecurityMortalityProbability::probability).reduce(BigDecimal.ZERO, BigDecimal::add)));
            assertTrue(distribution.stream().allMatch(p -> p.probability().signum() >= 0));
        }
        assertEquals(valuation, after.presentValueBaseDate());
        assertEquals(LocalDate.of(2026, 7, 1), LongevityWeightedAnalysisRequestFactoryTest.plan().getPlanningAssumptions().getProjectionStartDate());
        assertNotEquals(0, reduced(before).cells().getFirst().expectedPresentValue()
                .compareTo(reduced(after).cells().getFirst().expectedPresentValue()));
        // Conditioning legitimately changes the analysis population; do not constrain its ranking.
    }

    private static SocialSecurityMortalityWeightedClaimingGridResult reduced(SocialSecurityMortalityWeightedClaimingGridRequest request) {
        return new SocialSecurityMortalityWeightedClaimingGridCalculator().calculate(new SocialSecurityMortalityWeightedClaimingGridRequest(
                request.baseStrategy(), List.of(62, 70), List.of(67), request.primaryMortality(), request.spouseMortality(),
                request.mortalityBaseDate(), request.presentValueBaseDate(), request.realDiscountRate()));
    }

    private static List<Integer> order(SocialSecurityMortalityWeightedClaimingGridResult result) {
        return result.cells().stream().sorted(Comparator.comparing(SocialSecurityMortalityWeightedClaimingGridCell::expectedPresentValue).reversed())
                .map(SocialSecurityMortalityWeightedClaimingGridCell::primaryClaimAge).toList();
    }
}
