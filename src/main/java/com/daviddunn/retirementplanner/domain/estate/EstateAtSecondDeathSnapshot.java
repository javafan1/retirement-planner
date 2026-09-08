package com.daviddunn.retirementplanner.domain.estate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Investable assets only; estimated heir tax is a valuation, not a portfolio withdrawal. */
public record EstateAtSecondDeathSnapshot(
        LocalDate effectiveSecondDeathDate,
        LocalDate balanceDate,
        BigDecimal nominalInvestableAssets,
        BigDecimal estimatedHeirTax,
        BigDecimal nominalAfterTaxEstate) {
    public EstateAtSecondDeathSnapshot {
        Objects.requireNonNull(effectiveSecondDeathDate);
        Objects.requireNonNull(balanceDate);
        Objects.requireNonNull(nominalInvestableAssets);
        Objects.requireNonNull(estimatedHeirTax);
        Objects.requireNonNull(nominalAfterTaxEstate);
    }
}
