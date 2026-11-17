package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent IRS RP 2025-32 / RP 2026-35 and IRC 151 worksheets. No future CO rate is assumed. */
class ColoradoFederalBridgeCompletionTest {
    @ParameterizedTest
    @CsvSource({"2026,false,50000,16100,2050,6000,25850,0",
            "2026,false,75000,16100,2050,6000,50850,0",
            "2026,false,75001,16100,2050,5999.94,50851.06,0",
            "2026,false,175000,16100,2050,0,156850,0",
            "2026,true,150000,32200,3300,12000,102500,0",
            "2026,true,150001,32200,3300,11999.88,102501.12,0",
            "2026,true,250000,32200,3300,0,214500,0",
            "2026,false,300001,16100,2050,0,281851,17150",
            "2026,true,300001,32200,3300,0,264501,33500",
            "2027,false,50000,16600,2100,6000,25300,0",
            "2027,true,100000,33200,3400,12000,51400,0"})
    void completeDeductionsWithoutChangingFederalResult(int year, boolean joint, String agi, String basic,
            String age, String senior, String signed, String addback) {
        var status = joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        var federal = federal(agi, basic);
        var result = new ColoradoFederalTaxBaseBridge().calculate(year, status, context(year, joint), federal);
        same(basic, result.basicDeduction());
        same(age, result.ageDeduction());
        same(senior, result.enhancedSeniorDeduction());
        same(signed, result.signedFederalBase());
        same(addback, result.standardDeductionAddback());
        same(agi, federal.getAdjustedGrossIncome());
        same(basic, federal.getStandardDeduction());
        same("123", federal.getFederalIncomeTax());
        same(n(agi).subtract(n(basic)).max(BigDecimal.ZERO).toPlainString(), federal.getTaxableIncome());
    }

    @Test
    void signedBaseRemainsNegativeUntilStateReconciliation() {
        var result = new ColoradoFederalTaxBaseBridge().calculate(2026, FilingStatus.SINGLE,
                context(2026, false), federal("10000", "16100"));
        same("-14150", result.signedFederalBase());
        same("-14150", result.beforeStateRetirementSubtractions());
    }

    @ParameterizedTest
    @CsvSource({"2026,75000,1,6000", "2028,75000,1,6000", "2029,75000,1,0",
            "2033,75000,1,0", "2028,150001,2,11999.88", "2028,250000,2,0"})
    void temporarySeniorDeductionExpiresIndependentlyOfUnverifiedStateRates(int year, String agi,
            int seniors, String expected) {
        same(expected, new ColoradoTaxRules().enhancedSeniorDeduction(year,
                seniors == 2 ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE, n(agi), seniors));
        if (year != 2026) assertThrows(IllegalArgumentException.class, () -> new ColoradoTaxRules().rate(year));
    }

    @Test
    void inconsistentFederalBaseAndUnknownPublishedDeductionsFailSafely() {
        var bridge = new ColoradoFederalTaxBaseBridge();
        assertThrows(IllegalArgumentException.class, () -> bridge.calculate(2027, FilingStatus.SINGLE,
                context(2027, false), federal("50000", "16100")));
        assertThrows(IllegalArgumentException.class, () -> bridge.calculate(2028, FilingStatus.SINGLE,
                context(2028, false), federal("50000", "16600")));
        assertThrows(IllegalArgumentException.class, () -> bridge.calculate(2026, FilingStatus.SINGLE,
                context(2026, false), new FederalTaxCalculation(n("50000"), n("0"), n("16100"), n("100"), n("0"))));
    }

    static TaxHouseholdContext context(int year, boolean joint) {
        var births = joint ? Map.of(AccountOwnership.PRIMARY, LocalDate.of(year - 67, 6, 1),
                AccountOwnership.SPOUSE, LocalDate.of(year - 66, 8, 1))
                : Map.of(AccountOwnership.PRIMARY, LocalDate.of(year - 67, 6, 1));
        return new TaxHouseholdContext(LocalDate.of(year, 1, 1), births, births.keySet(), births.keySet(), Optional.empty());
    }

    static FederalTaxCalculation federal(String agi, String basic) {
        return new FederalTaxCalculation(n(agi), n("0"), n(basic), n(agi).subtract(n(basic)).max(BigDecimal.ZERO), n("123"));
    }

    static BigDecimal n(String amount) { return new BigDecimal(amount); }
    static void same(String expected, BigDecimal actual) { assertEquals(0, n(expected).compareTo(actual), expected + " vs " + actual); }
}
