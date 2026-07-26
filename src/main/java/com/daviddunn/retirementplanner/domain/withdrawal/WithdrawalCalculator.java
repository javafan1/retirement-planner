package com.daviddunn.retirementplanner.domain.withdrawal;

import java.math.BigDecimal;
import java.util.Objects;

public class WithdrawalCalculator {

    /*
     * Existing calculation path.
     *
     * Retained for existing callers and tests.
     * With no RMD supplied, the withdrawal is
     * simply the cash-flow shortfall.
     */
    public WithdrawalResult calculateWithdrawal(
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses) {

        return calculateWithdrawal(
                guaranteedIncome,
                annualExpenses,
                BigDecimal.ZERO);
    }

    /*
     * RMD-aware calculation path.
     */
    public WithdrawalResult calculateWithdrawal(
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses,
            BigDecimal requiredMinimumDistribution) {

        Objects.requireNonNull(
                guaranteedIncome,
                "Guaranteed income is required.");

        Objects.requireNonNull(
                annualExpenses,
                "Annual expenses are required.");

        Objects.requireNonNull(
                requiredMinimumDistribution,
                "Required minimum distribution is required.");

        if (requiredMinimumDistribution.signum() < 0) {
            throw new IllegalArgumentException(
                    "Required minimum distribution cannot be negative.");
        }

        BigDecimal cashFlowNeed =
                annualExpenses
                        .subtract(guaranteedIncome)
                        .max(BigDecimal.ZERO);

        /*
         * The RMD is not added to the cash-flow
         * withdrawal.
         *
         * The portfolio must distribute at least
         * enough to satisfy BOTH requirements.
         */
        BigDecimal totalWithdrawal =
                cashFlowNeed.max(
                        requiredMinimumDistribution);

        return new WithdrawalResult(
                cashFlowNeed,
                requiredMinimumDistribution,
                totalWithdrawal);
    }
}