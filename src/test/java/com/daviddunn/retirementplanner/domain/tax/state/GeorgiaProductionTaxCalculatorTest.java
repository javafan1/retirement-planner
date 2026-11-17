package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent IT-511/HB463 worksheets: Phase 6E-I §13, not calculator-generated expectations. */
public class GeorgiaProductionTaxCalculatorTest {
    @ParameterizedTest
    @CsvSource({"2026,61,50000,0,0,1746.50", "2026,62,50000,0,0,0.00",
            "2026,64,50000,0,0,0.00", "2026,65,50000,0,0,0.00",
            "2026,62,50000,20000,0,998.00", "2026,65,100000,0,0,998.00",
            "2027,65,100000,0,0,748.50", "2040,65,100000,0,0,748.50",
            "2026,65,85000,0,0,249.50", "2026,62,50000,0,5000,249.50",
            "2026,65,0,0,50000,0.00", "2026,62,50000.11,0,0,0.01",
            "2026,65,80000,0,0,0.00", "2027,65,85000,0,0,0.00"})
    void singleWorksheet(int year, int age, String ira, String conversion, String investment, String expected) throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b(ira), b(conversion), b(investment),
                List.of(retirement(AccountOwnership.PRIMARY, ira, year), conversion(AccountOwnership.PRIMARY, conversion, year)));
        assertTax(expected, request(StateTaxJurisdiction.GA, year, false, age, age, income));
    }

    @ParameterizedTest
    @CsvSource({"50000,50000,0.00", "100000,0,1746.50", "0,100000,1746.50"})
    void jointIndividualCaps(String a, String c, String expected) throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b(a).add(b(c)), b("0"), b("0"),
                List.of(retirement(AccountOwnership.PRIMARY, a, 2026), retirement(AccountOwnership.SPOUSE, c, 2026)));
        assertTax(expected, request(StateTaxJurisdiction.GA, 2026, true, 62, 62, income));
    }

    @Test
    void pensionIraAndConversionShareOwnersPoolAndSsIsSeparate() throws Exception {
        var pension = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.PENSION, IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY), Optional.empty(), Optional.empty(), "Ordinary private pension",
                b("30000"), Optional.of(b("30000")), OptionalInt.of(2026), Optional.empty());
        var own = new TaxIncome(b("30000"), b("0"), b("20000"), b("20000"), b("0"),
                List.of(pension, retirement(AccountOwnership.PRIMARY, "20000", 2026), conversion(AccountOwnership.PRIMARY, "20000", 2026)));
        assertTax("998.00", request(StateTaxJurisdiction.GA, 2026, false, 62, 62, own));
        assertTax("249.50", request(StateTaxJurisdiction.GA, 2026, true, 62, 62, own));
        assertTax("0.00", request(StateTaxJurisdiction.GA, 2026, true, 62, 62, own.withIncomeSources(
                List.of(pension, retirement(AccountOwnership.PRIMARY, "20000", 2026), conversion(AccountOwnership.SPOUSE, "20000", 2026)))));
        var ss = new TaxIncome(b("0"), b("30000"), b("50000"), b("20000"), b("0"),
                List.of(retirement(AccountOwnership.PRIMARY, "50000", 2026), conversion(AccountOwnership.PRIMARY, "20000", 2026)));
        var r = request(StateTaxJurisdiction.GA, 2026, false, 62, 62, ss);
        assertEquals(0, b("25500").compareTo(r.federal().getTaxableSocialSecurity()));
        assertTax("998.00", r);
    }

    @Test
    void ambiguousJanuaryFirstThresholdDoesNotGuessAndDeathDoesNotAgeFiler() throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"), List.of(retirement(AccountOwnership.PRIMARY, "50000", 2026)));
        var r = request(StateTaxJurisdiction.GA, 2026, false, 61, 61, income);
        var h = new TaxHouseholdContext(LocalDate.of(2026,1,1), Map.of(AccountOwnership.PRIMARY, LocalDate.of(1965,1,1)),
                Set.of(AccountOwnership.PRIMARY), Set.of(AccountOwnership.PRIMARY), Optional.empty());
        var result = new GeorgiaProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026, r.filingStatus(), h, income, r.federal(), r.projectedRules(), r.assumptions()));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertTrue(String.join(" ", result.limitations()).contains("January 1"));
    }

    public static IncomeSourceAttribution retirement(AccountOwnership owner, String value, int year) {
        return KentuckyProductionTaxCalculatorTest.retirement(owner, value, year);
    }
    public static IncomeSourceAttribution conversion(AccountOwnership owner, String value, int year) {
        return KentuckyProductionTaxCalculatorTest.conversion(owner, value, year);
    }
    public static StateTaxCalculationRequest request(StateTaxJurisdiction state, int year, boolean joint, int ageA, int ageB, TaxIncome income) throws Exception {
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var status = joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        var births = new EnumMap<AccountOwnership, LocalDate>(AccountOwnership.class);
        births.put(AccountOwnership.PRIMARY, LocalDate.of(year-ageA,6,1));
        if (joint) births.put(AccountOwnership.SPOUSE, LocalDate.of(year-ageB,6,1));
        var h = new TaxHouseholdContext(LocalDate.of(year,1,1), births, births.keySet(), births.keySet(), Optional.empty());
        return new StateTaxCalculationRequest(year, status, h, income, new FederalTaxCalculator().calculate(income,status,rules), rules, StateTaxAssumptions.simplified(state));
    }
    public static void assertTax(String expected, StateTaxCalculationRequest r) {
        var result = r.assumptions().jurisdiction() == StateTaxJurisdiction.GA
                ? new GeorgiaProductionTaxCalculator().calculate(r) : new VirginiaProductionTaxCalculator().calculate(r);
        assertEquals(StateTaxCalculationStatus.CALCULATED, result.calculationStatus(), () -> result.limitations().toString());
        assertEquals(b(expected), result.incomeTax());
    }
    public static BigDecimal b(String v) { return new BigDecimal(v); }
}
