package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;

/** Dispatcher-confined phase progress. Percentages describe only the current phase. */
public final class SocialSecurityAnalysisProgressModel {

    private AnalysisProgress update;
    private boolean cancelled;
    private boolean complete;

    public void accept(AnalysisProgress next) {
        if (cancelled || complete || !advances(update, next)) {
            return;
        }
        update = next;
    }

    static boolean advances(AnalysisProgress previous, AnalysisProgress next) {
        return previous == null || order(next.phase()) > order(previous.phase())
                || (next.phase() == previous.phase()
                && next.completedWork() >= previous.completedWork()
                && next.totalWork() == previous.totalWork());
    }

    private static int order(AnalysisPhase phase) {
        return switch (phase) {
            case SOCIAL_SECURITY_RETIREMENT_GRID, CURRENT_PLAN_BASELINE,
                    LONGEVITY_INTEGRATED_SCENARIOS -> 0;
            case LONGEVITY_STRATEGY_EQUIVALENCE -> 1;
            case SOCIAL_SECURITY_SURVIVOR_STRATEGIES, QUICK_COMPARISON_CANDIDATES,
                    EXHAUSTIVE_INTEGRATED_STRATEGIES, LONGEVITY_INTEGRATED_COMPARISON -> 2;
        };
    }

    public AnalysisProgress update() {
        return update;
    }

    public void cancel() {
        cancelled = true;
    }

    public void complete() {
        complete = true;
    }

    public String text() {
        if (cancelled) {
            return "Cancelling…";
        }
        if (complete) {
            return "Complete";
        }
        if (update == null) {
            return "Preparing longevity scenarios…";
        }
        return switch (update.phase()) {
            case LONGEVITY_STRATEGY_EQUIVALENCE -> "Proving equivalent claiming strategies…";
            case LONGEVITY_INTEGRATED_COMPARISON -> "Evaluating retirement outcomes…";
            default -> update.phase().displayName();
        };
    }
}
