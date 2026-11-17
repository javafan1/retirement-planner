package com.daviddunn.retirementplanner.domain.tax.state.mississippi;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/** Form80-105 standard-subset column equivalence; never moves income between spouses. */
public final class MississippiMarriedWorksheet {
    public BigDecimal tax(int year, FilingStatus status, Map<AccountOwnership, BigDecimal> netByOwner,
            BigDecimal unallocated, BigDecimal exemptions) {
        Objects.requireNonNull(netByOwner);
        Objects.requireNonNull(unallocated);
        Objects.requireNonNull(exemptions);
        if (unallocated.signum() < 0 || exemptions.signum() < 0
                || netByOwner.values().stream().anyMatch(v -> v.signum() < 0)
                || netByOwner.containsKey(AccountOwnership.JOINT))
            throw new IllegalArgumentException("Mississippi column income must be nonnegative and individually owned.");
        var rules = new MississippiTaxRules();
        var allowance = rules.standardDeduction(status).add(exemptions);
        BigDecimal positive;
        if (status == FilingStatus.SINGLE || unallocated.signum() > 0) {
            // Joint method: one zero band. Unallocated MFJ income is never divided in half.
            positive = netByOwner.values().stream().reduce(unallocated, BigDecimal::add)
                    .subtract(MississippiTaxRules.ZERO_BAND);
        } else {
            positive = netByOwner.values().stream()
                    .map(v -> v.subtract(MississippiTaxRules.ZERO_BAND).max(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return positive.subtract(allowance).max(BigDecimal.ZERO).multiply(rules.rate(year))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
