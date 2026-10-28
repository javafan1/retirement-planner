package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Independently calculated small histories for association tests, not capacity evidence. */
public final class Phase12EHistoryFixtures {
    public static Map<AnalysisKind, MonteCarloSnapshot> calculate() {
        var values = new EnumMap<AnalysisKind, MonteCarloSnapshot>(AnalysisKind.class);
        var start = Instant.parse("2026-10-20T12:00:00Z");
        for (boolean couple : new boolean[]{false, true}) for (boolean mortality : new boolean[]{false, true}) for (boolean paired : new boolean[]{false, true}) {
            var plan = MonteCarloCapacityProbeTest.fixture(couple);
            var settings = MonteCarloSettings.forPlan(plan, 3, 417, new BigDecimal("0.12"));
            var request = couple ? new MonteCarloMortalityRequest(plan, settings,
                    LongevitySessionSettings.defaults(plan.getPlanningAssumptions().getProjectionStartDate()), 67)
                    : MonteCarloMortalityRequest.individual(plan, settings, SocialSecurityMortalityAdjustment.standard());
            MonteCarloSnapshot snapshot;
            if (paired) {
                var alternative = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
                alternative.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("999999.0012300"));
                MonteCarloStrategyComparisonRequest.Assumptions assumptions = mortality ? new MonteCarloStrategyComparisonRequest.Longevity(request)
                        : new MonteCarloStrategyComparisonRequest.Fixed(settings, plan.getPlanningAssumptions().getProjectionStartDate(), 2076,
                        couple ? MonteCarloLifetime.couple(com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario.bothSurvive())
                                : new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty())));
                var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(new MonteCarloStrategyComparisonRequest(
                        new MonteCarloStrategyCandidate("Current Plan", plan), new MonteCarloStrategyCandidate("Saved Baseline", alternative), assumptions));
                snapshot = MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,
                        mortality ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN, "Frozen test paired assumptions", 1000), start, start.plusSeconds(10));
            } else {
                var run = mortality ? new MonteCarloRunService().runMortality(plan, request, AnalysisProgressListener.none(), AnalysisCancellationToken.none())
                        : new MonteCarloRunService().run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
                snapshot = MonteCarloSnapshotPresentationMapper.capture(run, plan, start, start.plusSeconds(10));
            }
            values.put(AnalysisKind.valueOf((couple ? "COUPLE" : "INDIVIDUAL") + "_MONTE_CARLO_" + (paired ? "PAIRED_" : "") + (mortality ? "LONGEVITY" : "FIXED")), snapshot);
        }
        return Map.copyOf(values);
    }
}
