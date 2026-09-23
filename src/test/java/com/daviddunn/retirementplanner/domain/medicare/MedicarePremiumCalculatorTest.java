package com.daviddunn.retirementplanner.domain.medicare;

import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.GovernmentRuleProjectionService;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MedicarePremiumCalculatorTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void formerGapAndHighestTierUseProjectedPremiumsPerParticipant(int participants) throws Exception {
        var published = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var assumptions = new PlanningAssumptions(
                new EconomicAssumptions(new BigDecimal("0.055"), new BigDecimal("0.0275"),
                        new BigDecimal("0.06"), new BigDecimal("0.03")),
                null, 30, LocalDate.of(2027, 1, 1));
        var projected = new GovernmentRuleProjectionService().project(published, assumptions, 2044);
        var baseTiers = published.getIrmaaRules().getBrackets().stream()
                .filter(bracket -> bracket.getFilingStatus() == FilingStatus.MARRIED_FILING_JOINTLY).toList();
        var projectedTiers = projected.getIrmaaRules().getBrackets().stream()
                .filter(bracket -> bracket.getFilingStatus() == FilingStatus.MARRIED_FILING_JOINTLY).toList();
        for (String income : List.of("446503.81", "446503.812074615590867589", "1E100")) {
            BigDecimal magi = new BigDecimal(income);
            int tierIndex = income.equals("1E100") ? 5 : 2;
            var result = new MedicarePremiumCalculator().calculate(
                    new FederalTaxCalculation(magi, BigDecimal.ZERO, BigDecimal.ZERO, magi, BigDecimal.ZERO),
                    FilingStatus.MARRIED_FILING_JOINTLY, projected, participants);
            assertSame(projectedTiers.get(tierIndex), result.irmaaBracket());
            assertEquals(magi, result.modifiedAdjustedGrossIncome());
            assertEquals(participants, result.coveredMedicareParticipants());
            BigDecimal multiplier = new BigDecimal("1.06").pow(18);
            BigDecimal expectedB = baseTiers.get(tierIndex).getMonthlyPartBPremium()
                    .multiply(multiplier).setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(participants));
            BigDecimal expectedD = baseTiers.get(tierIndex).getMonthlyPartDPremium()
                    .multiply(multiplier).setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(participants));
            assertEquals(expectedB, result.monthlyPartBPremium());
            assertEquals(expectedD, result.monthlyPartDPremium());
            assertEquals(expectedB.multiply(BigDecimal.valueOf(12)), result.annualPartBPremium());
            assertEquals(expectedD.multiply(BigDecimal.valueOf(12)), result.annualPartDPremium());
            assertEquals(expectedB.add(expectedD).multiply(BigDecimal.valueOf(12)), result.totalAnnualMedicarePremium());
        }
    }
}
