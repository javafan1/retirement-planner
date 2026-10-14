package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Legal premises and official source trail: PHASE-6D4-ADDITIONAL-NO-INCOME-TAX-STATES.md. */
class NoIncomeTaxAdmissionTest {
    static final List<StateTaxJurisdiction> CANDIDATES = List.of(StateTaxJurisdiction.AK, StateTaxJurisdiction.NV,
            StateTaxJurisdiction.NH, StateTaxJurisdiction.SD, StateTaxJurisdiction.TN, StateTaxJurisdiction.TX, StateTaxJurisdiction.WY);
    static final BigDecimal AMOUNT = new BigDecimal("2000000");
    static final BigDecimal ZERO = BigDecimal.ZERO;

    static Stream<Arguments> categories() {
        return CANDIDATES.stream().flatMap(state -> Stream.of("Social Security", "IRA", "IRA RMD", "401k", "403b",
                "Pension", "Survivor pension", "Conversion", "Qualified Roth", "Nonqualified Roth", "Interest",
                "Ordinary dividends", "Qualified dividends", "Short gains", "Long gains", "Mixed")
                .map(category -> Arguments.of(state, category)));
    }

    static TaxIncome income(String category) {
        Account account = switch (category) {
            case "401k" -> new Traditional401K("Employer", AccountOwnership.PRIMARY, AMOUNT);
            case "403b" -> new Traditional403B("Employer", AccountOwnership.PRIMARY, AMOUNT);
            case "Qualified Roth", "Nonqualified Roth" -> new RothIRA("Roth", AccountOwnership.PRIMARY, AMOUNT);
            default -> new TraditionalIRA("Individual IRA", AccountOwnership.PRIMARY, AMOUNT);
        };
        var kind = switch (category) {
            case "Social Security" -> IncomeSourceAttribution.Kind.SOCIAL_SECURITY;
            case "Pension", "Survivor pension" -> IncomeSourceAttribution.Kind.PENSION;
            case "Conversion" -> IncomeSourceAttribution.Kind.ROTH_CONVERSION;
            case "IRA RMD" -> IncomeSourceAttribution.Kind.RMD;
            case "Interest", "Mixed" -> IncomeSourceAttribution.Kind.INTEREST;
            case "Ordinary dividends", "Qualified dividends" -> IncomeSourceAttribution.Kind.DIVIDEND;
            case "Short gains", "Long gains" -> IncomeSourceAttribution.Kind.CAPITAL_GAIN;
            default -> IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL;
        };
        var source = kind == IncomeSourceAttribution.Kind.PENSION
                ? IncomeSourceAttribution.pension(new Pension("Pension", AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1), null, AMOUNT, ZERO),
                        category.equals("Survivor pension") ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY, AMOUNT, 2026)
                : kind == IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL || kind == IncomeSourceAttribution.Kind.RMD
                ? IncomeSourceAttribution.withdrawal(account, AMOUNT, kind, IncomeSourceAttribution.Purpose.CASH_FLOW)
                : IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME, category, AMOUNT, Optional.of(AMOUNT));
        boolean investment = Set.of("Interest", "Ordinary dividends", "Qualified dividends", "Short gains", "Long gains", "Mixed").contains(category);
        return new TaxIncome(kind == IncomeSourceAttribution.Kind.PENSION || category.equals("Mixed") ? AMOUNT : ZERO,
                kind == IncomeSourceAttribution.Kind.SOCIAL_SECURITY || category.equals("Mixed") ? AMOUNT : ZERO,
                account.getType().getTaxTreatment() == TaxTreatment.TAX_DEFERRED && source.isRetirementDistribution()
                        && kind != IncomeSourceAttribution.Kind.ROTH_CONVERSION || category.equals("Mixed") ? AMOUNT : ZERO,
                kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION || category.equals("Mixed") ? AMOUNT : ZERO,
                investment ? AMOUNT : ZERO, List.of(source));
    }

    static StateTaxCalculationRequest request(StateTaxJurisdiction state, int year, TaxIncome income) throws Exception {
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var household = new Household(new Person("Primary", "", LocalDate.of(1960, 1, 1)),
                new Person("Spouse", "", LocalDate.of(1961, 1, 1)));
        return new StateTaxCalculationRequest(year, FilingStatus.MARRIED_FILING_JOINTLY,
                TaxHouseholdContext.capture(household, LocalDate.of(year, 1, 1), null), income,
                new FederalTaxCalculator().calculate(income, FilingStatus.MARRIED_FILING_JOINTLY, rules), rules,
                StateTaxAssumptions.simplified(state));
    }

    @ParameterizedTest(name = "{0}: {1} -> independently expected calculated zero")
    @MethodSource("categories")
    void eachAdmittedStateAndIncomeCategoryHasNoIndividualIncomeTax(StateTaxJurisdiction state, String category) throws Exception {
        var result = new ProductionStateTaxService().calculate(request(state, 2026, income(category)));
        assertEquals(state, result.jurisdiction());
        assertEquals(StateTaxCalculationStatus.CALCULATED, result.calculationStatus());
        assertEquals(StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE, result.calculationMethod());
        assertEquals(Optional.of(ZERO), result.estimatedIncomeTax());
        assertTrue(result.unavailableReasons().isEmpty());
        assertTrue(result.michiganDetails().isEmpty());
        assertTrue(result.limitations().getFirst().contains(state.displayName()));
        assertTrue(result.limitations().getFirst().contains("2026"));
        assertFalse(result.limitations().getFirst().contains("Florida"));
    }

    @Test void newHampshireRepealLeavesNo2026InterestOrDividendTax() throws Exception {
        // NH DRA: repeal for taxable periods beginning after December 31, 2024.
        for (var category : List.of("Interest", "Ordinary dividends", "Qualified dividends")) {
            assertEquals(ZERO, new ProductionStateTaxService().calculate(request(StateTaxJurisdiction.NH, 2026, income(category))).incomeTax());
        }
    }

    @Test void tennesseeHallRepealLeavesNo2026InterestOrDividendTax() throws Exception {
        // Tennessee DOR: no Hall return for periods beginning January 1, 2021 or later.
        for (var category : List.of("Interest", "Ordinary dividends")) {
            assertEquals(ZERO, new ProductionStateTaxService().calculate(request(StateTaxJurisdiction.TN, 2026, income(category))).incomeTax());
        }
    }

    @ParameterizedTest @EnumSource(value = StateTaxJurisdiction.class, names = {"AK", "NV", "NH", "SD", "TN", "TX", "WY"})
    void unvalidatedHistoricalYearsAreNotSilentlyZeroAndFuturePolicyIsExplicit(StateTaxJurisdiction state) throws Exception {
        var service = new ProductionStateTaxService();
        var historical = service.calculate(request(state, 2024, income("Interest")));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, historical.calculationStatus());
        assertTrue(historical.estimatedIncomeTax().isEmpty());
        assertEquals(List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), historical.unavailableReasons());
        var future = service.calculate(request(state, 2050, income("Mixed")));
        assertEquals(ZERO, future.incomeTax());
        assertTrue(future.limitations().getFirst().contains("2050"));
        assertTrue(future.limitations().getFirst().contains("without tax-law forecasting"));
    }

    @Test void pennsylvaniaUnknownPensionIsStillUnavailableAndUnadmittedCodesReject() throws Exception {
        var result = new ProductionStateTaxService().calculate(request(StateTaxJurisdiction.PA, 2026, income("Pension")));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertTrue(result.estimatedIncomeTax().isEmpty());
        assertTrue(result.unavailableReasons().contains(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION));
        for (var code : List.of("WA", "CA", "MS", "NY", "CO", "NJ", "UNKNOWN")) {
            assertThrows(IllegalArgumentException.class, () -> StateTaxJurisdiction.fromCode(code));
        }
        assertEquals(12, StateTaxJurisdiction.values().length);
    }
}
