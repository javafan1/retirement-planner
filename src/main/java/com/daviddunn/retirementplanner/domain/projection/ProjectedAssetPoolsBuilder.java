package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectedAssetPoolsBuilder {

    public ProjectedAssetPools build(
            AccountPortfolio accountPortfolio) {

        Objects.requireNonNull(
                accountPortfolio,
                "Account portfolio is required.");

        BigDecimal taxableBalance =
                BigDecimal.ZERO;

        BigDecimal taxDeferredBalance =
                BigDecimal.ZERO;

        BigDecimal rothBalance =
                BigDecimal.ZERO;

        for (Account account :
                accountPortfolio.getAccounts()) {

            switch (account.getProjectionAssetType()) {

                case TAXABLE ->

                        taxableBalance =
                                taxableBalance.add(
                                        account.getCurrentBalance());

                case TAX_DEFERRED ->

                        taxDeferredBalance =
                                taxDeferredBalance.add(
                                        account.getCurrentBalance());

                case ROTH ->

                        rothBalance =
                                rothBalance.add(
                                        account.getCurrentBalance());
            }
        }

        return new ProjectedAssetPools(
                taxableBalance,
                taxDeferredBalance,
                rothBalance);
    }
}