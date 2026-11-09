package com.daviddunn.retirementplanner.domain.benefit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

/** Local closed schema; does not relax the application's other Jackson contracts. */
public final class ConditionalBenefitDefinitionDeserializer extends JsonDeserializer<ConditionalBenefitDefinition> {
    private static final ObjectReader TREE = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true)).reader();
    @Override public ConditionalBenefitDefinition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        try {
            JsonNode node = TREE.readTree(parser);
            object(node, Set.of("id", "label", "category", "trigger", "recipient", "paymentPattern", "grossAmount",
                    "notBefore", "endDate", "coverageStart", "coverageEnd", "annualEscalationRate", "escalationOrigin", "taxCharacter", "destination", "lifeInsuranceQualification", "annuityTerms"));
            String identity = text(node, "id");
            UUID id = UUID.fromString(identity);
            if (!id.toString().equals(identity)) throw new IllegalArgumentException("Canonical benefit ID required.");
            JsonNode trigger = node.get("trigger");
            object(trigger, Set.of("type", "date", "person", "age"));
            var type = TriggerType.valueOf(text(trigger, "type"));
            var person = optional(trigger, "person") ? null : PersonReference.valueOf(text(trigger, "person"));
            Integer age = null;
            if (!optional(trigger, "age")) {
                var value = trigger.get("age");
                if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new IllegalArgumentException("Integer age required.");
                age = value.intValue();
            }
            return new ConditionalBenefitDefinition(id, text(node, "label"), Category.valueOf(text(node, "category")),
                    new Trigger(type, date(trigger, "date"), person, age), PersonReference.valueOf(text(node, "recipient")),
                    PaymentPattern.valueOf(text(node, "paymentPattern")), number(node, "grossAmount"), date(node, "notBefore"),
                    date(node, "endDate"), date(node, "coverageStart"), date(node, "coverageEnd"), number(node, "annualEscalationRate"),
                    EscalationOrigin.valueOf(text(node, "escalationOrigin")), TaxCharacter.valueOf(text(node, "taxCharacter")),
                    ReceiptDestination.valueOf(text(node, "destination")), optional(node, "lifeInsuranceQualification") ? null
                            : LifeInsuranceQualification.valueOf(text(node, "lifeInsuranceQualification")), annuity(node));
        } catch (RuntimeException invalid) {
            throw JsonMappingException.from(parser, "Invalid conditional benefit definition.", invalid);
        }
    }

    private static void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Benefit object required.");
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown benefit property."); });
    }
    private static boolean optional(JsonNode node, String field) { return !node.hasNonNull(field); }
    private static NonqualifiedAnnuityTerms annuity(JsonNode node) {
        if (optional(node, "annuityTerms")) return null;
        var facts = node.get("annuityTerms");
        object(facts, Set.of("acquisitionDate", "taxAnnuityStartingDate", "excludedPerPayment", "originalInvestment",
                "openingInvestment", "basisAsOf", "qualification"));
        return new NonqualifiedAnnuityTerms(date(facts, "acquisitionDate"), date(facts, "taxAnnuityStartingDate"),
                number(facts, "excludedPerPayment"), number(facts, "originalInvestment"), number(facts, "openingInvestment"),
                date(facts, "basisAsOf"), NonqualifiedAnnuityTerms.Qualification.valueOf(text(facts, "qualification")));
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Required text property: " + field);
        return value.textValue();
    }
    private static BigDecimal number(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isNumber() || value.asText().length() > 64)
            throw new IllegalArgumentException("Required bounded numeric property: " + field);
        return value.decimalValue();
    }
    private static LocalDate date(JsonNode node, String field) {
        return optional(node, field) ? null : LocalDate.parse(text(node, field));
    }
}
