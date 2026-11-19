package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.ArkansasMinimumFundingSolver;
import com.daviddunn.retirementplanner.domain.tax.ArkansasRothFunding;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasTaxRules;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Arkansas result from the gated engine, with the facts required for retained history. */
public record ArkansasValidationTax(ArkansasProductionTaxCalculator.Result calculation, FilingStatus filingStatus,
        BigDecimal taxableFundingIncome, ArkansasMinimumFundingSolver.Metrics fundingMetrics,
        Optional<ArkansasRothFunding.TargetMetrics> targetMetrics, Map<String, String> versions,
        ArkansasAnnualProvenance provenance, boolean restoredHistory) {

    ArkansasValidationTax(ArkansasProductionTaxCalculator.Result calculation, FilingStatus filingStatus,
            BigDecimal taxableFundingIncome, ArkansasMinimumFundingSolver.Metrics fundingMetrics,
            Optional<ArkansasRothFunding.TargetMetrics> targetMetrics, Map<String, String> versions,
            ArkansasAnnualProvenance provenance) {
        this(calculation, filingStatus, taxableFundingIncome, fundingMetrics, targetMetrics, versions, provenance, false);
    }

    public static Map<String, String> reviewedVersions() {
        return Map.of("arkansas-income-tax-rules", ArkansasTaxRules.VERSION,
                "arkansas-tax-funding", ArkansasMinimumFundingSolver.POLICY_VERSION,
                "arkansas-roth-target", ArkansasRothFunding.TARGET_POLICY_VERSION);
    }

    public ArkansasValidationTax {
        if (calculation.estimatedIncomeTax().isEmpty() || taxableFundingIncome.signum() < 0) {
            throw new IllegalArgumentException("A validation year requires actual Arkansas tax and funding facts.");
        }
        versions = Map.copyOf(versions);
        if (provenance.filingStatus() != filingStatus
                || !calculation.election().orElseThrow().equals(provenance.election())
                || targetMetrics.isPresent() != (provenance.targetedFederalTaxableIncome() != null)) {
            throw new IllegalArgumentException("Inconsistent Arkansas annual execution provenance.");
        }
    }

    public boolean currentFor(Map<String, String> reviewed) {
        return versions.equals(reviewed);
    }
}
