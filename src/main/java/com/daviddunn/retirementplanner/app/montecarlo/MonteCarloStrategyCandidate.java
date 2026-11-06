package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.util.Objects;

/** A complete privately frozen financial input; no generated scenario paths are retained. */
public final class MonteCarloStrategyCandidate {
    private final String label;
    private final RetirementPlan frozenPlan;

    public MonteCarloStrategyCandidate(String label, RetirementPlan plan) {
        this.label = Objects.requireNonNull(label);
        if (label.isBlank()) {
            throw new IllegalArgumentException("A strategy label is required.");
        }
        frozenPlan = new RetirementPlanScenarioCopyService().copy(Objects.requireNonNull(plan));
    }

    public String label() {
        return label;
    }
    /** Immutable effective election provenance for accepted analyzer parameters. */
    public Integer survivorClaimingAge() {
        return frozenPlan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge();
    }

    /** One isolated working copy per run, never one per world. The frozen source is not exposed. */
    RetirementPlan copyForRun() {
        return new RetirementPlanScenarioCopyService().copy(frozenPlan);
    }

    /** Pure validation on private frozen inputs; no mutable plan escapes and no execution is admitted. */
    void validateExpenseConfiguration() {
        ExpenseVolatilityAdmission.requireStandaloneSupported(frozenPlan);
    }
}
