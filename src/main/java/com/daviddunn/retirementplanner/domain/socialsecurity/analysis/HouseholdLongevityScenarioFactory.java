package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared mortality preparation without financial valuation or projection logic. */
public final class HouseholdLongevityScenarioFactory {

    private final SocialSecurityMortalityDistributionProvider provider;

    public HouseholdLongevityScenarioFactory(SocialSecurityMortalityTable table) {
        provider = new SocialSecurityMortalityDistributionProvider(table);
    }

    public HouseholdLongevityScenarios create(
            LocalDate primaryBirthDate,
            LocalDate spouseBirthDate,
            AnalyzerLongevityAssumptions assumptions) {
        Objects.requireNonNull(assumptions, "Longevity assumptions are required.");
        if (!provider.metadata().equals(assumptions.tableMetadata())) {
            throw new IllegalArgumentException("Longevity assumptions must identify the supplied mortality table.");
        }
        SocialSecurityMortalityDistributionResult primary = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        primaryBirthDate, assumptions.mortalityBaseDate(),
                        assumptions.primaryCategory(), assumptions.primaryAdjustment()));
        SocialSecurityMortalityDistributionResult spouse = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        spouseBirthDate, assumptions.mortalityBaseDate(),
                        assumptions.spouseCategory(), assumptions.spouseAdjustment()));
        if (primary.partialYearConvention() != assumptions.partialYearConvention()
                || spouse.partialYearConvention() != assumptions.partialYearConvention()) {
            throw new IllegalArgumentException("Unsupported mortality timing convention.");
        }
        return new HouseholdLongevityScenarios(assumptions, primary, spouse);
    }

    /** Compatibility path for callers supplying already normalized distributions. */
    public static List<SocialSecurityJointMortalityScenario> combine(
            LocalDate primaryBirthDate,
            LocalDate spouseBirthDate,
            SocialSecurityMortalityDistribution primaryMortality,
            SocialSecurityMortalityDistribution spouseMortality) {
        Objects.requireNonNull(primaryBirthDate, "Primary birth date is required.");
        Objects.requireNonNull(spouseBirthDate, "Spouse birth date is required.");
        Objects.requireNonNull(primaryMortality, "Primary mortality is required.");
        Objects.requireNonNull(spouseMortality, "Spouse mortality is required.");
        SocialSecurityIndependentJointMortalityCalculator jointCalculator =
                new SocialSecurityIndependentJointMortalityCalculator();
        List<SocialSecurityJointMortalityScenario> scenarios = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SocialSecurityMortalityProbability primary
                : primaryMortality.probabilities()) {
            LocalDate primaryDeathDate = SocialSecurityDeathDateCalculator
                    .calculateDeathDate(
                            primaryBirthDate,
                            primary.deathAge());
            for (SocialSecurityMortalityProbability spouse
                    : spouseMortality.probabilities()) {
                LocalDate spouseDeathDate = SocialSecurityDeathDateCalculator
                        .calculateDeathDate(
                                spouseBirthDate,
                                spouse.deathAge());
                BigDecimal joint = jointCalculator.calculate(
                        primary.probability(), spouse.probability());
                scenarios.add(new SocialSecurityJointMortalityScenario(
                        primary.deathAge(),
                        spouse.deathAge(),
                        primaryDeathDate,
                        spouseDeathDate,
                        primary.probability(),
                        spouse.probability(),
                        joint));
                total = total.add(joint);
            }
        }
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException(
                    "Joint mortality probabilities must sum exactly to 1.");
        }
        return List.copyOf(scenarios);
    }

}
