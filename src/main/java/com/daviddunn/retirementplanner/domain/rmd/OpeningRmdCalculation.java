package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import java.util.List;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Separates the statutory RMD for the opening calendar year
 * from the amount that remains to be distributed by projection.
 */
public final class OpeningRmdCalculation {

    private final List<IncomeSourceAttribution> incomeSources;

    public List<IncomeSourceAttribution> getIncomeSources() {
        return incomeSources;
    }

    private final HouseholdRmdResult annualRequirement;
    private final HouseholdRmdResult remainingRequirement;
    private final BigDecimal distributedBeforeProjection;

    public OpeningRmdCalculation(
            HouseholdRmdResult annualRequirement,
            HouseholdRmdResult remainingRequirement,
            BigDecimal distributedBeforeProjection) {
        this(annualRequirement, remainingRequirement, distributedBeforeProjection, List.of());
    }

    public OpeningRmdCalculation(
            HouseholdRmdResult annualRequirement,
            HouseholdRmdResult remainingRequirement,
            BigDecimal distributedBeforeProjection,
            List<IncomeSourceAttribution> incomeSources) {
        this.incomeSources = List.copyOf(incomeSources);

        this.annualRequirement = Objects.requireNonNull(
                annualRequirement,
                "Annual RMD requirement is required.");
        this.remainingRequirement = Objects.requireNonNull(
                remainingRequirement,
                "Remaining RMD requirement is required.");
        this.distributedBeforeProjection = Objects.requireNonNull(
                distributedBeforeProjection,
                "RMD distributed before projection is required.");

        if (distributedBeforeProjection.signum() < 0) {
            throw new IllegalArgumentException(
                    "RMD distributed before projection cannot be negative.");
        }
    }

    public HouseholdRmdResult getAnnualRequirement() {
        return annualRequirement;
    }

    public HouseholdRmdResult getRemainingRequirement() {
        return remainingRequirement;
    }

    public BigDecimal getDistributedBeforeProjection() {
        return distributedBeforeProjection;
    }
}
