package com.daviddunn.retirementplanner.domain.tax.state.alabama;

import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class AlabamaFederalLiabilityDeductionTest {
    private final AlabamaFederalLiabilityDeduction helper = new AlabamaFederalLiabilityDeduction();

    @Test
    void independentSeniorLiabilitiesDoNotChangeFederalCash() throws Exception {
        var single = request(StateTaxJurisdiction.MI, 2026, false, 65, 65,
                new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0")));
        var result = helper.calculate(single);
        assertEquals(0, b("3820").compareTo(single.federal().getFederalIncomeTax()));
        assertEquals(b("2854.00"), result.eligibleLiability());
        assertEquals(0, b("966").compareTo(result.modeledCashTax().subtract(result.eligibleLiability())));
        var joint = request(StateTaxJurisdiction.MI, 2026, true, 65, 65,
                new TaxIncome(b("0"), b("0"), b("100000"), b("0"), b("0")));
        assertEquals(b("5804.00"), helper.calculate(joint).eligibleLiability());
        var mixed = request(StateTaxJurisdiction.MI, 2026, true, 64, 65, joint.income());
        assertEquals(0, b("6722").compareTo(helper.calculate(mixed).eligibleLiability()));
    }

    @Test
    void temporaryDeductionPhaseoutAndExpirationAreIndependentWorksheets() {
        var rules = new AlabamaTaxRules();
        assertEquals(0, b("6000").compareTo(rules.enhancedSeniorDeduction(2026, FilingStatus.SINGLE, b("75000"), 1)));
        assertEquals(0, b("3000").compareTo(rules.enhancedSeniorDeduction(2026, FilingStatus.SINGLE, b("125000"), 1)));
        assertEquals(0, b("0").compareTo(rules.enhancedSeniorDeduction(2026, FilingStatus.SINGLE, b("175000"), 1)));
        assertEquals(0, b("6000").compareTo(rules.enhancedSeniorDeduction(2028, FilingStatus.MARRIED_FILING_JOINTLY, b("200000"), 2)));
        assertEquals(0, b("0").compareTo(rules.enhancedSeniorDeduction(2029, FilingStatus.MARRIED_FILING_JOINTLY, b("100000"), 2)));
    }

    @Test
    void eligibleWorksheetAddsNiitOnceAndSubtractsOnlySupportedCredits() {
        assertEquals(b("4400.00"), helper.fromWorksheet(b("4000"), b("500"), b("100")));
        assertEquals(b("0.00"), helper.fromWorksheet(b("100"), b("0"), b("200")));
        assertEquals(b("100.0049"), helper.fromWorksheet(b("100.0049"), b("0"), b("0")));
    }

    @Test
    void actualEligibleLiabilityUsesExpirationAndEvidencedNiitOnce() throws Exception {
        var income = new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"));
        var y2028=request(StateTaxJurisdiction.AL,2028,false,65,65,income);
        var y2029=request(StateTaxJurisdiction.AL,2029,false,66,66,income);
        assertEquals(b("2848.00"),helper.calculate(y2028).eligibleLiability());
        assertEquals(b("3568.00"),helper.calculate(y2029).eligibleLiability());
        assertEquals(0,b("3820").compareTo(y2028.federal().getFederalIncomeTax()));
        assertEquals(0,b("3820").compareTo(y2029.federal().getFederalIncomeTax()));
        var r=request(StateTaxJurisdiction.AL,2026,false,65,65,new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("300000")));
        var n=new com.daviddunn.retirementplanner.domain.tax.NetInvestmentIncomeTaxCalculation(
                com.daviddunn.retirementplanner.domain.tax.NetInvestmentIncomeTaxCalculation.MODEL,FilingStatus.SINGLE,
                b("300000"),b("0"),b("300000"),b("0"),b("0"),b("200000"),b(".038"),b("3800.00"));
        var f=r.federal();
        var evidenced=new FederalTaxCalculation(f.getAdjustedGrossIncome(),f.getTaxableSocialSecurity(),f.getStandardDeduction(),
                f.getTaxableIncome(),f.getFederalIncomeTax().add(n.tax()),java.util.Optional.of(n));
        var result=helper.calculate(new StateTaxCalculationRequest(r.taxYear(),r.filingStatus(),r.household(),r.income(),
                evidenced,r.projectedRules(),r.assumptions()));
        assertEquals(b("71216.75"),result.eligibleLiability());
        assertEquals(b("71934.25"),result.modeledCashTax());
    }

    @Test
    void unexplainedFederalCreditOrSpecialTaxIsNotAssumedSupported() throws Exception {
        var r = request(StateTaxJurisdiction.MI, 2026, false, 60, 60,
                new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0")));
        var f = r.federal();
        var altered = new FederalTaxCalculation(f.getAdjustedGrossIncome(), f.getTaxableSocialSecurity(),
                f.getStandardDeduction(), f.getTaxableIncome(), f.getFederalIncomeTax().subtract(b("100")));
        assertThrows(IllegalArgumentException.class, () -> helper.calculate(new StateTaxCalculationRequest(
                r.taxYear(), r.filingStatus(), r.household(), r.income(), altered, r.projectedRules(), r.assumptions())));
    }
}
