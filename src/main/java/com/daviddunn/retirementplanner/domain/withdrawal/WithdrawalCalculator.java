package com.daviddunn.retirementplanner.domain.withdrawal;

import java.math.BigDecimal;

public class WithdrawalCalculator {

    public WithdrawalResult calculateWithdrawal(
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses) {

        BigDecimal withdrawal =
                annualExpenses
                        .subtract(guaranteedIncome)
                        .max(BigDecimal.ZERO);

        return new WithdrawalResult(withdrawal);
    }
}