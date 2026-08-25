package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FederalTaxRuleProjectionService {

    private final TaxParameterProjectionService
            projectionService;

    public FederalTaxRuleProjectionService() {

        projectionService =
                new TaxParameterProjectionService();
    }

    public FederalTaxRules project(
            FederalTaxRules publishedRules,
            int publishedTaxYear,
            int projectionTaxYear,
            PlanningAssumptions planningAssumptions) {

        Objects.requireNonNull(
                publishedRules,
                "Federal tax rules are required.");

        Objects.requireNonNull(
                planningAssumptions,
                "Planning assumptions are required.");

        BigDecimal projectedStandardDeduction =
                projectionService.project(
                        publishedRules.getStandardDeduction(),
                        publishedTaxYear,
                        projectionTaxYear,
                        planningAssumptions
                                .getTaxAssumptions()
                                .getStandardDeductionGrowthRate());

        List<FederalTaxBracket> projectedBrackets =
                projectTaxBrackets(
                        publishedRules.getTaxBrackets(),
                        publishedTaxYear,
                        projectionTaxYear,
                        planningAssumptions);

        return new FederalTaxRules(
                publishedRules.getFilingStatus(),
                projectedStandardDeduction,
                projectedBrackets);
    }

    private List<FederalTaxBracket> projectTaxBrackets(
            List<FederalTaxBracket> brackets,
            int publishedTaxYear,
            int projectionTaxYear,
            PlanningAssumptions planningAssumptions) {

        List<FederalTaxBracket> projectedBrackets =
                new ArrayList<>();

        for (FederalTaxBracket bracket : brackets) {

            projectedBrackets.add(
                    projectBracket(
                            bracket,
                            publishedTaxYear,
                            projectionTaxYear,
                            planningAssumptions));
        }

        return projectedBrackets;
    }

    private FederalTaxBracket projectBracket(
            FederalTaxBracket bracket,
            int publishedTaxYear,
            int projectionTaxYear,
            PlanningAssumptions planningAssumptions) {

        BigDecimal projectedLowerBound =
                projectionService.project(
                        bracket.getLowerBound(),
                        publishedTaxYear,
                        projectionTaxYear,
                        planningAssumptions
                                .getTaxAssumptions()
                                .getFederalTaxBracketGrowthRate());

        BigDecimal projectedUpperBound =
                bracket.hasUpperBound()
                        ? projectionService.project(
                        bracket.getUpperBound(),
                        publishedTaxYear,
                        projectionTaxYear,
                        planningAssumptions
                                .getTaxAssumptions()
                                .getFederalTaxBracketGrowthRate())
                        : null;

        return new FederalTaxBracket(
                projectedLowerBound,
                projectedUpperBound,
                projectTaxRate(
                        bracket.getTaxRate(),
                        projectionTaxYear,
                        planningAssumptions));
    }

    private BigDecimal projectTaxRate(
            BigDecimal publishedTaxRate,
            int projectionTaxYear,
            PlanningAssumptions planningAssumptions) {

        BigDecimal adjustment = planningAssumptions
                .getTaxAssumptions()
                .getFutureFederalMarginalRateAdjustment();

        Integer effectiveYear = planningAssumptions
                .getTaxAssumptions()
                .getFutureFederalMarginalRateEffectiveYear();

        if (adjustment.signum() == 0
                || effectiveYear == null
                || projectionTaxYear < effectiveYear) {

            return publishedTaxRate;
        }

        BigDecimal adjustedTaxRate =
                publishedTaxRate.add(adjustment);

        if (adjustedTaxRate.signum() < 0
                || adjustedTaxRate.compareTo(BigDecimal.ONE) > 0) {

            throw new IllegalArgumentException(
                    "Future federal marginal rate adjustment produces an invalid tax rate.");
        }

        return adjustedTaxRate;
    }
}
