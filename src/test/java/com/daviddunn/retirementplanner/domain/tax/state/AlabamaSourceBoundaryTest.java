package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;

class AlabamaSourceBoundaryTest {
    @Test
    void representedUnmetEmployerQualificationDoesNotReceiveOrdinarySeniorRelief() throws Exception {
        var account=new com.daviddunn.retirementplanner.domain.financial.Traditional401K("Employer",AccountOwnership.PRIMARY,b("100000"));
        account.setRetirementParticipantFacts(new RetirementParticipantFacts(null,null,
                RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET,null));
        var source=IncomeSourceAttribution.withdrawal(account,b("50000"),IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var income=new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"),List.of(source));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new AlabamaProductionTaxCalculator().calculate(
                request(StateTaxJurisdiction.AL,2026,false,70,70,income)).calculationStatus());
    }

    @Test
    void ageBirthdayAndDeceasedFilerTestsRefuseOnlyMaterialAmbiguity() throws Exception {
        for(var value:List.of("0","50000")) {
            var i=new TaxIncome(b("0"),b("0"),b(value),b("0"),b("0"),List.of(retirement(AccountOwnership.PRIMARY,value,2026)));
            var r=request(StateTaxJurisdiction.AL,2026,false,64,64,i);
            var h=new TaxHouseholdContext(LocalDate.of(2026,1,1),Map.of(AccountOwnership.PRIMARY,LocalDate.of(1962,1,1)),
                    Set.of(AccountOwnership.PRIMARY),Set.of(AccountOwnership.PRIMARY),Optional.empty());
            var result=new AlabamaProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026,r.filingStatus(),h,i,r.federal(),r.projectedRules(),r.assumptions()));
            assertEquals(value.equals("0")?StateTaxCalculationStatus.CALCULATED:StateTaxCalculationStatus.UNSUPPORTED,result.calculationStatus());
        }
        var i=new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"),List.of(retirement(AccountOwnership.SPOUSE,"50000",2026)));
        var r=request(StateTaxJurisdiction.AL,2026,true,65,65,i);
        var h=r.household().withActiveOwners(Set.of(AccountOwnership.SPOUSE));
        var result=new AlabamaProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026,r.filingStatus(),h,i,r.federal(),r.projectedRules(),r.assumptions()));
        assertEquals(StateTaxCalculationStatus.CALCULATED,result.calculationStatus());
        assertTrue(result.limitations().getLast().contains("capped senior subtraction 6000"));
    }

    @Test
    void rmdAndConversionShareCapAndEmployerConversionDoesNotAcquireIraQualification() throws Exception {
        var account=new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA",AccountOwnership.PRIMARY,b("1000000"));
        var rmd=IncomeSourceAttribution.withdrawal(account,b("30000"),IncomeSourceAttribution.Kind.RMD,IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var i=new TaxIncome(b("0"),b("0"),b("30000"),b("20000"),b("0"),List.of(rmd,conversion(AccountOwnership.PRIMARY,"20000",2026)));
        AlabamaProductionTaxCalculatorTest.check("1817.30",request(StateTaxJurisdiction.AL,2026,false,75,75,i));
        var original=conversion(AccountOwnership.PRIMARY,"50000",2026);
        var employer=new IncomeSourceAttribution(original.kind(),original.purpose(),original.owner(),Optional.of(IncomeSourceAttribution.AccountReference.capture(
                new com.daviddunn.retirementplanner.domain.financial.Traditional401K("Employer",AccountOwnership.PRIMARY,b("1000000")))),original.destination(),original.description(),original.grossAmount(),
                original.modeledTaxableAmount(),original.taxYear(),original.pension());
        var unsupported=new TaxIncome(b("0"),b("0"),b("0"),b("50000"),b("0"),List.of(employer));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new AlabamaProductionTaxCalculator().calculate(request(StateTaxJurisdiction.AL,2026,false,75,75,unsupported)).calculationStatus());
    }

    @Test
    void specialFactsPartialBasisDuplicateAndWrongYearNeverBecomeOrdinaryByName() throws Exception {
        var partial=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.PENSION,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY),Optional.empty(),Optional.empty(),"Verified DB by name only",b("50000"),Optional.of(b("20000")),OptionalInt.of(2026),Optional.empty());
        var i=new TaxIncome(b("20000"),b("0"),b("0"),b("0"),b("0"),List.of(partial));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new AlabamaProductionTaxCalculator().calculate(request(StateTaxJurisdiction.AL,2026,false,70,70,i)).calculationStatus());
        var s=retirement(AccountOwnership.PRIMARY,"50000",2026);
        for(var sources:List.of(List.of(s,s),List.of(s.inYear(2027)),List.of(IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.OTHER,
                IncomeSourceAttribution.Purpose.INCOME,"Represented special government service/basis",b("50000"),Optional.of(b("50000"))).inYear(2026)))) {
            var income=new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"),sources);
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new AlabamaProductionTaxCalculator().calculate(request(StateTaxJurisdiction.AL,2026,false,70,70,income)).calculationStatus());
        }
    }
}
