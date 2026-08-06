package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxBracketLookupService;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionPlanner {

    private final FederalTaxBracketLookupService
            taxBracketLookupService;

    private static final BigDecimal
            TWELVE_PERCENT =
            new BigDecimal("0.12");

    private static final BigDecimal
            TWENTY_TWO_PERCENT =
            new BigDecimal("0.22");

    private static final BigDecimal
            TWENTY_FOUR_PERCENT =
            new BigDecimal("0.24");

    public RothConversionPlanner() {

        this.taxBracketLookupService =
                new FederalTaxBracketLookupService();
    }

    public RothConversionResult plan(

            FederalTaxRules federalTaxRules,

            PlanningAssumptions planningAssumptions,

            TaxIncome taxIncome,

            BigDecimal availableTraditionalBalance) {

        Objects.requireNonNull(
                federalTaxRules,
                "Federal tax rules are required.");

        Objects.requireNonNull(
                planningAssumptions,
                "Planning assumptions are required.");

        Objects.requireNonNull(
                taxIncome,
                "Tax income is required.");

        Objects.requireNonNull(
                availableTraditionalBalance,
                "Available traditional balance is required.");

        RothConversionStrategy strategy =
                planningAssumptions
                        .getWithdrawalAssumptions()
                        .getRothConversionStrategy();

        if (strategy ==
                RothConversionStrategy.NONE) {

            return new RothConversionResult(
                    BigDecimal.ZERO);
        }

        /*
         * Determine the household's taxable income
         * before any Roth conversion.
         */
        BigDecimal taxableIncome =
                taxIncome
                        .getOrdinaryIncomeBeforeSocialSecurity()
                        .subtract(
                                federalTaxRules
                                        .getStandardDeduction());

        if (taxableIncome.signum() < 0) {

            taxableIncome =
                    BigDecimal.ZERO;
        }

        BigDecimal targetTaxRate =
                getTargetTaxRate(
                        strategy);

        BigDecimal remainingCapacity =
                taxBracketLookupService
                        .calculateRemainingCapacity(
                                federalTaxRules,
                                taxableIncome,
                                targetTaxRate);

        BigDecimal conversionAmount =
                remainingCapacity.min(
                        availableTraditionalBalance);

        if (conversionAmount.signum() < 0) {

            conversionAmount =
                    BigDecimal.ZERO;
        }

        return new RothConversionResult(
                conversionAmount);
    }

    private BigDecimal getTargetTaxRate(
            RothConversionStrategy strategy) {

        return switch (strategy) {

            case FILL_22_PERCENT_BRACKET ->
                    TWENTY_TWO_PERCENT;

            case NONE ->
                    BigDecimal.ZERO;
        };
    }
}