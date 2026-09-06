package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Immutable inputs for a deterministic integrated retirement claiming grid. */
public record IntegratedRetirementClaimingGridRequest(
        RetirementPlan plan,
        List<Integer> primaryRetirementAges,
        List<Integer> spouseRetirementAges,
        IntegratedRetirementClaimingGridSurvivorPolicy survivorPolicy) {

    private static final List<Integer> DEFAULT_AGES =
            IntStream.rangeClosed(62, 70).boxed().toList();

    public IntegratedRetirementClaimingGridRequest {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        primaryRetirementAges = validatedAges(primaryRetirementAges, "Primary");
        spouseRetirementAges = validatedAges(spouseRetirementAges, "Spouse");
        Objects.requireNonNull(survivorPolicy, "Fixed survivor policy is required.");
    }

    public static IntegratedRetirementClaimingGridRequest standard(
            RetirementPlan plan,
            IntegratedRetirementClaimingGridSurvivorPolicy survivorPolicy) {
        return new IntegratedRetirementClaimingGridRequest(
                plan, DEFAULT_AGES, DEFAULT_AGES, survivorPolicy);
    }

    private static List<Integer> validatedAges(List<Integer> ages, String owner) {
        List<Integer> values = List.copyOf(Objects.requireNonNull(
                ages, owner + " retirement ages are required."));
        if (values.isEmpty()) {
            throw new IllegalArgumentException(owner + " retirement ages cannot be empty.");
        }
        if (values.stream().anyMatch(age -> age == null || age < 62 || age > 70)) {
            throw new IllegalArgumentException(
                    owner + " retirement ages must be between 62 and 70.");
        }
        if (values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException(owner + " retirement ages cannot contain duplicates.");
        }
        return values;
    }
}
