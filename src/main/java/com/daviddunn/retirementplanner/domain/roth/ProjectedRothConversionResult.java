package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import java.util.Optional;
import java.util.OptionalInt;
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

    public List<IncomeSourceAttribution> getIncomeSources(int taxYear) {
        return allocations.stream().map(allocation -> new IncomeSourceAttribution(
                IncomeSourceAttribution.Kind.ROTH_CONVERSION,
                IncomeSourceAttribution.Purpose.CONVERSION,
                Optional.of(allocation.ownership()),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(allocation.sourceAccount())),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(allocation.destinationAccount())),
                allocation.sourceAccount().getName(), allocation.amount(), Optional.of(allocation.amount()),
                OptionalInt.of(taxYear), Optional.empty())).toList();
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
