package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

public class NonqualifiedAnnuityEvaluatorTest {
    public static ConditionalBenefitDefinition annuity(PersonReference owner, LocalDate opening, LocalDate firstPayment,
            String gross, String excluded, String basis) {
        return new ConditionalBenefitDefinition(UUID.randomUUID(), "Quoted annuity", Category.NONQUALIFIED_INCOME_ANNUITY,
                new Trigger(TriggerType.FIXED_DATE, firstPayment, null, null), owner, PaymentPattern.MONTHLY_RECURRING,
                new BigDecimal(gross), null, null, null, null, BigDecimal.ZERO, EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR,
                TaxCharacter.NONQUALIFIED_GENERAL_RULE_FIXED_EXCLUSION, ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH,
                null, new NonqualifiedAnnuityTerms(LocalDate.of(2020,1,1), firstPayment,
                    new BigDecimal(excluded), firstPayment.isBefore(opening) ? new BigDecimal("100000") : new BigDecimal(basis), new BigDecimal(basis), opening,
                    NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1));
    }
    ConditionalBenefitReceipt evaluate(ConditionalBenefitDefinition definition, int year, HouseholdLifetimeScenario deaths) {
        var plan = BenefitFixtures.plan(true);
        var view = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.of(deaths));
        return new ConditionalBenefitEvaluator().evaluate(List.of(definition),new BenefitEvaluationContext(LocalDate.of(1960,1,1),
                Optional.of(LocalDate.of(1960,1,1)),view,definition.annuityTerms().basisAsOf(),year)).receipts().getFirst();
    }
    @Test void independentFiniteBasisOraclesAndReentrancy() {
        var definition = annuity(PersonReference.PRIMARY,LocalDate.of(2030,1,1),LocalDate.of(2025,1,1),"1200","720","10000");
        var first=evaluate(definition,2030,HouseholdLifetimeScenario.bothSurvive());
        assertEquals(new BigDecimal("14400.00"),first.gross()); assertEquals(new BigDecimal("8640.00"),first.excluded());
        assertEquals(new BigDecimal("5760.00"),first.taxable()); assertEquals(new BigDecimal("1360.00"),first.annuityBasis().closing());
        var second=evaluate(definition,2031,HouseholdLifetimeScenario.bothSurvive());
        assertEquals(new BigDecimal("1360.00"),second.excluded()); assertEquals(new BigDecimal("13040.00"),second.taxable());
        assertEquals(new BigDecimal("0.00"),second.annuityBasis().closing());
        assertEquals(new BigDecimal("14400.00"),evaluate(definition,2032,HouseholdLifetimeScenario.bothSurvive()).taxable());
        assertEquals(first,evaluate(definition,2030,HouseholdLifetimeScenario.bothSurvive()));
        var terms=definition.annuityTerms();
        var january=new AnnuityBasisRecovery(terms,12,1,new BigDecimal("1360"),new BigDecimal("720"),new BigDecimal("640"));
        var february=new AnnuityBasisRecovery(terms,13,1,new BigDecimal("640"),new BigDecimal("640"),BigDecimal.ZERO);
        var march=new AnnuityBasisRecovery(terms,14,1,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals(List.of(new BigDecimal("720"),new BigDecimal("640"),BigDecimal.ZERO),List.of(january.recovery(),february.recovery(),march.recovery()));
        assertThrows(IllegalArgumentException.class,()->new AnnuityBasisRecovery(terms,13,1,new BigDecimal("640"),new BigDecimal("720"),BigDecimal.ZERO));
    }
    @Test void partialCommencementAndFutureIncome() {
        var definition=annuity(PersonReference.PRIMARY,LocalDate.of(2030,1,1),LocalDate.of(2030,7,1),"1200","720","10000");
        var receipt=evaluate(definition,2030,HouseholdLifetimeScenario.bothSurvive());
        assertEquals(6,receipt.months()); assertEquals(new BigDecimal("7200.00"),receipt.gross());
        assertEquals(new BigDecimal("4320.00"),receipt.excluded()); assertEquals(new BigDecimal("2880.00"),receipt.taxable());
    }
    @Test void onlyAnnuitantsDeathStopsRecoveryAndOtherSpouseContinues() {
        var definition=annuity(PersonReference.PRIMARY,LocalDate.of(2030,1,1),LocalDate.of(2025,1,1),"1200","720","10000");
        var primaryDeath=new HouseholdLifetimeScenario(Optional.of(Year.of(2031)),Optional.empty());
        var stopped=evaluate(definition,2031,primaryDeath);
        assertEquals(new BigDecimal("0.00"),stopped.gross()); assertEquals(new BigDecimal("1360.00"),stopped.annuityBasis().closing());
        assertEquals(new BigDecimal("1360.00"),evaluate(definition,2032,primaryDeath).annuityBasis().closing());
        assertEquals(new BigDecimal("14400.00"),evaluate(definition,2031,new HouseholdLifetimeScenario(Optional.empty(),Optional.of(Year.of(2031)))).gross());
    }
    @Test void precisionAndMonthAlignmentAreRejectedRatherThanRounded() {
        assertThrows(IllegalArgumentException.class,()->annuity(PersonReference.PRIMARY,LocalDate.of(2030,1,15),LocalDate.of(2025,1,1),"1200","720","10000"));
        assertThrows(IllegalArgumentException.class,()->annuity(PersonReference.PRIMARY,LocalDate.of(2030,1,1),LocalDate.of(2025,1,15),"1200","720","10000"));
        assertThrows(IllegalArgumentException.class,()->annuity(PersonReference.PRIMARY,LocalDate.of(2030,1,1),LocalDate.of(2025,1,1),"1200.001","720","10000"));
    }
}
