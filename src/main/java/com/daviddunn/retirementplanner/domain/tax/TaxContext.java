package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.USState;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxContext {

    private final int taxYear;

    private final FilingStatus filingStatus;

    private final USState state;

    private final BigDecimal taxableOrdinaryIncome;

    private final BigDecimal qualifiedDividendIncome;

    private final BigDecimal longTermCapitalGainIncome;

    private final BigDecimal localIncomeTaxRate;

    public TaxContext(
            int taxYear,
            FilingStatus filingStatus,
            USState state,
            BigDecimal taxableOrdinaryIncome,
            BigDecimal qualifiedDividendIncome,
            BigDecimal longTermCapitalGainIncome,
            BigDecimal localIncomeTaxRate) {

        this.taxYear = taxYear;

        this.filingStatus =
                Objects.requireNonNull(filingStatus);

        this.state =
                Objects.requireNonNull(state);

        this.taxableOrdinaryIncome =
                Objects.requireNonNull(taxableOrdinaryIncome);

        this.qualifiedDividendIncome =
                Objects.requireNonNull(qualifiedDividendIncome);

        this.longTermCapitalGainIncome =
                Objects.requireNonNull(longTermCapitalGainIncome);

        this.localIncomeTaxRate =
                Objects.requireNonNull(localIncomeTaxRate);
    }

    public int getTaxYear() {
        return taxYear;
    }

    public FilingStatus getFilingStatus() {
        return filingStatus;
    }

    public USState getState() {
        return state;
    }

    public BigDecimal getTaxableOrdinaryIncome() {
        return taxableOrdinaryIncome;
    }

    public BigDecimal getQualifiedDividendIncome() {
        return qualifiedDividendIncome;
    }

    public BigDecimal getLongTermCapitalGainIncome() {
        return longTermCapitalGainIncome;
    }

    public BigDecimal getLocalIncomeTaxRate() {
        return localIncomeTaxRate;
    }
}