package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitCalculator;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class IncomeSummaryService {

    public IncomeSummary summarize(
            RetirementPlan retirementPlan) {

        Objects.requireNonNull(
                retirementPlan,
                "Retirement plan is required.");

        Person primary =
                retirementPlan.getHousehold()
                        .getPrimaryPerson();

        Person spouse =
                retirementPlan.getHousehold()
                        .getSpouse();

        return new IncomeSummary(

                calculateSocialSecurity(primary),
                calculateSocialSecurity(spouse),

                calculatePension(primary),
                calculatePension(spouse)
        );
    }

    private BigDecimal calculateSocialSecurity(
            Person person) {

        BigDecimal total =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            if (income instanceof SocialSecurityIncome socialSecurity) {

                total = total.add(
                        SocialSecurityBenefitCalculator
                                .calculateMonthlyBenefit(
                                        socialSecurity.getFullRetirementMonthlyBenefit(),
                                        person.getBirthDate(),
                                        socialSecurity.getClaimingAge()));
            }
        }

        return total.setScale(
                2,
                RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePension(
            Person person) {

        BigDecimal total =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            if (income instanceof Pension pension) {

                total = total.add(
                        pension.getMonthlyBenefit());
            }
        }

        return total.setScale(
                2,
                RoundingMode.HALF_UP);
    }
}