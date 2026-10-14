package com.daviddunn.retirementplanner.domain.tax.state.iowa;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.persistence.IowaTaxRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class IowaReliefAndCreditTest {
    private final IowaTaxRules rules = new IowaTaxRulesRepository().load();

    @ParameterizedTest
    @CsvSource({
            "SINGLE,false,8999,76,40,0", "SINGLE,false,9000,76,40,0", "SINGLE,false,9001,76,40,1",
            "SINGLE,true,23999,76,60,0", "SINGLE,true,24000,76,60,0", "SINGLE,true,24001,76,60,1",
            "MARRIED_FILING_JOINTLY,false,13499,76,80,0", "MARRIED_FILING_JOINTLY,false,13500,76,80,0",
            "MARRIED_FILING_JOINTLY,false,13501,76,80,0", "MARRIED_FILING_JOINTLY,true,31999,76,100,0",
            "MARRIED_FILING_JOINTLY,true,32000,76,100,0", "MARRIED_FILING_JOINTLY,true,32001,76,100,0",
            "MARRIED_FILING_JOINTLY,false,23500,380,80,300", "MARRIED_FILING_JOINTLY,false,23500,500,80,350",
            "MARRIED_FILING_JOINTLY,false,23500,430,80,350", "SINGLE,false,100000,3800,40,3760",
            "SINGLE,false,-16100,0,40,0", "SINGLE,false,20000,30,40,0"})
    void independentFormulaGoldens(FilingStatus status, boolean senior, String income, String regular, String credit, String expected) {
        var result = new IowaLowIncomeReliefCalculator().calculate(new IowaLowIncomeReliefCalculator.Inputs(
                status, new BigDecimal(regular), new BigDecimal(income), senior, new BigDecimal(credit)), rules);
        assertEquals(0, new BigDecimal(expected).compareTo(result.finalRawTax()));
        assertEquals(status == FilingStatus.MARRIED_FILING_JOINTLY, result.alternativeTax().isPresent());
        assertTrue(result.creditApplied().compareTo(result.selectedPreCreditTax()) <= 0);
    }

    @ParameterizedTest
    @CsvSource({"1961-12-31,true,20", "1962-01-01,false,20", "1962-01-02,false,0"})
    void threeIndependentAgeBoundaries(String dob, boolean senior, String ageCredit) {
        var context = context(Map.of(AccountOwnership.PRIMARY, LocalDate.parse(dob)), Set.of(AccountOwnership.PRIMARY));
        var allowance = new IowaPersonalCreditCalculator().calculate(2026, FilingStatus.SINGLE, context, rules).allowance().orElseThrow();
        assertEquals(senior, allowance.seniorReliefEligible());
        assertEquals(0, new BigDecimal(ageCredit).compareTo(allowance.ageCredit()));
    }

    @Test
    void singleSurvivorSelectsSpouseAndDoesNotCountDeadPrimary() {
        var context = context(Map.of(AccountOwnership.PRIMARY, LocalDate.of(1950, 1, 1),
                AccountOwnership.SPOUSE, LocalDate.of(1970, 1, 1)), Set.of(AccountOwnership.SPOUSE));
        var allowance = new IowaPersonalCreditCalculator().calculate(2026, FilingStatus.SINGLE, context, rules).allowance().orElseThrow();
        assertEquals(Set.of(AccountOwnership.SPOUSE), allowance.filers());
        assertFalse(allowance.seniorReliefEligible());
        assertEquals(0, allowance.ageCredit().signum());
    }

    @Test
    void deceasedFutureBirthdayRefusesInsteadOfInventingAge() {
        var context = context(Map.of(AccountOwnership.PRIMARY, LocalDate.of(1961, 12, 31),
                AccountOwnership.SPOUSE, LocalDate.of(1970, 1, 1)), Set.of(AccountOwnership.SPOUSE));
        var admission = new IowaPersonalCreditCalculator().calculate(2026, FilingStatus.MARRIED_FILING_JOINTLY, context, rules);
        assertTrue(admission.allowance().isEmpty());
        assertFalse(admission.reasons().isEmpty());
    }

    private static TaxHouseholdContext context(Map<AccountOwnership, LocalDate> births, Set<AccountOwnership> active) {
        return new TaxHouseholdContext(LocalDate.of(2026, 12, 31), births, births.keySet(), active, Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({"1970-01-01,1970-01-01,false,0", "1961-06-01,1970-01-01,true,20", "1961-06-01,1960-01-01,true,40"})
    void jointFilersQualifyIndependently(String primary, String spouse, boolean senior, String ageCredit) {
        var h = context(Map.of(AccountOwnership.PRIMARY, LocalDate.parse(primary), AccountOwnership.SPOUSE, LocalDate.parse(spouse)),
                Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE));
        var allowance = new IowaPersonalCreditCalculator().calculate(2026, FilingStatus.MARRIED_FILING_JOINTLY, h, rules).allowance().orElseThrow();
        assertEquals(senior, allowance.seniorReliefEligible());
        assertEquals(new BigDecimal("80"), allowance.personalCredit());
        assertEquals(0, new BigDecimal(ageCredit).compareTo(allowance.ageCredit()));
    }

    @Test
    void retainsComparisonPrecisionUntilFinalCents() {
        var result = new IowaLowIncomeReliefCalculator().calculate(new IowaLowIncomeReliefCalculator.Inputs(
                FilingStatus.SINGLE, new BigDecimal("76.005"), new BigDecimal("9001.005"), false, new BigDecimal("40")), rules);
        assertEquals(0, new BigDecimal("1.005").compareTo(result.finalRawTax()));
        assertEquals(new BigDecimal("1.01"), result.finalRawTax().setScale(2, java.math.RoundingMode.HALF_UP));
        var joint = new IowaLowIncomeReliefCalculator().calculate(new IowaLowIncomeReliefCalculator.Inputs(
                FilingStatus.MARRIED_FILING_JOINTLY, new BigDecimal("81.695"), new BigDecimal("15400"), false, new BigDecimal("80")), rules);
        assertEquals(new BigDecimal("81.695"), joint.selectedPreCreditTax());
        assertEquals(0, new BigDecimal("81.7").compareTo(joint.alternativeTax().orElseThrow()));
        assertEquals(new BigDecimal("1.70"), joint.finalRawTax().setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
