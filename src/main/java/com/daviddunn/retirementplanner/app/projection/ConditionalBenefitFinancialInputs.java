package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.domain.benefit.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;

/** Optional financial-input capability projection, not a new stored result/fingerprint version. */
public final class ConditionalBenefitFinancialInputs {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    private ConditionalBenefitFinancialInputs() { }

    public static void normalize(ObjectNode plan) {
        JsonNode source = plan.remove("conditionalBenefits");
        if (source == null || source.isNull() || source.isArray() && source.isEmpty()) return;
        if (!source.isArray() || source.size() > ConditionalBenefits.MAX_DEFINITIONS)
            throw new IllegalArgumentException("Invalid conditional benefit financial inputs.");
        try {
            var definitions = new ArrayList<ObjectNode>();
            var identities = new HashSet<UUID>();
            for (var entry : source) {
                var definition = JSON.treeToValue(entry, ConditionalBenefitDefinition.class);
                if (!identities.add(definition.id())) throw new IllegalArgumentException("Duplicate conditional benefit ID.");
                ObjectNode financial = JSON.valueToTree(definition);
                financial.remove(List.of("id", "label"));
                financial.set("grossAmount", DecimalNode.valueOf(definition.grossAmount().stripTrailingZeros()));
                financial.set("annualEscalationRate", DecimalNode.valueOf(definition.annualEscalationRate().stripTrailingZeros()));
                definitions.add(financial);
            }
            // Identical independent contracts remain repeated entries, even though identity and labels are excluded.
            definitions.sort(Comparator.comparing(JsonNode::toString));
            var group = plan.putObject("conditionalBenefits");
            group.put("inputContract", definitions.stream().anyMatch(d ->
                    d.path("taxCharacter").asText().equals("QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED"))
                    ? "CONDITIONAL_BENEFIT_LIFE_PRINCIPAL_INPUTS_V2" : "CONDITIONAL_BENEFIT_INPUTS_V1");
            var ordered = group.putArray("definitions");
            definitions.forEach(ordered::add);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("Invalid conditional benefit financial inputs.", invalid);
        }
    }
}
