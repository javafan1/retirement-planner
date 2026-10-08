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
}
