package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;

public enum AccountType {

    TRADITIONAL_IRA(
            "Traditional IRA",
            TaxTreatment.TAX_DEFERRED,
            true),

    ROLLOVER_IRA(
            "Rollover IRA",
            TaxTreatment.TAX_DEFERRED,
            true),

    TRADITIONAL_401K(
            "Traditional 401(k)",
            TaxTreatment.TAX_DEFERRED,
            true),

    TRADITIONAL_403B(
            "Traditional 403(b)",
            TaxTreatment.TAX_DEFERRED,
            true),

    ROTH_IRA(
            "Roth IRA",
            TaxTreatment.ROTH,
            false),

    ROTH_401K(
            "Roth 401(k)",
            TaxTreatment.ROTH,
            false),

    INHERITED_TRADITIONAL_IRA(
            "Inherited Traditional IRA",
            TaxTreatment.TAX_DEFERRED,
            false),

    INHERITED_ROTH_IRA(
            "Inherited Roth IRA",
            TaxTreatment.ROTH,
            false),

    BROKERAGE(
            "Brokerage",
            TaxTreatment.TAXABLE,
            false),

    CHECKING(
            "Checking",
            TaxTreatment.CASH,
            false),

    SAVINGS(
            "Savings",
            TaxTreatment.CASH,
            false);

    private final String displayName;
    private final TaxTreatment taxTreatment;
    private final boolean subjectToOwnerRmd;

    AccountType(
            String displayName,
            TaxTreatment taxTreatment,
            boolean subjectToOwnerRmd) {

        this.displayName = displayName;
        this.taxTreatment = taxTreatment;
        this.subjectToOwnerRmd = subjectToOwnerRmd;
    }

    public TaxTreatment getTaxTreatment() {
        return taxTreatment;
    }

    public boolean isSubjectToOwnerRmd() {
        return subjectToOwnerRmd;
    }


    public RmdAccountCategory getRmdAccountCategory() {

        return switch (this) {

            case TRADITIONAL_IRA,
                 ROLLOVER_IRA ->
                    RmdAccountCategory.IRA;

            case TRADITIONAL_401K ->
                    RmdAccountCategory.TRADITIONAL_401K;

            case TRADITIONAL_403B ->
                    RmdAccountCategory.TRADITIONAL_403B;

            default ->
                    RmdAccountCategory.NOT_APPLICABLE;
        };
    }

    public ProjectionAssetType getProjectionAssetType() {

        return switch (this) {

            case BROKERAGE,
                 CHECKING,
                 SAVINGS ->
                    ProjectionAssetType.TAXABLE;

            case TRADITIONAL_IRA,
                 ROLLOVER_IRA,
                 TRADITIONAL_401K,
                 TRADITIONAL_403B,
                 INHERITED_TRADITIONAL_IRA ->
                    ProjectionAssetType.TAX_DEFERRED;

            case ROTH_IRA,
                 ROTH_401K,
                 INHERITED_ROTH_IRA ->
                    ProjectionAssetType.ROTH;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}