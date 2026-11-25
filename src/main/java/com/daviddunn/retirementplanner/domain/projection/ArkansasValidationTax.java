package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy;
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

    public static final String LIFE_PRINCIPAL_POLICY_KEY = "arkansas-life-principal";
    public static final String LIFE_PRINCIPAL_POLICY = "AR-LIFE-PRINCIPAL-P3D3C-1";
    public static final String LOW_INCOME_POLICY_KEY = "arkansas-low-income-policy";
    public static final String REGULAR_TAX_ONLY_POLICY = "AR-REGULAR-TAX-ONLY-P3D3C3-1";
    public static final String REGULAR_TAX_ONLY_FUNDING = "AR-FUNDING-P3D3C3-1";
    public static final String REGULAR_TAX_ONLY_TARGET = "AR-TARGET-P3D3C3-1";

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

    public static Map<String, String> withLifePrincipalPolicy(Map<String, String> versions) {
        var scoped = new java.util.HashMap<>(versions);
        scoped.put(LIFE_PRINCIPAL_POLICY_KEY, LIFE_PRINCIPAL_POLICY);
        return Map.copyOf(scoped);
    }

    public static Map<String, String> withRegularTaxOnlyPolicy(Map<String, String> versions) {
        var scoped = new java.util.HashMap<>(versions);
        scoped.put(LOW_INCOME_POLICY_KEY, REGULAR_TAX_ONLY_POLICY);
        scoped.put("arkansas-tax-funding", REGULAR_TAX_ONLY_FUNDING);
        scoped.put("arkansas-roth-target", REGULAR_TAX_ONLY_TARGET);
        return Map.copyOf(scoped);
    }

    public ArkansasLowIncomePolicy lowIncomePolicy() {
        return REGULAR_TAX_ONLY_POLICY.equals(versions.get(LOW_INCOME_POLICY_KEY))
                ? ArkansasLowIncomePolicy.REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED : ArkansasLowIncomePolicy.STRICT;
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
