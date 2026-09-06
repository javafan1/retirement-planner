package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable whole-age death distribution conditional on survival through an
 * analysis base date. Inputs must already be complete and exactly normalized.
 */
public final class SocialSecurityMortalityDistribution {

    private final List<SocialSecurityMortalityProbability> probabilities;

    public SocialSecurityMortalityDistribution(
            List<SocialSecurityMortalityProbability> probabilities) {

        Objects.requireNonNull(probabilities, "Mortality probabilities are required.");
        if (probabilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mortality distribution cannot be empty.");
        }

        Set<Integer> ages = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SocialSecurityMortalityProbability probability : probabilities) {
            Objects.requireNonNull(
                    probability,
                    "Mortality distribution cannot contain a null entry.");
            if (!ages.add(probability.deathAge())) {
                throw new IllegalArgumentException(
                        "Mortality distribution cannot contain duplicate death age "
                                + probability.deathAge()
                                + ".");
            }
            total = total.add(probability.probability());
        }
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException(
                    "Mortality probabilities must sum exactly to 1; actual total is "
                            + total.toPlainString()
                            + ".");
        }
        this.probabilities = List.copyOf(probabilities);
    }

    public List<SocialSecurityMortalityProbability> probabilities() {
        return probabilities;
    }

    public List<Integer> deathAges() {
        return probabilities.stream()
                .map(SocialSecurityMortalityProbability::deathAge)
                .toList();
    }

    public Optional<SocialSecurityMortalityProbability> probabilityFor(
            int deathAge) {
        return probabilities.stream()
                .filter(probability -> probability.deathAge() == deathAge)
                .findFirst();
    }
}
