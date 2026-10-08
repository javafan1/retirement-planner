package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.PersonInformationValidation;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

/** Session-local plan; never reads or writes the active controller or a repository. */
public final class NewPlanDraft {

    private final RetirementPlan plan = RetirementPlanFactory.createSinglePersonPlan();

    public RetirementPlan getPlan() {

        return plan;
    }

    public RetirementPlan complete() {

        plan.getHousehold().members().forEach(person ->
                PersonInformationValidation.validate(person.getBirthDate(), person.getMortalityCategory()));
        plan.validateHouseholdReferences();
        return plan;
    }

    /** Keep the same DOB/claiming-age election convention as the existing Social Security editor. */
    public void refreshSocialSecurityDates() {
        plan.getHousehold().members().forEach(person -> {
            for (var income : person.getIncomeSources()) {
                if (income instanceof com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome socialSecurity) {
                    var start = com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitStartDateCalculator
                            .calculate(person, socialSecurity.getClaimingAge()).orElseThrow();
                    if (!start.equals(socialSecurity.getStartDate())) {
                        person.replaceIncomeSource(socialSecurity,
                                new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(
                                        socialSecurity.getName(), socialSecurity.getOwnership(), start,
                                        socialSecurity.getEndDate(), socialSecurity.getFullRetirementMonthlyBenefit(),
                                        socialSecurity.getClaimingAge(), socialSecurity.getAnnualColaRate(),
                                        socialSecurity.getBenefitValuationYear()));
                    }
                }
            }
        });
    }
}
