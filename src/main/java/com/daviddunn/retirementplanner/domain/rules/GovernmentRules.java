package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class GovernmentRules {

    private final String rulesVersion;
    private final int taxYear;
    private final LocalDate effectiveDate;

    private final List<FederalTaxRules> federalTaxRules;
    private final RmdRules rmdRules;

    @JsonCreator
    public GovernmentRules(

            @JsonProperty("rulesVersion")
            String rulesVersion,

            @JsonProperty("taxYear")
            int taxYear,

            @JsonProperty("effectiveDate")
            LocalDate effectiveDate,

            @JsonProperty("federalTaxRules")
            List<FederalTaxRules> federalTaxRules,

            @JsonProperty("rmdRules")
            RmdRules rmdRules) {

        this.rulesVersion =
                Objects.requireNonNull(
                        rulesVersion,
                        "Rules version is required.");

        if (taxYear <= 0) {
            throw new IllegalArgumentException(
                    "Tax year must be greater than zero.");
        }

        this.taxYear =
                taxYear;

        this.effectiveDate =
                Objects.requireNonNull(
                        effectiveDate,
                        "Effective date is required.");

        this.federalTaxRules =
                List.copyOf(
                        Objects.requireNonNull(
                                federalTaxRules,
                                "Federal tax rules are required."));

        if (this.federalTaxRules.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one federal tax rule is required.");
        }

        this.rmdRules =
                Objects.requireNonNull(
                        rmdRules,
                        "RMD rules are required.");
    }

    public RmdRules getRmdRules() {
        return rmdRules;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public int getTaxYear() {
        return taxYear;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public List<FederalTaxRules> getFederalTaxRules() {
        return federalTaxRules;
    }

    public FederalTaxRules getFederalTaxRules(
            TaxFilingStatus filingStatus) {

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        return federalTaxRules
                .stream()
                .filter(rules ->
                        rules.getFilingStatus()
                                == filingStatus)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No federal tax rules found for filing status: "
                                        + filingStatus));
    }

    @Override
    public String toString() {

        return "GovernmentRules{" +
                "rulesVersion='" +
                rulesVersion + '\'' +
                ", taxYear=" +
                taxYear +
                ", effectiveDate=" +
                effectiveDate +
                ", federalTaxRules=" +
                federalTaxRules +
                ", rmdRules=" +
                rmdRules +
                '}';
    }


}