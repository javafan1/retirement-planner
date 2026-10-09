package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.*;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StateTaxHardeningTest {

    private final SimplifiedStateTaxCalculator calculator = new SimplifiedStateTaxCalculator();
    StateTaxHardeningTest() throws Exception { }

    @ParameterizedTest
    @CsvSource({"999999,0", "1000000,0", "1000001,.01"})
    void behavioralHealthSurchargeBoundary(String taxable, String additional) {
        BigDecimal agi = bd(taxable).add(bd("5900"));
        var result = calculator.calculate(new StateTaxRequest("CA", 2026, FilingStatus.SINGLE,
                agi, BigDecimal.ZERO, List.of(person(Owner.PRIMARY,"60", "0", line(IncomeType.INTEREST, agi.toPlainString())))));
        amount(additional, result.estimate().orElseThrow().additionalTaxes());
    }

    @Test
    void coloradoCoupleLimitsRemainPerPersonAndFullSocialSecurityCanExceedCap() {
        var request = new StateTaxRequest("CO", 2026, FilingStatus.MARRIED_FILING_JOINTLY,
                bd("100000"), bd("80000"), List.of(
                person(Owner.PRIMARY,"65","40000",line(IncomeType.SOCIAL_SECURITY,"30000"),qualified(IncomeType.PENSION,"30000","COLORADO_QUALIFYING_PENSION_ANNUITY")),
                person(Owner.SPOUSE,"60","0",qualified(IncomeType.PENSION,"30000","COLORADO_QUALIFYING_PENSION_ANNUITY"))));
        // Primary SS30000 exceeds24000: no primary pension room. Spouse separately gets20000.
        var estimate = calculator.calculate(request).estimate().orElseThrow();
        amount("30000",estimate.taxableIncome());
        amount("1320",estimate.finalEstimatedTax());
    }

    @ParameterizedTest
    @CsvSource({"94999,25000", "95000,25000", "95001,20000"})
    void coloradoJointUnder65SocialSecurityIncomeThreshold(String agi,String exclusion) {
        var request = new StateTaxRequest("CO",2026,FilingStatus.MARRIED_FILING_JOINTLY,
                bd(agi),bd("60000"),List.of(
                person(Owner.PRIMARY,"64","40000",qualified(IncomeType.SOCIAL_SECURITY,"25000","COLORADO_QUALIFYING_PENSION_ANNUITY")),
                person(Owner.SPOUSE,"54","0")));
        var estimate=calculator.calculate(request).estimate().orElseThrow();
        amount(exclusion,estimate.retirementExclusions().getFirst().amount());
    }

    @Test
    void coloradoCannotUseArbitraryTaxableSocialSecurityAllocationToFavorOlderSpouse() {
        var request = new StateTaxRequest("CO",2026,FilingStatus.MARRIED_FILING_JOINTLY,bd("100000"),bd("80000"),List.of(
                person(Owner.PRIMARY,"65","40000",line(IncomeType.SOCIAL_SECURITY,"30000")),
                person(Owner.SPOUSE,"54","40000",line(IncomeType.SOCIAL_SECURITY,"10000"))));
        unsupported(calculator.calculate(request));
    }

    @Test
    void newYorkRecaptureBoundaryUsesAdjustedStateAgiNotFederalAgi() {
        var request = new StateTaxRequest("NY",2026,FilingStatus.SINGLE,bd("127650"),BigDecimal.ZERO,List.of(
                person(Owner.PRIMARY,"65","0",qualified(IncomeType.TRADITIONAL_IRA,"20000","QUALIFYING_PRIVATE_RETIREMENT"),line(IncomeType.INTEREST,"107650"))));
        var estimate=calculator.calculate(request).estimate().orElseThrow();
        amount("99650",estimate.taxableIncome());
        amount("5311.10",estimate.finalEstimatedTax());
    }

    @Test
    void newYorkStateGovernmentPensionIsUnlimitedAndNoPrivateExclusionIsConsumed() {
        Income pension=new Income(IncomeType.PENSION,bd("50000"),bd("50000"),PensionSource.NEW_YORK_STATE_LOCAL_GOVERNMENT,Map.of());
        var request = new StateTaxRequest("NY",2026,FilingStatus.SINGLE,bd("80000"),BigDecimal.ZERO,List.of(
                person(Owner.PRIMARY,"65","0",pension,qualified(IncomeType.ROTH_CONVERSION,"30000","QUALIFYING_PRIVATE_RETIREMENT"))));
        amount("2000",calculator.calculate(request).estimate().orElseThrow().taxableIncome());
    }

    @ParameterizedTest
    @CsvSource({"59.4,52000", "59.5,32000"})
    void newYorkConversionAgeBoundary(String age,String taxable) {
        var request=new StateTaxRequest("NY",2026,FilingStatus.SINGLE,bd("60000"),BigDecimal.ZERO,List.of(
                person(Owner.PRIMARY,age,"0",qualified(IncomeType.ROTH_CONVERSION,"60000","QUALIFYING_PRIVATE_RETIREMENT"))));
        amount(taxable,calculator.calculate(request).estimate().orElseThrow().taxableIncome());
    }

    @Test
    void newJerseyEstablishedStateConversionBasisMayDifferFromFederalTaxableAmount() {
        Income conversion=new Income(IncomeType.ROTH_CONVERSION,bd("40000"),bd("20000"),PensionSource.PRIVATE,
                Map.of("STATE_TAXABLE_CONVERSION_AMOUNT_ESTABLISHED",Qualification.ELIGIBLE));
        var request=new StateTaxRequest("NJ",2026,FilingStatus.SINGLE,bd("40000"),BigDecimal.ZERO,List.of(person(Owner.PRIMARY,"60","0",conversion)));
        var estimate=calculator.calculate(request).estimate().orElseThrow();
        amount("20000",estimate.startingTaxBase());
        amount("266",estimate.finalEstimatedTax());
    }

    @ParameterizedTest
    @CsvSource({"PA,COMPLETE_IRA_TO_ROTH_CONVERSION", "NJ,STATE_TAXABLE_CONVERSION_AMOUNT_ESTABLISHED"})
    void zeroStateConversionAmountDoesNotProveQualificationOrBasis(String state,String qualification) {
        Income conversion=new Income(IncomeType.ROTH_CONVERSION,bd("40000"),BigDecimal.ZERO,PensionSource.PRIVATE,Map.of());
        var request=new StateTaxRequest(state,2026,FilingStatus.SINGLE,bd("40000"),BigDecimal.ZERO,List.of(person(Owner.PRIMARY,"65","0",conversion)));
        unsupported(calculator.calculate(request));
    }

    @Test
    void unknownAndExplicitlyIneligibleCompletePennsylvaniaConversionAreUnsupported() {
        Income conversion=new Income(IncomeType.ROTH_CONVERSION,bd("10000"),bd("10000"),PensionSource.PRIVATE,
                Map.of("COMPLETE_IRA_TO_ROTH_CONVERSION",Qualification.INELIGIBLE));
        var request=new StateTaxRequest("PA",2026,FilingStatus.SINGLE,bd("10000"),BigDecimal.ZERO,List.of(person(Owner.PRIMARY,"65","0",conversion)));
        unsupported(calculator.calculate(request));
    }

    private static PersonIncome person(Owner owner,String age,String grossSs,Income... income) {
        BigDecimal taxable=java.util.Arrays.stream(income).filter(line->line.type()==IncomeType.SOCIAL_SECURITY)
                .map(Income::federalIncluded).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new PersonIncome(owner,bd(age),bd(grossSs),taxable,Set.of(),List.of(income));
    }
    private static Income line(IncomeType type,String value) { return new Income(type,bd(value),bd(value),PensionSource.PRIVATE,Map.of()); }
    private static Income qualified(IncomeType type,String value,String qualification) { return new Income(type,bd(value),bd(value),PensionSource.PRIVATE,Map.of(qualification,Qualification.ELIGIBLE)); }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static void amount(String expected,BigDecimal actual) { assertEquals(0,bd(expected).compareTo(actual)); }
    private static void unsupported(StateTaxResult result) { assertEquals(StateTaxResult.Status.UNSUPPORTED,result.status()); assertTrue(result.estimate().isEmpty()); }
}
