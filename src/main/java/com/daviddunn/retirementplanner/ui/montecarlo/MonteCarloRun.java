package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalysisResult;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnalysisResult;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;

/**
 * Frozen result-time metadata. No references to the mutable active plan.
 */
public record MonteCarloRun(
        Result analysis,
        MonteCarloFanModel fan,
        BreakEvenPlanSummary people,
        int firstYear,
        int lastYear,
        boolean referenceIncomplete,
        long elapsedNanos,
        String planDetails) {
    public sealed interface Result permits Fixed, Mortality {
    }

    public record Fixed(MonteCarloAnalysisResult value) implements Result {
        public Fixed {
            java.util.Objects.requireNonNull(value);
        }
    }

    public record Mortality(MonteCarloMortalityAnalysisResult value) implements Result {
        public Mortality {
            java.util.Objects.requireNonNull(value);
        }
    }

    public MonteCarloRun {
        java.util.Objects.requireNonNull(analysis);
        java.util.Objects.requireNonNull(planDetails);
    }

    public MonteCarloRun(Result analysis, MonteCarloFanModel fan, BreakEvenPlanSummary people,
            int firstYear, int lastYear, boolean referenceIncomplete, long elapsedNanos) {
        this(analysis, fan, people, firstYear, lastYear, referenceIncomplete, elapsedNanos,
                "Additional plan metadata was not captured by this run.");
    }

    public MonteCarloRun withPlanDetails(String details) {
        return new MonteCarloRun(analysis, fan, people, firstYear, lastYear, referenceIncomplete, elapsedNanos, details);
    }

    public MonteCarloRun(MonteCarloAnalysisResult result, MonteCarloFanModel fan,
                         BreakEvenPlanSummary people, int firstYear, int lastYear,
                         boolean referenceIncomplete, long elapsedNanos) {
        this(new Fixed(result), fan, people, firstYear, lastYear, referenceIncomplete, elapsedNanos);
    }

    public MonteCarloMode mode() {
        return analysis instanceof Fixed ? MonteCarloMode.FIXED_LIFESPAN : MonteCarloMode.LONGEVITY_ADJUSTED;
    }

    public MonteCarloAnalysisResult result() {
        if (analysis instanceof Fixed fixed) {
            return fixed.value();
        }
        throw new IllegalStateException("This run contains longevity-adjusted results.");
    }

    public MonteCarloMortalityAnalysisResult mortalityResult() {
        if (analysis instanceof Mortality mortality) {
            return mortality.value();
        }
        throw new IllegalStateException("This run contains fixed-lifespan results.");
    }

    public MonteCarloSettings settings() {
        return switch (analysis) {
            case Fixed fixed -> fixed.value().settings();
            case Mortality mortality -> mortality.value().request().settings();
        };
    }
}
