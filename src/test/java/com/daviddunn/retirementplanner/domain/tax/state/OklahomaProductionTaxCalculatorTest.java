package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaTaxRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent Phase 6E-K OK-01..28 statutory-rate worksheets, not return-table goldens. */
public class OklahomaProductionTaxCalculatorTest {
    @ParameterizedTest
    @CsvSource({"false,0,0.00", "false,3750,0.00", "false,3751,0.03",
            "false,4900,28.75", "false,4901,28.79", "false,7200,109.25", "false,7201,109.30",
            "true,7500,0.00", "true,7501,0.03", "true,9800,57.50", "true,9801,57.54",
            "true,14400,218.50", "true,14401,218.55"})
    void marginalBoundaries(boolean joint, String taxable, String expected) {
        var status = joint ? com.daviddunn.retirementplanner.domain.rules.FilingStatus.MARRIED_FILING_JOINTLY
                : com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE;
        assertEquals(b(expected), new OklahomaTaxRules().tax(2026, status, b(taxable)));
    }

    @ParameterizedTest
    @CsvSource({"60,0,0,11100,0.00", "60,0,0,11101,0.03", "60,50000,0,0,1254.50",
            "60,9000,0,20000,354.50", "60,10000,0,20000,354.50", "60,15000,0,20000,579.50",
            "60,50000,20000,0,2154.50", "60,30000,20000,0,1254.50",
            "65,0,0,14000,55.00", "64,0,0,14000,90.00", "65,0,0,15000,90.00",
            "65,0,0,15000.01,129.50", "65,0,20000,14000,489.50", "60,0,0,50000,1704.50"})
    void ordinaryAndSeniorWorksheets(int age, String ira, String conversion, String investment, String expected) throws Exception {
        check(expected, request(state(),2026,false,age,age,income(ira,conversion,investment)));
    }

    @ParameterizedTest
    @CsvSource({"50000,50000,2509.00", "100000,0,2959.00", "0,100000,2959.00"})
    void individualCapsNeverTransfer(String a, String c, String expected) throws Exception {
        var income = new TaxIncome(b("0"),b("0"),b(a).add(b(c)),b("0"),b("0"),
                List.of(retirement(AccountOwnership.PRIMARY,a,2026),retirement(AccountOwnership.SPOUSE,c,2026)));
        check(expected, request(state(),2026,true,60,75,income));
    }

    @Test
    void socialPensionRmdAndFutureHold() throws Exception {
        var ss = new TaxIncome(b("0"),b("30000"),b("50000"),b("0"),b("0"),List.of(retirement(AccountOwnership.PRIMARY,"50000",2026)));
        check("1254.50", request(state(),2026,false,60,60,ss));
        var pension = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.PENSION,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY),Optional.empty(),Optional.empty(),"CSRS is only a display name",
                b("30000"),Optional.of(b("30000")),OptionalInt.of(2026),Optional.empty());
        check("1254.50",request(state(),2026,false,60,60,new TaxIncome(b("30000"),b("0"),b("20000"),b("0"),b("0"),
                List.of(pension,retirement(AccountOwnership.PRIMARY,"20000",2026)))));
        BigDecimal total=BigDecimal.ZERO;
        for(int year=2026;year<=2030;year++) {
            var r=request(state(),year,false,60,60,new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"),List.of(retirement(AccountOwnership.PRIMARY,"50000",year))));
            total=total.add(new OklahomaProductionTaxCalculator().calculate(r).incomeTax());
        }
        assertEquals(b("6272.50"),total);
    }

    @Test
    void januaryFirstAndDeceasedAgeNeverAdvance() throws Exception {
        var r=request(state(),2026,false,64,64,income("0","0","14000"));
        for(var entry:Map.of(LocalDate.of(1962,1,1),"55.00",LocalDate.of(1962,1,2),"90.00").entrySet()) {
            var h=new TaxHouseholdContext(r.household().distributionDate(),Map.of(AccountOwnership.PRIMARY,entry.getKey()),
                    Set.of(AccountOwnership.PRIMARY),Set.of(AccountOwnership.PRIMARY),Optional.empty());
            check(entry.getValue(),new StateTaxCalculationRequest(2026,r.filingStatus(),h,r.income(),r.federal(),r.projectedRules(),r.assumptions()));
        }
    }

    public static StateTaxJurisdiction state() { return StateTaxJurisdiction.valueOf("OK"); }
    public static TaxIncome income(String ira,String conversion,String investment) {
        return new TaxIncome(b("0"),b("0"),b(ira),b(conversion),b(investment),List.of(
                retirement(AccountOwnership.PRIMARY,ira,2026),conversion(AccountOwnership.PRIMARY,conversion,2026)));
    }
    public static void check(String expected,StateTaxCalculationRequest r) {
        var result=new OklahomaProductionTaxCalculator().calculate(r);
        assertEquals(StateTaxCalculationStatus.CALCULATED,result.calculationStatus(),()->result.limitations().toString());
        assertEquals(b(expected),result.incomeTax());
    }
}
