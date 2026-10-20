package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisSnapshotCodecTest {
    final AnalysisSnapshotCodec codec = new AnalysisSnapshotCodec();
    @Test void completeCoupleRoundTripPreservesEveryFieldAndPurePresentation() throws Exception {
        var original = SnapshotFixtures.snapshot(UUID.randomUUID());
        var restored = codec.decode(codec.encode(original));
        assertEquals(original, restored);
        assertEquals(5, restored.payload().couple().entries().getFirst().aggregate().expectedPvAfterTaxEstate().scale());
        var before = new LongevityWeightedIntegratedPresentation(original.payload().couple().restore(), 7, 9);
        var after = new LongevityWeightedIntegratedPresentation(restored.payload().couple().restore(), 7, 9);
        assertEquals(LongevityWeightedHeatMapAdapter.from(before), LongevityWeightedHeatMapAdapter.from(after));
        assertEquals(before.currentPosition(), after.currentPosition());
        assertEquals(before.provenEquivalentCount(1), after.provenEquivalentCount(1));
        assertEquals(1, after.result().failedStrategyCount());
        assertEquals(List.of(1, 1), after.result().rankedSuccessfulEntries().stream().map(e -> e.rank().orElseThrow()).toList());
        assertEquals(original.context(), restored.context());
        var rows = restored.context().historicalComparisons().getFirst().rows();
        assertEquals(rows, rows.stream().map(AnalysisSnapshotPresentationMapper::restore).map(AnalysisSnapshotPresentationMapper::capture).toList());
        assertNotSame(before.result(), after.result());
    }
    @ParameterizedTest @EnumSource(SinglePersonMortalityAnalysis.Mode.class)
    void realIndividualCompletedFixtureRoundTripsExactly(SinglePersonMortalityAnalysis.Mode mode) throws Exception {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var result = SinglePersonMortalityAnalysisTest.run(plan, SinglePersonMortalityAnalysisTest.mortality(plan, "1.00"), "0.0100", mode);
        assertTrue(AnalysisSuccessPolicy.accepted(result));
        var kind = mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED ? AnalysisKind.INDIVIDUAL_WEIGHTED : AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY;
        var payload = new WeightedSnapshots.Payload(null, WeightedSnapshots.Individual.from(result));
        var context = SnapshotFixtures.individualContext(plan);
        var s = new AnalysisSnapshot(1, 1, UUID.randomUUID(), kind, kind.shape(), UUID.randomUUID(), Instant.now(), "test",
                Map.of("algorithm", "v1"), Map.of("table", "v1"), SnapshotFixtures.HASH, SnapshotFixtures.HASH,
                codec.digest(payload, context), payload, context, null);
        var copy = codec.decode(codec.encode(s));
        assertEquals(s, copy);
        var rebuilt = copy.payload().individual().restore();
        assertEquals(payload.individual(), WeightedSnapshots.Individual.from(rebuilt));
        assertEquals(result.ranked(), rebuilt.ranked());
        var frozen = IndividualReportContext.capture(plan);
        var before = IndividualAnalyzerReportAdapter.mortality(frozen, result, 67);
        var after = IndividualAnalyzerReportAdapter.mortality(frozen, rebuilt, 67);
        assertEquals(before.rankedTables(), after.rankedTables());
        assertEquals(before.assumptions(), after.assumptions());
        assertEquals(before.results(), after.results());
        assertEquals(before.visualization(), after.visualization());
    }
    @Test void wrongKindUnknownSchemaChecksumAndUnsafePreferencesAreRejected() throws Exception {
        var s = SnapshotFixtures.snapshot(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> s.payload().validate(AnalysisKind.COUPLE_EXHAUSTIVE));
        String raw = new String(codec.raw(s), java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip(raw.replace("\"schemaVersion\":1", "\"schemaVersion\":99"))));
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip(raw.replace("100.00400", "100.00900"))));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotContext("h", "p", null, Instant.now(), List.of(), List.of(), List.of(), Map.of("path", "../../escape")));
        assertThrows(java.io.IOException.class, () -> codec.decode(new byte[AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES + 1]));
    }
    static byte[] gzip(String value) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(out)) { gzip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        return out.toByteArray();
    }
    @Test void completeSnapshotRejectsUnexpectedTrailingJson() throws Exception {
        String raw = new String(codec.raw(SnapshotFixtures.snapshot(UUID.randomUUID())),
                java.nio.charset.StandardCharsets.UTF_8);
        for (String trailing : List.of(" {}", " []", " null", " 42")) {
            var failure = assertThrows(com.fasterxml.jackson.databind.exc.MismatchedInputException.class,
                    () -> codec.decode(gzip(raw + trailing)));
            assertTrue(failure.getMessage().contains("Trailing token"));
        }
        // Whitespace is valid after the enclosing document, and its nested payload remains readable.
        assertEquals(codec.decode(gzip(raw)), codec.decode(gzip(raw + " \r\n\t")));
    }
    @Test void compressedBombAndExcessiveNestingAreRejected() throws Exception {
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip("[".repeat(90) + "0" + "]".repeat(90))));
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip("x".repeat(AnalysisSnapshotCodec.MAX_RAW_BYTES + 1))));
    }
    @Test void sharedScalarRecordSchemaCannotSilentlyGrow() {
        var schemas = Map.of(
                LongevityWeightedStrategyAggregate.class, "expectedPvAfterTaxEstate,expectedNominalEstateAtSecondDeath,expectedInvestableAssetsAtSecondDeath,minimumNominalScenarioEstate,maximumNominalScenarioEstate,totalEvaluatedProbability,originalScenarioCount,actualProjectionRunCount",
                LongevityWeightedIntegratedStrategyComparisonResult.Metadata.class, "methodologyVersion,methodologyReference,longevityAssumptions,valuationDate,generalInflationRate,realDiscountRate,originalScenarioCount,originalScenarioProbability,financialLimitations",
                LongevityWeightedIntegratedStrategyComparisonResult.Work.class, "stageFourEvaluations,scenarioEvaluations,completedScenarioEvaluations,projectionEngineRuns,completedProjectionEngineRuns,continuationEvaluations,continuation",
                SinglePersonMortalityAnalysis.Outcome.class, "deathYear,probability,nominalEstate,investableAssets,presentValue");
        schemas.forEach((type, names) -> assertEquals(names, String.join(",", Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).toList()),
                "A shared leaf schema changed: explicitly revise the storage codec/version first."));
    }
}
