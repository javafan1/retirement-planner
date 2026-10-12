package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.util.Objects;

/** Immutable annual facts, including attributed income; no mutable plan or JavaFX objects. */
public record StateTaxCalculationRequest(
        int taxYear,
        FilingStatus filingStatus,
        TaxHouseholdContext household,
        TaxIncome income,
        FederalTaxCalculation federal,
        GovernmentRules projectedRules,
        StateTaxAssumptions assumptions) {
    public StateTaxCalculationRequest {
        if (taxYear <= 0 || taxYear != Objects.requireNonNull(household).distributionDate().getYear()) {
            throw new IllegalArgumentException("Tax year must match the household tax date.");
        }
        Objects.requireNonNull(filingStatus);
        Objects.requireNonNull(income);
        Objects.requireNonNull(federal);
        Objects.requireNonNull(projectedRules);
        Objects.requireNonNull(assumptions);
    }
}
