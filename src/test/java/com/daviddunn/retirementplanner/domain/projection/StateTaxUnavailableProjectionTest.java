package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxFundingCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateTaxUnavailableProjectionTest {
    @Test void unsupportedTaxIsNotSuccessfulProjectionOrInsufficientFunds() {
        var plan = FloridaProductionProjectionTest.plan(false, StateTaxJurisdiction.MI);
        var unsupported = ProductionStateTaxResult.unsupported(StateTaxJurisdiction.MI,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                List.of(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION), "Required facts are unavailable.");
        var engine = new ProjectionEngine(new TaxFundingCalculator(new ProductionStateTaxService(
                Map.of(StateTaxJurisdiction.MI, request -> unsupported))));
        for (var structured : List.of(false, true)) {
            var failure = assertThrows(StateTaxUnavailableException.class, () -> {
                if (structured) engine.projectWithOutcome(plan);
                else engine.project(plan);
            });
            assertSame(unsupported, failure.result());
            assertTrue(failure.getMessage().contains("Required facts are unavailable"));
        }
    }

    @Test void knownParticipantMetadataDoesNotChangeAnyExistingStateFinancialRows() {
        for (var state : List.of(StateTaxJurisdiction.MI, StateTaxJurisdiction.FL, StateTaxJurisdiction.IL)) {
            var plan = FloridaProductionProjectionTest.plan(true, state);
            var baseline = FloridaProductionProjectionTest.financialRows(new ProjectionEngine().project(plan));
            var facts = new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                    java.time.LocalDate.of(2030, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET,
                    java.time.LocalDate.of(2031, 1, 1));
            for (var account : plan.getAccountPortfolio().getAccounts()) {
                if (account.supportsRetirementParticipantFacts()) account.setRetirementParticipantFacts(facts);
            }
            for (var person : List.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse())) {
                for (var income : person.getIncomeSources()) {
                    if (income instanceof com.daviddunn.retirementplanner.domain.income.Pension pension) {
                        pension.setRetirementParticipantFacts(facts);
                    }
                }
            }
            assertEquals(baseline, FloridaProductionProjectionTest.financialRows(new ProjectionEngine().project(plan)));
        }
    }
}
