package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchRequest;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.util.List;

/** Capture on the UI thread, then transfer exclusive ownership of the copy to the job. */
final class SocialSecurityAnalyzerInputs {

    private SocialSecurityAnalyzerInputs() { }

    record Quick(RetirementPlan plan,
            List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected) { }

    static Quick quick(RetirementPlan plan,
            List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected) {
        return new Quick(new RetirementPlanScenarioCopyService().copy(plan), List.copyOf(selected));
    }

    static IntegratedSocialSecurityCompleteStrategySearchRequest exhaustive(RetirementPlan plan) {
        return IntegratedSocialSecurityCompleteStrategySearchRequest.standard(
                new RetirementPlanScenarioCopyService().copy(plan));
    }
}
