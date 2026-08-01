package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxRuleProjectionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GovernmentRuleProjectionService {

    private final FederalTaxRuleProjectionService
            federalTaxRuleProjectionService;

    private final MichiganTaxRuleProjectionService
            michiganTaxRuleProjectionService;

    public GovernmentRuleProjectionService() {

        this.federalTaxRuleProjectionService =
                new FederalTaxRuleProjectionService();

        this.michiganTaxRuleProjectionService =
                new MichiganTaxRuleProjectionService();
    }

    public GovernmentRules project(
            GovernmentRules publishedRules,
            PlanningAssumptions planningAssumptions,
            int projectionYear) {

        Objects.requireNonNull(
                publishedRules,
                "Published government rules are required.");

        Objects.requireNonNull(
                planningAssumptions,
                "Planning assumptions are required.");

        List<FederalTaxRules> projectedFederalRules =
                new ArrayList<>();

        for (FederalTaxRules rules :
                publishedRules.getFederalTaxRules()) {

            projectedFederalRules.add(
                    federalTaxRuleProjectionService.project(
                            rules,
                            publishedRules.getTaxYear(),
                            projectionYear,
                            planningAssumptions));
        }

        MichiganTaxRules projectedMichiganRules =
                michiganTaxRuleProjectionService.project(
                        publishedRules.getMichiganTaxRules(),
                        publishedRules.getTaxYear(),
                        projectionYear,
                        planningAssumptions);

        return new GovernmentRules(
                publishedRules.getRulesVersion(),
                projectionYear,
                publishedRules.getEffectiveDate(),
                projectedFederalRules,
                projectedMichiganRules,
                publishedRules.getSocialSecurityTaxRules(),
                publishedRules.getRmdRules());
    }
}