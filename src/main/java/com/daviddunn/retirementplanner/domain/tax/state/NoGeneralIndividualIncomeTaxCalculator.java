package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Stateless, shared strategy for admitted full-year-resident 2026 individual income rules. */
final class NoGeneralIndividualIncomeTaxCalculator implements StateIncomeTaxCalculator {
    static final int VALIDATED_RULE_YEAR = 2026;
    static final String RULE_VERSION = "no-general-individual-income-tax-2026-v1";

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        var jurisdiction = request.assumptions().jurisdiction();
        if (!jurisdiction.usesNoGeneralIndividualIncomeTaxModel()) {
            throw new IllegalArgumentException("Jurisdiction has not been admitted to the no-income-tax model.");
        }
        // Florida's established historical compatibility and diagnostics remain unchanged.
        if (jurisdiction == StateTaxJurisdiction.FL) {
            return new ProductionStateTaxResult(jurisdiction, request.assumptions().calculationMethod(),
                    StateTaxCalculationStatus.CALCULATED, BigDecimal.ZERO,
                    List.of("Full-year Florida resident. Florida does not impose individual state income tax."),
                    Optional.empty());
        }
        if (request.taxYear() < VALIDATED_RULE_YEAR) {
            return ProductionStateTaxResult.unsupported(jurisdiction, request.assumptions().calculationMethod(),
                    List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE),
                    "No validated historical individual income-tax rules for " + jurisdiction.displayName()
                            + " in " + request.taxYear() + ". The admitted baseline is 2026.");
        }
        return new ProductionStateTaxResult(jurisdiction, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, BigDecimal.ZERO,
                List.of("Full-year " + jurisdiction.displayName() + " resident. No modeled individual income tax under validated 2026 rules."
                        + " Rule version: " + RULE_VERSION + "; modeled tax year: " + request.taxYear() + "."
                        + " The validated baseline is projected forward without tax-law forecasting."
                        + " Property, sales, business and local taxes are outside this estimate."), Optional.empty());
    }
}
