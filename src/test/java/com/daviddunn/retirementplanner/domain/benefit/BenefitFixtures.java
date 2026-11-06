package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

public final class BenefitFixtures {
    public static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
    private BenefitFixtures() { }
    public static ConditionalBenefitDefinition monthly() {
        return new ConditionalBenefitDefinition(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Synthetic benefit",
                Category.CONDITIONAL_INCOME, new Trigger(TriggerType.FIXED_DATE, LocalDate.of(2032, 1, 1), null, null),
                PersonReference.PRIMARY, PaymentPattern.MONTHLY_RECURRING, new BigDecimal("2500.12345678"),
                null, null, null, null, new BigDecimal("0.0200"), EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR,
                TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION, ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH);
    }
    public static ConditionalBenefitDefinition change(ConditionalBenefitDefinition source, String field, Object value) {
        ObjectNode node = JSON.valueToTree(source);
        node.set(field, value instanceof LocalDate date
                ? com.fasterxml.jackson.databind.node.TextNode.valueOf(date.toString()) : JSON.valueToTree(value));
        try { return JSON.treeToValue(node, ConditionalBenefitDefinition.class); }
        catch (Exception failure) { throw new IllegalArgumentException("Invalid synthetic definition", failure); }
    }
    public static RetirementPlan plan(boolean couple) {
        var plan = com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.plan(couple);
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        if (couple) {
            var spouse = plan.getHousehold().getSpouse();
            spouse.setMortalityCategory(MortalityCategory.FEMALE);
            spouse.addIncomeSource(new SocialSecurityIncome("Zero spouse SS", AccountOwnership.SPOUSE,
                    spouse.getBirthDate().plusYears(67), null, BigDecimal.ZERO, 67, BigDecimal.ZERO, 2026));
        }
        return plan;
    }
}
