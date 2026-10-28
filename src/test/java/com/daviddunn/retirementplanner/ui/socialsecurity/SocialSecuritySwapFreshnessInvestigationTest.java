package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;

/** Audit reproducer: asserts existing behavior; does not change or prescribe fingerprint semantics. */
class SocialSecuritySwapFreshnessInvestigationTest {
    private static final AnalysisKind KIND = AnalysisKind.COUPLE_SOCIAL_SECURITY;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);
    @TempDir Path directory;
    @BeforeAll static void setupFx() throws Exception { startFx(); }

    @Test void newestSeventySixtyFiveSlotRemainsOutdatedWhenOriginalSeventySixtyTwoProjectionReturns() throws Exception {
        var source = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(directory.resolve("settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        try {
            var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
            election(plan.getHousehold().getPrimaryPerson(), 70, null);
            election(plan.getHousehold().getSpouse(), 62, null);
            fx(() -> { source.newPlan(plan); source.getCurrentProjection(); source.saveAs(directory.resolve("plan.json")); return null; });
            var projectionA = source.getAcceptedProjectionSnapshot();
            var contextA = context(source.getCurrentPlan());
            var resultA = new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(contextA.request());
            var completionA = accept(source, contextA, resultA, Instant.parse("2026-10-27T18:00:00Z"));
            fx(() -> { source.saveCurrentAsBaseline("Original 70/62"); source.save(); return null; });

            fx(() -> {
                election(source.getCurrentPlan().getHousehold().getSpouse(), 65, null);
                source.markModified(); source.invalidateProjection(); source.getCurrentProjection(); source.save(); return null;
            });
            var projectionB = source.getAcceptedProjectionSnapshot();
            var contextB = context(source.getCurrentPlan());
            var resultB = new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(contextB.request());
            var completionB = accept(source, contextB, resultB, Instant.parse("2026-10-27T18:01:00Z"));
            fx(() -> { source.save(); return null; });
            assertNotEquals(projectionA.id(), projectionB.id());
            assertNotEquals(completionA.inputFingerprint(), completionB.inputFingerprint());
            assertEquals(completionA.contextFingerprint(), completionB.contextFingerprint());
            // Independently calculated candidate values can be equal while source request/provenance differs.
            assertEquals(resultA.retirementGrid().cells(), resultB.retirementGrid().cells());
            assertEquals(resultA.evaluatedStrategies(), resultB.evaluatedStrategies());
            assertNotEquals(contextA.spouseCurrentClaimDate(), contextB.spouseCurrentClaimDate());

            // No projection or analyzer is allowed during role exchange, reload or passive view restoration.
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(source, "projectionEngine", Phase12EAssociationTest.throwing());
            var prepared = source.prepareProjectionSwap().toCompletableFuture().get(20, TimeUnit.SECONDS);
            fx(() -> { assertTrue(source.commitProjectionSwap(prepared)); source.save(); source.open(source.getCurrentFile()); return null; });
            source.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(projectionA.id(), source.getAcceptedProjectionSnapshot().id());
            assertEquals(projectionA.inputDigest(), source.getAcceptedProjectionSnapshot().inputDigest());
            assertTrue(source.currentProjectionInputsMatch());

            try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
                await(view.persistence().notice, "Historical Analysis");
                var displayed = fx(() -> view.persistence().resultMetadata());
                var actualInputs = fx(() -> {
                    var method = view.dialog().getClass().getDeclaredMethod("socialSecurityInputs"); method.setAccessible(true);
                    return (AnalysisFingerprintService.Inputs) method.invoke(view.dialog());
                });
                assertEquals(inputs(), actualInputs, "Restored controls exactly match the original effective mortality/valuation/discount inputs.");
                var current = new AnalysisFingerprintService().assess(KIND, source.getCurrentPlan(), actualInputs, null);
                assertTrue(current.valid());
                assertEquals(completionB.runId(), displayed.runId());
                assertNotEquals(completionA.runId(), displayed.runId());
                assertEquals(completionB.inputFingerprint(), displayed.inputFingerprint());
                var durableSlot = new JsonAnalysisSnapshotRepository(directory.resolve("results"))
                        .load(source.getAnalysisSessionId(), KIND).orElseThrow();
                assertEquals(displayed.runId(), durableSlot.runId());
                assertEquals(displayed.inputFingerprint(), durableSlot.inputFingerprint());
                assertEquals(completionA.inputFingerprint(), current.fingerprint());
                assertEquals(displayed.contextFingerprint(), current.contextFingerprint());
                assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, fx(() -> view.persistence().reportAssessment().state()));
                assertFalse(fx(() -> view.persistence().freshness().pending()));
                assertEquals(AnalysisResultFreshness.Association.HISTORICAL_PROJECTION, source.analysisAssociation(displayed));
                assertFalse(fx(() -> view.persistence().associated.isDisabled()));
                assertEquals("View Current Projection's Associated Result", fx(() -> view.persistence().associated.getText()));
                var provenance = fx(() -> view.persistence().provenance());
                assertTrue(provenance.stream().anyMatch(s -> s.contains("last-success analysis (run " + completionB.runId())));
                assertTrue(provenance.contains("Spouse claiming age: last-success 65; Current 62."));
                assertTrue(provenance.stream().anyMatch(s -> s.contains("does not replace this last-success analysis")));
                assertTrue(fx(() -> view.persistence().notice.getText()).contains("Spouse claiming age: last-success 65; Current 62."));
                assertEquals(0, fx(view.jobs()::generation));
                assertEquals(70, ss(source.getCurrentPlan().getHousehold().getPrimaryPerson()).getClaimingAge());
                assertEquals(62, ss(source.getCurrentPlan().getHousehold().getSpouse()).getClaimingAge());
                assertEquals("1", fx(() -> field(view.dialog(), "primaryMortalityAdjustment", TextField.class).getText()));
                var restoredParameters = source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), KIND).orElseThrow();
                assertEquals(SocialSecurityParameterMapping.socialSecurity(contextB), restoredParameters.parameters());

                var refA = source.getAnalyzerAssociations(projectionA.id()).getFirst();
                var refB = source.getAnalyzerAssociations(projectionB.id()).getFirst();
                assertEquals(displayed.runId(), refB.runId());
                var associatedA = source.loadAssociatedAnalyzer(refA).toCompletableFuture().get(20, TimeUnit.SECONDS);
                assertEquals(completionA.runId(), associatedA.runId());
                assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION, source.analysisAssociation(associatedA));
                assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new AnalysisCompatibilityPolicy().assess(associatedA, current,
                        AnalysisVersions.algorithms(KIND), AnalysisVersions.references(KIND)).state());
                assertEquals(completionB.runId(), fx(() -> view.persistence().resultMetadata().runId()), "Reading the association must not promote it into the last-success session.");
                var report = SocialSecurityAndQuickPdfTest.capture(view.dialog(), true);
                assertTrue(report.context().contains("Spouse claiming age: last-success 65; Current 62."));
                assertTrue(report.context().stream().anyMatch(s -> s.contains("last-success analysis (run " + completionB.runId())));
                assertTrue(SocialSecurityAndQuickPdfTest.pdf(report, "phase12g-provenance-swap-outdated").contains("Spouse claiming age: last-success 65; Current 62."));
                fx(() -> {
                    var pane = view.persistence().controls;
                    var preview = new javafx.scene.layout.VBox(10, pane);
                    new javafx.scene.Scene(preview, 1100, 500);
                    preview.applyCss(); preview.layout();
                    javax.imageio.ImageIO.write(SharedClaimingAgeGridTest.toBuffered(preview.snapshot(null, null)), "png",
                            Path.of("target/phase11c-pdf/phase12g-provenance-status.png").toFile());
                    preview.getChildren().clear();
                    return null;
                });
                // Exercise the actual action's nested dialog, including asynchronous artifact loading.
                var opened = new java.util.concurrent.CompletableFuture<Boolean>();
                javafx.application.Platform.runLater(() -> {
                    var poll = new javafx.animation.Timeline();
                    poll.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(40), e -> {
                        for (var window : List.copyOf(javafx.stage.Window.getWindows())) {
                            if (!(window instanceof javafx.stage.Stage stage) || !"Projection Analyzer History".equals(stage.getTitle())) continue;
                            var labels = stage.getScene().getRoot().lookupAll(".label");
                            if (labels.stream().filter(javafx.scene.control.Label.class::isInstance)
                                    .map(javafx.scene.control.Label.class::cast).anyMatch(l -> l.getText().contains("Original saved analyzer result | Projection " + projectionA.id()))) {
                                opened.complete(true); poll.stop(); stage.close();
                            }
                        }
                    }));
                    poll.setCycleCount(500);
                    poll.setOnFinished(e -> { opened.complete(false); for (var window : List.copyOf(javafx.stage.Window.getWindows()))
                        if (window instanceof javafx.stage.Stage stage && "Projection Analyzer History".equals(stage.getTitle())) stage.close(); });
                    poll.play(); view.persistence().associated.fire();
                });
                assertTrue(opened.get(30, TimeUnit.SECONDS), "Action must render Current's recorded A result.");
                assertEquals(completionB.runId(), fx(() -> view.persistence().resultMetadata().runId()));
                assertEquals(actualInputs, fx(() -> {
                    var method = view.dialog().getClass().getDeclaredMethod("socialSecurityInputs"); method.setAccessible(true);
                    return (AnalysisFingerprintService.Inputs) method.invoke(view.dialog());
                }));

                var evidence = new LinkedHashMap<String, Object>();
                evidence.put("fixtureOnly", true); evidence.put("owner", source.getAnalysisSessionId());
                evidence.put("projectionA", projectionA.id()); evidence.put("projectionAInputDigest", projectionA.inputDigest());
                evidence.put("projectionB", projectionB.id()); evidence.put("projectionBInputDigest", projectionB.inputDigest());
                evidence.put("runA", associatedA.runId()); evidence.put("runBDisplayed", displayed.runId());
                evidence.put("inputFingerprintA", associatedA.inputFingerprint()); evidence.put("inputFingerprintB", displayed.inputFingerprint());
                evidence.put("contextFingerprint", current.contextFingerprint());
                evidence.put("payloadDigestA", associatedA.payloadDigest()); evidence.put("payloadDigestB", displayed.payloadDigest());
                evidence.put("assessment", fx(() -> view.persistence().reportAssessment().state()));
                evidence.put("uiInputsMatchSavedParameters", actualInputs.equals(inputs()));
                evidence.put("mismatchedFields", List.of("S.people[1].records[0].claimingAge", "S.people[1].records[0].claimDate"));
                evidence.put("claimingA", "70/62"); evidence.put("claimingBDisplayed", "70/65");
                evidence.put("spouseClaimDateA", contextA.spouseCurrentClaimDate().toString());
                evidence.put("spouseClaimDateB", contextB.spouseCurrentClaimDate().toString());
                evidence.put("stage1CellsPerRun", resultA.retirementGrid().cells().size());
                evidence.put("stage2StrategiesPerRun", resultA.evaluatedStrategies().size());
                evidence.put("independentlyCalculatedFinancialResultsEqual", resultA.evaluatedStrategies().equals(resultB.evaluatedStrategies()));
                evidence.put("notice", fx(() -> view.persistence().notice.getText()));
                Files.createDirectories(Path.of("target/phase12g-ss-swap"));
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                        .writeValue(Path.of("target/phase12g-ss-swap/evidence.json").toFile(), evidence);
            }
        } finally {
            source.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertTrue(source.getAnalysisParameters().drain(Duration.ofSeconds(15)).complete()); source.getAnalysisParameters().shutdown();
            stop(source.getAnalysisResults());
        }
    }

    @Test void selectedAgeAndSelectedClaimDateIndependentlyParticipateInSsOnlyFingerprint() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        election(plan.getHousehold().getPrimaryPerson(), 70, null); election(plan.getHousehold().getSpouse(), 62, null);
        var service = new AnalysisFingerprintService();
        var original = service.fingerprint(KIND, plan, inputs());
        var originalDate = ss(plan.getHousehold().getSpouse()).getStartDate();
        election(plan.getHousehold().getSpouse(), 65, originalDate);
        assertNotEquals(original, service.fingerprint(KIND, plan, inputs()), "Changing only the stored age affects S.people[1].records[0].claimingAge");
        election(plan.getHousehold().getSpouse(), 62, plan.getHousehold().getSpouse().getBirthDate().plusYears(65));
        assertNotEquals(original, service.fingerprint(KIND, plan, inputs()), "Changing only the stored date affects S.people[1].records[0].claimDate");
        election(plan.getHousehold().getSpouse(), 62, originalDate);
        assertEquals(original, service.fingerprint(KIND, plan, inputs()));
    }

    private AcceptedAnalysisCompletion accept(ApplicationController source, SocialSecurityStrategyAnalysisContext context,
            SocialSecuritySurvivorClaimingOptimizationResult result, Instant completed) throws Exception {
        var fingerprints = new AnalysisFingerprintService();
        var fingerprint = fingerprints.fingerprint(KIND, source.getCurrentPlan(), inputs());
        var token = source.prepareAnalyzerAssociation(KIND, fingerprint); assertNotNull(token);
        var completion = new AcceptedAnalysisCompletion(source.getAnalysisSessionId(), source.getCurrentFile(), KIND, token.ticket(), completed,
                fingerprint, fingerprints.effectiveContextFingerprint(KIND, source.getCurrentPlan()),
                AnalysisVersions.algorithms(KIND), AnalysisVersions.references(KIND), CoupleSocialSecuritySnapshotMapper.capture(context, result),
                new SnapshotContext("Fixture household", context.primaryName(), context.spouseName(), completed.minusSeconds(1), List.of(), List.of(), List.of(), Map.of())).associatedWith(token);
        fx(() -> {
            var coordinator = source.getAnalysisParameters();
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(coordinator.bind(source.getCurrentPlan(), source.getCurrentFile()),
                    coordinator.nextAcceptanceSequence(), new AnalysisParameterConfiguration(1, KIND, KIND.shape(), completion.runId(), completed,
                    SocialSecurityParameterMapping.socialSecurity(context), Map.of()))));
            source.getAnalysisResults().accept(completion); assertTrue(source.associateAnalyzer(token, completion)); return null;
        });
        settle(source.getAnalysisResults(), source.getAnalysisSessionId(), KIND);
        assertTrue(source.getAnalysisParameters().drain(Duration.ofSeconds(15)).complete());
        return completion;
    }

    private static SocialSecurityStrategyAnalysisContext context(RetirementPlan plan) {
        var original = new SocialSecurityStrategyAnalysisRequestFactory().create(plan, SocialSecurityMortalityAdjustment.standard(),
                SocialSecurityMortalityAdjustment.standard(), new BigDecimal("0.010"), DATE, DATE);
        // Bounded, independently calculated test universe; no duplicated outcomes or changed production algorithms.
        var mortality = new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(80, new BigDecimal("0.40")),
                new SocialSecurityMortalityProbability(95, new BigDecimal("0.60"))));
        var grid = new SocialSecurityMortalityWeightedClaimingGridRequest(original.request().retirementGridRequest().baseStrategy(),
                List.of(62, 65, 70), List.of(62, 65, 70), mortality, mortality, DATE, DATE, new BigDecimal("0.010"));
        return new SocialSecurityStrategyAnalysisContext(new SocialSecuritySurvivorClaimingOptimizationRequest(grid), original.primaryName(), original.spouseName(),
                original.primaryFraMonthlyBenefit(), original.spouseFraMonthlyBenefit(), original.primaryBenefitValuationYear(), original.spouseBenefitValuationYear(),
                original.primaryCurrentClaimDate(), original.spouseCurrentClaimDate(), original.primaryMortalityCategory(), original.spouseMortalityCategory(),
                original.primaryMortalityAdjustment(), original.spouseMortalityAdjustment(), original.mortalityMetadata());
    }
    private static AnalysisFingerprintService.Inputs inputs() {
        return new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE, DATE, DATE, new BigDecimal("0.010"), null, null, List.of(), List.of(), null);
    }
    private static SocialSecurityIncome ss(Person person) {
        return (SocialSecurityIncome) person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).findFirst().orElseThrow();
    }
    private static void election(Person person, int age, LocalDate explicitDate) {
        var old = ss(person);
        person.replaceIncomeSource(old, new SocialSecurityIncome(old.getName(), old.getOwnership(), explicitDate == null ? person.getBirthDate().plusYears(age) : explicitDate,
                old.getEndDate(), old.getFullRetirementMonthlyBenefit(), age, old.getAnnualColaRate(), old.getBenefitValuationYear()));
    }
}
