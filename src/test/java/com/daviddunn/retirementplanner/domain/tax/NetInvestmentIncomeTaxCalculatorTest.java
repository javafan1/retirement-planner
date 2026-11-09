package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NetInvestmentIncomeTaxCalculatorTest {
    private final NetInvestmentIncomeTaxCalculator calculator = new NetInvestmentIncomeTaxCalculator();
    @Test void documentedControlAndOtherInterest() {
        assertEquals(new BigDecimal("218.88"), calculator.calculate(FilingStatus.MARRIED_FILING_JOINTLY,
                new BigDecimal("300000"), new BigDecimal("5760"), BigDecimal.ZERO).tax());
        assertEquals(new BigDecimal("598.88"), calculator.calculate(FilingStatus.MARRIED_FILING_JOINTLY,
                new BigDecimal("310000"), new BigDecimal("5760"), new BigDecimal("10000")).tax());
    }
    @ParameterizedTest @EnumSource(FilingStatus.class)
    void thresholdsAndExcessLimit(FilingStatus status) {
        var threshold = switch(status) {
            case SINGLE, HEAD_OF_HOUSEHOLD -> new BigDecimal("200000");
            case MARRIED_FILING_SEPARATELY -> new BigDecimal("125000");
            case MARRIED_FILING_JOINTLY, QUALIFYING_SURVIVING_SPOUSE -> new BigDecimal("250000");
        };
        assertEquals(new BigDecimal("0.00"), calculator.calculate(status, threshold, new BigDecimal("10000"), BigDecimal.ZERO).tax());
        assertEquals(new BigDecimal("38.00"), calculator.calculate(status, threshold.add(new BigDecimal("1000")),
                new BigDecimal("10000"), BigDecimal.ZERO).tax());
    }
    @Test void noInvestmentIncomeIsNotInventedFromMagi() {
        assertEquals(new BigDecimal("0.00"), calculator.calculate(FilingStatus.SINGLE,
                new BigDecimal("500000"), BigDecimal.ZERO, BigDecimal.ZERO).tax());
    }
    @Test void invalidInputsAndTamperedEvidenceRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(FilingStatus.SINGLE,
                new BigDecimal("300000"), new BigDecimal("-1"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new NetInvestmentIncomeTaxCalculation(
                NetInvestmentIncomeTaxCalculation.MODEL, FilingStatus.SINGLE, new BigDecimal("300000"),
                new BigDecimal("5760"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("250000"), new BigDecimal("0.038"), new BigDecimal("218.88")));
    }
    @Test void allAttributedInterestIncludedWhileIraDistributionAndConversionOnlyRaiseMagi() throws Exception {
        var income=attributedIncome();
        var rules=new com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var result=new FederalTaxCalculator().calculate(income,FilingStatus.MARRIED_FILING_JOINTLY,rules);
        assertEquals(0,new BigDecimal("310000").compareTo(result.getAdjustedGrossIncome()));
        assertEquals(new BigDecimal("598.88"),result.niit().orElseThrow().tax());
        assertEquals(0,new BigDecimal("52466.88").compareTo(result.getFederalIncomeTax()));
    }
    @Test void incompleteOrUnsupportedInvestmentFactsCannotBecomeZeroNiit() {
        var original=attributedIncome();
        var sources=new java.util.ArrayList<>(original.getIncomeSources());
        sources.add(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.aggregate(
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.DIVIDEND,
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.INCOME,"Unassessed dividend",
                new BigDecimal("1000"),java.util.Optional.of(new BigDecimal("1000"))));
        assertThrows(NiitUnsupportedInputException.class,()->calculator.calculate(original.withIncomeSources(sources),FilingStatus.MARRIED_FILING_JOINTLY,new BigDecimal("311000")));
        var missing=original.getIncomeSources().stream().filter(s->s.kind()!=com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST).toList();
        assertThrows(NiitUnsupportedInputException.class,()->calculator.calculate(original.withIncomeSources(missing),FilingStatus.MARRIED_FILING_JOINTLY,new BigDecimal("310000")));
    }
    private TaxIncome attributedIncome() {
        var plan=com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest.fixture(true);
        var deaths=com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                plan.getPlanningAssumptions().getDeathScenarioAssumptions(),java.util.Optional.empty());
        var receipt=new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator().evaluate(plan.getConditionalBenefits(),
                com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext.capture(plan.getHousehold(),deaths,java.time.LocalDate.of(2030,1,1),2030)).receipts().getFirst();
        var ira=plan.getAccountPortfolio().getAccounts().get(1);
        var sources=java.util.List.of(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.conditional(receipt),
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.withdrawal(ira,new BigDecimal("50000"),
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CASH_FLOW),
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.withdrawal(ira,new BigDecimal("244240"),
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ROTH_CONVERSION,
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CONVERSION),
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.aggregate(
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST,
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.INCOME,"Personal taxable interest",
                    new BigDecimal("10000"),java.util.Optional.of(new BigDecimal("10000"))));
        return TaxIncomeAttribution.attach(new TaxIncome(BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("50000"),
                new BigDecimal("244240"),new BigDecimal("10000")),sources,2030);
    }
}
