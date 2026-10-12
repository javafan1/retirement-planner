package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MichiganEligibilityBoundaryTest {
    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class, names = {"INTEREST", "DIVIDEND", "CAPITAL_GAIN", "OTHER"})
    void ordinaryInvestmentIncomeNeverReceivesOrConsumesRetirementSubtraction(IncomeSourceAttribution.Kind kind) throws Exception {
        var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                "Ordinary taxable investment income", new BigDecimal("10000"), Optional.of(new BigDecimal("10000")));
        var household = new Household(new Person("Primary", "Fixture", LocalDate.of(1966, 1, 1)));
        var context = MichiganTaxContext.capture(household, LocalDate.of(2026, 1, 1), null);
        assertEquals(MichiganRetirementEligibility.Qualification.NONQUALIFYING,
                new MichiganRetirementEligibility().assess(source, context).qualification());
        var result = new MichiganTaxCalculator().calculate(new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000"), List.of(source)), FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getMichiganTaxRules(), context);
        assertEquals(0, result.retirementDeduction().signum());
        assertEquals(0, new BigDecimal("174.25").compareTo(result.incomeTax()));
    }

    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class,
            names = {"ORDINARY_RETIREMENT_WITHDRAWAL", "RMD", "OPENING_RMD", "ROTH_CONVERSION"})
    void iraDistributionClassificationDoesNotChangeSharedLimit(IncomeSourceAttribution.Kind kind) throws Exception {
        var household = new Household(new Person("Primary", "Fixture", LocalDate.of(1960, 1, 1)));
        var source = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY,
                new BigDecimal("100000")), new BigDecimal("100000"), kind, IncomeSourceAttribution.Purpose.CASH_FLOW);
        var income = new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO,
                kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION ? BigDecimal.ZERO : new BigDecimal("100000"),
                kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION ? new BigDecimal("100000") : BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(source));
        var result = new MichiganTaxCalculator().calculate(income, FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getMichiganTaxRules(),
                MichiganTaxContext.capture(household, LocalDate.of(2026, 1, 1), null));
        assertEquals(0, new BigDecimal("67610").compareTo(result.retirementDeduction()));
        assertEquals(0, new BigDecimal("1125.825").compareTo(result.incomeTax()));
    }

    @Test
    void rolloverOriginalPlanIsUnknownAndQualifiedRothDoesNotAddTaxableIncome() {
        var household = new Household(new Person("Primary", "Fixture", LocalDate.of(1960, 1, 1)));
        var context = MichiganTaxContext.capture(household, LocalDate.of(2026, 1, 1), null);
        var eligibility = new MichiganRetirementEligibility();
        var rollover = new RolloverIRA("Unknown original plan", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        assertEquals(MichiganRetirementEligibility.Qualification.UNKNOWN, eligibility.assess(
                IncomeSourceAttribution.withdrawal(rollover, new BigDecimal("100000"),
                        IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW), context).qualification());
        var roth = new RothIRA("Roth", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        assertEquals(MichiganRetirementEligibility.Qualification.NONQUALIFYING, eligibility.assess(
                IncomeSourceAttribution.withdrawal(roth, new BigDecimal("100000"),
                        IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW), context).qualification());
    }

    @Test
    void missingOrNegativeRuleExemptionFailsRatherThanBecomingZero() {
        var mapper = new ObjectMapper();
        String missing = "{\"incomeTaxRate\":0.0425,\"retirementDeductionSingle\":67610,\"retirementDeductionMarried\":135220}";
        assertThrows(Exception.class, () -> mapper.readValue(missing, com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules.class));
        assertThrows(IllegalArgumentException.class, () -> new com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules(
                new BigDecimal("0.0425"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1")));
    }
}
