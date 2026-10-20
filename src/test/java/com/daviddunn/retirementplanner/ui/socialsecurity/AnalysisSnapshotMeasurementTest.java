package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisSnapshotMeasurementTest {
    @Test void measureImplementedPayloadFamiliesWithoutProductionSearch() throws Exception {
        var samples = new ArrayList<AnalysisSnapshot>();
        samples.add(SnapshotFixtures.snapshot(UUID.randomUUID()));
        samples.add(SnapshotFixtures.fullUniverseDtoOnly());
        var plan = SinglePersonMortalityAnalysisTest.plan();
        for (var mode : SinglePersonMortalityAnalysis.Mode.values()) {
            var result = SinglePersonMortalityAnalysisTest.run(plan, SinglePersonMortalityAnalysisTest.mortality(plan, "1.00"), "0.0100", mode);
            var kind = mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED ? AnalysisKind.INDIVIDUAL_WEIGHTED : AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY;
            var payload = new WeightedSnapshots.Payload(null, WeightedSnapshots.Individual.from(result));
            var context = SnapshotFixtures.individualContext(plan); var codec = new AnalysisSnapshotCodec();
            samples.add(new AnalysisSnapshot(1, 1, UUID.randomUUID(), kind, kind.shape(), UUID.randomUUID(), Instant.now(), "measurement",
                    Map.of("algorithm", "v1"), Map.of("table", "fixture"), SnapshotFixtures.HASH, SnapshotFixtures.HASH,
                    codec.digest(payload, context), payload, context, null));
        }
        System.out.println("SNAPSHOT_ENV jdk=" + System.getProperty("java.runtime.version") + " os=" + System.getProperty("os.name")
                + " cpu=" + System.getenv("PROCESSOR_IDENTIFIER") + " processors=" + Runtime.getRuntime().availableProcessors()
                + " maxHeap=" + Runtime.getRuntime().maxMemory());
        for (var sample : samples) {
            var codec = new AnalysisSnapshotCodec();
            for (int i = 0; i < 3; i++) codec.decode(codec.encode(sample));
            long encode = 0, decode = 0, restore = 0, presentation = 0;
            long usedBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            for (int i = 0; i < 10; i++) {
                long t = System.nanoTime(); var bytes = codec.encode(sample); encode += System.nanoTime() - t;
                t = System.nanoTime(); var copy = codec.decode(bytes); decode += System.nanoTime() - t;
                t = System.nanoTime();
                if (copy.payload().couple() != null) {
                    var result = copy.payload().couple().restore(); restore += System.nanoTime() - t;
                    t = System.nanoTime(); LongevityWeightedHeatMapAdapter.from(new LongevityWeightedIntegratedPresentation(result, 1, 1));
                    presentation += System.nanoTime() - t;
                } else {
                    var result = copy.payload().individual().restore(); restore += System.nanoTime() - t;
                    t = System.nanoTime(); result.ranked(); presentation += System.nanoTime() - t;
                }
                assertEquals(sample, copy);
            }
            long usedAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            int strategies = sample.payload().couple() != null ? sample.payload().couple().entries().size() : sample.payload().individual().entries().size();
            int outcomes = sample.payload().individual() == null
                    ? sample.payload().couple().entries().stream().mapToInt(e -> e.details() == null ? 0 : e.details().size()).sum()
                    : sample.payload().individual().entries().stream().mapToInt(e -> e.outcomes().size()).sum();
            System.out.printf(Locale.ROOT, "SNAPSHOT_MEASURE kind=%s fixture=%s strategies=%d retainedProjectionYears=0 outcomes=%d raw=%d gzip=%d encodeMs=%.3f decodeMs=%.3f restoreMs=%.3f presentationMs=%.3f heapDelta=%d%n",
                    sample.kind(), sample.applicationBuild(), strategies, outcomes, codec.raw(sample).length, codec.encode(sample).length,
                    encode / 10_000_000.0, decode / 10_000_000.0, restore / 10_000_000.0, presentation / 10_000_000.0, usedAfter - usedBefore);
        }
    }
}
