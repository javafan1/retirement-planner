package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import java.util.List;
import java.math.BigDecimal;
import java.util.Objects;

public final class TaxIncome {

    private final List<IncomeSourceAttribution> incomeSources;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<IncomeSourceAttribution> getIncomeSources() {
        return incomeSources;
    }

    private final BigDecimal pensionIncome;
    private final BigDecimal socialSecurityIncome;
    private final BigDecimal taxDeferredWithdrawals;
    private final BigDecimal rothConversion;
    private final BigDecimal taxableInterestIncome;
    private final BigDecimal conditionalOrdinaryIncome;

    public TaxIncome(
            BigDecimal pensionIncome,
            BigDecimal socialSecurityIncome,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome) {
        this(pensionIncome, socialSecurityIncome, taxDeferredWithdrawals, rothConversion, taxableInterestIncome, List.of());
    }

    public TaxIncome(
            BigDecimal pensionIncome,
            BigDecimal socialSecurityIncome,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            List<IncomeSourceAttribution> incomeSources) {
        this(pensionIncome, socialSecurityIncome, taxDeferredWithdrawals, rothConversion, taxableInterestIncome, incomeSources, BigDecimal.ZERO);
    }

    private TaxIncome(BigDecimal pensionIncome, BigDecimal socialSecurityIncome, BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion, BigDecimal taxableInterestIncome, List<IncomeSourceAttribution> incomeSources,
            BigDecimal conditionalOrdinaryIncome) {
        this.conditionalOrdinaryIncome = requireNonNegative(conditionalOrdinaryIncome, "Conditional ordinary income");
        this.incomeSources = List.copyOf(Objects.requireNonNull(incomeSources));

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

    public TaxIncome withIncomeSources(List<IncomeSourceAttribution> sources) {
        return new TaxIncome(pensionIncome, socialSecurityIncome, taxDeferredWithdrawals,
                rothConversion, taxableInterestIncome, sources, conditionalOrdinaryIncome);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getConditionalOrdinaryIncome() { return conditionalOrdinaryIncome; }

    TaxIncome withConditionalOrdinaryIncome(BigDecimal amount) {
        return new TaxIncome(pensionIncome, socialSecurityIncome, taxDeferredWithdrawals,
                rothConversion, taxableInterestIncome, incomeSources, amount);
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

        BigDecimal ordinary = pensionIncome
                .add(taxDeferredWithdrawals)
                .add(rothConversion)
                .add(taxableInterestIncome);
        return conditionalOrdinaryIncome.signum() == 0 ? ordinary : ordinary.add(conditionalOrdinaryIncome);
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
