package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** V1 retains its original five required properties. V2 adds one required evidence property. */
public final class ArkansasAnalysisProvenanceReader extends JsonDeserializer<ArkansasAnalysisProvenance> {
    private static final Set<String> V1 = Set.of("version", "jurisdiction", "dependencies", "policies", "weightedPensions");

    @Override
    public ArkansasAnalysisProvenance deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            return (ArkansasAnalysisProvenance) context.handleUnexpectedToken(ArkansasAnalysisProvenance.class, parser);
        }
        var seen = new HashSet<String>();
        int version = 0;
        StateTaxJurisdiction jurisdiction = null;
        List<ArkansasAnalysisProvenance.Dependency> dependencies = null;
        Map<String, String> policies = null;
        List<ArkansasAnalysisProvenance.WeightedPension> pensions = null;
        ArkansasAnalysisEvidenceV2 evidence = null;
        var types = context.getTypeFactory();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME)) throw new IOException("Arkansas evidence property name required.");
            String name = parser.currentName();
            parser.nextToken();
            if (!seen.add(name) || parser.hasToken(JsonToken.VALUE_NULL)) {
                throw new IOException("Duplicate or null Arkansas evidence property.");
            }
            switch (name) {
                case "version" -> {
                    version = context.readValue(parser, int.class);
                    if (version != 1 && version != 2) throw new IOException("Unknown Arkansas analyzer provenance version.");
                }
                case "jurisdiction" -> jurisdiction = context.readValue(parser, StateTaxJurisdiction.class);
                case "dependencies" -> dependencies = context.readValue(parser,
                        types.constructCollectionType(List.class, ArkansasAnalysisProvenance.Dependency.class));
                case "policies" -> policies = context.readValue(parser, types.constructMapType(Map.class, String.class, String.class));
                case "weightedPensions" -> pensions = context.readValue(parser,
                        types.constructCollectionType(List.class, ArkansasAnalysisProvenance.WeightedPension.class));
                case "executionEvidence" -> evidence = context.readValue(parser, ArkansasAnalysisEvidenceV2.class);
                default -> throw new IOException("Unknown Arkansas evidence property.");
            }
        }
        if (!seen.containsAll(V1) || (version == 2) != seen.contains("executionEvidence")) {
            throw new IOException("Missing or incompatible Arkansas evidence property.");
        }
        return new ArkansasAnalysisProvenance(version, jurisdiction, dependencies, policies, pensions, evidence);
    }
}
