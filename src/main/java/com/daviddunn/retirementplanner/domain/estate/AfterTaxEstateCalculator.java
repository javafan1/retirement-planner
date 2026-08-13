package com.daviddunn.retirementplanner.domain.estate;

import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class AfterTaxEstateCalculator {
    public BigDecimal calculateEstimatedTax(
            List<ProjectedAccountSnapshot> endingAccountSnapshots,
            BigDecimal heirTaxRate) {

        Objects.requireNonNull(
                endingAccountSnapshots,
                "Ending account snapshots are required.");

        Objects.requireNonNull(
                heirTaxRate,
                "Heir tax rate is required.");

        validateRate(heirTaxRate);

        BigDecimal taxDeferredBalance =
                endingAccountSnapshots
                        .stream()
                        .filter(snapshot ->
                                snapshot
                                        .getAccount()
                                        .getTaxTreatment()
                                        == TaxTreatment.TAX_DEFERRED)
                        .map(ProjectedAccountSnapshot::getEndingBalance)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add);

        return taxDeferredBalance
                .multiply(heirTaxRate);
    }
    public BigDecimal calculateAfterTaxEstateValue(
            BigDecimal endingInvestableAssets,
            BigDecimal estimatedHeirTax) {

        Objects.requireNonNull(
                endingInvestableAssets,
                "Ending investable assets are required.");

        Objects.requireNonNull(
                estimatedHeirTax,
                "Estimated heir tax is required.");

        return endingInvestableAssets
                .subtract(estimatedHeirTax);
    }

    private void validateRate(
            BigDecimal heirTaxRate) {

        if (heirTaxRate.signum() < 0
                || heirTaxRate.compareTo(
                BigDecimal.ONE) > 0) {

            throw new IllegalArgumentException(
                    "Heir tax rate must be between 0 and 1.");
        }
    }
}