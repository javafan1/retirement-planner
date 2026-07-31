package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class TaxIncomeCalculator {

    public TaxIncome calculate(
            Household household,
            LocalDate projectionDate,
            BigDecimal taxDeferredWithdrawals) {

        Objects.requireNonNull(
                household,
                "Household is required.");

        Objects.requireNonNull(
                projectionDate,
                "Projection date is required.");

        Objects.requireNonNull(
                taxDeferredWithdrawals,
                "Tax-deferred withdrawals are required.");

        if (taxDeferredWithdrawals.signum() < 0) {
            throw new IllegalArgumentException(
                    "Tax-deferred withdrawals cannot be negative.");
        }

        BigDecimal pensionIncome =
                BigDecimal.ZERO;

        BigDecimal socialSecurityIncome =
                BigDecimal.ZERO;

        IncomeTotals primaryTotals =
                calculateIncome(
                        household.getPrimaryPerson(),
                        projectionDate);

        pensionIncome =
                pensionIncome.add(
                        primaryTotals.pensionIncome());

        socialSecurityIncome =
                socialSecurityIncome.add(
                        primaryTotals.socialSecurityIncome());

        IncomeTotals spouseTotals =
                calculateIncome(
                        household.getSpouse(),
                        projectionDate);

        pensionIncome =
                pensionIncome.add(
                        spouseTotals.pensionIncome());

        socialSecurityIncome =
                socialSecurityIncome.add(
                        spouseTotals.socialSecurityIncome());

        return new TaxIncome(
                pensionIncome,
                socialSecurityIncome,
                taxDeferredWithdrawals);
    }

    private IncomeTotals calculateIncome(
            Person person,
            LocalDate projectionDate) {

        BigDecimal pensionIncome =
                BigDecimal.ZERO;

        BigDecimal socialSecurityIncome =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            BigDecimal annualIncome =
                    income.getAnnualIncome(
                            person,
                            projectionDate);

            if (income instanceof Pension) {

                pensionIncome =
                        pensionIncome.add(
                                annualIncome);

            } else if (income instanceof SocialSecurityIncome) {

                socialSecurityIncome =
                        socialSecurityIncome.add(
                                annualIncome);
            }
        }

        return new IncomeTotals(
                pensionIncome,
                socialSecurityIncome);
    }

//    private IncomeTotals calculateIncome(
//            Person person,
//            LocalDate projectionDate) {
//
//        BigDecimal pensionIncome =
//                BigDecimal.ZERO;
//
//        BigDecimal socialSecurityIncome =
//                BigDecimal.ZERO;
//
//        for (IncomeSource income :
//                person.getIncomeSources()) {
//
//            BigDecimal annualIncome =
//                    income.getAnnualIncome(
//                            projectionDate);
//
//            if (income instanceof Pension) {
//
//                pensionIncome =
//                        pensionIncome.add(
//                                annualIncome);
//
//            } else if (income instanceof SocialSecurityIncome) {
//
//                socialSecurityIncome =
//                        socialSecurityIncome.add(
//                                annualIncome);
//            }
//        }
//
//        return new IncomeTotals(
//                pensionIncome,
//                socialSecurityIncome);
//    }

    private record IncomeTotals(
            BigDecimal pensionIncome,
            BigDecimal socialSecurityIncome) {
    }
}