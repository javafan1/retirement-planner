package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent Phase 6E-K MS-01..32 worksheets; qualification is part of each premise. */
public class MississippiProductionTaxCalculatorTest {
    @ParameterizedTest
    @CsvSource({"2026,60,18300,0.00", "2026,60,18301,0.04", "2026,60,18300.12,0.00",
            "2026,60,18300.13,0.01", "2026,60,50000,1268.00", "2027,60,50000,1188.75",
            "2028,60,50000,1109.50", "2029,60,50000,1030.25", "2030,60,50000,951.00",
            "2031,60,50000,951.00", "2060,60,50000,951.00", "2026,65,50000,1208.00"})
    void annualInvestmentWorksheet(int year,int age,String investment,String expected) throws Exception {
        check(expected,request(state(),year,false,age,age,new TaxIncome(b("0"),b("0"),b("0"),b("0"),b(investment))));
    }

    @ParameterizedTest
    @CsvSource({"60,50000,0,0.00", "50,50000,0,1268.00", "50,0,50000,0.00",
            "50,30000,20000,468.00", "75,30000,20000,0.00"})
    void earlyCashAndStatutoryConversionAreDifferent(int age,String ira,String conversion,String expected) throws Exception {
        check(expected,request(state(),2026,false,age,age,OklahomaProductionTaxCalculatorTest.income(ira,conversion,"0")));
    }

    @ParameterizedTest
    @CsvSource({"50000,50000,60,60,2536.00", "90000,10000,60,60,2536.00",
            "100000,0,60,60,2936.00", "50000,50000,65,60,2476.00", "50000,50000,65,65,2416.00"})
    void combinedColumnsUseActualOwners(String a,String c,int ageA,int ageB,String expected) throws Exception {
        var income=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b(a).add(b(c)),List.of(investment(AccountOwnership.PRIMARY,a),investment(AccountOwnership.SPOUSE,c)));
        check(expected,request(state(),2026,true,ageA,ageB,income));
    }

    @Test
    void unknownAndJointOwnershipUseDisclosedJointMethodNotGeorgiaAllocation() throws Exception {
        var unowned=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("100000"));
        var r=request(state(),2026,true,65,65,unowned);
        check("2816.00",r);
        var joint=unowned.withIncomeSources(List.of(investment(AccountOwnership.JOINT,"100000")));
        check("2816.00",request(state(),2026,true,65,65,joint));
        assertTrue(new MississippiProductionTaxCalculator().calculate(r).limitations().stream().anyMatch(s->s.contains("one-column")));
    }

    @Test
    void crossingCashIsUnavailableButCompletedConversionIsExempt() throws Exception {
        var cash=OklahomaProductionTaxCalculatorTest.income("50000","0","0");
        var r=request(state(),2026,false,59,59,cash);
        var h=new TaxHouseholdContext(LocalDate.of(2026,1,1),Map.of(AccountOwnership.PRIMARY,LocalDate.of(1967,3,1)),
                Set.of(AccountOwnership.PRIMARY),Set.of(AccountOwnership.PRIMARY),Optional.empty());
        var result=new MississippiProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026,r.filingStatus(),h,cash,r.federal(),r.projectedRules(),r.assumptions()));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result.calculationStatus());
        assertTrue(String.join(" ",result.limitations()).contains("59"));
        check("0.00",request(state(),2026,false,59,59,OklahomaProductionTaxCalculatorTest.income("0","50000","0")));
    }

    @Test
    void qualifiedPensionSocialAndFiveYearTotal() throws Exception {
        var pension=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.PENSION,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY),Optional.empty(),Optional.empty(),"Ordinary private pension",b("50000"),Optional.of(b("50000")),OptionalInt.of(2026),Optional.empty());
        check("1268.00",request(state(),2026,false,60,60,new TaxIncome(b("50000"),b("0"),b("0"),b("0"),b("50000"),List.of(pension))));
        check("0.00",request(state(),2026,false,60,60,new TaxIncome(b("0"),b("30000"),b("50000"),b("0"),b("0"),List.of(retirement(AccountOwnership.PRIMARY,"50000",2026)))));
        BigDecimal total=BigDecimal.ZERO;
        for(int year=2026;year<=2030;year++) total=total.add(new MississippiProductionTaxCalculator().calculate(
                request(state(),year,false,60,60,new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("50000")))).incomeTax());
        assertEquals(b("5547.50"),total);
    }

    public static IncomeSourceAttribution investment(AccountOwnership owner,String amount) {
        return new IncomeSourceAttribution(IncomeSourceAttribution.Kind.CAPITAL_GAIN,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(owner),Optional.empty(),Optional.empty(),"Already modeled ordinary investment",b(amount),Optional.of(b(amount)),OptionalInt.of(2026),Optional.empty());
    }
    public static StateTaxJurisdiction state() { return StateTaxJurisdiction.valueOf("MS"); }
    public static void check(String expected,StateTaxCalculationRequest r) {
        var result=new MississippiProductionTaxCalculator().calculate(r);
        assertEquals(StateTaxCalculationStatus.CALCULATED,result.calculationStatus(),()->result.limitations().toString());
        assertEquals(b(expected),result.incomeTax());
    }
}
