package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.Objects;

/** Authoritative post-tax settlement of one projection year's household cash. */
public record HouseholdCashSettlement(
        BigDecimal guaranteedIncome,
        BigDecimal projectedPeriodRmdCash,
        BigDecimal spendingWithdrawal,
        BigDecimal taxFundingWithdrawal,
        BigDecimal ordinaryExpenses,
        BigDecimal medicarePremiums,
        BigDecimal incomeTaxes,
        BigDecimal retainedHouseholdSurplus,
        BigDecimal retainedFromExcessRmd,
        BigDecimal retainedFromGuaranteedIncome) {

    private static final BigDecimal RECONCILIATION_TOLERANCE =
            new BigDecimal("0.02");

    public HouseholdCashSettlement {
        requireNonNegative(guaranteedIncome, "Guaranteed income");
        requireNonNegative(projectedPeriodRmdCash, "Projected-period RMD cash");
        requireNonNegative(spendingWithdrawal, "Spending withdrawal");
        requireNonNegative(taxFundingWithdrawal, "Tax-funding withdrawal");
        requireNonNegative(ordinaryExpenses, "Ordinary expenses");
        requireNonNegative(medicarePremiums, "Medicare premiums");
        requireNonNegative(incomeTaxes, "Income taxes");
        requireNonNegative(retainedHouseholdSurplus, "Retained household surplus");
        requireNonNegative(retainedFromExcessRmd, "Retained from excess RMD");
        requireNonNegative(retainedFromGuaranteedIncome,
                "Retained from guaranteed income");

        BigDecimal attributed = retainedFromExcessRmd
                .add(retainedFromGuaranteedIncome);
        if (attributed.compareTo(retainedHouseholdSurplus) != 0) {
            throw new IllegalArgumentException(
                    "Retained-surplus attribution must equal total retained surplus.");
        }

        BigDecimal sources = guaranteedIncome
                .add(projectedPeriodRmdCash)
                .add(spendingWithdrawal)
                .add(taxFundingWithdrawal);
        BigDecimal uses = ordinaryExpenses
                .add(medicarePremiums)
                .add(incomeTaxes)
                .add(retainedHouseholdSurplus);
        if (sources.subtract(uses).abs()
                .compareTo(RECONCILIATION_TOLERANCE) > 0) {
            throw new IllegalArgumentException(
                    "Household cash sources and uses do not reconcile: sources="
                            + sources + ", uses=" + uses);
        }
    }

    public static HouseholdCashSettlement calculate(
            BigDecimal guaranteedIncome,
            BigDecimal projectedPeriodRmdCash,
            BigDecimal spendingWithdrawal,
            BigDecimal taxFundingWithdrawal,
            BigDecimal ordinaryExpenses,
            BigDecimal medicarePremiums,
            BigDecimal incomeTaxes) {

        BigDecimal nonTaxObligations =
                ordinaryExpenses.add(medicarePremiums);

        BigDecimal guaranteedIncomeRemaining =
                guaranteedIncome.subtract(nonTaxObligations)
                        .max(BigDecimal.ZERO);
        BigDecimal obligationsAfterGuaranteedIncome =
                nonTaxObligations.subtract(guaranteedIncome)
                        .max(BigDecimal.ZERO);
        BigDecimal rmdRemaining =
                projectedPeriodRmdCash
                        .subtract(obligationsAfterGuaranteedIncome)
                        .max(BigDecimal.ZERO);

        BigDecimal retainedFromGuaranteedIncome =
                guaranteedIncomeRemaining.subtract(incomeTaxes)
                        .max(BigDecimal.ZERO);
        BigDecimal taxesAfterGuaranteedIncome =
                incomeTaxes.subtract(guaranteedIncomeRemaining)
                        .max(BigDecimal.ZERO);
        BigDecimal retainedFromExcessRmd =
                rmdRemaining.subtract(taxesAfterGuaranteedIncome)
                        .max(BigDecimal.ZERO);
        BigDecimal retainedSurplus =
                retainedFromGuaranteedIncome.add(retainedFromExcessRmd);

        return new HouseholdCashSettlement(
                guaranteedIncome,
                projectedPeriodRmdCash,
                spendingWithdrawal,
                taxFundingWithdrawal,
                ordinaryExpenses,
                medicarePremiums,
                incomeTaxes,
                retainedSurplus,
                retainedFromExcessRmd,
                retainedFromGuaranteedIncome);
    }

    public static HouseholdCashSettlement zero() {
        return calculate(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    public BigDecimal availableHouseholdCashBeforeTaxes() {
        return guaranteedIncome
                .add(projectedPeriodRmdCash)
                .subtract(ordinaryExpenses)
                .subtract(medicarePremiums)
                .max(BigDecimal.ZERO);
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required.");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative.");
        }
    }
}
