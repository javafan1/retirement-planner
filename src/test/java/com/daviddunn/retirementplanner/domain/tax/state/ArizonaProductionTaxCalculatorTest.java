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

class ArizonaProductionTaxCalculatorTest {
    // Independent worksheets: ARS 43-1041(A,H) + Rev Proc 2025-32 .14;
    // ARS 43-1022(10,35), 43-1023(E); Schedule 1-A Part V.
    @ParameterizedTest
    @CsvSource({
            "SINGLE,1956-06-01,,2026,0,30000,0,0,0,0",
            "MARRIED_FILING_JOINTLY,1956-06-01,1958-06-01,2026,0,40000,60000,0,0,290.00",
            "SINGLE,1966-06-01,,2026,0,0,0,20000,0,97.50",
            "SINGLE,1966-06-01,,2026,0,0,50000,20000,0,1347.50",
            "SINGLE,1956-06-01,,2026,45000,25000,0,0,0,520.00",
            "SINGLE,1966-06-01,,2026,0,0,50000,0,5000,972.50",
            "SINGLE,1956-06-01,,2026,0,0,0,0,0,0",
            "SINGLE,1956-06-01,,2026,0,0,0,40000,0,395.00",
            "SINGLE,1956-06-01,,2028,0,0,60000,0,0,895.00",
            "SINGLE,1956-06-01,,2029,0,0,60000,0,0,1045.00",
            "MARRIED_FILING_JOINTLY,1956-06-01,1962-06-01,2026,0,0,60000,0,0,492.50",
            "SINGLE,1962-01-01,,2026,0,0,60000,0,0,895.00",
            "SINGLE,1962-01-02,,2026,0,0,60000,0,0,1097.50",
            "SINGLE,1956-06-01,,2026,0,0,75000,1,0,1270.03",
            "MARRIED_FILING_JOINTLY,1956-06-01,1958-06-01,2026,0,0,150000,1,0,2540.03",
            "SINGLE,1956-06-01,,2026,0,40000,70000,0,0,1188.50",
            "SINGLE,1956-06-01,,2026,0,40000,70000,20000,0,1718.50",
            "MARRIED_FILING_JOINTLY,1956-06-01,1958-06-01,2026,0,40000,140000,0,0,2362.00",
            "MARRIED_FILING_JOINTLY,1956-06-01,1958-06-01,2026,0,40000,140000,20000,0,2922.00"
    })
    void independentGoldenWorksheets(FilingStatus status, String primary, String spouse, int year,
            String pension, String ss, String ira, String conversion, String interest, String tax) throws Exception {
        var income = new TaxIncome(b(pension), b(ss), b(ira), b(conversion), b(interest));
        var result = calculate(year, status, primary, spouse, income, null);
        assertEquals(StateTaxCalculationStatus.CALCULATED, result.calculationStatus());
        assertEquals(0, b(tax).compareTo(result.incomeTax()));
        assertTrue(result.limitations().getFirst().contains("government and military"));
    }

    @Test
    void deathYearDoesNotGrantLaterBirthdayAndSingleUsesSurvivor() throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b("60000"), b("0"), b("0"));
        assertEquals(b("492.50"), calculate(2026, FilingStatus.MARRIED_FILING_JOINTLY,
                "1961-06-01", "1956-06-01", income, Set.of(AccountOwnership.SPOUSE)).incomeTax());
        assertEquals(b("895.00"), calculate(2027, FilingStatus.SINGLE,
                "1961-06-01", "1956-06-01", income, Set.of(AccountOwnership.SPOUSE)).incomeTax());
    }

    @Test
    void missingBrokerageBasisOrGainFactsDoNotInventIncomeOrPreventThe2027Estimate() throws Exception {
        for (var kind : List.of(IncomeSourceAttribution.Kind.CAPITAL_GAIN, IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL)) {
            var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                    "Unclassified", b("50000"), Optional.empty()).inYear(2026);
            var income = new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(source));
            var result = calculate(2026, FilingStatus.SINGLE, "1956-06-01", null, income, null);
            assertEquals(StateTaxCalculationStatus.CALCULATED, result.calculationStatus());
            assertEquals(b("0.00"), result.incomeTax());
        }
        var brokerage = new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Ordinary proceeds", AccountOwnership.PRIMARY, b("500000"));
        var proceeds = IncomeSourceAttribution.withdrawal(brokerage, b("50000"),
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2027);
        assertTrue(proceeds.modeledTaxableAmount().isEmpty());
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("20000"), b("0"), List.of(proceeds));
        var result = calculate(2027, FilingStatus.SINGLE, "1966-06-01", null, income, null);
        assertEquals(b("1347.50"), result.incomeTax());
        assertEquals(b("500000"), brokerage.getCurrentBalance());
    }

    @ParameterizedTest
    @CsvSource({"CAPITAL_GAIN,5000,5000", "CAPITAL_GAIN,50000,5000", "ORDINARY_WITHDRAWAL,50000,5000"})
    void modeledTaxableInvestmentAmountIsIncludedOnceWithoutAnUnverifiedArizonaSubtraction(
            IncomeSourceAttribution.Kind kind, String gross, String taxable) throws Exception {
        var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                "Already modeled income", b(gross), Optional.of(b(taxable))).inYear(2027);
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("5000"), List.of(source));
        var result = calculate(2027, FilingStatus.SINGLE, "1966-06-01", null, income, null);
        // 50000 IRA + 5000 modeled taxable investment income - 16100 basic deduction.
        // Proceeds are not added, and no unverified 25% state gain subtraction is used.
        assertEquals(b("972.50"), result.incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,0.00", "50000,0,0,847.50", "0,30000,0,0.00", "0,0,20000,97.50"})
    void ordinaryProceedsCanAccompanyEachRetirementIncomeSourceIndependently(
            String ira, String socialSecurity, String conversion, String expected) throws Exception {
        var proceeds = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW, "Unknown basis, known proceeds", b("100000"), Optional.empty())
                .inYear(2027);
        var income = new TaxIncome(b("0"), b(socialSecurity), b(ira), b(conversion), b("0"), List.of(proceeds));
        assertEquals(b(expected), calculate(2027, FilingStatus.SINGLE, "1966-06-01", null, income, null).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"CAPITAL_GAIN", "ORDINARY_WITHDRAWAL"})
    void explicitTaxableAttributionMustReconcileWithModeledIncome(IncomeSourceAttribution.Kind kind) throws Exception {
        var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                "Contradiction", b("50000"), Optional.of(b("5000"))).inYear(2027);
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"), List.of(source));
        var result = calculate(2027, FilingStatus.SINGLE, "1966-06-01", null, income, null);
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertTrue(result.estimatedIncomeTax().isEmpty());
        assertTrue(result.unavailableReasons().contains(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE));
    }

    @Test
    void specializedExemptIncomeAndUnclassifiedIncomeRemainUnsupported() throws Exception {
        for (var kind : List.of(IncomeSourceAttribution.Kind.INTEREST, IncomeSourceAttribution.Kind.OTHER)) {
            var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                    "Unsupported source", b("5000"), Optional.of(b("0"))).inYear(2027);
            var income = new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(source));
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,
                    calculate(2027, FilingStatus.SINGLE, "1966-06-01", null, income, null).calculationStatus());
        }
    }

    @Test
    void ordinaryDividendMustAlreadyBeIncludedAndCannotBeAddedAgain() throws Exception {
        var dividend = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.DIVIDEND,
                IncomeSourceAttribution.Purpose.INCOME, "Dividend", b("5000"), Optional.of(b("5000"))).inYear(2026);
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("5000"), List.of(dividend));
        assertEquals(b("972.50"), calculate(2026, FilingStatus.SINGLE, "1966-06-01", null, income, null).incomeTax());
        var missing = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"), List.of(dividend));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,
                calculate(2026, FilingStatus.SINGLE, "1966-06-01", null, missing, null).calculationStatus());
    }

    @Test
    void doesNotClassifyPensionByNameOrRecalculateFederalTax() throws Exception {
        var source = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.PENSION,
                IncomeSourceAttribution.Purpose.INCOME, "Military sounding name is not evidence", b("45000"), Optional.of(b("45000")));
        var income = new TaxIncome(b("45000"), b("0"), b("0"), b("0"), b("0"), List.of(source));
        assertEquals(b("520.00"), calculate(2026, FilingStatus.SINGLE, "1956-06-01", null, income, null).incomeTax());
    }

    @Test
    void rejectsHistoricalYearsAndUnsupportedFilingStatus() throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,
                calculate(2025, FilingStatus.SINGLE, "1956-06-01", null, income, null).calculationStatus());
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,
                calculate(2026, FilingStatus.HEAD_OF_HOUSEHOLD, "1956-06-01", null, income, null).calculationStatus());
    }

    @Test
    void qualifiedRothAndCashPrincipalNeverBecomeTaxableIncome() throws Exception {
        var roth = new com.daviddunn.retirementplanner.domain.financial.RothIRA(
                "Qualified", AccountOwnership.PRIMARY, b("50000"));
        var source = IncomeSourceAttribution.withdrawal(roth, b("40000"),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var income = new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(source));
        assertEquals(b("0.00"), calculate(2026, FilingStatus.SINGLE, "1956-06-01", null, income, null).incomeTax());
        var cash = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW, "Known principal", b("50000"), Optional.of(b("0")));
        assertEquals(b("0.00"), calculate(2026, FilingStatus.SINGLE, "1956-06-01", null,
                income.withIncomeSources(List.of(cash)), null).incomeTax());
    }

    @Test
    void missingBirthDateAndInconsistentFederalBaseAreUnavailable() throws Exception {
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var income = new TaxIncome(b("0"), b("0"), b("60000"), b("0"), b("0"));
        var context = new TaxHouseholdContext(LocalDate.of(2026, 1, 1), Map.of(),
                Set.of(AccountOwnership.PRIMARY), Set.of(AccountOwnership.PRIMARY), Optional.empty());
        var federal = new FederalTaxCalculator().calculate(income, FilingStatus.SINGLE, rules);
        var request = new StateTaxCalculationRequest(2026, FilingStatus.SINGLE, context, income,
                federal, rules, StateTaxAssumptions.simplified(StateTaxJurisdiction.AZ));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,
                new ArizonaProductionTaxCalculator().calculate(request).calculationStatus());
        var wrong = new FederalTaxCalculation(b("90000"), b("0"), b("16100"), b("73900"), b("10000"));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, new ArizonaProductionTaxCalculator().calculate(
                new StateTaxCalculationRequest(2026, FilingStatus.SINGLE, context, income, wrong, rules,
                        request.assumptions())).calculationStatus());
    }

    @Test
    void statutoryFloorAndPlanningCentRoundingAreExplicit() throws Exception {
        var below = new TaxIncome(b("0"), b("0"), b("16099.99"), b("0"), b("0"));
        assertEquals(b("0.00"), calculate(2026, FilingStatus.SINGLE, "1966-06-01", null, below, null).incomeTax());
        var above = new TaxIncome(b("0"), b("0"), b("16100.20"), b("0"), b("0"));
        var result = calculate(2026, FilingStatus.SINGLE, "1966-06-01", null, above, null);
        assertEquals(b("0.01"), result.incomeTax());
        assertTrue(result.limitations().stream().anyMatch(note -> note.contains("whole-dollar rounding")));
    }

    private ProductionStateTaxResult calculate(int year, FilingStatus status, String primary, String spouse,
            TaxIncome income, Set<AccountOwnership> active) throws Exception {
        var household = new Household(new Person("Primary", "Example", LocalDate.parse(primary)),
                spouse == null ? null : new Person("Spouse", "Example", LocalDate.parse(spouse)));
        var context = TaxHouseholdContext.capture(household, LocalDate.of(year, 1, 1), null);
        if (active != null) context = context.withActiveOwners(active);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var federal = new FederalTaxCalculator().calculate(income, status, rules);
        var before = federal.getFederalIncomeTax();
        var result = new ProductionStateTaxService().calculate(new StateTaxCalculationRequest(year, status,
                context, income, federal, rules, StateTaxAssumptions.simplified(StateTaxJurisdiction.AZ)));
        assertEquals(before, federal.getFederalIncomeTax());
        return result;
    }

    private static BigDecimal b(String value) { return new BigDecimal(value); }
}
