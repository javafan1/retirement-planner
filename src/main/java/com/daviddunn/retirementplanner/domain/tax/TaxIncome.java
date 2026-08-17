package com.daviddunn.retirementplanner.domain.tax;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxIncome {

    private final BigDecimal pensionIncome;
    private final BigDecimal socialSecurityIncome;
    private final BigDecimal taxDeferredWithdrawals;
    private final BigDecimal rothConversion;
    private final BigDecimal taxableInterestIncome;

    public TaxIncome(
            BigDecimal pensionIncome,
            BigDecimal socialSecurityIncome,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome) {

        this.pensionIncome =
                requireNonNegative(
                        pensionIncome,
                        "Pension income");

        this.socialSecurityIncome =
                requireNonNegative(
                        socialSecurityIncome,
                        "Social Security income");

        this.taxDeferredWithdrawals =
                requireNonNegative(
                        taxDeferredWithdrawals,
                        "Tax-deferred withdrawals");

        this.rothConversion =
                requireNonNegative(
                        rothConversion,
                        "Roth conversion");

        this.taxableInterestIncome =
                requireNonNegative(
                        taxableInterestIncome,
                        "Taxable interest income");
    }

    public BigDecimal getPensionIncome() {
        return pensionIncome;
    }

    public BigDecimal getSocialSecurityIncome() {
        return socialSecurityIncome;
    }

    public BigDecimal getTaxDeferredWithdrawals() {
        return taxDeferredWithdrawals;
    }

    public BigDecimal getRothConversion() {
        return rothConversion;
    }

    public BigDecimal getTaxableInterestIncome() {
        return taxableInterestIncome;
    }

    public BigDecimal getOrdinaryIncomeBeforeSocialSecurity() {

        return pensionIncome
                .add(taxDeferredWithdrawals)
                .add(rothConversion)
                .add(taxableInterestIncome);
    }

    public BigDecimal getMichiganRetirementIncome() {

        return pensionIncome
                .add(taxDeferredWithdrawals)
                .add(rothConversion);
    }

    private static BigDecimal requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(
                amount,
                description + " is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }

        return amount;
    }
}