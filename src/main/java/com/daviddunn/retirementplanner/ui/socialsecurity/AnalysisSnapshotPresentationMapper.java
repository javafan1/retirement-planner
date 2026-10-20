package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.util.*;

/** Pure optional/rank mapping for historical comparisons. No controls, live plans or calculation engines. */
final class AnalysisSnapshotPresentationMapper {
    private AnalysisSnapshotPresentationMapper() { }
    static java.util.function.Function<com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy, OptionalInt>
            ranks(List<SnapshotContext.ComparisonRow> rows) {
        Map<List<Object>, Integer> values = new HashMap<>();
        rows.stream().filter(row -> row.deterministicRank() != null).forEach(row ->
                values.put(LongevityWeightedIntegratedPresentation.identity(row.strategy().restore()), row.deterministicRank()));
        var frozen = Map.copyOf(values);
        return strategy -> {
            var rank = frozen.get(LongevityWeightedIntegratedPresentation.identity(strategy));
            return rank == null ? OptionalInt.empty() : OptionalInt.of(rank);
        };
    }
    static SnapshotContext.ComparisonRow capture(IntegratedAnalysisComparisonPresentation.Row row) {
        return new SnapshotContext.ComparisonRow(row.role(), WeightedSnapshots.Strategy.from(row.strategy()),
                row.deterministicRank().isPresent() ? row.deterministicRank().getAsInt() : null,
                row.weightedRank().isPresent() ? row.weightedRank().getAsInt() : null,
                row.deterministicEstate().orElse(null), row.weightedPv().orElse(null), row.deterministicInvestableAssets().orElse(null),
                row.weightedInvestableAssets().orElse(null), row.weightedHeirValue().orElse(null));
    }
    static IntegratedAnalysisComparisonPresentation.Row restore(SnapshotContext.ComparisonRow row) {
        return new IntegratedAnalysisComparisonPresentation.Row(row.role(), row.strategy().restore(),
                row.deterministicRank() == null ? OptionalInt.empty() : OptionalInt.of(row.deterministicRank()),
                row.weightedRank() == null ? OptionalInt.empty() : OptionalInt.of(row.weightedRank()),
                Optional.ofNullable(row.deterministicEstate()), Optional.ofNullable(row.weightedPv()),
                Optional.ofNullable(row.deterministicInvestableAssets()), Optional.ofNullable(row.weightedInvestableAssets()),
                Optional.ofNullable(row.weightedHeirValue()));
    }
}
