package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStateTaxServiceTest {
    static final LocalDate DATE = LocalDate.of(2026, 1, 1);
    private final ProductionStateTaxService service = new ProductionStateTaxService();

    static Stream<org.junit.jupiter.params.provider.Arguments> floridaCases() {
        return Stream.of(false, true).flatMap(couple -> Stream.of("SS", "PENSION", "IRA", "RMD", "CONVERSION",
                "ROTH", "INTEREST", "DIVIDEND", "GAIN", "MIXED").map(kind ->
                org.junit.jupiter.params.provider.Arguments.of(couple, kind)));
    }

    @ParameterizedTest
    @MethodSource("floridaCases")
    void floridaZeroIsCalculatedForModeledIncomeFacts(boolean couple, String kind) throws Exception {
        BigDecimal amount = new BigDecimal("2000000");
        var sourceKind = switch (kind) {
            case "SS" -> IncomeSourceAttribution.Kind.SOCIAL_SECURITY;
            case "PENSION" -> IncomeSourceAttribution.Kind.PENSION;
            case "IRA", "ROTH" -> IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL;
            case "RMD" -> IncomeSourceAttribution.Kind.RMD;
            case "CONVERSION" -> IncomeSourceAttribution.Kind.ROTH_CONVERSION;
            case "DIVIDEND" -> IncomeSourceAttribution.Kind.DIVIDEND;
            case "GAIN" -> IncomeSourceAttribution.Kind.CAPITAL_GAIN;
            default -> IncomeSourceAttribution.Kind.INTEREST;
        };
        var source = IncomeSourceAttribution.aggregate(sourceKind, IncomeSourceAttribution.Purpose.INCOME,
                kind, amount, Optional.of(kind.equals("ROTH") ? BigDecimal.ZERO : amount));
        var income = new TaxIncome(kind.equals("PENSION") || kind.equals("MIXED") ? amount : BigDecimal.ZERO,
                kind.equals("SS") || kind.equals("MIXED") ? amount : BigDecimal.ZERO,
                kind.equals("IRA") || kind.equals("RMD") || kind.equals("MIXED") ? amount : BigDecimal.ZERO,
                kind.equals("CONVERSION") || kind.equals("MIXED") ? amount : BigDecimal.ZERO,
                Set.of("INTEREST", "DIVIDEND", "GAIN", "MIXED").contains(kind) ? amount : BigDecimal.ZERO,
                List.of(source));
        var result = calculate(couple, StateTaxJurisdiction.FL, income);
        assertEquals(StateTaxJurisdiction.FL, result.jurisdiction());
        assertEquals(ProductionStateTaxResult.Status.CALCULATED, result.calculationStatus());
        assertEquals(0, result.incomeTax().signum());
        assertTrue(result.michiganDetails().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"false,10000,0,174.25", "true,20000,0,348.50",
            "false,0,100000,1125.83", "true,0,200000,2251.65"})
    void originalMichiganTargetsSurviveAdapter(boolean couple, String interest, String ira, String expected) throws Exception {
        var amount = new BigDecimal(ira);
        var account = new TraditionalIRA("Known IRA", AccountOwnership.PRIMARY, amount);
        var source = IncomeSourceAttribution.withdrawal(account, amount,
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        var result = calculate(couple, StateTaxJurisdiction.MI, new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO,
                amount, BigDecimal.ZERO, new BigDecimal(interest), List.of(source)));
        assertEquals(new BigDecimal(expected), result.incomeTax().setScale(2, java.math.RoundingMode.HALF_UP));
        assertTrue(result.michiganDetails().isPresent());
    }

    @Test
    void stateChoiceDoesNotChangeFixedInputFederalTax() throws Exception {
        var income = new TaxIncome(new BigDecimal("30000"), new BigDecimal("25000"), new BigDecimal("100000"),
                new BigDecimal("40000"), new BigDecimal("10000"));
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var calculator = new FederalTaxCalculator();
        var before = calculator.calculate(income, FilingStatus.SINGLE, rules);
        calculate(false, StateTaxJurisdiction.MI, income);
        calculate(false, StateTaxJurisdiction.FL, income);
        var after = calculator.calculate(income, FilingStatus.SINGLE, rules);
        assertEquals(before.getFederalIncomeTax(), after.getFederalIncomeTax());
        assertEquals(before.getTaxableIncome(), after.getTaxableIncome());
        assertEquals(before.getAdjustedGrossIncome(), after.getAdjustedGrossIncome());
    }

    private ProductionStateTaxResult calculate(boolean couple, StateTaxJurisdiction state, TaxIncome income) throws Exception {
        var primary = new Person("Primary", "Example", LocalDate.of(1960, 1, 1));
        var spouse = couple ? new Person("Spouse", "Example", LocalDate.of(1961, 1, 1)) : null;
        var household = new Household(primary, spouse);
        var filing = couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        return service.calculate(new StateTaxCalculationRequest(2026, filing,
                TaxHouseholdContext.capture(household, DATE, null), income,
                new FederalTaxCalculator().calculate(income, filing, rules), rules,
                StateTaxAssumptions.simplified(state)));
    }
}
