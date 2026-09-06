package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidateGenerator;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Immutable search universe and result-retention settings. */
public record IntegratedSocialSecurityCompleteStrategySearchRequest(
        RetirementPlan plan,
        List<Integer> primaryRetirementAges,
        List<Integer> spouseRetirementAges,
        List<SocialSecuritySurvivorClaimingCandidate> primarySurvivorCandidates,
        List<SocialSecuritySurvivorClaimingCandidate> spouseSurvivorCandidates,
        IntegratedStrategyRankingMeasure rankingMeasure,
        int detailRetentionCount) {

    public static final int DEFAULT_DETAIL_RETENTION_COUNT = 20;
    private static final List<Integer> STANDARD_AGES =
            IntStream.rangeClosed(62, 70).boxed().toList();

    public IntegratedSocialSecurityCompleteStrategySearchRequest {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        primaryRetirementAges = validateAges(primaryRetirementAges, "Primary");
        spouseRetirementAges = validateAges(spouseRetirementAges, "Spouse");
        primarySurvivorCandidates = validateCandidates(
                primarySurvivorCandidates, "Primary");
        spouseSurvivorCandidates = validateCandidates(
                spouseSurvivorCandidates, "Spouse");
        Objects.requireNonNull(rankingMeasure, "Ranking measure is required.");
        if (detailRetentionCount < 0) {
            throw new IllegalArgumentException("Detail retention count cannot be negative.");
        }
    }

    public static IntegratedSocialSecurityCompleteStrategySearchRequest standard(
            RetirementPlan plan) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Person primary = plan.getHousehold().getPrimaryPerson();
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
        return Math.multiplyExact(
                Math.multiplyExact(primaryRetirementAges.size(), spouseRetirementAges.size()),
                Math.multiplyExact(primarySurvivorCandidates.size(),
                        spouseSurvivorCandidates.size()));
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
