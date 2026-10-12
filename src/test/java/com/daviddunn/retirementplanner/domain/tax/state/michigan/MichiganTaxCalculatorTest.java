package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MichiganTaxCalculatorTest {

    private MichiganTaxCalculation calculateAttributed(String amount) {
        var person = new com.daviddunn.retirementplanner.domain.model.Person("Primary", "Fixture", LocalDate.of(1960, 1, 1));
        var household = new com.daviddunn.retirementplanner.domain.model.Household(person);
        var account = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, new BigDecimal(amount));
        var source = com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.withdrawal(account,
                new BigDecimal(amount), com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CASH_FLOW);
        return calculator.calculate(new com.daviddunn.retirementplanner.domain.tax.TaxIncome(BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(amount), BigDecimal.ZERO, BigDecimal.ZERO, java.util.List.of(source)),
                FilingStatus.SINGLE, projectedRules, MichiganTaxContext.capture(household, LocalDate.of(2027, 1, 1), null));
    }

    private MichiganTaxCalculator calculator;
    private MichiganTaxRules projectedRules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new MichiganTaxCalculator();

        GovernmentRules governmentRules =
                new GovernmentRulesRepository()
                        .load("/rules/government-rules-2026.json");

        PlanningAssumptions planningAssumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.070"),
                        new BigDecimal("0.025"),
                        30,
                        LocalDate.of(2026, 1, 1));

        projectedRules =
                new MichiganTaxRuleProjectionService()
                        .project(
                                governmentRules.getMichiganTaxRules(),
                                governmentRules.getTaxYear(),
                                2027,
                                planningAssumptions);
    }

    @Test
    void incomeBelowRetirementDeductionProducesNoTax() {

        MichiganTaxCalculation calculation =
                calculateAttributed("30000");

        assertEquals(
                BigDecimal.ZERO,
                calculation.taxableIncome());

        assertEquals(
                BigDecimal.ZERO,
                calculation.incomeTax());
    }

    @Test
    void incomeAboveRetirementDeductionProducesTax() {

        MichiganTaxCalculation calculation =
                calculateAttributed("100000");

        BigDecimal expectedTaxableIncome =
                BigDecimal.valueOf(100_000)
                        .subtract(
                                projectedRules
                                        .getRetirementDeductionSingle())
                        .subtract(projectedRules.getPersonalExemption());

        BigDecimal expectedTax =
                expectedTaxableIncome.multiply(
                        projectedRules.getIncomeTaxRate());

        assertEquals(
                expectedTaxableIncome,
                calculation.taxableIncome());

        assertEquals(
                expectedTax,
                calculation.incomeTax());
    }
}
