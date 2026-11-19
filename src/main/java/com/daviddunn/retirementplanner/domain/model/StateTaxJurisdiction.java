package com.daviddunn.retirementplanner.domain.model;

/** Stable jurisdiction identities for inputs and results. Historical identities can be read independently. */
public enum StateTaxJurisdiction {
    MI("Michigan"),
    FL("Florida", true),
    IL("Illinois"),
    PA("Pennsylvania"),
    AK("Alaska", true),
    NV("Nevada", true),
    NH("New Hampshire", true),
    SD("South Dakota", true),
    TN("Tennessee", true),
    TX("Texas", true),
    WY("Wyoming", true),
    IA("Iowa"),
    AZ("Arizona"),
    NC("North Carolina"),
    KY("Kentucky"),
    GA("Georgia"),
    VA("Virginia"),
    OK("Oklahoma"),
    MS("Mississippi"),
    AL("Alabama"),
    AR("Arkansas");

    private final String displayName;
    private final boolean noGeneralIndividualIncomeTax;

    StateTaxJurisdiction(String displayName) {
        this(displayName, false);
    }

    StateTaxJurisdiction(String displayName, boolean noGeneralIndividualIncomeTax) {
        this.displayName = displayName;
        this.noGeneralIndividualIncomeTax = noGeneralIndividualIncomeTax;
    }

    public String displayName() {
        return displayName;
    }

    /** Admitted no-general-individual-income-tax model under validated 2026 law. */
    public boolean usesNoGeneralIndividualIncomeTaxModel() {
        return noGeneralIndividualIncomeTax;
    }

    public static java.util.List<StateTaxJurisdiction> alphabeticalChoices() {
        return productionChoices().stream()
                .sorted(java.util.Comparator.comparing(StateTaxJurisdiction::displayName))
                .toList();
    }

    public static java.util.List<StateTaxJurisdiction> productionChoices() {
        return java.util.Arrays.stream(values()).filter(StateTaxJurisdiction::productionAdmitted).toList();
    }

    public boolean productionAdmitted() {
        return this != AR;
    }

    /** Persist stable jurisdiction codes; numeric enum ordinals are not jurisdictions. */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static StateTaxJurisdiction fromCode(String code) {
        return valueOf(java.util.Objects.requireNonNull(code, "State of residence is required."));
    }

    @Override
    public String toString() {
        return displayName;
    }
}
