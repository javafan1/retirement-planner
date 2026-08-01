package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;
import com.daviddunn.retirementplanner.domain.tax.TaxParameterProjectionService;

import java.math.BigDecimal;
import java.util.Objects;

public final class MichiganTaxRuleProjectionService {

    private final TaxParameterProjectionService
            projectionService;

    public MichiganTaxRuleProjectionService() {

        projectionService =
                new TaxParameterProjectionService();
    }

    public MichiganTaxRules project(
            MichiganTaxRules publishedRules,
            int publishedTaxYear,
            int projectionYear,
            PlanningAssumptions planningAssumptions) {

        Objects.requireNonNull(
                publishedRules,
                "Michigan tax rules are required.");

        Objects.requireNonNull(
                planningAssumptions,
                "Planning assumptions are required.");

        BigDecimal projectedSingleDeduction =
                projectionService.project(
                        publishedRules.getRetirementDeductionSingle(),
                        publishedTaxYear,
                        projectionYear,
                        planningAssumptions);

        BigDecimal projectedMarriedDeduction =
                projectionService.project(
                        publishedRules.getRetirementDeductionMarried(),
                        publishedTaxYear,
                        projectionYear,
                        planningAssumptions);

        /*
         * Tax rate is not indexed.
         */
        return new MichiganTaxRules(
                publishedRules.getIncomeTaxRate(),
                projectedSingleDeduction,
                projectedMarriedDeduction);
    }
}