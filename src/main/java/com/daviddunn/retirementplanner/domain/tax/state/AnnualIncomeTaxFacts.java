package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;

import java.util.Objects;

/** Annual tax evidence before a jurisdiction has been selected or admitted. */
public record AnnualIncomeTaxFacts(int taxYear, FilingStatus filingStatus, TaxHouseholdContext household,
                                  TaxIncome income, FederalTaxCalculation federal, GovernmentRules projectedRules) {
    public AnnualIncomeTaxFacts {
        if (taxYear != Objects.requireNonNull(household).distributionDate().getYear()) {
            throw new IllegalArgumentException("Annual tax date and year disagree.");
        }
        Objects.requireNonNull(filingStatus);
        Objects.requireNonNull(income);
        Objects.requireNonNull(federal);
        Objects.requireNonNull(projectedRules);
    }
}
