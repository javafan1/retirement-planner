package com.daviddunn.retirementplanner.domain.model;

public enum AccountType {

    TRADITIONAL_IRA(
            "Traditional IRA",
            TaxTreatment.TAX_DEFERRED),

    ROLLOVER_IRA(
            "Rollover IRA",
            TaxTreatment.TAX_DEFERRED),

    TRADITIONAL_401K(
            "Pre-Tax 401(k)",
            TaxTreatment.TAX_DEFERRED),

    TRADITIONAL_403B(
            "Pre-Tax 403(b)",
            TaxTreatment.TAX_DEFERRED),

    ROTH_IRA(
            "Roth IRA",
            TaxTreatment.ROTH),

    ROTH_401K(
            "Roth 401(k)",
            TaxTreatment.ROTH),

    BROKERAGE(
            "Brokerage",
            TaxTreatment.TAXABLE),

    CHECKING(
            "Checking",
            TaxTreatment.CASH),

    SAVINGS(
            "Savings",
            TaxTreatment.CASH),

    INHERITED_TRADITIONAL_IRA(
            "Inherited Traditional IRA",
            TaxTreatment.TAX_DEFERRED),

    INHERITED_ROTH_IRA(
            "Inherited Roth IRA",
            TaxTreatment.ROTH);

    private final String displayName;
    private final TaxTreatment taxTreatment;

    AccountType(
            String displayName,
            TaxTreatment taxTreatment) {

        this.displayName = displayName;
        this.taxTreatment = taxTreatment;
    }

    public TaxTreatment getTaxTreatment() {
        return taxTreatment;
    }

    @Override
    public String toString() {
        return displayName;
    }
}