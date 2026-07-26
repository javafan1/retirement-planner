package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TaxableFirstWithdrawalStrategy
        implements WithdrawalStrategy {

    @Override
    public List<ProjectedAccountBalance> orderAccounts(
            ProjectedPortfolio portfolio) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        return portfolio
                .getAccountBalances()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                projected ->
                                        priority(
                                                projected
                                                        .getAccount()
                                                        .getType()
                                                        .getTaxTreatment())))
                .toList();
    }

    private int priority(
            TaxTreatment taxTreatment) {

        return switch (taxTreatment) {

            case CASH ->
                    1;

            case TAXABLE ->
                    2;

            case TAX_DEFERRED ->
                    3;

            case ROTH ->
                    4;
        };
    }
}