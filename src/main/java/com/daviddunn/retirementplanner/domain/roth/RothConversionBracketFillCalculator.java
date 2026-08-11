package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.TaxFundingCalculator;
import com.daviddunn.retirementplanner.domain.tax.TaxFundingResult;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class RothConversionBracketFillCalculator {

    private static final BigDecimal TOLERANCE =
            new BigDecimal("0.01");

    private static final int MAX_ITERATIONS =
            100;

    private final TaxFundingCalculator
            taxFundingCalculator;

    private final RothConversionBracketFillStrategy
            bracketFillStrategy;

    public RothConversionBracketFillCalculator() {

        this.taxFundingCalculator =
                new TaxFundingCalculator();

        this.bracketFillStrategy =
                new RothConversionBracketFillStrategy();
    }

    public BigDecimal calculateConversion(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            FederalTaxBracket targetBracket) {

        Objects.requireNonNull(
                household,
                "Household is required.");

        Objects.requireNonNull(
                projectionDate,
                "Projection date is required.");

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        Objects.requireNonNull(
                existingWithdrawals,
                "Existing withdrawals are required.");

        Objects.requireNonNull(
                withdrawalStrategy,
                "Withdrawal strategy is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                projectedGovernmentRules,
                "Projected government rules are required.");

        Objects.requireNonNull(
                targetBracket,
                "Target federal tax bracket is required.");

        if (!targetBracket.hasUpperBound()) {

            throw new IllegalArgumentException(
                    "Target federal tax bracket has no upper bound.");
        }

        /*
         * First determine taxable income with
         * no Roth conversion.
         */
        TaxFundingResult preConversionResult =
                taxFundingCalculator.calculate(
                        household,
                        projectionDate,
                        portfolio,
                        existingWithdrawals,
                        withdrawalStrategy,
                        filingStatus,
                        projectedGovernmentRules,
                        BigDecimal.ZERO);

        FederalTaxCalculation
                preConversionFederalTax =
                preConversionResult
                        .getFederalTaxCalculation();

        /*
         * Use the existing bracket-fill strategy
         * to calculate the first conversion
         * proposal.
         */
        BigDecimal rothConversion =
                bracketFillStrategy.calculateConversion(
                        preConversionFederalTax,
                        targetBracket);

        /*
         * If there is no room in the bracket,
         * there is nothing to convert.
         */
        if (rothConversion.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        /*
         * Iterate because a tax-deferred tax-funding
         * withdrawal can itself increase taxable income.
         */

        BigDecimal previousConversion = null;
        BigDecimal previousDifference = null;

        for (int iteration = 0;
             iteration < MAX_ITERATIONS;
             iteration++) {

            TaxFundingResult taxFundingResult =
                    taxFundingCalculator.calculate(
                            household,
                            projectionDate,
                            portfolio,
                            existingWithdrawals,
                            withdrawalStrategy,
                            filingStatus,
                            projectedGovernmentRules,
                            rothConversion);

            BigDecimal finalTaxableIncome =
                    taxFundingResult
                            .getFederalTaxCalculation()
                            .getTaxableIncome();

            BigDecimal difference =
                    targetBracket
                            .getUpperBound()
                            .subtract(
                                    finalTaxableIncome);

            if (difference.abs()
                    .compareTo(TOLERANCE) <= 0) {

                return rothConversion;
            }

            BigDecimal currentConversion =
                    rothConversion;

            if (previousConversion != null &&
                    previousDifference != null &&
                    previousDifference.signum()
                            != difference.signum()) {

                BigDecimal denominator =
                        difference.subtract(
                                previousDifference);

                BigDecimal conversionDifference =
                        previousConversion
                                .subtract(
                                        currentConversion);

                BigDecimal adjustment =
                        difference
                                .multiply(
                                        conversionDifference)
                                .divide(
                                        denominator,
                                        20,
                                        java.math.RoundingMode.HALF_UP);

                rothConversion =
                        currentConversion.add(
                                adjustment);

            } else {

                rothConversion =
                        currentConversion.add(
                                difference);
            }

            previousConversion =
                    currentConversion;

            previousDifference =
                    difference;

            if (rothConversion.signum() < 0) {
                return BigDecimal.ZERO;
            }
        }

        throw new IllegalStateException(
                "Roth conversion bracket calculation did not converge.");
    }
}