package com.daviddunn.retirementplanner.app.analysis.parameters;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisParameterConfigurationsTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void exactTypedRoundTripForEveryIdentity(AnalysisKind kind) throws Exception {
        var configuration = ParameterFixtures.configuration(kind);
        var configurations = AnalysisParameterConfigurations.empty().with(configuration);
        var restored = mapper.readValue(mapper.writeValueAsBytes(configurations), AnalysisParameterConfigurations.class);
        assertEquals(configuration, restored.get(kind).orElseThrow());
        assertEquals(configurations.json(), restored.json());
    }
    @Test void allFifteenSlotsRemainIndependentAndCollectionIsImmutable() {
        var configurations = AnalysisParameterConfigurations.empty();
        for (var kind : AnalysisKind.values()) configurations = configurations.with(ParameterFixtures.configuration(kind));
        for (var kind : AnalysisKind.values()) assertEquals(kind, configurations.get(kind).orElseThrow().kind());
        var selected = configurations.get(AnalysisKind.COUPLE_QUICK).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> selected.provenance().put("draft", "bad"));
    }
    @Test void missingLegacySectionAndUnknownFutureDataSurviveManualSerialization() throws Exception {
        var plan = RetirementPlanFactory.createSinglePersonPlan();
        assertNull(mapper.readValue(mapper.writeValueAsBytes(plan), RetirementPlan.class).getAnalyzerConfigurations());
        var future = AnalysisParameterConfigurations.parse("{\"schemaVersion\":2,\"future\":{\"value\":1.2300}}");
        plan.setAnalyzerConfigurations(future);
        var restored = mapper.readValue(mapper.writeValueAsBytes(plan), RetirementPlan.class).getAnalyzerConfigurations();
        assertFalse(restored.supported()); assertTrue(restored.json().contains("1.2300"));
        assertThrows(IllegalStateException.class, () -> restored.with(ParameterFixtures.configuration(AnalysisKind.INDIVIDUAL_WEIGHTED)));
    }
    @Test void unknownEntriesAndContainerMetadataRetainExactDecimalsWhileKnownEntryChanges() {
        var source = AnalysisParameterConfigurations.parse("{\"schemaVersion\":1,\"note\":null,\"configurations\":{\"FUTURE_KIND\":{\"configurationVersion\":9,\"number\":1.2300}}}");
        var updated = source.with(ParameterFixtures.configuration(AnalysisKind.COUPLE_QUICK));
        assertTrue(updated.json().contains("1.2300")); assertTrue(updated.json().contains("\"note\":null"));
        var knownFuture = AnalysisParameterConfigurations.parse("{\"schemaVersion\":1,\"configurations\":{\"COUPLE_QUICK\":{\"configurationVersion\":2}}}");
        assertThrows(IllegalStateException.class, () -> knownFuture.with(ParameterFixtures.configuration(AnalysisKind.COUPLE_QUICK)));
    }
    @Test void malformedOversizedDuplicatedAndTrailingDocumentsAreRejected() {
        for (String input : new String[]{"null", "[]", "{}", "{\"schemaVersion\":0}",
                "{\"schemaVersion\":1,\"configurations\":null}",
                "{\"schemaVersion\":2,\"schemaVersion\":2}",
                "{\"schemaVersion\":2} {}", "{\"schemaVersion\":2,\"x\":\"" + "x".repeat(2049) + "\"}"}) {
            assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(input));
        }
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(" ".repeat(65_537)));
        String valid = AnalysisParameterConfigurations.empty().with(ParameterFixtures.configuration(AnalysisKind.COUPLE_QUICK)).json();
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(valid.replace("\"candidateCount\":10", "\"candidateCount\":21")));
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(valid.replace("\"shape\":\"COUPLE\"", "\"shape\":\"SINGLE\"")));
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(valid.replace("\"candidateCount\":10", "\"candidateCount\":10,\"unexpected\":1")));
    }
    @Test void optionalAbsentNullAndUnavailableAreDistinctWithoutParsingInvalidText() {
        var absent = new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.ABSENT, null, null, null);
        var explicitNull = new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.NULL, null, null, null);
        var unavailable = new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.UNAVAILABLE, "6x", "Analyzer", null);
        assertNotEquals(absent, explicitNull); assertNotEquals(explicitNull, unavailable); assertNull(unavailable.age());
        assertThrows(IllegalArgumentException.class, () -> new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.UNAVAILABLE, "6x", "Analyzer", 67));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.VALUE, "6x", "Analyzer", 67));
        assertEquals(10_000, new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.VALUE, "10000", "Analyzer", 10_000).age());
    }
    @Test void nestedValueDoesNotConsumeEnclosingFieldsAndExplicitNullIsRejected() throws Exception {
        var source = mapper.writeValueAsString(RetirementPlanFactory.createSinglePersonPlan());
        var with = source.substring(0, source.length() - 1) + ",\"analyzerConfigurations\":{\"schemaVersion\":1,\"configurations\":{}}}";
        assertNotNull(mapper.readValue(with, RetirementPlan.class).getAnalyzerConfigurations());
        assertThrows(Exception.class, () -> mapper.readValue(with.replace("{\"schemaVersion\":1,\"configurations\":{}}", "null"), RetirementPlan.class));
    }
    @Test void structuralLimitsMissingValuesAndWrongParameterFamiliesAreRejected() {
        var fields = new java.util.ArrayList<String>();
        for (int i = 0; i < 33; i++) fields.add("\"FUTURE_" + i + "\":{\"configurationVersion\":2}");
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(
                "{\"schemaVersion\":1,\"configurations\":{" + String.join(",", fields) + "}}"));
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(
                "{\"schemaVersion\":2,\"deep\":" + "[".repeat(13) + "0" + "]".repeat(13) + "}"));
        var quick = ParameterFixtures.configuration(AnalysisKind.COUPLE_QUICK);
        String valid = AnalysisParameterConfigurations.empty().with(quick).json();
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(valid.replace("\"candidateCount\":10,", "")));
        assertThrows(IllegalArgumentException.class, () -> AnalysisParameterConfigurations.parse(valid.replace("\"candidateCount\":10", "\"candidateCount\":null")));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisParameterConfiguration(1, AnalysisKind.COUPLE_EXHAUSTIVE,
                AnalysisKind.Shape.COUPLE, quick.acceptedRunId(), quick.acceptedAt(), quick.parameters(), java.util.Map.of()));
    }
}
