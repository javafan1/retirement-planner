package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Baseline, generation-ordered entries, and a metric-specific deterministic ranking. */
public record IntegratedSocialSecurityCompleteStrategySearchResult(
        IntegratedSocialSecurityStrategyResult currentPlanBaseline,
        IntegratedStrategyRankingMeasure rankingMeasure,
        List<IntegratedSocialSecurityCompleteStrategySearchEntry> entries,
        List<IntegratedSocialSecurityCompleteStrategySearchEntry> rankedSuccessfulEntries,
        OptionalInt currentPlanRank) {

    public IntegratedSocialSecurityCompleteStrategySearchResult {
        Objects.requireNonNull(currentPlanBaseline);
        Objects.requireNonNull(rankingMeasure);
        entries = List.copyOf(Objects.requireNonNull(entries));
        rankedSuccessfulEntries = List.copyOf(Objects.requireNonNull(rankedSuccessfulEntries));
        currentPlanRank = Objects.requireNonNull(currentPlanRank);
    }

    public int totalStrategyCount() {
        return entries.size();
    }

    public long successfulStrategyCount() {
        return rankedSuccessfulEntries.size();
    }

    public long failedStrategyCount() {
        return entries.size() - successfulStrategyCount();
    }

    public List<IntegratedSocialSecurityCompleteStrategySearchEntry> top(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Top count cannot be negative.");
        }
        return rankedSuccessfulEntries.subList(
                0, Math.min(count, rankedSuccessfulEntries.size()));
    }

    public Optional<IntegratedSocialSecurityCompleteStrategySearchEntry> entryFor(
            SocialSecurityHouseholdClaimingStrategy strategy) {
        Objects.requireNonNull(strategy);
        return entries.stream().filter(entry -> sameElections(entry.strategy(), strategy)).findFirst();
    }

    public OptionalInt rankOf(SocialSecurityHouseholdClaimingStrategy strategy) {
        return entryFor(strategy).map(IntegratedSocialSecurityCompleteStrategySearchEntry
                        ::afterTaxEstateRank)
                .orElseGet(OptionalInt::empty);
    }

    private static boolean sameElections(
            SocialSecurityHouseholdClaimingStrategy first,
            SocialSecurityHouseholdClaimingStrategy second) {
        return first.primaryRetirementAge() == second.primaryRetirementAge()
                && first.spouseRetirementAge() == second.spouseRetirementAge()
                && first.primaryRetirementClaimDate().equals(
                        second.primaryRetirementClaimDate())
                && first.spouseRetirementClaimDate().equals(
                        second.spouseRetirementClaimDate())
                && first.primarySurvivorElection().claimDate().equals(
                        second.primarySurvivorElection().claimDate())
                && first.spouseSurvivorElection().claimDate().equals(
                        second.spouseSurvivorElection().claimDate());
    }
}
