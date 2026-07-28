package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class SocialSecurityTaxRules {

    private final BigDecimal firstThreshold;
    private final BigDecimal secondThreshold;
    private final TaxFilingStatus filingStatus;

    @JsonCreator
    public SocialSecurityTaxRules(

            @JsonProperty("filingStatus")
            TaxFilingStatus filingStatus,

            @JsonProperty("firstThreshold")
            BigDecimal firstThreshold,

            @JsonProperty("secondThreshold")
            BigDecimal secondThreshold) {

        this.filingStatus =
                Objects.requireNonNull(
                        filingStatus,
                        "Filing status is required.");

        this.firstThreshold =
                Objects.requireNonNull(
                        firstThreshold,
                        "First Social Security tax threshold is required.");

        this.secondThreshold =
                Objects.requireNonNull(
                        secondThreshold,
                        "Second Social Security tax threshold is required.");

        if (firstThreshold.signum() < 0) {
            throw new IllegalArgumentException(
                    "First threshold cannot be negative.");
        }

        if (secondThreshold.compareTo(
                firstThreshold) <= 0) {

            throw new IllegalArgumentException(
                    "Second threshold must be greater than first threshold.");
        }
    }

    public BigDecimal getFirstThreshold() {
        return firstThreshold;
    }

    public BigDecimal getSecondThreshold() {
        return secondThreshold;
    }

    public TaxFilingStatus getFilingStatus() {
        return filingStatus;
    }

    @Override
    public String toString() {

        return "SocialSecurityTaxRules{" +
                "filingStatus=" + filingStatus +
                ", firstThreshold=" + firstThreshold +
                ", secondThreshold=" + secondThreshold +
                '}';
    }
}