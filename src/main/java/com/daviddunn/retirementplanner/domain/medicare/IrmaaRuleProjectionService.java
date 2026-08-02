package com.daviddunn.retirementplanner.domain.medicare;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
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

        BigDecimal inflationMultiplier =
                BigDecimal.ONE.add(
                                planningAssumptions
                                        .getExpectedAnnualInflationRate())
                        .pow(yearsToProject);

        List<IrmaaBracket> projectedBrackets =
                new ArrayList<>();

        for (IrmaaBracket bracket :
                publishedRules.getBrackets()) {

            BigDecimal projectedMinimumIncome =
                    bracket.getMinimumModifiedAdjustedGrossIncome()
                            .multiply(inflationMultiplier)
                            .setScale(
                                    0,
                                    RoundingMode.HALF_UP);

            BigDecimal projectedMaximumIncome =
                    null;

            if (bracket.getMaximumModifiedAdjustedGrossIncome()
                    != null) {

                projectedMaximumIncome =
                        bracket.getMaximumModifiedAdjustedGrossIncome()
                                .multiply(inflationMultiplier)
                                .setScale(
                                        0,
                                        RoundingMode.HALF_UP);
            }

            projectedBrackets.add(
                    new IrmaaBracket(
                            bracket.getFilingStatus(),
                            projectedMinimumIncome,
                            projectedMaximumIncome,
                            bracket.getMonthlyPartBPremium(),
                            bracket.getMonthlyPartDPremium()));
        }

        return new IrmaaRules(
                projectedBrackets);
    }
}