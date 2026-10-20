package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchResult;
import java.util.IdentityHashMap;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the independent Gate 1 private-field/getter/identity oracle across test packages. */
public final class ExhaustivePdfFidelitySupport {
    private ExhaustivePdfFidelitySupport() { }

    public static void assertExact(IntegratedSocialSecurityCompleteStrategySearchResult original,
            IntegratedSocialSecurityCompleteStrategySearchResult restored) throws Exception {
        assertEquals(original.rankingMeasure(), restored.rankingMeasure());
        assertEquals(original.currentPlanRank(), restored.currentPlanRank());
        assertEquals(original.entries().size(), restored.entries().size());
        assertEquals(original.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList(),
                restored.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList());
        RetainedProjectionCodecTest.assertGraph(original.currentPlanBaseline(), restored.currentPlanBaseline(), new IdentityHashMap<>(), "baseline");
        RetainedProjectionCodecTest.assertYearGetters(original.currentPlanBaseline().projection(), restored.currentPlanBaseline().projection());
        for (int i = 0; i < original.entries().size(); i++) {
            var a = original.entries().get(i); var b = restored.entries().get(i);
            assertEquals(a.generationOrder(), b.generationOrder()); assertEquals(a.strategy(), b.strategy());
            assertEquals(a.afterTaxEstateRank(), b.afterTaxEstateRank()); assertEquals(a.metrics(), b.metrics());
            assertEquals(a.differencesFromCurrentPlan(), b.differencesFromCurrentPlan()); assertEquals(a.failure(), b.failure());
            var before = original.entries().get(i).retainedDetail();
            var after = restored.entries().get(i).retainedDetail();
            RetainedProjectionCodecTest.assertGraph(before, after, new IdentityHashMap<>(), "detail-" + i);
            if (before.isPresent()) RetainedProjectionCodecTest.assertYearGetters(before.orElseThrow().projection(), after.orElseThrow().projection());
        }
    }
}
