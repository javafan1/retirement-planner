package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectedWithdrawalAllocator;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculator;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class TaxFundingCalculator {

    private static final BigDecimal TOLERANCE =
            new BigDecimal("0.01");

    private static final int MAX_ITERATIONS =
            100;

    private final TaxIncomeCalculator
            taxIncomeCalculator;

    private final FederalTaxCalculator
            federalTaxCalculator;

    private final MichiganTaxCalculator
            michiganTaxCalculator;

    private final ProjectedWithdrawalAllocator
            withdrawalAllocator;

    public TaxFundingCalculator() {

        this.taxIncomeCalculator =
                new TaxIncomeCalculator();

        this.federalTaxCalculator =
                new FederalTaxCalculator();

        this.michiganTaxCalculator =
                new MichiganTaxCalculator();

        this.withdrawalAllocator =
                new ProjectedWithdrawalAllocator();
    }

    public TaxFundingResult calculate(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome) {

        return calculate(
                household,
                projectionDate,
                portfolio,
                existingWithdrawals,
                withdrawalStrategy,
                filingStatus,
                projectedGovernmentRules,
                rothConversion,
                taxableInterestIncome,
                null);
    }

    public TaxFundingResult calculate(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate) {

        return calculate(
                household, projectionDate, portfolio, existingWithdrawals,
                withdrawalStrategy, filingStatus, projectedGovernmentRules,
                rothConversion, taxableInterestIncome,
                socialSecurityColaRate, null);
    }

    public TaxFundingResult calculate(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate,
            DeathScenarioAssumptions deathAssumptions) {

        return calculate(
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
                BigDecimal.ZERO);
    }

    /**
     * The opening-year RMD amount has already left the account before the
     * projection starts. It is taxable annual income, but not a projected
     * withdrawal or cash-flow receipt.
     */
    public TaxFundingResult calculate(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            FilingStatus filingStatus,
            GovernmentRules projectedGovernmentRules,
            BigDecimal rothConversion,
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
                rothConversion,
                "Roth conversion is required.");

        if (rothConversion.signum() < 0) {
            throw new IllegalArgumentException(
                    "Roth conversion cannot be negative.");
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

        BigDecimal additionalWithdrawal =
                BigDecimal.ZERO;

        for (int iteration = 0;
             iteration < MAX_ITERATIONS;
             iteration++) {

            /*
             * Determine how the proposed tax-funding
             * withdrawal would be distributed across
             * account tax treatments.
             */
            WithdrawalBreakdown taxFundingBreakdown =
                    withdrawalAllocator
                            .calculateWithdrawalBreakdown(
                                    portfolio,
                                    additionalWithdrawal,
                                    withdrawalStrategy);

            WithdrawalBreakdown totalWithdrawals =
                    existingWithdrawals.plus(
                            taxFundingBreakdown);

            /*
             * Only tax-deferred distributions are
             * ordinary income in the current MVP
             * withdrawal tax model.
             */
            TaxIncome taxIncome =
                    taxIncomeCalculator.calculate(
                            household,
                            projectionDate,
                            totalWithdrawals
                                    .getTaxDeferredWithdrawal(),
                            openingTaxDeferredDistribution,
                            rothConversion,
                            taxableInterestIncome,
                            socialSecurityColaRate,
                            deathAssumptions);

            FederalTaxCalculation federalTaxCalculation =
                    federalTaxCalculator.calculate(
                            taxIncome,
                            filingStatus,
                            projectedGovernmentRules);

            MichiganTaxCalculation michiganTaxCalculation =
                    michiganTaxCalculator.calculate(
                            taxIncome.getOrdinaryIncomeBeforeSocialSecurity(),
                            filingStatus,
                            projectedGovernmentRules.getMichiganTaxRules());

            BigDecimal requiredWithdrawal =
                    federalTaxCalculation
                            .getFederalIncomeTax()
                            .add(
                                    michiganTaxCalculation
                                            .incomeTax());

            BigDecimal difference =
                    requiredWithdrawal
                            .subtract(additionalWithdrawal)
                            .abs();

            if (difference.compareTo(
                    TOLERANCE) <= 0) {

                /*
                 * Recalculate once using the final withdrawal
                 * amount so that the returned calculations
                 * are based on the final solution.
                 */
                WithdrawalBreakdown finalTaxFundingBreakdown =
                        withdrawalAllocator
                                .calculateWithdrawalBreakdown(
                                        portfolio,
                                        requiredWithdrawal,
                                        withdrawalStrategy);

                WithdrawalBreakdown finalTotalWithdrawals =
                        existingWithdrawals.plus(
                                finalTaxFundingBreakdown);

                TaxIncome finalTaxIncome =
                        taxIncomeCalculator.calculate(
                                household,
                                projectionDate,
                                finalTotalWithdrawals
                                        .getTaxDeferredWithdrawal(),
                                openingTaxDeferredDistribution,
                                rothConversion,
                                taxableInterestIncome,
                                socialSecurityColaRate,
                                deathAssumptions);

                FederalTaxCalculation finalFederalTaxCalculation =
                        federalTaxCalculator.calculate(
                                finalTaxIncome,
                                filingStatus,
                                projectedGovernmentRules);

                MichiganTaxCalculation finalMichiganTaxCalculation =
                        michiganTaxCalculator.calculate(
                                finalTaxIncome
                                        .getOrdinaryIncomeBeforeSocialSecurity(),
                                filingStatus,
                                projectedGovernmentRules
                                        .getMichiganTaxRules());

                return new TaxFundingResult(
                        requiredWithdrawal,
                        finalFederalTaxCalculation,
                        finalMichiganTaxCalculation);
            }

            additionalWithdrawal =
                    requiredWithdrawal;
        }

        throw new IllegalStateException(
                "Federal tax funding calculation did not converge.");
    }
}
