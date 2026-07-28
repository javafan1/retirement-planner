package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectedWithdrawalAllocator;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;
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

    private final TaxIncomeCalculator taxIncomeCalculator;
    private final FederalTaxCalculator federalTaxCalculator;
    private final ProjectedWithdrawalAllocator withdrawalAllocator;

    public TaxFundingCalculator() {

        this.taxIncomeCalculator =
                new TaxIncomeCalculator();

        this.federalTaxCalculator =
                new FederalTaxCalculator();

        this.withdrawalAllocator =
                new ProjectedWithdrawalAllocator();
    }

    public TaxFundingResult calculate(
            Household household,
            LocalDate projectionDate,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown existingWithdrawals,
            WithdrawalStrategy withdrawalStrategy,
            TaxFilingStatus filingStatus,
            GovernmentRules governmentRules) {

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
                governmentRules,
                "Government rules are required.");

        BigDecimal additionalWithdrawal =
                BigDecimal.ZERO;

        FederalTaxCalculation federalTaxCalculation =
                null;

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
                                    .getTaxDeferredWithdrawal());

            federalTaxCalculation =
                    federalTaxCalculator.calculate(
                            taxIncome,
                            filingStatus,
                            governmentRules);

            BigDecimal requiredWithdrawal =
                    federalTaxCalculation
                            .getFederalIncomeTax();

            /*
             * We have converged when the amount
             * required to fund tax is within one
             * cent of our current estimate.
             */
            BigDecimal difference =
                    requiredWithdrawal
                            .subtract(additionalWithdrawal)
                            .abs();

            if (difference.compareTo(
                    TOLERANCE) <= 0) {

                /*
                 * Recalculate once using the final withdrawal
                 * amount so that the returned tax calculation
                 * and returned funding withdrawal are based on
                 * the same value.
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
                                        .getTaxDeferredWithdrawal());

                FederalTaxCalculation finalTaxCalculation =
                        federalTaxCalculator.calculate(
                                finalTaxIncome,
                                filingStatus,
                                governmentRules);

                return new TaxFundingResult(
                        requiredWithdrawal,
                        finalTaxCalculation);
            }

            additionalWithdrawal =
                    requiredWithdrawal;
        }

        throw new IllegalStateException(
                "Federal tax funding calculation did not converge.");
    }
}