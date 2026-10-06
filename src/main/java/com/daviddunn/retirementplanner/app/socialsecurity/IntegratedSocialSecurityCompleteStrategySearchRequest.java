package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidateGenerator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.time.Year;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import java.util.stream.IntStream;

/** Immutable search universe and result-retention settings. */
public record IntegratedSocialSecurityCompleteStrategySearchRequest(
        RetirementPlan plan,
        List<Integer> primaryRetirementAges,
        List<Integer> spouseRetirementAges,
        List<SocialSecuritySurvivorClaimingCandidate> primarySurvivorCandidates,
        List<SocialSecuritySurvivorClaimingCandidate> spouseSurvivorCandidates,
        IntegratedStrategyRankingMeasure rankingMeasure,
        int detailRetentionCount,
        Optional<Year> primaryDeathYear) {

    public static final int DEFAULT_DETAIL_RETENTION_COUNT = 20;
    private static final List<Integer> STANDARD_AGES =
            IntStream.rangeClosed(62, 70).boxed().toList();

    public IntegratedSocialSecurityCompleteStrategySearchRequest {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        primaryRetirementAges = validateAges(primaryRetirementAges, "Primary");
        Objects.requireNonNull(primaryDeathYear);
        if (!plan.getHousehold().hasSpouse()) {
            spouseRetirementAges = List.copyOf(spouseRetirementAges);
            primarySurvivorCandidates = List.copyOf(primarySurvivorCandidates);
            spouseSurvivorCandidates = List.copyOf(spouseSurvivorCandidates);
            if (!spouseRetirementAges.isEmpty() || !primarySurvivorCandidates.isEmpty() || !spouseSurvivorCandidates.isEmpty()) {
                throw new IllegalArgumentException("Single-person search has no spouse or survivor candidates.");
            }
            primaryDeathYear.ifPresent(year -> {
                if (year.getValue() < plan.getPlanningAssumptions().getProjectionStartDate().getYear()) {
                    throw new IllegalArgumentException("Primary death year cannot precede the projection start year.");
                }
            });
        } else {
            if (primaryDeathYear.isPresent()) throw new IllegalArgumentException("Use the couple's configured death scenario.");
            spouseRetirementAges = validateAges(spouseRetirementAges, "Spouse");
            primarySurvivorCandidates = validateCandidates(
                primarySurvivorCandidates, "Primary");
            spouseSurvivorCandidates = validateCandidates(
                spouseSurvivorCandidates, "Spouse");
        }
        Objects.requireNonNull(rankingMeasure, "Ranking measure is required.");
        if (detailRetentionCount < 0) {
            throw new IllegalArgumentException("Detail retention count cannot be negative.");
        }
    }

    public IntegratedSocialSecurityCompleteStrategySearchRequest(RetirementPlan plan,
            List<Integer> primaryAges, List<Integer> spouseAges,
            List<SocialSecuritySurvivorClaimingCandidate> primarySurvivors,
            List<SocialSecuritySurvivorClaimingCandidate> spouseSurvivors,
            IntegratedStrategyRankingMeasure measure, int retained) {
        this(plan, primaryAges, spouseAges, primarySurvivors, spouseSurvivors, measure, retained, Optional.empty());
    }

    public static IntegratedSocialSecurityCompleteStrategySearchRequest standard(
            RetirementPlan plan) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Person primary = plan.getHousehold().getPrimaryPerson();
        if (!plan.getHousehold().hasSpouse()) {
            return new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, STANDARD_AGES,
                    List.of(), List.of(), List.of(), IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,
                    DEFAULT_DETAIL_RETENTION_COUNT);
        }
        Person spouse = plan.getHousehold().getSpouse();
        if (primary == null || spouse == null) {
            throw new IllegalArgumentException(
                    "Complete integrated Social Security search requires primary and spouse.");
        }
        SocialSecuritySurvivorClaimingCandidateGenerator generator =
                new SocialSecuritySurvivorClaimingCandidateGenerator();
        return new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan,
                STANDARD_AGES,
                STANDARD_AGES,
                generator.generate(primary.getBirthDate()),
                generator.generate(spouse.getBirthDate()),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,
                DEFAULT_DETAIL_RETENTION_COUNT);
    }

    public int strategyCount() {
        if (!plan.getHousehold().hasSpouse()) return primaryRetirementAges.size();
        return Math.multiplyExact(
                Math.multiplyExact(primaryRetirementAges.size(), spouseRetirementAges.size()),
                Math.multiplyExact(primarySurvivorCandidates.size(),
                        spouseSurvivorCandidates.size()));
    }

    /** Ordered real elections only. Couple generation order remains age/age/survivor/survivor. */
    public List<SocialSecurityHouseholdClaimingStrategy> strategies() {
        var results = new ArrayList<SocialSecurityHouseholdClaimingStrategy>();
        var primary = plan.getHousehold().getPrimaryPerson();
        for (int primaryAge : primaryRetirementAges) {
            var date = SocialSecurityRetirementDateCalculator
                    .calculateRetirementClaimDate(primary.getBirthDate(), primaryAge);
            if (!plan.getHousehold().hasSpouse()) {
                results.add(SocialSecurityHouseholdClaimingStrategy.primaryOnly(primaryAge, date));
            } else {
                for (int spouseAge : spouseRetirementAges) {
                    var spouseDate = SocialSecurityRetirementDateCalculator
                            .calculateRetirementClaimDate(plan.getHousehold().getSpouse().getBirthDate(), spouseAge);
                    for (var primarySurvivor : primarySurvivorCandidates) for (var spouseSurvivor : spouseSurvivorCandidates) {
                        results.add(new SocialSecurityHouseholdClaimingStrategy(
                                primaryAge, spouseAge, date, spouseDate, primarySurvivor, spouseSurvivor));
                    }
                }
            }
        }
        return List.copyOf(results);
    }

    private static List<Integer> validateAges(List<Integer> ages, String owner) {
        List<Integer> values = List.copyOf(Objects.requireNonNull(ages));
        if (values.isEmpty() || values.stream().anyMatch(
                age -> age == null || age < 62 || age > 70)) {
            throw new IllegalArgumentException(
                    owner + " retirement ages must contain values from 62 through 70.");
        }
        if (values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException(owner + " retirement ages cannot contain duplicates.");
        }
        return values;
    }

    private static List<SocialSecuritySurvivorClaimingCandidate> validateCandidates(
            List<SocialSecuritySurvivorClaimingCandidate> candidates,
            String owner) {
        List<SocialSecuritySurvivorClaimingCandidate> values = List.copyOf(
                Objects.requireNonNull(candidates));
        if (values.isEmpty()) {
            throw new IllegalArgumentException(owner + " survivor candidates cannot be empty.");
        }
        if (values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException(owner + " survivor candidates cannot contain duplicates.");
        }
        return values;
    }
}
