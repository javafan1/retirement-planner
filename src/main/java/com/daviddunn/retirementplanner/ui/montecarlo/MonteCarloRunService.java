package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;

import java.util.List;

/**
 * Worker-only adapter: one normal reference and one sequential analysis of a captured plan.
 */
public final class MonteCarloRunService {
    public MonteCarloRun run(RetirementPlan frozenPlan, MonteCarloSettings settings, Projection cachedReference,
                             AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        long started = System.nanoTime();
        cancellation.throwIfCancellationRequested();
        ProjectionEngine engine = new ProjectionEngine();
        ProjectionExecutionResult reference = cachedReference == null
                ? engine.projectWithOutcome(frozenPlan)
                : new ProjectionExecutionResult.Completed(cachedReference.getYears());
        cancellation.throwIfCancellationRequested();
        var people = BreakEvenPlanSummary.from(frozenPlan.getHousehold());
        int first = frozenPlan.getPlanningAssumptions().getProjectionStartDate().getYear();
        int last = Math.addExact(first, frozenPlan.getPlanningAssumptions().getProjectionLengthYears() - 1);
        var actual = ProjectionChartModel.from(reference.completedYears(), List.of(), people,
                frozenPlan.getAccountPortfolio().getAccounts());
        var context = new ProjectionChartModel(actual.years(), ProjectionChartModel.claims(people, first, last),
                actual.rothPeriods(), actual.rmdPeriods());
        var result = new MonteCarloAnalyzer(engine).analyzeWithReferenceOutcome(
                frozenPlan, settings, reference, progress, cancellation);
        cancellation.throwIfCancellationRequested();
        return new MonteCarloRun(result, MonteCarloFanModel.from(result, context), people, first, last,
                reference instanceof ProjectionExecutionResult.InsufficientFunds, System.nanoTime() - started);
    }
}
