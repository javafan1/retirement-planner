package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitEvaluatorTest {
    final ConditionalBenefitEvaluator evaluator = new ConditionalBenefitEvaluator();
    ConditionalBenefitDefinition level(LocalDate event) {
        return change(change(change(monthly(), "grossAmount", new BigDecimal("2500")), "annualEscalationRate", BigDecimal.ZERO),
                "trigger", new Trigger(TriggerType.FIXED_DATE, event, null, null));
    }
    BenefitEvaluationContext context(LocalDate opening, int year, HouseholdLifetimeScenario scenario) {
        var plan = plan(true);
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), Optional.of(scenario));
        return new BenefitEvaluationContext(LocalDate.of(1960, 2, 29), Optional.of(LocalDate.of(1962, 1, 1)), deaths, opening, year);
    }
    AnnualBenefitEvaluation run(ConditionalBenefitDefinition benefit, LocalDate opening, int year) {
        return evaluator.evaluate(List.of(benefit), context(opening, year, HouseholdLifetimeScenario.bothSurvive()));
    }
    @ParameterizedTest @CsvSource({"2032-01-01,2032-01-01,30000", "2032-03-17,2032-01-01,25000",
            "2032-12-31,2032-01-01,2500", "2030-01-01,2032-07-20,15000", "2033-01-01,2032-01-01,0"})
    void inclusiveMonthsAndOpeningCutoff(String event, String opening, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(run(level(LocalDate.parse(event)), LocalDate.parse(opening), 2032).gross()));
    }
    @Test void sameMonthEndAndAlreadyTerminatedOpeningAreDifferent() {
        var benefit = change(level(LocalDate.of(2032, 3, 17)), "endDate", LocalDate.of(2032, 3, 31));
        assertEquals(new BigDecimal("2500.00"), run(benefit, LocalDate.of(2032, 1, 1), 2032).gross());
        assertEquals(BigDecimal.ZERO, run(benefit, LocalDate.of(2032, 4, 1), 2032).gross());
        assertEquals(BigDecimal.ZERO, run(change(level(LocalDate.of(2030,1,1)), "endDate", LocalDate.of(2032,7,1)), LocalDate.of(2032,7,20), 2032).gross());
    }
    @Test void escalationUsesInitialEligibilityAndNotProjectionOpening() {
        var benefit = change(level(LocalDate.of(2030, 1, 1)), "annualEscalationRate", new BigDecimal(".10"));
        assertEquals(new BigDecimal("18150.00"), run(benefit, LocalDate.of(2032,7,1), 2032).gross());
        assertEquals(new BigDecimal("39930.00"), run(benefit, LocalDate.of(2032,7,1), 2033).gross());
        benefit = change(benefit, "notBefore", LocalDate.of(2032,7,20));
        assertEquals(new BigDecimal("15000.00"), run(benefit, LocalDate.of(2032,1,1), 2032).gross());
    }
    @Test void leapBirthdayAgeUsesFrozenDobAndCanPayBeforeAProjectionHorizon() {
        var benefit = change(level(LocalDate.of(2032,1,1)), "trigger", new Trigger(TriggerType.AGE, null, PersonReference.PRIMARY, 65));
        var result = run(benefit, LocalDate.of(2025,1,1), 2025);
        assertEquals(LocalDate.of(2025,2,28), result.receipts().getFirst().eventDate());
        assertEquals(new BigDecimal("27500.00"), result.gross());
    }
    @Test void deathsDriveBothSurvivorDirectionsAndRecipientCessationWithoutNewMortality() {
        for (var insured : PersonReference.values()) {
            var recipient = insured == PersonReference.PRIMARY ? PersonReference.SPOUSE : PersonReference.PRIMARY;
            var benefit = change(change(level(LocalDate.of(2032,1,1)), "recipient", recipient), "trigger", new Trigger(TriggerType.DEATH, null, insured, null));
            var deaths = insured == PersonReference.PRIMARY
                    ? new HouseholdLifetimeScenario(Optional.of(Year.of(2030)), Optional.of(Year.of(2033)))
                    : new HouseholdLifetimeScenario(Optional.of(Year.of(2033)), Optional.of(Year.of(2030)));
            assertEquals(new BigDecimal("15000.00"), evaluator.evaluate(List.of(benefit), context(LocalDate.of(2032,7,1),2032,deaths)).gross());
            assertEquals(BigDecimal.ZERO, evaluator.evaluate(List.of(benefit), context(LocalDate.of(2032,7,1),2033,deaths)).gross());
        }
    }
    @Test void allThreeLumpTriggersAreIdempotentAndNeverReplay() {
        for (var trigger : List.of(new Trigger(TriggerType.FIXED_DATE, LocalDate.of(2032,1,1),null,null),
                new Trigger(TriggerType.AGE,null,PersonReference.PRIMARY,72), new Trigger(TriggerType.DEATH,null,PersonReference.PRIMARY,null))) {
            var benefit = change(change(change(level(LocalDate.of(2032,1,1)), "paymentPattern", PaymentPattern.ONE_TIME_LUMP_SUM),
                    "recipient", PersonReference.SPOUSE), "trigger", trigger);
            benefit = change(change(benefit,"coverageStart", LocalDate.of(2032,1,1)),"coverageEnd", LocalDate.of(2032,12,31));
            var ctx = context(LocalDate.of(2032,1,1),2032,new HouseholdLifetimeScenario(Optional.of(Year.of(2032)),Optional.of(Year.of(2034))));
            var first = evaluator.evaluate(List.of(benefit),ctx);
            assertEquals(new BigDecimal("2500.00"), first.gross()); assertEquals(first,evaluator.evaluate(List.of(benefit),ctx));
            assertEquals(first.gross(),first.taxable()); assertEquals(BigDecimal.ZERO,first.excluded());
            assertEquals(BigDecimal.ZERO,evaluator.evaluate(List.of(benefit),context(LocalDate.of(2033,1,1),2033,new HouseholdLifetimeScenario(Optional.of(Year.of(2032)),Optional.of(Year.of(2034))))).gross());
        }
    }
    @Test void boundaryCoverageAndDistinctIdsPreserveImmutableLineageAndCents() {
        var benefit = change(change(level(LocalDate.of(2032,1,1)),"coverageStart", LocalDate.of(2032,1,1)),"coverageEnd",LocalDate.of(2032,1,1));
        benefit = change(benefit,"grossAmount",new BigDecimal("0.12345678"));
        var second = change(benefit,"id",new UUID(0,2));
        var ctx = context(LocalDate.of(2032,1,1),2032,HouseholdLifetimeScenario.bothSurvive());
        var result = evaluator.evaluate(List.of(benefit,second),ctx);
        assertEquals(new BigDecimal("2.96"),result.gross()); assertEquals(2,result.receipts().size());
        assertNotEquals(result.receipts().getFirst().benefitId(),result.receipts().getLast().benefitId());
        assertThrows(UnsupportedOperationException.class,()->result.receipts().clear());
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(List.of(second,second),ctx));
    }
}
