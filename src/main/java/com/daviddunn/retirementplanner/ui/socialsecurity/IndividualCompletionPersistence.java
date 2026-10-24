package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import java.time.Instant;
import java.util.*;

/** Accepted individual weighted handoff and pure, asynchronous restoration. */
final class IndividualCompletionPersistence {
    private IndividualCompletionPersistence() { }
    static void attach(SinglePersonMortalityView view, ApplicationController source) {
        int noticeIndex = view.getChildren().indexOf(view.persistenceNotice);
        view.getChildren().remove(view.persistenceNotice);
        view.persistence = new WeightedResultPersistence(source, AnalysisKind.INDIVIDUAL_WEIGHTED,
                () -> new AnalysisFingerprintService.Inputs(com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment
                        .of(new java.math.BigDecimal(view.factor.getText().trim())).factor(), null,
                        WeightedResultPersistence.date(view.conditioning), WeightedResultPersistence.date(view.valuation),
                        WeightedResultPersistence.rate(view.discount), null, null, List.of(), List.of(), null),
                saved -> {
                    var result = saved.payload().individual().restore();
                    var context = saved.context();
                    var report = new IndividualReportContext(context.household(), context.primary(), context.startedAt(),
                            context.assumptions().stream().map(s -> new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section(s.title(), s.lines())).toList());
                    return () -> view.restore(result, report);
                }, view::refreshSavedState, view::clearSavedResult, view.persistenceNotice);
        view.getChildren().add(noticeIndex, view.persistence.controls);
        view.conditioning.getEditor().textProperty().addListener((o, a, b) -> { if (view.parameters == null || !view.parameters.applying()) { WeightedResultPersistence.dateEdited(view.conditioning); view.persistence.reassess(); } });
        view.valuation.getEditor().textProperty().addListener((o, a, b) -> { if (view.parameters == null || !view.parameters.applying()) { WeightedResultPersistence.dateEdited(view.valuation); view.persistence.reassess(); } });
        view.acceptedCompletion = input -> {
            UUID identity = input.plan().getPlanId() == null ? source.getAnalysisSessionId() : UUID.fromString(input.plan().getPlanId());
            var file = source.canPersistAnalysisResults() && Objects.equals(input.plan().getPlanId(), source.getCurrentPlan().getPlanId())
                    ? source.getCurrentFile() : null;
            var kind = AnalysisKind.INDIVIDUAL_WEIGHTED;
            var request = input.mortality().individual().request();
            var next = new AnalysisFingerprintService.Inputs(request.mortalityAdjustment().factor(), null,
                    request.mortalityBaseDate(), input.valuation(), input.rate(), null, null, List.of(), List.of(), null);
            var fingerprints = new AnalysisFingerprintService();
            String fingerprint = fingerprints.fingerprint(kind, input.plan(), next);
            var context = new SnapshotContext(input.context().household(), input.context().primary(), null,
                    input.context().started(), input.context().assumptions().stream()
                    .map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(),
                    List.of(new SnapshotContext.Section("Inputs for completed run", List.of(
                            "Factor: " + request.mortalityAdjustment().factor(), "Conditioning: " + request.mortalityBaseDate(),
                            "Valuation: " + input.valuation(), "Real discount: " + input.rate()))), List.of(), Map.of());
            String contextFingerprint = fingerprints.effectiveContextFingerprint(kind, input.plan());
            return result -> {
                if (!AnalysisSuccessPolicy.accepted(result)) return;
                var completion = new AcceptedAnalysisCompletion(identity, file, kind, UUID.randomUUID(), Instant.now(),
                        fingerprint, contextFingerprint, AnalysisVersions.algorithms(kind), Map.of(),
                        new WeightedSnapshots.Payload(null, WeightedSnapshots.Individual.from(result)), context);
                view.persistence.accept(completion);
            };
        };
    }
}
