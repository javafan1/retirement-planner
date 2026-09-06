package com.daviddunn.retirementplanner.ui.socialsecurity;

import java.util.List;
import java.util.Objects;

/** Selects the first N analyzer-ordered complete strategies, retaining cutoff ties. */
public final class IntegratedSocialSecurityCandidateSelector {

    public List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> select(
            List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> ranked,
            int requestedCount) {
        List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> candidates =
                List.copyOf(Objects.requireNonNull(ranked, "Ranked strategies are required."));
        if (requestedCount < 1 || requestedCount > 20) {
            throw new IllegalArgumentException(
                    "Integrated candidate count must be between 1 and 20.");
        }
        if (candidates.size() <= requestedCount) {
            return candidates;
        }
        int cutoffRank = candidates.get(requestedCount - 1).rank();
        return candidates.stream()
                .takeWhile(candidate -> candidate.rank() <= cutoffRank)
                .toList();
    }
}
