package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.util.Objects;

public final class EligibleTraditionalBalanceCalculator {

    public BigDecimal calculate(
            AccountPortfolio accountPortfolio) {

        Objects.requireNonNull(
                accountPortfolio,
                "Account portfolio is required.");

        BigDecimal total =
                BigDecimal.ZERO;

        for (Account account :
                accountPortfolio.getAccounts(
                        AccountOwnership.PRIMARY)) {

            if (!account.isEligibleForRothConversion()) {
                continue;
            }

            total =
                    total.add(
                            account.getCurrentBalance());
        }

        return total;
    }
}