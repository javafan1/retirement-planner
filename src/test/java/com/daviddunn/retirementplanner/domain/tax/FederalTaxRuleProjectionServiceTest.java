package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FederalTaxRuleProjectionServiceTest {

    private FederalTaxRuleProjectionService service;
    private FederalTaxRules publishedRules;

    @BeforeEach
    void setUp() throws Exception {

        service = new FederalTaxRuleProjectionService();

        GovernmentRules governmentRules =
                new GovernmentRulesRepository()
                        .load("/rules/government-rules-2026.json");

        publishedRules = governmentRules.getFederalTaxRules(
                FilingStatus.MARRIED_FILING_JOINTLY);
    }

    @Test
    void usesDedicatedGrowthRatesAndPreservesPublishedBaseYear() {

        PlanningAssumptions assumptions = planningAssumptions(
                new BigDecimal("0.025"),
                new BigDecimal("0.030"),
                new BigDecimal("0.99"));

        FederalTaxRules baseYearRules = service.project(
                publishedRules,
                2026,
                2026,
                assumptions);

        assertRulesEqual(publishedRules, baseYearRules);

        FederalTaxRules projectedRules = service.project(
                publishedRules,
                2026,
                2028,
                assumptions);

        assertEquals(
                projected(publishedRules.getStandardDeduction(),
                        new BigDecimal("0.030"), 2),
                projectedRules.getStandardDeduction());

        assertEquals(
                projected(publishedRules.getTaxBrackets().get(1).getUpperBound(),
                        new BigDecimal("0.025"), 2),
                projectedRules.getTaxBrackets().get(1).getUpperBound());
    }

    @Test
    void zeroGrowthLeavesFutureFederalParametersAtPublishedAmounts() {

        FederalTaxRules projectedRules = service.project(
                publishedRules,
                2026,
                2031,
                planningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("0.20")));

        assertRulesEqual(publishedRules, projectedRules);
    }

    @Test
    void generalInflationDoesNotChangeFederalTaxParameters() {

        FederalTaxRules lowInflationRules = service.project(
                publishedRules,
                2026,
                2031,
                planningAssumptions(
                        new BigDecimal("0.025"),
                        new BigDecimal("0.030"),
                        new BigDecimal("0.01")));

        FederalTaxRules highInflationRules = service.project(
                publishedRules,
                2026,
                2031,
                planningAssumptions(
                        new BigDecimal("0.025"),
                        new BigDecimal("0.030"),
                        new BigDecimal("0.20")));

        assertRulesEqual(lowInflationRules, highInflationRules);
    }

    @Test
    void bracketAndStandardDeductionGrowthOperateIndependently() {

        FederalTaxRules bracketGrowthRules = service.project(
                publishedRules,
                2026,
                2028,
                planningAssumptions(
                        new BigDecimal("0.05"),
                        BigDecimal.ZERO,
                        new BigDecimal("0.025")));

        FederalTaxRules deductionGrowthRules = service.project(
                publishedRules,
                2026,
                2028,
                planningAssumptions(
                        BigDecimal.ZERO,
                        new BigDecimal("0.05"),
                        new BigDecimal("0.025")));

        assertNotEquals(
                bracketGrowthRules.getTaxBrackets().get(1).getUpperBound(),
                deductionGrowthRules.getTaxBrackets().get(1).getUpperBound());

        assertEquals(
                publishedRules.getStandardDeduction(),
                bracketGrowthRules.getStandardDeduction());

        assertEquals(
                publishedRules.getTaxBrackets().get(1).getUpperBound(),
                deductionGrowthRules.getTaxBrackets().get(1).getUpperBound());

        assertNotEquals(
                bracketGrowthRules.getStandardDeduction(),
                deductionGrowthRules.getStandardDeduction());
    }

    @Test
    void appliesFutureMarginalRateAdjustmentWithoutChangingParameters() {

        PlanningAssumptions assumptions =
                planningAssumptions(
                        new BigDecimal("0.025"),
                        new BigDecimal("0.030"),
                        new BigDecimal("0.01"),
                        new BigDecimal("0.03"),
                        2031);

        FederalTaxRules beforeEffectiveYear = service.project(
                publishedRules, 2026, 2030, assumptions);

        FederalTaxRules effectiveYearRules = service.project(
                publishedRules, 2026, 2031, assumptions);

        FederalTaxRules laterYearRules = service.project(
                publishedRules, 2026, 2032, assumptions);

        assertEquals(
                new BigDecimal("0.22"),
                beforeEffectiveYear.getTaxBrackets().get(2).getTaxRate());

        assertEquals(
                new BigDecimal("0.25"),
                effectiveYearRules.getTaxBrackets().get(2).getTaxRate());

        assertEquals(
                new BigDecimal("0.25"),
                laterYearRules.getTaxBrackets().get(2).getTaxRate());

        FederalTaxRules noAdjustmentRules = service.project(
                publishedRules,
                2026,
                2031,
                planningAssumptions(
                        new BigDecimal("0.025"),
                        new BigDecimal("0.030"),
                        new BigDecimal("0.20"),
                        BigDecimal.ZERO,
                        null));

        assertEquals(
                noAdjustmentRules.getTaxBrackets().get(2).getUpperBound(),
                effectiveYearRules.getTaxBrackets().get(2).getUpperBound());

        assertEquals(
                noAdjustmentRules.getStandardDeduction(),
                effectiveYearRules.getStandardDeduction());
    }

    @Test
    void supportsNegativeFutureMarginalRateAdjustment() {

        FederalTaxRules projectedRules = service.project(
                publishedRules,
                2026,
                2031,
                planningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("-0.02"),
                        2031));

        assertEquals(
                new BigDecimal("0.20"),
                projectedRules.getTaxBrackets().get(2).getTaxRate());
    }

    private PlanningAssumptions planningAssumptions(
            BigDecimal bracketGrowthRate,
            BigDecimal deductionGrowthRate,
            BigDecimal generalInflationRate) {

        return planningAssumptions(
                bracketGrowthRate,
                deductionGrowthRate,
                generalInflationRate,
                BigDecimal.ZERO,
                null);
    }

    private PlanningAssumptions planningAssumptions(
            BigDecimal bracketGrowthRate,
            BigDecimal deductionGrowthRate,
            BigDecimal generalInflationRate,
            BigDecimal futureMarginalRateAdjustment,
            Integer futureMarginalRateEffectiveYear) {

        return new PlanningAssumptions(
                new EconomicAssumptions(
                        new BigDecimal("0.070"),
                        generalInflationRate),
                new TaxAssumptions(
                        bracketGrowthRate,
                        deductionGrowthRate,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        new BigDecimal("0.25"),
                        futureMarginalRateAdjustment,
                        futureMarginalRateEffectiveYear),
                30,
                LocalDate.of(2026, 1, 1));
    }

    private BigDecimal projected(
            BigDecimal publishedValue,
            BigDecimal growthRate,
            int years) {

        return publishedValue
                .multiply(BigDecimal.ONE.add(growthRate).pow(years))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private void assertRulesEqual(
            FederalTaxRules expected,
            FederalTaxRules actual) {

        assertEquals(expected.getFilingStatus(), actual.getFilingStatus());
        assertEquals(expected.getStandardDeduction(), actual.getStandardDeduction());
        assertEquals(
                expected.getTaxBrackets().size(),
                actual.getTaxBrackets().size());

        for (int i = 0; i < expected.getTaxBrackets().size(); i++) {

            FederalTaxBracket expectedBracket =
                    expected.getTaxBrackets().get(i);

            FederalTaxBracket actualBracket =
                    actual.getTaxBrackets().get(i);

            assertEquals(
                    expectedBracket.getLowerBound(),
                    actualBracket.getLowerBound());

            assertEquals(
                    expectedBracket.getUpperBound(),
                    actualBracket.getUpperBound());

            assertEquals(
                    expectedBracket.getTaxRate(),
                    actualBracket.getTaxRate());
        }
    }
}
