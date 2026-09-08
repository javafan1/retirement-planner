package com.daviddunn.retirementplanner.app.socialsecurity;

import java.util.Objects;
import java.util.Set;

/** Explicit selection by one-based input position, so duplicate strategies remain distinguishable. */
public record LongevityWeightedDetailRetentionPolicy(
        boolean retainBaseline,
        Set<Integer> selectedCandidateOrders) {
    public static final int MAX_RETAINED_STRATEGIES = 20;

    public LongevityWeightedDetailRetentionPolicy {
        selectedCandidateOrders = Set.copyOf(Objects.requireNonNull(selectedCandidateOrders));
        if (selectedCandidateOrders.stream().anyMatch(order -> order < 1)) {
            throw new IllegalArgumentException("Selected candidate orders must be positive.");
        }
        if (selectedCandidateOrders.size() + (retainBaseline ? 1 : 0) > MAX_RETAINED_STRATEGIES) {
            throw new IllegalArgumentException("At most 20 strategies may retain scenario detail, including baseline.");
        }
    }

    public static LongevityWeightedDetailRetentionPolicy aggregateOnly() {
        return new LongevityWeightedDetailRetentionPolicy(false, Set.of());
    }
}
