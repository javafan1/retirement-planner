package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;


import java.math.BigDecimal;
import java.util.Objects;

public final class RmdBalanceAggregator {

    public BigDecimal getBalance(
            AccountPortfolio portfolio,
            AccountOwnership ownership,
            RmdAccountCategory category) {

        Objects.requireNonNull(
                portfolio,
                "Account portfolio is required.");

        Objects.requireNonNull(
                ownership,
                "Account ownership is required.");

        Objects.requireNonNull(
                category,
                "RMD account category is required.");

        return portfolio
                .getAccounts(ownership)
                .stream()
                .filter(account ->
                        account.getType()
                                .isSubjectToOwnerRmd())
                .filter(account ->
                        account.getType()
                                .getRmdAccountCategory()
                                == category)
                .map(Account::getCurrentBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }
}