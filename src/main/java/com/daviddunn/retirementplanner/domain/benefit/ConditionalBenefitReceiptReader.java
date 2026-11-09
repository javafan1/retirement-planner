package com.daviddunn.retirementplanner.domain.benefit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

/** Only the new optional annuity evidence may be absent; old receipt fields remain required. */
public final class ConditionalBenefitReceiptReader extends JsonDeserializer<ConditionalBenefitReceipt> {
    private static final ObjectReader TREE = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true)).reader();
    @Override public ConditionalBenefitReceipt deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = TREE.readTree(parser);
        var required = Set.of("benefitId", "label", "category", "trigger", "recipient", "pattern", "eventDate",
                "firstEligibleDate", "paymentStart", "paymentEnd", "months", "year", "gross", "taxable", "excluded", "taxCharacter", "destination");
        if (!node.isObject()) throw JsonMappingException.from(parser, "Receipt object required.");
        var keys = new HashSet<String>(); node.fieldNames().forEachRemaining(keys::add);
        if (!keys.containsAll(required) || keys.stream().anyMatch(k -> !required.contains(k) && !k.equals("annuityBasis")))
            throw JsonMappingException.from(parser, "Missing or unknown conditional receipt field.");
        return new ConditionalBenefitReceipt(v(context,node,"benefitId",UUID.class),v(context,node,"label",String.class),
                v(context,node,"category",Category.class),v(context,node,"trigger",Trigger.class),v(context,node,"recipient",PersonReference.class),
                v(context,node,"pattern",PaymentPattern.class),v(context,node,"eventDate",LocalDate.class),v(context,node,"firstEligibleDate",LocalDate.class),
                v(context,node,"paymentStart",LocalDate.class),v(context,node,"paymentEnd",LocalDate.class),v(context,node,"months",Integer.class),
                v(context,node,"year",Integer.class),v(context,node,"gross",BigDecimal.class),v(context,node,"taxable",BigDecimal.class),
                v(context,node,"excluded",BigDecimal.class),v(context,node,"taxCharacter",TaxCharacter.class),v(context,node,"destination",ReceiptDestination.class),
                v(context,node,"annuityBasis",AnnuityBasisRecovery.class));
    }
    private static <T> T v(DeserializationContext context, JsonNode node, String name, Class<T> type) throws IOException {
        return node.hasNonNull(name) ? context.readTreeAsValue(node.get(name), type) : null;
    }
}
