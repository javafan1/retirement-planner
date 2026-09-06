package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Projected conversion execution result, intentionally runtime-only. */
public final class ProjectedRothConversionResult {

    private final ProjectedPortfolio portfolio;
    private final List<RothConversionAllocation> allocations;

    public ProjectedRothConversionResult(
            ProjectedPortfolio portfolio,
            List<RothConversionAllocation> allocations) {

        this.portfolio = Objects.requireNonNull(
                portfolio, "Projected portfolio is required.");
        this.allocations = List.copyOf(Objects.requireNonNull(
                allocations, "Roth conversion allocations are required."));
    }

    public ProjectedPortfolio getPortfolio() {
        return portfolio;
    }

    public List<RothConversionAllocation> getAllocations() {
        return allocations;
    }

    public BigDecimal getTotalConversion() {
        return allocations.stream()
                .map(RothConversionAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getConversion(AccountOwnership ownership) {
        Objects.requireNonNull(ownership, "Account ownership is required.");
        return allocations.stream()
                .filter(allocation -> allocation.ownership() == ownership)
                .map(RothConversionAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
