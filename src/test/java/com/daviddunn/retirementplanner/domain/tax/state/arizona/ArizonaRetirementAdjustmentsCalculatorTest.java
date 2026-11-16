package com.daviddunn.retirementplanner.domain.tax.state.arizona;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ArizonaRetirementAdjustmentsCalculatorTest {
    private final ArizonaRetirementAdjustmentsCalculator calculator =
            new ArizonaRetirementAdjustmentsCalculator();

    // Independent worksheets: ARS 43-1022(10),(35), 43-1023(E),
    // IRS Schedule 1-A Part V lines 31-37. No standard deduction/tax is stipulated.
    @ParameterizedTest
    @CsvSource({
            "SINGLE,75000,1,6000",
            "SINGLE,75001,1,5999.94",
            "SINGLE,100000,1,4500",
            "SINGLE,175000,1,0",
            "SINGLE,200000,1,0",
            "MARRIED_FILING_JOINTLY,150000,2,12000",
            "MARRIED_FILING_JOINTLY,150001,2,11999.88",
            "MARRIED_FILING_JOINTLY,178000,2,8640",
            "MARRIED_FILING_JOINTLY,178000,1,4320",
            "MARRIED_FILING_JOINTLY,250000,2,0",
            "MARRIED_FILING_JOINTLY,60000,0,0"
    })
    void independentlyCalculatedPhaseout(FilingStatus status, String agi, int seniors, String expected) {
        var result = calculator.calculate(2026, status, money(agi), BigDecimal.ZERO, seniors);
        equalMoney(expected, result.enhancedSeniorSubtraction());
        equalMoney(Integer.toString(2100 * seniors), result.ageExemption());
    }

    @Test
    void removesOnlyFederallyTaxableSocialSecurityAndPreservesSignedBase() {
        var result = calculator.calculate(2026, FilingStatus.SINGLE, money("20000"), money("20000"), 1);
        equalMoney("20000", result.socialSecuritySubtraction());
        equalMoney("-8100", result.signedAdjustedIncome());
        // This component must not floor or report a final taxable-income/tax amount.
    }

    @Test
    void conversionAndWithdrawalRemainInBaseAndSocialSecurityStillAffectsMagi() {
        // Ordinary withdrawal 50,000 + conversion 20,000 + taxable SS 20,000.
        var result = calculator.calculate(2026, FilingStatus.SINGLE, money("90000"), money("20000"), 1);
        equalMoney("5100", result.enhancedSeniorSubtraction());
        equalMoney("62800", result.signedAdjustedIncome());
        var withoutConversion = calculator.calculate(2026, FilingStatus.SINGLE,
                money("70000"), money("20000"), 1);
        equalMoney("20900", result.signedAdjustedIncome().subtract(withoutConversion.signedAdjustedIncome()));
    }

    @Test
    void enhancedSubtractionExpiresButAgeExemptionRemainsNominal() {
        var before = calculator.calculate(2028, FilingStatus.MARRIED_FILING_JOINTLY,
                money("60000"), BigDecimal.ZERO, 2);
        var after = calculator.calculate(2029, FilingStatus.MARRIED_FILING_JOINTLY,
                money("60000"), BigDecimal.ZERO, 2);
        equalMoney("43800", before.signedAdjustedIncome());
        equalMoney("55800", after.signedAdjustedIncome());
        equalMoney("4200", after.ageExemption());
    }

    @Test
    void filingStatusAndEligibleCountAreIndependentAnnualFacts() {
        var joint = calculator.calculate(2026, FilingStatus.MARRIED_FILING_JOINTLY,
                money("160000"), BigDecimal.ZERO, 2);
        var single = calculator.calculate(2027, FilingStatus.SINGLE,
                money("160000"), BigDecimal.ZERO, 1);
        equalMoney("10800", joint.enhancedSeniorSubtraction());
        equalMoney("900", single.enhancedSeniorSubtraction());
    }

    @Test
    void rejectsInvalidOrUnsupportedFacts() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(2025,
                FilingStatus.SINGLE, money("100"), BigDecimal.ZERO, 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(2026,
                FilingStatus.HEAD_OF_HOUSEHOLD, money("100"), BigDecimal.ZERO, 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(2026,
                FilingStatus.SINGLE, money("100"), BigDecimal.ZERO, 2));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(2026,
                FilingStatus.SINGLE, money("100"), BigDecimal.ZERO, -1));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(2026,
                FilingStatus.SINGLE, money("100"), money("101"), 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(2026,
                FilingStatus.SINGLE, money("-1"), BigDecimal.ZERO, 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(2026,
                FilingStatus.SINGLE, money("100"), money("-1"), 0));
        assertThrows(NullPointerException.class, () -> calculator.calculate(2026,
                null, money("100"), BigDecimal.ZERO, 0));
    }

    @Test
    void arizonaIsAdmittedAfterMandatoryRuleResolution() {
        assertEquals(StateTaxJurisdiction.AZ, StateTaxJurisdiction.fromCode("AZ"));
        assertTrue(StateTaxJurisdiction.alphabeticalChoices().stream()
                .anyMatch(state -> state.name().equals("AZ")));
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static void equalMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }
}
