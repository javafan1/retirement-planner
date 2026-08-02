package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.IrmaaBracket;

import java.math.BigDecimal;

public class IrmaaBracketBuilder {

    private FilingStatus filingStatus =
            FilingStatus.MARRIED_FILING_JOINTLY;

    private BigDecimal minimumModifiedAdjustedGrossIncome =
            BigDecimal.ZERO;

    private BigDecimal maximumModifiedAdjustedGrossIncome =
            BigDecimal.valueOf(212000);

    private BigDecimal monthlyPartBPremium =
            BigDecimal.valueOf(185.00);

    private BigDecimal monthlyPartDPremium =
            BigDecimal.ZERO;

    public static IrmaaBracketBuilder anIrmaaBracket() {
        return new IrmaaBracketBuilder();
    }

    private IrmaaBracketBuilder() {
    }

    public IrmaaBracketBuilder withFilingStatus(
            FilingStatus filingStatus) {

        this.filingStatus = filingStatus;
        return this;
    }

    public IrmaaBracketBuilder withMinimumIncome(
            long income) {

        this.minimumModifiedAdjustedGrossIncome =
                BigDecimal.valueOf(income);

        return this;
    }

    public IrmaaBracketBuilder withMaximumIncome(
            long income) {

        this.maximumModifiedAdjustedGrossIncome =
                BigDecimal.valueOf(income);

        return this;
    }

    public IrmaaBracketBuilder withNoMaximumIncome() {

        this.maximumModifiedAdjustedGrossIncome = null;
        return this;
    }

    public IrmaaBracketBuilder withMonthlyPartBPremium(
            String premium) {

        this.monthlyPartBPremium =
                new BigDecimal(premium);

        return this;
    }

    public IrmaaBracketBuilder withMonthlyPartDPremium(
            String premium) {

        this.monthlyPartDPremium =
                new BigDecimal(premium);

        return this;
    }

    public IrmaaBracket build() {

        return new IrmaaBracket(
                filingStatus,
                minimumModifiedAdjustedGrossIncome,
                maximumModifiedAdjustedGrossIncome,
                monthlyPartBPremium,
                monthlyPartDPremium);
    }
}