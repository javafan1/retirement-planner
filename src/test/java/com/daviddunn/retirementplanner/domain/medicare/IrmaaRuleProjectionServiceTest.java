package com.daviddunn.retirementplanner.domain.medicare;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.IrmaaRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IrmaaRuleProjectionServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0275"})
    void projectedBoundariesRemainContinuousThrough2056(String inflation) throws Exception {
        IrmaaRules published = new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getIrmaaRules();
        PlanningAssumptions assumptions = new PlanningAssumptions(
                BigDecimal.ZERO, new BigDecimal(inflation), 31, LocalDate.of(2026, 1, 1));
        for (int year = 2026; year <= 2056; year++) {
            IrmaaRules projected = new IrmaaRuleProjectionService().project(published, 2026, year, assumptions);
            BigDecimal multiplier = BigDecimal.ONE.add(new BigDecimal(inflation)).pow(year - 2026);
            for (FilingStatus status : List.of(FilingStatus.SINGLE, FilingStatus.MARRIED_FILING_JOINTLY)) {
                var base = published.getBrackets().stream().filter(bracket -> bracket.getFilingStatus() == status).toList();
                var tiers = projected.getBrackets().stream().filter(bracket -> bracket.getFilingStatus() == status).toList();
                assertSame(tiers.getFirst(), projected.getBracket(status, BigDecimal.ZERO));
                assertNull(tiers.getLast().getMaximumModifiedAdjustedGrossIncome());
                assertSame(tiers.getLast(), projected.getBracket(status, new BigDecimal("1E100")));
                for (int i = 0; i < tiers.size() - 1; i++) {
                    var left = tiers.get(i);
                    var right = tiers.get(i + 1);
                    BigDecimal threshold = left.getMaximumModifiedAdjustedGrossIncome();
                    assertEquals(base.get(i).getMaximumModifiedAdjustedGrossIncome().multiply(multiplier)
                            .setScale(0, RoundingMode.HALF_UP), threshold);
                    assertEquals(threshold, right.getMinimumModifiedAdjustedGrossIncome());
                    assertEquals(base.get(i).isMinimumIncomeInclusive(), left.isMinimumIncomeInclusive());
                    assertEquals(base.get(i).isMaximumIncomeInclusive(), left.isMaximumIncomeInclusive());
                    assertEquals(base.get(i + 1).isMinimumIncomeInclusive(), right.isMinimumIncomeInclusive());
                    for (String offset : List.of("-0.01", "-0.000000001", "0", "0.000000001", "0.01")) {
                        BigDecimal delta = new BigDecimal(offset);
                        BigDecimal income = threshold.add(delta);
                        assertEquals(1, tiers.stream().filter(bracket -> bracket.contains(income)).count(),
                                status + " " + year + " " + income);
                        assertSame(delta.signum() < 0 ? left : delta.signum() > 0 ? right : i == 4 ? right : left,
                                projected.getBracket(status, income));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"446503.812074615590867589", "446503.81"})
    void former2044GapResolvesToThirdTier(String income) throws Exception {
        IrmaaRules published = new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getIrmaaRules();
        IrmaaRules projected = new IrmaaRuleProjectionService().project(published, 2026, 2044,
                new PlanningAssumptions(BigDecimal.ZERO, new BigDecimal("0.0275"), 30, LocalDate.of(2027, 1, 1)));
        var tier = projected.getBracket(FilingStatus.MARRIED_FILING_JOINTLY, new BigDecimal(income));
        assertSame(projected.getBrackets().stream()
                .filter(bracket -> bracket.getFilingStatus() == FilingStatus.MARRIED_FILING_JOINTLY).toList().get(2), tier);
        assertEquals(new BigDecimal("446502"), tier.getMinimumModifiedAdjustedGrossIncome());
        assertEquals(new BigDecimal("557313"), tier.getMaximumModifiedAdjustedGrossIncome());
        assertFalse(tier.isMinimumIncomeInclusive());
        assertTrue(tier.isMaximumIncomeInclusive());
    }
}
