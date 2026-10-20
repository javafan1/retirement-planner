package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Pure capture/reconstruction. Called off FX; owns neither controls nor financial services. */
final class ExhaustiveResultSnapshots {
    private ExhaustiveResultSnapshots() { }

    record SocialSecurityBasis(WeightedSnapshots.Strategy strategy, BigDecimal presentValue, long sourceRevision,
            UUID runId, String inputFingerprint) {
        SocialSecurityBasis(WeightedSnapshots.Strategy strategy, BigDecimal presentValue, long sourceRevision) {
            this(strategy, presentValue, sourceRevision, null, null);
        }
    }
    record Restored(ExhaustiveIntegratedSearchPresentation model, IntegratedReportContext report,
            ExhaustiveSnapshot.CrossReference reference, ExhaustiveSnapshot.Selection selection) { }

    static ExhaustiveSnapshot capture(ExhaustiveIntegratedSearchPresentation model, IntegratedReportContext context,
            SocialSecurityBasis basis, Instant completed, String metric, String tab) {
        var reference = reference(model.result(), basis);
        var first = model.groups().isEmpty() ? null : model.groups().getFirst().representative();
        return ExhaustiveSnapshot.capture(model.result(), model.runtime(), 20,
                model.groups().stream().map(g -> new ExhaustiveSnapshot.Group(
                        g.representative().generationOrder(), g.equivalentStrategyCount())).toList(),
                new ExhaustiveSnapshot.Report(context.household(), context.primary(), context.spouse(), context.runStarted(),
                        context.assumptions(), context.baseline()), reference, completed,
                new ExhaustiveSnapshot.Selection(first == null ? null : first.generationOrder(),
                        first == null ? null : WeightedSnapshots.Strategy.from(first.strategy()), metric, tab));
    }

    private static ExhaustiveSnapshot.CrossReference reference(IntegratedSocialSecurityCompleteStrategySearchResult result,
            SocialSecurityBasis basis) {
        if (basis == null) return new ExhaustiveSnapshot.CrossReference(
                ExhaustiveSnapshot.ReferenceStatus.NO_CURRENT_SOCIAL_SECURITY_RESULT,
                "No current Social Security Only result was available at analysis start.", null, null, null, null, null, null, null, null, null);
        var match = result.entryFor(basis.strategy().restore()).filter(e -> e.successful());
        if (match.isEmpty()) return new ExhaustiveSnapshot.CrossReference(ExhaustiveSnapshot.ReferenceStatus.OUTSIDE_SEARCH_UNIVERSE,
                "The original Social Security Only recommendation was outside the successful exhaustive results.",
                basis.runId(), basis.inputFingerprint(), basis.sourceRevision(), basis.strategy(), basis.presentValue(), null, null, null, null);
        var e = match.orElseThrow();
        var estate = e.metrics().orElseThrow().afterTaxEstate();
        return new ExhaustiveSnapshot.CrossReference(ExhaustiveSnapshot.ReferenceStatus.AVAILABLE,
                "Social Security Only recommendation captured for the original run.", basis.runId(), basis.inputFingerprint(), basis.sourceRevision(),
                basis.strategy(), basis.presentValue(), e.generationOrder(), estate, e.afterTaxEstateRank().orElseThrow(),
                estate.subtract(result.rankedSuccessfulEntries().getFirst().metrics().orElseThrow().afterTaxEstate()));
    }

    static Restored restore(ExhaustiveSnapshot payload) {
        var result = payload.restore();
        var entries = new HashMap<Integer, IntegratedSocialSecurityCompleteStrategySearchEntry>();
        result.entries().forEach(e -> entries.put(e.generationOrder(), e));
        var model = new ExhaustiveIntegratedSearchPresentation(result, payload.displayedGroups().stream()
                .map(g -> new ExhaustiveIntegratedSearchPresentation.Group(entries.get(g.representativeOrder()), g.equivalentStrategyCount())).toList(),
                payload.runtime());
        var r = payload.report();
        return new Restored(model, new IntegratedReportContext(r.household(), r.primary(), r.spouse(), r.runStarted(), r.assumptions(), r.baseline()),
                payload.socialSecurityReference(), payload.selection());
    }
}
