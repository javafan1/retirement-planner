package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

public class LifeInsurancePrincipalTest {
    public static ObjectNode document() {
        ObjectNode node = JSON.valueToTree(monthly());
        node.put("category", "DEATH_BENEFIT");
        ObjectNode trigger = node.putObject("trigger");
        trigger.put("type", "DEATH"); trigger.putNull("date"); trigger.put("person", "PRIMARY"); trigger.putNull("age");
        node.put("recipient", "SPOUSE"); node.put("paymentPattern", "ONE_TIME_LUMP_SUM");
        node.put("grossAmount", new BigDecimal("400000")); node.put("annualEscalationRate", BigDecimal.ZERO);
        node.put("coverageStart", "2030-01-01"); node.put("coverageEnd", "2040-12-31");
        node.put("taxCharacter", "QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED");
        node.put("lifeInsuranceQualification", "PERSONAL_LIFE_PRINCIPAL_ONLY_V1");
        return node;
    }

    public static ConditionalBenefitDefinition principal() {
        try { return JSON.treeToValue(document(), ConditionalBenefitDefinition.class); }
        catch (Exception invalid) { throw new IllegalArgumentException(invalid); }
    }

    @Test void qualifyingPrincipalUsesTheSameDeathEventWithSeparateCashAndTaxFacts() {
        var plan = plan(true);
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), Optional.of(
                        new HouseholdLifetimeScenario(Optional.of(Year.of(2031)), Optional.of(Year.of(2035)))));
        var context = BenefitEvaluationContext.capture(plan.getHousehold(), deaths, LocalDate.of(2030,1,1),2031);
        var evaluation = new ConditionalBenefitEvaluator().evaluate(List.of(principal()), context);
        assertEquals(new BigDecimal("400000.00"), evaluation.gross());
        assertEquals(0, evaluation.taxable().signum());
        assertEquals(evaluation.gross(), evaluation.excluded());
        assertEquals(evaluation, new ConditionalBenefitEvaluator().evaluate(List.of(principal()), context));
    }

    @ParameterizedTest
    @CsvSource({"taxCharacter,FULLY_TAXABLE_ORDINARY_NON_PENSION", "category,CONDITIONAL_INCOME",
            "paymentPattern,MONTHLY_RECURRING", "recipient,PRIMARY", "lifeInsuranceQualification,UNKNOWN"})
    void inconsistentOrUnknownPersistedQualificationIsRejected(String field, String value) {
        var node = document(); node.put(field,value);
        assertThrows(Exception.class, () -> JSON.treeToValue(node, ConditionalBenefitDefinition.class));
    }

    @Test void qualificationIsExplicitAndNoLegacyDefaultOrWireIsChanged() throws Exception {
        var node = document(); node.remove("lifeInsuranceQualification");
        assertThrows(Exception.class, () -> JSON.treeToValue(node, ConditionalBenefitDefinition.class));
        node.put("taxCharacter", "FULLY_TAXABLE_ORDINARY_NON_PENSION");
        var taxable = JSON.treeToValue(node, ConditionalBenefitDefinition.class);
        assertFalse(taxable.excludesLifePrincipal());
        assertFalse(JSON.valueToTree(taxable).has("lifeInsuranceQualification"));
        assertEquals(taxable,JSON.readValue(JSON.writeValueAsBytes(taxable),ConditionalBenefitDefinition.class));
        assertEquals(principal(),JSON.readValue(JSON.writeValueAsBytes(principal()),ConditionalBenefitDefinition.class));
        assertThrows(IllegalArgumentException.class,()->plan(false).setConditionalBenefits(List.of(principal())));
        var withInterest=document(); withInterest.put("settlementInterest",1);
        assertThrows(Exception.class,()->JSON.treeToValue(withInterest,ConditionalBenefitDefinition.class));
        for (String triggerType : List.of("FIXED_DATE","AGE")) {
            var wrongTrigger=document(); var trigger=(ObjectNode)wrongTrigger.get("trigger");
            trigger.put("type",triggerType);
            if (triggerType.equals("FIXED_DATE")) { trigger.put("date","2031-01-01"); trigger.putNull("person"); }
            else trigger.put("age",70);
            assertThrows(Exception.class,()->JSON.treeToValue(wrongTrigger,ConditionalBenefitDefinition.class));
        }
    }

    @ParameterizedTest
    @CsvSource({"PRIMARY,2031,2035,2031,2030-01-01,400000.00", "SPOUSE,2031,2035,2031,2030-01-01,400000.00",
            "PRIMARY,2029,2035,2031,2030-01-01,0", "PRIMARY,2031,2035,2032,2030-01-01,0",
            "PRIMARY,2041,2045,2041,2030-01-01,0", "PRIMARY,2031,2031,2031,2030-01-01,0",
            "PRIMARY,2031,2035,2031,2031-07-01,0"})
    void bothInsuredDirectionsCoverageRecipientsAndNoReplay(String insured,int death,int survivorDeath,int year,String opening,String amount) throws Exception {
        var node = document();
        ((ObjectNode)node.get("trigger")).put("person",insured);
        node.put("recipient",insured.equals("PRIMARY") ? "SPOUSE" : "PRIMARY");
        var definition = JSON.treeToValue(node,ConditionalBenefitDefinition.class);
        var household = plan(true).getHousehold();
        var timing = new HouseholdLifetimeScenario(Optional.of(Year.of(insured.equals("PRIMARY")?death:survivorDeath)),
                Optional.of(Year.of(insured.equals("PRIMARY")?survivorDeath:death)));
        var view = EffectiveHouseholdDeathView.resolve(household,new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.of(timing));
        var result = new ConditionalBenefitEvaluator().evaluate(List.of(definition),BenefitEvaluationContext.capture(household,view,LocalDate.parse(opening),year));
        assertEquals(0,new BigDecimal(amount).compareTo(result.gross()));
        assertEquals(0,result.taxable().signum()); assertEquals(result.gross(),result.excluded());
    }

    @Test void noDeathAndAnnualRoundingRemainAuthoritative() throws Exception {
        var household = plan(true).getHousehold();
        var deaths = EffectiveHouseholdDeathView.resolve(household,new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.empty());
        assertTrue(new ConditionalBenefitEvaluator().evaluate(List.of(principal()),
                BenefitEvaluationContext.capture(household,deaths,LocalDate.of(2030,1,1),2031)).receipts().isEmpty());
        var node = document(); node.put("grossAmount",new BigDecimal("400000.005"));
        var benefit = JSON.treeToValue(node,ConditionalBenefitDefinition.class);
        deaths = EffectiveHouseholdDeathView.resolve(household,new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES,2031),Optional.empty());
        var receipt = new ConditionalBenefitEvaluator().evaluate(List.of(benefit),
                BenefitEvaluationContext.capture(household,deaths,LocalDate.of(2030,1,1),2031)).receipts().getFirst();
        assertEquals(new BigDecimal("400000.01"),receipt.gross());
        assertEquals(receipt.gross(),receipt.taxable().add(receipt.excluded()));
        var changed=receipt.gross().add(BigDecimal.ONE);
        var corrupted=new ConditionalBenefitReceipt(receipt.benefitId(),receipt.label(),receipt.category(),receipt.trigger(),receipt.recipient(),
                receipt.pattern(),receipt.eventDate(),receipt.firstEligibleDate(),receipt.paymentStart(),receipt.paymentEnd(),receipt.months(),receipt.year(),
                changed,receipt.taxable(),changed,receipt.taxCharacter(),receipt.destination());
        assertThrows(IllegalArgumentException.class,()->corrupted.validateDefinition(benefit));
    }
}
