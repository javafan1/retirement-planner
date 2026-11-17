package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent Form760/current-statute fixtures, including shared joint phaseout. */
class VirginiaProductionTaxCalculatorTest {
    @ParameterizedTest
    @CsvSource({"2026,60,50000,0,0,0,2060.90", "2027,60,50000,0,0,0,2035.03",
            "2028,60,50000,0,0,0,2029.28", "2029,60,50000,0,0,0,2029.28", "2030,60,50000,0,0,0,2391.53",
            "2026,65,50000,0,0,0,1324.90", "2026,65,50000,20000,0,0,3164.90",
            "2026,65,50000,0,30000,0,1324.90", "2026,60,50000,0,0,5000,2348.40",
            "2026,60,0,0,0,0,0.00", "2026,60,11949.99,0,0,0,0.00", "2026,60,11950,0,0,0,45.40",
            "2026,65,23949.99,0,0,0,0.00", "2026,65,23950,0,0,0,29.40"})
    void singleWorksheet(int year,int age,String ira,String conversion,String ss,String investment,String expected) throws Exception {
        assertTax(expected, request(StateTaxJurisdiction.VA,year,false,age,age,
                new TaxIncome(b("0"),b(ss),b(ira),b(conversion),b(investment))));
    }
    @ParameterizedTest
    @CsvSource({"65,65,80000,2044.80", "65,60,80000,2780.80", "65,65,99000,4229.80",
            "60,60,100000,4379.30", "60,60,23899.99,0.00", "60,60,23900,106.20"})
    void jointWorksheet(int a,int c,String amount,String expected) throws Exception {
        assertTax(expected,request(StateTaxJurisdiction.VA,2026,true,a,c,new TaxIncome(b("0"),b("0"),b(amount),b("0"),b("0"))));
    }
    @Test
    void grandfatherAndMovingBirthdayCutoff() throws Exception {
        for (var example : List.of(new String[]{"1939-01-01","4199.90"},new String[]{"1939-01-02","4889.90"},
                new String[]{"1962-01-01","4889.90"},new String[]{"1962-01-02","4935.90"})) {
            var r = request(StateTaxJurisdiction.VA,2026,false,65,65,new TaxIncome(b("0"),b("0"),b("100000"),b("0"),b("0")));
            var h = new TaxHouseholdContext(r.household().distributionDate(),Map.of(AccountOwnership.PRIMARY,LocalDate.parse(example[0])),
                    r.household().householdOwners(),r.household().activeOwners(),Optional.empty());
            assertTax(example[1],new StateTaxCalculationRequest(2026,r.filingStatus(),h,r.income(),r.federal(),r.projectedRules(),r.assumptions()));
        }
    }
    @ParameterizedTest
    @CsvSource({"0,0.00", "0.25,0.01", "3000,60.00", "3000.01,60.00", "5000,120.00", "17000,720.00", "17000.01,720.00"})
    void graduatedRateUnits(String base,String expected) {
        assertEquals(b(expected),new com.daviddunn.retirementplanner.domain.tax.state.virginia.VirginiaTaxRules().tax(b(base)).setScale(2,java.math.RoundingMode.HALF_UP));
    }
    @ParameterizedTest
    @CsvSource({"false,65,60,50000,12000", "false,65,60,50001,11999", "false,65,60,62000,0",
            "true,65,60,75000,12000", "true,65,60,75001,11999", "true,65,60,87000,0",
            "true,65,65,75000,24000", "true,65,65,80000,19000", "true,65,65,99000,0"})
    void independentAgeWorksheetBoundaries(boolean joint,int a,int c,String afagi,String expected) throws Exception {
        var r=request(StateTaxJurisdiction.VA,2026,joint,a,c,new TaxIncome(b("0"),b("0"),b(afagi),b("0"),b("0")));
        var age=new com.daviddunn.retirementplanner.domain.tax.state.virginia.VirginiaAgeDeduction()
                .calculate(2026,r.filingStatus(),r.household(),r.household().householdOwners(),b(afagi));
        assertEquals(0,b(expected).compareTo(age.deduction()));
    }
    @Test
    void mixedGrandfatheredSpouseAndDeathBeforeBirthday() throws Exception {
        var income=new TaxIncome(b("0"),b("0"),b("100000"),b("0"),b("0"));
        var r=request(StateTaxJurisdiction.VA,2026,true,65,65,income);
        var h=new TaxHouseholdContext(r.household().distributionDate(),Map.of(AccountOwnership.PRIMARY,LocalDate.of(1939,1,1),
                AccountOwnership.SPOUSE,LocalDate.of(1961,6,1)),r.household().householdOwners(),r.household().activeOwners(),Optional.empty());
        assertTax("3597.30",new StateTaxCalculationRequest(2026,r.filingStatus(),h,income,r.federal(),r.projectedRules(),r.assumptions()));
        var death=request(StateTaxJurisdiction.VA,2026,true,65,65,new TaxIncome(b("0"),b("0"),b("80000"),b("0"),b("0")));
        assertTax("2780.80",new StateTaxCalculationRequest(2026,death.filingStatus(),death.household().withActiveOwners(Set.of(AccountOwnership.SPOUSE)),
                death.income(),death.federal(),death.projectedRules(),death.assumptions()));
    }
}
