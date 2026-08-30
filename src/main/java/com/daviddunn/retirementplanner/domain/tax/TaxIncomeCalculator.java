package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityIncomeCalculator;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
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
                BigDecimal.ZERO,
                rothConversion,
                taxableInterestIncome,
                null,
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
                BigDecimal.ZERO,
                rothConversion,
                taxableInterestIncome,
                socialSecurityColaRate,
                null);
    }

    public TaxIncome calculate(
            Household household,
            LocalDate projectionDate,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal openingTaxDeferredDistribution,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate,
            DeathScenarioAssumptions deathAssumptions) {

        return calculate(
                household,
                projectionDate,
                taxDeferredWithdrawals,
                openingTaxDeferredDistribution,
                rothConversion,
                taxableInterestIncome,
                socialSecurityColaRate,
                deathAssumptions,
                null);
    }

    public TaxIncome calculate(
            Household household,
            LocalDate projectionDate,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal openingTaxDeferredDistribution,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate,
            DeathScenarioAssumptions deathAssumptions,
            HouseholdSocialSecurityResult socialSecurityResult) {

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
                        socialSecurityColaRate,
                        socialSecurityResult != null);

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
                        socialSecurityColaRate,
                        socialSecurityResult != null);

        pensionIncome =
                pensionIncome.add(
                        spouseTotals.pensionIncome());

        socialSecurityIncome =
                socialSecurityIncome.add(
                        spouseTotals.socialSecurityIncome());

        if (socialSecurityResult != null) {

            socialSecurityIncome =
                    socialSecurityResult.householdBenefit();

        } else if (deathAssumptions != null
                && socialSecurityColaRate != null) {

            socialSecurityIncome =
                    householdSocialSecurityIncomeCalculator
                            .calculateAnnualIncome(
                                    household,
                                    projectionDate,
                                    deathAssumptions,
                                    socialSecurityColaRate);
        }

        Objects.requireNonNull(
                openingTaxDeferredDistribution,
                "Opening tax-deferred distribution is required.");

        if (openingTaxDeferredDistribution.signum() < 0) {
            throw new IllegalArgumentException(
                    "Opening tax-deferred distribution cannot be negative.");
        }

        return new TaxIncome(
                pensionIncome,
                socialSecurityIncome,
                taxDeferredWithdrawals.add(
                        openingTaxDeferredDistribution),
                rothConversion,
                taxableInterestIncome);
    }

    public TaxIncome calculate(
            Household household,
            LocalDate projectionDate,
            BigDecimal taxDeferredWithdrawals,
            BigDecimal rothConversion,
            BigDecimal taxableInterestIncome,
            BigDecimal socialSecurityColaRate,
            DeathScenarioAssumptions deathAssumptions) {

        return calculate(
                household,
                projectionDate,
                taxDeferredWithdrawals,
                BigDecimal.ZERO,
                rothConversion,
                taxableInterestIncome,
                socialSecurityColaRate,
                deathAssumptions);
    }

    private IncomeTotals calculateIncome(
            Person person,
            LocalDate projectionDate,
            BigDecimal socialSecurityColaRate,
            boolean socialSecurityAlreadyCalculated) {

        BigDecimal pensionIncome =
                BigDecimal.ZERO;

        BigDecimal socialSecurityIncome =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            if (socialSecurityAlreadyCalculated
                    && income instanceof SocialSecurityIncome) {
                continue;
            }

            BigDecimal annualIncome;

            if (income instanceof SocialSecurityIncome socialSecurity
                    && socialSecurityColaRate != null) {

                annualIncome = socialSecurity.getAnnualIncome(
                        person,
                        projectionDate,
                        socialSecurityColaRate);

            } else if (income instanceof SocialSecurityIncome) {

                throw new IllegalArgumentException(
                        "Social Security tax income requires the economic "
                                + "Social Security COLA rate or a calculated "
                                + "household Social Security result.");

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
