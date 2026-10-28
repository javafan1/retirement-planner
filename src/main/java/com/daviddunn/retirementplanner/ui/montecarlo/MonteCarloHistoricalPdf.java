package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot;
import java.util.ArrayList;

/** Export-time provenance over frozen data only; no plan, controls, engines or snapshot mutations. */
public final class MonteCarloHistoricalPdf {
    public enum Currency { CURRENT, OUTDATED, INPUTS_NEED_ATTENTION, UNASSESSED }
    private MonteCarloHistoricalPdf() { }

    public static MonteCarloPdfReport from(MonteCarloPdfReport original, MonteCarloSnapshot frozen,
            Currency currency, String durability, boolean historicalCopy) {
        return from(original, frozen, currency, durability, historicalCopy, null);
    }

    public static MonteCarloPdfReport from(MonteCarloPdfReport original, MonteCarloSnapshot frozen,
            Currency currency, String durability, boolean historicalCopy, String assessmentStatus) {
        var context = new ArrayList<String>();
        context.add(assessmentStatus != null ? assessmentStatus : switch (currency) {
            case CURRENT -> "Current Results — financial/request compatibility; projection association not assessed.";
            case OUTDATED -> "Historical Analysis — Outdated: These results were generated using previous retirement-plan or analysis assumptions.";
            case INPUTS_NEED_ATTENTION -> "Inputs Need Attention — original completed results remain available.";
            case UNASSESSED -> "Assessing completed result inputs — applicability has not been established.";
        });
        if (currency == Currency.INPUTS_NEED_ATTENTION)
            context.add("Current inputs need attention. The original completed results and assumptions are exported unchanged.");
        if (currency == Currency.UNASSESSED)
            context.add("Current input currency has not yet been assessed. Do not treat this historical report as matching current inputs.");
        context.add("Original analysis started: " + frozen.startedAt());
        context.add("Original analysis completed: " + frozen.completedAt());
        context.add("Result storage: " + durability + ". Export time is separate from analysis completion.");
        if (historicalCopy) context.add("Historical copy from another plan; original analysis provenance retained.");
        var provenance = frozen.provenance();
        context.add("Scenario source: " + provenance.source() + (frozen.paired() == null
                ? "; original indexed trial order retained." : "; original matched A/B trial order and lifetimes retained."));
        context.add("Return model: " + provenance.returns() + "; inflation model: " + provenance.inflation());
        context.add("Random stream contract: " + provenance.streams());
        if (provenance.mortality() != null) context.add("Mortality model: " + provenance.mortality());
        if (provenance.suppliedWorldFingerprint() != null)
            context.add("Original supplied-world identity: " + provenance.suppliedWorldFingerprint());
        context.addAll(original.context());
        return new MonteCarloPdfReport(original.title(), original.type(), original.filename(), original.exportedAt(),
                context, original.chart(), original.sections());
    }
}
