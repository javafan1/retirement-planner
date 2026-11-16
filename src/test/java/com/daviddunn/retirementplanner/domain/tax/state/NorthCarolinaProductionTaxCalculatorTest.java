package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.northcarolina.NorthCarolinaTaxRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class NorthCarolinaProductionTaxCalculatorTest {
    // Independent NC-40 worksheet: AGI - taxable SS - state deduction, times
    // S.L. 2026-41 section 44.1 rate. Final planning cents, not return rounding.
    @ParameterizedTest
    @CsvSource({
            "SINGLE,2026,0,30000,0,0,0,0.00",
            "SINGLE,2026,0,0,12749,0,0,0.00",
            "SINGLE,2026,0,0,12750,0,0,0.00",
            "SINGLE,2026,0,0,12751,0,0,0.04",
            "MARRIED_FILING_JOINTLY,2026,0,0,25500,0,0,0.00",
            "MARRIED_FILING_JOINTLY,2026,0,0,25501,0,0,0.04",
            "SINGLE,2026,0,0,50000,0,0,1486.28",
            "SINGLE,2026,0,0,50000,20000,0,2284.28",
            "MARRIED_FILING_JOINTLY,2026,0,40000,60000,0,0,1376.55",
            "SINGLE,2026,45000,25000,0,0,0,1286.78",
            "SINGLE,2026,0,0,50000,0,5000,1685.78",
            "SINGLE,2026,0,0,30000,15000,0,1286.78",
            "SINGLE,2026,0,0,12750.20,0,0,0.01",
            "SINGLE,2026,0,0,60000,0,0,1885.28",
            "SINGLE,2028,0,0,60000,0,0,1649.03",
            "SINGLE,2029,0,0,60000,0,0,1649.03",
            "MARRIED_FILING_JOINTLY,2026,100000,0,0,0,0,2972.55",
            "MARRIED_FILING_JOINTLY,2027,100000,0,0,0,0,2600.05",
            "MARRIED_FILING_JOINTLY,2029,100000,0,0,0,0,2600.05",
            "MARRIED_FILING_JOINTLY,2030,100000,0,0,0,0,2413.80",
            "MARRIED_FILING_JOINTLY,2032,100000,0,0,0,0,2413.80",
            "MARRIED_FILING_JOINTLY,2033,100000,0,0,0,0,2227.55",
            "MARRIED_FILING_JOINTLY,2035,100000,0,0,0,0,2227.55",
            "MARRIED_FILING_JOINTLY,2040,100000,0,0,0,0,2227.55",
            "MARRIED_FILING_JOINTLY,2041,100000,0,0,0,0,2227.55"
    })
    void independentWorksheets(FilingStatus status, int year, String pension, String ss,
            String ira, String conversion, String investment, String expected) throws Exception {
        var income = new TaxIncome(b(pension), b(ss), b(ira), b(conversion), b(investment));
        var result = calculate(year, status, income);
        assertEquals(StateTaxCalculationStatus.CALCULATED, result.calculationStatus());
        assertEquals(b(expected), result.incomeTax());
        assertEquals(6, result.limitations().size());
        assertTrue(result.limitations().stream().anyMatch(s -> s.contains(NorthCarolinaTaxRules.VERSION)));
    }

    @ParameterizedTest
    @CsvSource({"0,0.00", "50000,1486.28"})
    void unknownBrokerageBasisIsAdmittedWithoutInventingIncome(String ira, String expected) throws Exception {
        var brokerage = new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Brokerage", AccountOwnership.PRIMARY, b("200000"));
        var proceeds = IncomeSourceAttribution.withdrawal(brokerage, b("100000"),
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        assertTrue(proceeds.modeledTaxableAmount().isEmpty());
        var result = calculate(2026, FilingStatus.SINGLE,
                new TaxIncome(b("0"), b("0"), b(ira), b("0"), b("0"), List.of(proceeds)));
        assertEquals(b(expected), result.incomeTax());
        assertTrue(result.limitations().get(1).contains("Unmodeled realized gains"));
        assertEquals(b("200000"), brokerage.getCurrentBalance());
    }

    @ParameterizedTest
    @CsvSource({"CAPITAL_GAIN", "ORDINARY_WITHDRAWAL", "DIVIDEND", "INTEREST"})
    void knownInvestmentIncomeIsCheckedAndNeverAddedTwice(IncomeSourceAttribution.Kind kind) throws Exception {
        var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                "Modeled investment", b("5000"), Optional.of(b("5000"))).inYear(2026);
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("5000"), List.of(source));
        assertEquals(b("1685.78"), calculate(2026, FilingStatus.SINGLE, income).incomeTax());
        assertUnsupported(new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"), List.of(source)));
        assertUnsupported(income.withIncomeSources(List.of(source, source)));
    }

    @Test
    void qualifiedRothIsCashFlowAndPensionNamesDoNotProveSpecialTreatment() throws Exception {
        var roth = new com.daviddunn.retirementplanner.domain.financial.RothIRA("Qualified", AccountOwnership.PRIMARY, b("50000"));
        var source = IncomeSourceAttribution.withdrawal(roth, b("40000"),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        assertEquals(b("0.00"), calculate(2026, FilingStatus.SINGLE,
                new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(source))).incomeTax());
        var pension = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.PENSION,
                IncomeSourceAttribution.Purpose.INCOME, "Bailey military railroad name is not classification",
                b("45000"), Optional.of(b("45000")));
        assertEquals(b("1286.78"), calculate(2026, FilingStatus.SINGLE,
                new TaxIncome(b("45000"), b("0"), b("0"), b("0"), b("0"), List.of(pension))).incomeTax());
    }

    @Test
    void representedUnsupportedSourcesAndUnknownRetirementTaxabilityRemainUnavailable() throws Exception {
        for (var kind : List.of(IncomeSourceAttribution.Kind.OTHER, IncomeSourceAttribution.Kind.INTEREST,
                IncomeSourceAttribution.Kind.DIVIDEND, IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL)) {
            var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                    "Explicit special/unknown source", b("5000"), Optional.empty()).inYear(2026);
            assertUnsupported(new TaxIncome(b("0"), b("0"), b("5000"), b("0"), b("0"), List.of(source)));
        }
        var special = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.PENSION,
                IncomeSourceAttribution.Purpose.INCOME, "Represented partial taxability", b("5000"), Optional.of(b("1000")));
        assertUnsupported(new TaxIncome(b("5000"), b("0"), b("0"), b("0"), b("0"), List.of(special)));
        var wrongYear = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.CAPITAL_GAIN,
                IncomeSourceAttribution.Purpose.INCOME, "Wrong year", b("5000"), Optional.empty()).inYear(2027);
        assertUnsupported(new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(wrongYear)));
    }

    @Test
    void explicitPartialTraditionalBasisTaxableRothAndExcessSsAreNotSilentlyIgnored() throws Exception {
        var partial = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.INCOME, "Explicit partial traditional taxability", b("5000"), Optional.of(b("1000")));
        assertUnsupported(new TaxIncome(b("0"), b("0"), b("5000"), b("0"), b("0"), List.of(partial)));
        var roth = new com.daviddunn.retirementplanner.domain.financial.RothIRA("Nonqualified", AccountOwnership.PRIMARY, b("5000"));
        var taxableRoth = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW, Optional.of(AccountOwnership.PRIMARY),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(roth)), Optional.empty(),
                "Explicit taxable Roth", b("5000"), Optional.of(b("1000")), OptionalInt.of(2026), Optional.empty());
        assertUnsupported(new TaxIncome(b("0"), b("0"), b("1000"), b("0"), b("0"), List.of(taxableRoth)));
        var excessSs = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.SOCIAL_SECURITY,
                IncomeSourceAttribution.Purpose.INCOME, "Contradictory Social Security", b("40000"), Optional.empty());
        assertUnsupported(new TaxIncome(b("0"), b("30000"), b("0"), b("0"), b("0"), List.of(excessSs)));
    }

    @Test
    void historicalYearsStatusAndContradictoryFederalBaseAreUnavailable() throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, calculate(2025, FilingStatus.SINGLE, income).calculationStatus());
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, calculate(2026, FilingStatus.HEAD_OF_HOUSEHOLD, income).calculationStatus());
        var request = request(2026, FilingStatus.SINGLE, income);
        var wrongFederal = new FederalTaxCalculation(b("90000"), b("0"), b("16100"), b("73900"), b("10000"));
        var result = new NorthCarolinaProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026,
                request.filingStatus(), request.household(), income, wrongFederal, request.projectedRules(), request.assumptions()));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
    }

    private void assertUnsupported(TaxIncome income) throws Exception {
        var result = calculate(2026, FilingStatus.SINGLE, income);
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertTrue(result.estimatedIncomeTax().isEmpty());
        assertFalse(result.unavailableReasons().isEmpty());
    }

    static ProductionStateTaxResult calculate(int year, FilingStatus status, TaxIncome income) throws Exception {
        var request = request(year, status, income);
        var before = request.federal().getFederalIncomeTax();
        var result = new NorthCarolinaProductionTaxCalculator().calculate(request);
        assertEquals(before, request.federal().getFederalIncomeTax());
        return result;
    }

    static StateTaxCalculationRequest request(int year, FilingStatus status, TaxIncome income) throws Exception {
        var primary = new Person("Primary", "Example", LocalDate.of(1960, 6, 1));
        var spouse = status == FilingStatus.MARRIED_FILING_JOINTLY
                ? new Person("Spouse", "Example", LocalDate.of(1962, 6, 1)) : null;
        var context = TaxHouseholdContext.capture(new Household(primary, spouse), LocalDate.of(year, 1, 1), null);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var federal = new FederalTaxCalculator().calculate(income, status, rules);
        return new StateTaxCalculationRequest(year, status, context, income, federal, rules,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.NC));
    }

    private static BigDecimal b(String value) { return new BigDecimal(value); }
}
