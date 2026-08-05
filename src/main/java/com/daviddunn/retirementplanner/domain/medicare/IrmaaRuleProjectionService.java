package com.daviddunn.retirementplanner.domain.medicare;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.projection.ProjectionMath;
import com.daviddunn.retirementplanner.domain.rules.IrmaaBracket;
import com.daviddunn.retirementplanner.domain.rules.IrmaaRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IrmaaRuleProjectionService {

    public IrmaaRules project(

            IrmaaRules publishedRules,

            int publishedTaxYear,

            int projectedTaxYear,

            PlanningAssumptions planningAssumptions) {

        Objects.requireNonNull(
                publishedRules,
                "Published IRMAA rules are required.");

        Objects.requireNonNull(
                planningAssumptions,
                "Planning assumptions are required.");

        int yearsToProject =
                projectedTaxYear - publishedTaxYear;

        /*
         * IRMAA income thresholds are projected
         * using the general inflation rate.
         */
//        BigDecimal incomeThresholdMultiplier =
//                BigDecimal.ONE
//                        .add(
//                                planningAssumptions
//                                        .getExpectedAnnualInflationRate())
//                        .pow(yearsToProject);

        BigDecimal incomeThresholdMultiplier =
                ProjectionMath.calculateGrowthMultiplier(
                        planningAssumptions
                                .getExpectedAnnualInflationRate(),
                        yearsToProject);



        /*
         * Medicare premiums are projected
         * using the healthcare inflation rate.
         */
//        BigDecimal healthcareCostMultiplier =
//                BigDecimal.ONE
//                        .add(
//                                planningAssumptions
//                                        .getHealthcareInflationRate())
//                        .pow(yearsToProject);
        BigDecimal healthcareCostMultiplier =
                ProjectionMath.calculateGrowthMultiplier(
                        planningAssumptions
                                .getHealthcareInflationRate(),
                        yearsToProject);

        List<IrmaaBracket> projectedBrackets =
                new ArrayList<>();

        for (IrmaaBracket bracket :
                publishedRules.getBrackets()) {

            BigDecimal projectedMinimumIncome =
                    bracket.getMinimumModifiedAdjustedGrossIncome()
                            .multiply(
                                    incomeThresholdMultiplier)
                            .setScale(
                                    0,
                                    RoundingMode.HALF_UP);

            BigDecimal projectedMaximumIncome =
                    null;

            if (bracket.getMaximumModifiedAdjustedGrossIncome()
                    != null) {

                projectedMaximumIncome =
                        bracket.getMaximumModifiedAdjustedGrossIncome()
                                .multiply(
                                        incomeThresholdMultiplier)
                                .setScale(
                                        0,
                                        RoundingMode.HALF_UP);
            }

            BigDecimal projectedMonthlyPartBPremium =
                    bracket.getMonthlyPartBPremium()
                            .multiply(
                                    healthcareCostMultiplier)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP);

            BigDecimal projectedMonthlyPartDPremium =
                    bracket.getMonthlyPartDPremium()
                            .multiply(
                                    healthcareCostMultiplier)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP);

            projectedBrackets.add(
                    new IrmaaBracket(
                            bracket.getFilingStatus(),
                            projectedMinimumIncome,
                            projectedMaximumIncome,
                            projectedMonthlyPartBPremium,
                            projectedMonthlyPartDPremium));
        }

        return new IrmaaRules(projectedBrackets);
    }

    private static BigDecimal calculateGrowthMultiplier(
            BigDecimal annualRate,
            int years) {

        return BigDecimal.ONE
                .add(annualRate)
                .pow(years);
    }
}