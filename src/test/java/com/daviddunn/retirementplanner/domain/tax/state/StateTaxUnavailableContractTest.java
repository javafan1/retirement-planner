package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class StateTaxUnavailableContractTest {
    static ProductionStateTaxResult unavailable() {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.MI,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                List.of(StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS), "Distribution basis is unavailable.");
    }

    @Test void calculatedPositiveZeroAndUnavailableAreDistinct() {
        for (var amount : List.of(BigDecimal.ZERO, new BigDecimal("100"))) {
            var result = new ProductionStateTaxResult(StateTaxJurisdiction.FL,
                    StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                    StateTaxCalculationStatus.CALCULATED, amount, List.of(), Optional.empty());
            assertEquals(amount, result.estimatedIncomeTax().orElseThrow());
            assertEquals(amount, result.incomeTax());
            StateTaxUnavailableException.requireCalculated(result, 2026);
        }
        var result = unavailable();
        assertTrue(result.estimatedIncomeTax().isEmpty());
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertThrows(IllegalStateException.class, result::incomeTax);
        var failure = assertThrows(StateTaxUnavailableException.class,
                () -> StateTaxUnavailableException.requireCalculated(result, 2026));
        assertSame(result, failure.result());
        assertEquals(2026, failure.taxYear());
        assertTrue(failure.getMessage().contains("State income tax unavailable"));
        assertTrue(failure.getMessage().contains("MISSING_DISTRIBUTION_BASIS"));
    }

    @Test void invalidUnsupportedContractsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProductionStateTaxResult.unsupported(
                StateTaxJurisdiction.MI, StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                List.of(), "No facts"));
        assertThrows(IllegalArgumentException.class, () -> ProductionStateTaxResult.unsupported(
                StateTaxJurisdiction.MI, StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                List.of(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION), ""));
        assertThrows(IllegalArgumentException.class, () -> new ProductionStateTaxResult(StateTaxJurisdiction.MI,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE, StateTaxCalculationStatus.UNSUPPORTED,
                Optional.of(BigDecimal.ZERO), List.of("Unavailable"), Optional.empty(),
                List.of(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION)));
    }

    @Test void employerParticipantFactsDoNotChangeExistingProductionStateTaxRules() throws Exception {
        var account = new com.daviddunn.retirementplanner.domain.financial.Traditional401K(
                "Employer", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        for (var jurisdiction : StateTaxJurisdiction.values()) {
            var amounts = new ArrayList<BigDecimal>();
            for (var facts : List.of(RetirementParticipantFacts.UNKNOWN,
                    new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                            LocalDate.of(2020, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET,
                            LocalDate.of(2021, 1, 1)))) {
                account.setRetirementParticipantFacts(facts);
                var source = com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.withdrawal(account,
                        new BigDecimal("40000"),
                        com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                        com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CASH_FLOW);
                var income = new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40000"),
                        BigDecimal.ZERO, new BigDecimal("10000"), List.of(source));
                var template = IllinoisProductionTaxCalculatorTest.request(false, 65, income);
                var request = new StateTaxCalculationRequest(template.taxYear(), template.filingStatus(),
                        template.household(), income, template.federal(), template.projectedRules(),
                        StateTaxAssumptions.simplified(jurisdiction));
                assertEquals(facts, request.income().getIncomeSources().getFirst().account().orElseThrow().participantFacts());
                amounts.add(new ProductionStateTaxService().calculate(request).incomeTax());
            }
            assertEquals(amounts.getFirst(), amounts.getLast());
        }
    }

    @ParameterizedTest @EnumSource(StateTaxUnavailableReason.class)
    void stableReasonsCarryThroughStructuredResult(StateTaxUnavailableReason reason) {
        var result = ProductionStateTaxResult.unsupported(StateTaxJurisdiction.IL,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE, List.of(reason), "Missing facts.");
        assertEquals(List.of(reason), result.unavailableReasons());
        assertThrows(UnsupportedOperationException.class, () -> result.unavailableReasons().clear());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2})
    void fundingAbortsBeforeArithmeticOrAdditionalAllocation(int failOnCall) throws Exception {
        var calls = new AtomicInteger();
        var service = new ProductionStateTaxService(Map.of(StateTaxJurisdiction.MI, request -> {
            if (calls.incrementAndGet() == failOnCall) return unavailable();
            return new ProductionStateTaxResult(StateTaxJurisdiction.MI,
                    StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                    StateTaxCalculationStatus.CALCULATED, BigDecimal.ZERO, List.of(), Optional.empty());
        }));
        var person = new Person("Primary", "", LocalDate.of(1960, 1, 1));
        var household = new Household(person);
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account, account.getCurrentBalance())));
        var context = TaxHouseholdContext.capture(household, LocalDate.of(2026, 1, 1), null);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var existing = new ProjectedWithdrawalAllocator().calculateWithdrawalBreakdown(
                portfolio, BigDecimal.ZERO, new TaxDeferredFirstWithdrawalStrategy());
        var failure = assertThrows(StateTaxUnavailableException.class,
                () -> new TaxFundingCalculator(service).calculate(household, context.distributionDate(), portfolio,
                        existing, new TaxDeferredFirstWithdrawalStrategy(), FilingStatus.SINGLE, rules,
                        BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO, null,
                        BigDecimal.ZERO, Optional.empty(), List.of(), context, StateTaxAssumptions.michigan()));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, failure.result().calculationStatus());
        assertEquals(failOnCall, calls.get());
        assertEquals(new BigDecimal("100000"), portfolio.getBalance(account));
        assertEquals(new BigDecimal("100000"), account.getCurrentBalance());
    }
}
