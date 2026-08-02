package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;

import java.math.BigDecimal;

public class MichiganTaxRulesBuilder {

    private BigDecimal incomeTaxRate =
            new BigDecimal("0.0425");

    private BigDecimal retirementDeductionSingle =
            BigDecimal.ZERO;

    private BigDecimal retirementDeductionMarried =
            BigDecimal.ZERO;

    public static MichiganTaxRulesBuilder aMichiganTaxRules() {
        return new MichiganTaxRulesBuilder();
    }

    private MichiganTaxRulesBuilder() {
    }

    public MichiganTaxRulesBuilder withIncomeTaxRate(
            String rate) {

        this.incomeTaxRate =
                new BigDecimal(rate);

        return this;
    }

    public MichiganTaxRulesBuilder withRetirementDeductionSingle(
            String deduction) {

        this.retirementDeductionSingle =
                new BigDecimal(deduction);

        return this;
    }

    public MichiganTaxRulesBuilder withRetirementDeductionMarried(
            String deduction) {

        this.retirementDeductionMarried =
                new BigDecimal(deduction);

        return this;
    }

    public MichiganTaxRules build() {

        return new MichiganTaxRules(
                incomeTaxRate,
                retirementDeductionSingle,
                retirementDeductionMarried);
    }
}