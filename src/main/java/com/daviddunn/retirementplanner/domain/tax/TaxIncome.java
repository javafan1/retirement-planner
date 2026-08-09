package com.daviddunn.retirementplanner.domain.tax;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxIncome {

    private final BigDecimal pensionIncome;
    private final BigDecimal socialSecurityIncome;
    private final BigDecimal taxDeferredWithdrawals;
    private final BigDecimal rothConversion;

    public TaxIncome(
            BigDecimal pensionIncome,
            BigDecimal socialSecurityIncome,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion) {

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

    public BigDecimal getOrdinaryIncomeBeforeSocialSecurity() {

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

    public BigDecimal getMichiganRetirementIncome() {

        return pensionIncome
                .add(taxDeferredWithdrawals)
                .add(rothConversion);
    }

    public BigDecimal getRothConversion() {
        return rothConversion;
    }

}