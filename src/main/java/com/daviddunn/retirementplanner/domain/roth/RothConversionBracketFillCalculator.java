package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
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

    public RothConversionBracketFillCalculator() {

        this.taxFundingCalculator =
                new TaxFundingCalculator();
    }

    public BigDecimal calculateConversion(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            FederalTaxBracket targetBracket,
            BigDecimal taxableInterestIncome) {

        Objects.requireNonNull(
                targetBracket,
                "Target federal tax bracket is required.");

        if (!targetBracket.hasUpperBound()) {

            throw new IllegalArgumentException(
                    "Target federal tax bracket has no upper bound.");
        }

        return calculateConversion(
                household,
                projectionDate,
                portfolio,
                existingWithdrawals,
                withdrawalStrategy,
                filingStatus,
                projectedGovernmentRules,
                targetBracket.getUpperBound(),
                taxableInterestIncome);
    }

    public BigDecimal calculateConversion(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal targetTaxableIncome,
            BigDecimal taxableInterestIncome) {

        return calculateConversion(
                household,
                projectionDate,
                portfolio,
                existingWithdrawals,
                withdrawalStrategy,
                filingStatus,
                projectedGovernmentRules,
                targetTaxableIncome,
                taxableInterestIncome,
                null);
    }

    public BigDecimal calculateConversion(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal targetTaxableIncome,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate) {

        return calculateConversion(
                household, projectionDate, portfolio, existingWithdrawals,
                withdrawalStrategy, filingStatus, projectedGovernmentRules,
                targetTaxableIncome, taxableInterestIncome,
                socialSecurityColaRate, null);
    }

    public BigDecimal calculateConversion(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal targetTaxableIncome,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate,
            DeathScenarioAssumptions deathAssumptions) {

        return calculateConversion(
                household,
                projectionDate,
                portfolio,
                existingWithdrawals,
                withdrawalStrategy,
                filingStatus,
                projectedGovernmentRules,
                targetTaxableIncome,
                taxableInterestIncome,
                socialSecurityColaRate,
                deathAssumptions,
                BigDecimal.ZERO);
    }

    public BigDecimal calculateConversion(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal targetTaxableIncome,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate,
            DeathScenarioAssumptions deathAssumptions,
            BigDecimal openingTaxDeferredDistribution) {

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
                targetTaxableIncome,
                "Target taxable income is required.");

        if (targetTaxableIncome.signum() < 0) {
            throw new IllegalArgumentException(
                    "Target taxable income cannot be negative.");
        }

        Objects.requireNonNull(
                taxableInterestIncome,
                "Taxable interest income is required.");

        if (taxableInterestIncome.signum() < 0) {
            throw new IllegalArgumentException(
                    "Taxable interest income cannot be negative.");
        }

        Objects.requireNonNull(
                openingTaxDeferredDistribution,
                "Opening tax-deferred distribution is required.");

        if (openingTaxDeferredDistribution.signum() < 0) {
            throw new IllegalArgumentException(
                    "Opening tax-deferred distribution cannot be negative.");
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
                        BigDecimal.ZERO,
                        taxableInterestIncome,
                        socialSecurityColaRate,
                        deathAssumptions,
                        openingTaxDeferredDistribution);

        FederalTaxCalculation
                preConversionFederalTax =
                preConversionResult
                        .getFederalTaxCalculation();

        /*
         * The initial conversion proposal is the
         * remaining room below the target taxable
         * income.
         */
        BigDecimal rothConversion =
                targetTaxableIncome.subtract(
                        preConversionFederalTax
                                .getTaxableIncome());

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
                            rothConversion,
                            taxableInterestIncome,
                            socialSecurityColaRate,
                            deathAssumptions,
                            openingTaxDeferredDistribution);

            BigDecimal finalTaxableIncome =
                    taxFundingResult
                            .getFederalTaxCalculation()
                            .getTaxableIncome();

            BigDecimal difference =
                    targetTaxableIncome.subtract(
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
