package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityIncomeCalculator;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class TaxIncomeCalculator {

    private final HouseholdSocialSecurityIncomeCalculator
            householdSocialSecurityIncomeCalculator =
            new HouseholdSocialSecurityIncomeCalculator();

    public TaxIncome calculate(
            Household household,
            LocalDate projectionDate,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome) {

        return calculate(
                household,
                projectionDate,
                taxDeferredWithdrawals,
                rothConversion,
                taxableInterestIncome,
                null);
    }

    public TaxIncome calculate(
            Household household,
            LocalDate projectionDate,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate) {

        return calculate(
                household,
                projectionDate,
                taxDeferredWithdrawals,
                rothConversion,
                taxableInterestIncome,
                socialSecurityColaRate,
                null);
    }

    public TaxIncome calculate(
            Household household,
            LocalDate projectionDate,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate,
            DeathScenarioAssumptions deathAssumptions) {

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

        BigDecimal pensionIncome =
                BigDecimal.ZERO;

        BigDecimal socialSecurityIncome =
                BigDecimal.ZERO;

        IncomeTotals primaryTotals =
                calculateIncome(
                        household.getPrimaryPerson(),
                        projectionDate,
                        socialSecurityColaRate);

        pensionIncome =
                pensionIncome.add(
                        primaryTotals.pensionIncome());

        socialSecurityIncome =
                socialSecurityIncome.add(
                        primaryTotals.socialSecurityIncome());

        IncomeTotals spouseTotals =
                calculateIncome(
                        household.getSpouse(),
                        projectionDate,
                        socialSecurityColaRate);

        pensionIncome =
                pensionIncome.add(
                        spouseTotals.pensionIncome());

        socialSecurityIncome =
                socialSecurityIncome.add(
                        spouseTotals.socialSecurityIncome());

        if (deathAssumptions != null
                && socialSecurityColaRate != null) {

            socialSecurityIncome =
                    householdSocialSecurityIncomeCalculator
                            .calculateAnnualIncome(
                                    household,
                                    projectionDate,
                                    deathAssumptions,
                                    socialSecurityColaRate);
        }

        return new TaxIncome(
                pensionIncome,
                socialSecurityIncome,
                taxDeferredWithdrawals,
                rothConversion,
                taxableInterestIncome);
    }

    private IncomeTotals calculateIncome(
            Person person,
            LocalDate projectionDate,
            BigDecimal socialSecurityColaRate) {

        BigDecimal pensionIncome =
                BigDecimal.ZERO;

        BigDecimal socialSecurityIncome =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            BigDecimal annualIncome;

            if (income instanceof SocialSecurityIncome socialSecurity
                    && socialSecurityColaRate != null) {

                annualIncome = socialSecurity.getAnnualIncome(
                        person,
                        projectionDate,
                        socialSecurityColaRate);

            } else {

                annualIncome = income.getAnnualIncome(
                        person,
                        projectionDate);
            }

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


    private record IncomeTotals(
            BigDecimal pensionIncome,
            BigDecimal socialSecurityIncome) {
    }
}
