package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.baseline.*;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.baseline.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set;

class ProjectionSnapshotPersistenceTest {
    @Test void genuineArkansasHistoryKeepsItsResultsAndAnalyzerThroughSaveAsAndRepeatedSwaps() throws Exception {
        var controller = controller();
        var plan = com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.single(
                java.time.LocalDate.of(1965, 7, 1), java.time.LocalDate.of(2026, 1, 1), 2,
                WithdrawalStrategyType.TAXABLE_FIRST, "1000000");
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.CheckingAccount(
                "Tax reserve", AccountOwnership.PRIMARY, new BigDecimal("50000")));
        controller.newPlan(plan);
        var owner = controller.getAnalysisSessionId();
        plan.assignPlanId(owner);
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        var pension = new Pension("Original employer pension", AccountOwnership.PRIMARY, start, null,
                new BigDecimal("4500"), BigDecimal.ZERO);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        var engine = com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.validationEngine();
        var baselineEngine = com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.confirmedPensionEngine();
        var baseline = CompleteProjectionSnapshot.captureArkansas(owner, plan, baselineEngine.project(plan), java.time.Instant.now());
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        pension.setPlanClassification(com.daviddunn.retirementplanner.domain.income.PensionPlanClassification.DEFINED_BENEFIT_414J);
        var projection = engine.project(plan);
        var current = CompleteProjectionSnapshot.captureArkansas(owner, plan, projection, java.time.Instant.now());
        var started = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var completed = started.plusSeconds(1);
        var settings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(2, 19L, BigDecimal.ZERO, BigDecimal.ZERO);
        var people = current.people();
        var display = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot.Display(
                people, "Primary", null, List.of(),
                new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot.Chart(List.of(), List.of(), List.of(), List.of()),
                projection.getStartYear(), projection.getEndYear(), false, 0, "Bounded Arkansas test.");
        var captured = ArkansasAnalysisProvenance.capture(engine, AnalysisKind.INDIVIDUAL_MONTE_CARLO_FIXED, List.of(current), () -> {
            var analyzed = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer(engine)
                    .analyze(plan, settings, projection, index -> ProjectionEconomicPath.constant(index == 0
                            ? new BigDecimal(".04") : new BigDecimal("-.02")));
            return com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshotMapping.fixed(analyzed, plan,
                    display, started, completed, "a".repeat(64));
        });
        var payload = (com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot) captured.payload();
        var fingerprints = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService().storedFingerprint(payload);
        var completion = new AcceptedAnalysisCompletion(owner, directory.resolve("arkansas-original.json"), payload.kind(),
                UUID.randomUUID(), completed, fingerprints.fingerprint(), fingerprints.contextFingerprint(),
                AnalysisVersions.calculatedAlgorithms(payload.kind(), payload), AnalysisVersions.references(payload.kind()),
                payload, new SnapshotContext("Single", display.primaryName(), null, started, List.of(), List.of(), List.of(), Map.of()))
                .withArkansasExecution(captured);
        var zeroHash = "0".repeat(64);
        var association = new AnalyzerAssociation(1, owner, current.id(), current.inputDigest(), completion.kind(),
                completion.runId(), completion.inputFingerprint(), completion.contextFingerprint(), zeroHash, zeroHash, completed);
        plan.setBaseline(new ProjectionBaseline(baseline.restoreInputs(), java.time.LocalDateTime.now(), "Legacy origin", baseline.id()));
        plan.installProjectionComponents(Map.of(current.id(), current, baseline.id(), baseline));
        plan.installPendingAnalyzerAssociations(Map.of(completion.runId(), completion));
        plan.setProjectionState(new ProjectionRoleManifest(2, owner, new ProjectionRoleManifest.Reference(current.id(), zeroHash),
                new ProjectionRoleManifest.Reference(baseline.id(), zeroHash), List.of(association), List.of()));

        var before = System.nanoTime();
        controller.saveAs(directory.resolve("arkansas-original.json"));
        long saveMillis = (System.nanoTime() - before) / 1_000_000;
        var reopened = controller();
        set(reopened, "projectionEngine", throwing());
        before = System.nanoTime();
        reopened.open(controller.getCurrentFile());
        reopened.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        long reloadMillis = (System.nanoTime() - before) / 1_000_000;
        assertEquals(current.annual(), reopened.getAcceptedProjectionSnapshot().annual());
        assertFalse(reopened.currentProjectionDeterministicallyApplicable());
        assertNull(reopened.prepareAnalyzerAssociation(completion.kind()));
        var restored = reopened.loadAssociatedAnalyzer(reopened.getAnalyzerAssociations(current.id()).getFirst()).toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(current.id(), restored.arkansasExecution().dependencies().getFirst().projectionId());
        assertEquals(StateTaxJurisdiction.AR, restored.arkansasExecution().jurisdiction());
        assertEquals(19, ((com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot) restored.payload()).settings().seed());
        assertEquals(payload, restored.payload());
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, new AnalysisCompatibilityPolicy().assess(
                restored, fingerprints, restored.algorithms(), restored.references()).state());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new AnalysisCompatibilityPolicy().assessArkansas(
                restored, fingerprints, restored.algorithms(), restored.references(), ArkansasValidationTax.reviewedVersions()).state());
        assertThrows(IllegalArgumentException.class, () -> restored.arkansasExecution().validateAssociation(baseline.id(), baseline.inputDigest()));
        var codec = new AnalysisSnapshotCodec();
        System.out.println("Arkansas two-year lifecycle saveMs=" + saveMillis + ", reloadMs=" + reloadMillis
                + ", analysisBytes=" + codec.raw(restored).length + ", analysisGzipBytes=" + codec.encode(restored).length);

        for (var expected : List.of(baseline, current, baseline)) {
            assertTrue(reopened.commitProjectionSwap(reopened.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
            assertEquals(expected.id(), reopened.getAcceptedProjectionSnapshot().id());
            assertEquals(expected.annual(), reopened.getAcceptedProjectionSnapshot().annual());
            assertEquals(expected == current ? AnalysisResultFreshness.Association.CURRENT_PROJECTION
                    : AnalysisResultFreshness.Association.HISTORICAL_PROJECTION, reopened.analysisAssociation(restored));
            reopened.save();
        }
        reopened.saveAs(directory.resolve("arkansas-copy.json"));
        var copied = controller();
        set(copied, "projectionEngine", throwing());
        copied.open(reopened.getCurrentFile());
        copied.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertNotEquals(owner, copied.getAnalysisSessionId());
        assertEquals(baseline.id(), copied.getAcceptedProjectionSnapshot().id());
        assertEquals(current.id(), copied.getCurrentPlan().getProjectionState().baseline().id());
        var rebound = copied.loadAssociatedAnalyzer(copied.getAnalyzerAssociations(current.id()).getFirst())
                .toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(restored.arkansasExecution(), rebound.arkansasExecution());
        assertEquals(copied.getAnalysisSessionId(), rebound.planId());
        assertEquals(restored.payloadDigest(), rebound.payloadDigest());

        assertTrue(copied.commitProjectionSwap(copied.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
        var frozenCsv = directory.resolve("arkansas-history.csv");
        var exporter = new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter();
        exporter.export(copied.peekCurrentProjection(), List.of(), frozenCsv);
        var originalCsv = Files.readString(frozenCsv);
        assertTrue(originalCsv.contains("Arkansas retained history"));
        var changed = (Pension) copied.getCurrentPlan().getHousehold().getPrimaryPerson().getIncomeSources().getFirst();
        changed.setSourceOrigin(RetirementIncomeSourceOrigin.UNKNOWN);
        assertFalse(copied.currentProjectionInputsMatch());
        assertEquals(AnalysisResultFreshness.Association.INPUTS_EDITED, copied.analysisAssociation(rebound));
        exporter.export(copied.getAcceptedProjectionSnapshot().annual().restoreProjection(), List.of(), frozenCsv);
        assertEquals(originalCsv, Files.readString(frozenCsv));
        var scenario = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService()
                .copyAsScenario(copied.getCurrentPlan());
        assertNotEquals(copied.getCurrentPlan().getPlanId(), scenario.getPlanId());
        assertNull(scenario.getProjectionState());
        assertNull(scenario.getBaseline().getProjectionSnapshotId());
        assertTrue(scenario.getAssociatedAnalyzerResults().isEmpty());
    }

    @Test void pensionOriginChangesOnlyFrozenInputsAndSurvivesSaveAsScenarioAndRoleSwaps() throws Exception {
        var c = controller();
        var plan = c.getCurrentPlan();
        var person = plan.getHousehold().getPrimaryPerson();
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        var pension = new Pension("Origin fixture", AccountOwnership.PRIMARY, start, null,
                new BigDecimal("1000"), BigDecimal.ZERO);
        person.addIncomeSource(pension);
        c.invalidateProjection();
        c.getCurrentProjection();
        var legacy = c.getAcceptedProjectionSnapshot();
        assertFalse(legacy.inputsDocument().contains("sourceOrigin"));
        assertEquals(legacy.inputDigest(), CompleteProjectionSnapshot.digest(
                CompleteProjectionSnapshot.freeze(legacy.restoreInputs())));
        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, pensionOrigin(legacy.restoreInputs().getHousehold()));
        var fingerprints = new AnalysisFingerprintService();
        var inputs = AnalysisFingerprintService.Inputs.none();
        var legacyFingerprint = fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, inputs);
        var socialInputs = new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE, start, start,
                BigDecimal.ZERO, null, null, List.of(), List.of(), null);
        var socialFingerprint = fingerprints.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, socialInputs);
        c.saveCurrentAsBaseline("Original pension origin");

        var classified = new Pension(pension.getName(), pension.getOwnership(), start, null,
                pension.getMonthlyBenefit(), pension.getAnnualColaRate());
        classified.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        person.setIncomeSources(person.getIncomeSources().stream().map(s -> s == pension ? classified : s).toList());
        c.markModified();
        c.invalidateProjection();
        c.getCurrentProjection();
        var updated = c.getAcceptedProjectionSnapshot();
        assertNotEquals(legacy.inputDigest(), updated.inputDigest());
        assertNotEquals(legacyFingerprint, fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, inputs));
        assertEquals(socialFingerprint, fingerprints.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, socialInputs));
        assertEquals(legacy.annual(), updated.annual(), "Accepted-state annual serialization is independent of source origin.");
        assertEquals(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN, pensionOrigin(
                new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan).getHousehold()));
        c.saveAs(directory.resolve("pension-origin-original.json"));
        c.saveAs(directory.resolve("pension-origin-copy.json"));
        var reopened = controller();
        set(reopened, "projectionEngine", throwing());
        reopened.open(c.getCurrentFile());
        reopened.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(updated.id(), reopened.getAcceptedProjectionSnapshot().id());
        assertEquals(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN, pensionOrigin(reopened.getCurrentPlan().getHousehold()));
        assertTrue(reopened.commitProjectionSwap(reopened.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, pensionOrigin(reopened.getCurrentPlan().getHousehold()));
        assertEquals(legacy.inputDigest(), reopened.getAcceptedProjectionSnapshot().inputDigest());
        reopened.save();
        assertTrue(reopened.commitProjectionSwap(reopened.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
        assertEquals(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN, pensionOrigin(reopened.getCurrentPlan().getHousehold()));
    }

    private static RetirementIncomeSourceOrigin pensionOrigin(Household household) {
        return household.getPrimaryPerson().getIncomeSources().stream().filter(Pension.class::isInstance)
                .map(Pension.class::cast).filter(pension -> "Origin fixture".equals(pension.getName()))
                .findFirst().orElseThrow().getSourceOrigin();
    }

    private com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition enabledBenefit(String amount) {
        var f = com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.monthly();
        f = com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.change(f,"grossAmount",new BigDecimal(amount));
        return com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.change(f,"trigger",
                new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.Trigger(
                        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.TriggerType.FIXED_DATE,
                        java.time.LocalDate.of(2026,1,1),null,null));
    }

    @Test void enabledDeterministicCapabilitiesSurviveSaveAsRestartAndBothSwapDirections() throws Exception {
        var c = controller(); var first = enabledBenefit("2500");
        c.getCurrentPlan().setConditionalBenefits(List.of(first)); c.markModified(); c.invalidateProjection(); c.getCurrentProjection();
        var baseline = c.getAcceptedProjectionSnapshot();
        assertTrue(baseline.provenance().startsWith(ConditionalBenefitExecutionSemantics.KEY));
        assertTrue(baseline.calculationSemanticsCurrent()); assertTrue(c.currentProjectionDeterministicallyApplicable());
        for (var kind : AnalysisKind.values()) if (kind.shape()==AnalysisKind.Shape.COUPLE) {
            assertNotNull(c.prepareAnalyzerAssociation(kind,"a".repeat(64)),"Verified B3 family may capture admission: "+kind);
        }
        c.saveCurrentAsBaseline("Original conditional stream");
        var second = enabledBenefit("4000");
        c.getCurrentPlan().setConditionalBenefits(List.of(second)); c.markModified(); c.invalidateProjection(); c.getCurrentProjection();
        var current = c.getAcceptedProjectionSnapshot(); assertNotEquals(baseline.inputDigest(),current.inputDigest());
        c.saveAs(directory.resolve("enabled-benefits.json")); c.save();
        c.saveAs(directory.resolve("enabled-benefits-copy.json"));
        var copy = controller(); set(copy,"projectionEngine",throwing()); copy.open(c.getCurrentFile());
        copy.getProjectionRestoration().toCompletableFuture().get(15,TimeUnit.SECONDS);
        assertEquals(current.id(),copy.getAcceptedProjectionSnapshot().id()); assertTrue(copy.currentProjectionDeterministicallyApplicable());
        assertEquals(current.annual(),copy.getAcceptedProjectionSnapshot().annual());
        assertEquals(second,copy.getCurrentPlan().getConditionalBenefits().getFirst());
        var swap = copy.prepareProjectionSwap().toCompletableFuture().get(15,TimeUnit.SECONDS); assertTrue(copy.commitProjectionSwap(swap));
        assertEquals(first,copy.getCurrentPlan().getConditionalBenefits().getFirst());
        assertEquals(baseline.annual(),copy.getAcceptedProjectionSnapshot().annual()); assertTrue(copy.currentProjectionDeterministicallyApplicable());
        copy.save(); swap = copy.prepareProjectionSwap().toCompletableFuture().get(15,TimeUnit.SECONDS); assertTrue(copy.commitProjectionSwap(swap));
        assertEquals(second,copy.getCurrentPlan().getConditionalBenefits().getFirst()); assertEquals(current.inputDigest(),copy.getAcceptedProjectionSnapshot().inputDigest());
        assertTrue(copy.currentProjectionDeterministicallyApplicable()); copy.save();
    }

    @Test void explicitCapabilitiesAreBoundToOriginalInputsAndAnnualEvidenceAndCannotMasqueradeAsLegacy() throws Exception {
        var c = controller(); c.getCurrentPlan().setConditionalBenefits(List.of(enabledBenefit("2500"))); c.invalidateProjection(); c.getCurrentProjection();
        var original = c.getAcceptedProjectionSnapshot();
        for (var provenance : List.of(original.provenance().replace("deterministic-annual-v1","unknown-model"),
                PensionSelectionSemantics.PROVENANCE,original.provenance().replace("binding=","binding=f"))) {
            assertThrows(IllegalArgumentException.class,()->new CompleteProjectionSnapshot(original.version(),original.id(),original.owner(),original.sourcePlanId(),original.acceptedAt(),
                    original.inputsDocument(),original.inputDigest(),provenance,original.annual(),original.nonInvestable(),original.summary(),original.people()));
        }
        c.getCurrentPlan().setConditionalBenefits(List.of(enabledBenefit("4000")));
        var altered = CompleteProjectionSnapshot.freeze(c.getCurrentPlan());
        assertThrows(IllegalArgumentException.class,()->new CompleteProjectionSnapshot(original.version(),original.id(),original.owner(),original.sourcePlanId(),original.acceptedAt(),
                altered,CompleteProjectionSnapshot.digest(altered),original.provenance(),original.annual(),original.nonInvestable(),original.summary(),original.people()));
        assertEquals("2500",original.restoreInputs().getConditionalBenefits().getFirst().grossAmount().toPlainString());
    }

    @Test void anEngineThatIgnoresApplicablePaymentsCannotReplacePreviousAcceptedHistory() throws Exception {
        var c = controller(); var prior = c.getCurrentProjection(); var original = c.getAcceptedProjectionSnapshot();
        c.getCurrentPlan().setConditionalBenefits(List.of(enabledBenefit("2500"))); c.markModified(); c.invalidateProjection();
        set(c,"projectionEngine",new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan) { return new ProjectionExecutionResult.Completed(prior.getYears()); }
        });
        var failure = assertThrows(IllegalArgumentException.class,c::getCurrentProjection);
        assertTrue(failure.getMessage().contains("every applicable conditional benefit"));
        assertEquals(original.id(),c.getAcceptedProjectionSnapshot().id()); assertEquals(original.annual(),c.getAcceptedProjectionSnapshot().annual());
        c.saveAs(directory.resolve("failed-completion-preserved.json"));
        assertEquals(original.annual(),c.getAcceptedProjectionSnapshot().annual());
    }

    @Test void retainedIncomeKnownOptionalExtensionPreservesExactLegacyDecimalsAndStrictOriginalFields() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        var legacy = new RetainedProjectionSnapshot.Income(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST,
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.INCOME,null,null,null,"Exact legacy amount",
                new BigDecimal("123456789.123456789012300"),new BigDecimal("123456789.123456789012300"),2026,null);
        var wire = mapper.writeValueAsBytes(legacy); assertFalse(new String(wire,java.nio.charset.StandardCharsets.UTF_8).contains("conditionalBenefit"));
        assertEquals(legacy,mapper.readValue(wire,RetainedProjectionSnapshot.Income.class));
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(wire); node.remove("kind");
        assertThrows(Exception.class,()->mapper.treeToValue(node,RetainedProjectionSnapshot.Income.class));
        node.put("kind","INTEREST"); node.put("unexpected",true);
        assertThrows(Exception.class,()->mapper.treeToValue(node,RetainedProjectionSnapshot.Income.class));
    }

    @Test void isolatedFundingSurplusRetainsItsOriginalResultThroughSaveAsAndBothSwaps() throws Exception {
        var c = controller();
        c.getCurrentProjection();
        var original = c.getAcceptedProjectionSnapshot();
        var projection = com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.accountingFixture();
        var expected = RetainedProjectionSnapshot.capture(projection);
        var ending = projection.getFinalInvestableAssets();
        var isolated = new CompleteProjectionSnapshot(1, UUID.randomUUID(), original.owner(), original.sourcePlanId(),
                original.acceptedAt(), original.inputsDocument(), original.inputDigest(), "Nonproduction Arkansas cash fixture",
                expected, List.of(new CompleteProjectionSnapshot.NonInvestableYear(2026, List.of(), BigDecimal.ZERO)),
                new CompleteProjectionSnapshot.Summary(ending, ending, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO), original.people());
        var codec = new AnalysisSnapshotCodec();
        var originalBytes = codec.encodeProjectionDocument(original).bytes();
        var oldRoundTrip = codec.decodeDocument(originalBytes, CompleteProjectionSnapshot.class);
        assertArrayEquals(originalBytes, codec.encodeProjectionDocument(oldRoundTrip).bytes());
        var plan = c.getCurrentPlan();
        plan.setBaseline(new ProjectionBaseline(isolated.restoreInputs(), java.time.LocalDateTime.now(), "Isolated surplus", isolated.id()));
        plan.installProjectionComponents(Map.of(original.id(), original, isolated.id(), isolated));
        plan.setProjectionState(new ProjectionRoleManifest(1, original.owner(),
                new ProjectionRoleManifest.Reference(original.id(), "0".repeat(64)),
                new ProjectionRoleManifest.Reference(isolated.id(), "0".repeat(64))));
        set(c, "projectionEngine", throwing());
        c.saveAs(directory.resolve("surplus-original.json"));
        c.saveAs(directory.resolve("surplus-copy.json"));
        var restored = controller();
        set(restored, "projectionEngine", throwing());
        restored.open(c.getCurrentFile());
        restored.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(original.annual(), restored.getAcceptedProjectionSnapshot().annual());
        assertTrue(restored.commitProjectionSwap(restored.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
        assertEquals(isolated.id(), restored.getAcceptedProjectionSnapshot().id());
        assertEquals(expected, restored.getAcceptedProjectionSnapshot().annual());
        assertEquals(0, new BigDecimal("1.34").compareTo(restored.getAcceptedProjectionSnapshot().annual()
                .restoreProjection().getFirstYear().getRetainedFromTaxFundingSurplus()));
        restored.save();
        assertTrue(restored.commitProjectionSwap(restored.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
        assertEquals(original.id(), restored.getAcceptedProjectionSnapshot().id());
        assertEquals(original.annual(), restored.getAcceptedProjectionSnapshot().annual());
        restored.save();
    }

    @Test void benefitBearingPlanSaveAsSaveAndRestartNeverExecuteOrLoseStoredDefinitions() throws Exception {
        var c = controller();
        var benefit = com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.monthly();
        c.getCurrentPlan().setConditionalBenefits(List.of(benefit));
        c.markModified();
        set(c, "projectionEngine", throwing());
        var first = directory.resolve("stored-benefits.json");
        c.saveAs(first); c.save();
        var owner = c.getCurrentPlan().getPlanId();
        var second = directory.resolve("stored-benefits-copy.json");
        c.saveAs(second);
        assertNotEquals(owner, c.getCurrentPlan().getPlanId());
        assertEquals(List.of(benefit), new JsonRetirementPlanRepository().load(first).getConditionalBenefits());
        assertEquals(List.of(benefit), new JsonRetirementPlanRepository().load(second).getConditionalBenefits());
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(second);
        copy.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(List.of(benefit), copy.getCurrentPlan().getConditionalBenefits());
        assertTrue(copy.getProjectionSnapshotNotice().contains("deterministic projection"));
        copy.save();
        assertEquals(List.of(benefit), new JsonRetirementPlanRepository().load(second).getConditionalBenefits());
        set(copy, "projectionEngine", new ProjectionEngine()); copy.invalidateProjection();
        assertNotNull(copy.getCurrentProjection());
        assertTrue(copy.getAcceptedProjectionSnapshot().calculationSemanticsCurrent());
        assertEquals(List.of(benefit),copy.getAcceptedProjectionSnapshot().restoreInputs().getConditionalBenefits());
    }

    @Test void benefitEditsPreserveOriginalHistoricalProjectionAndBaselineWithoutRecalculation() throws Exception {
        var c = controller(); c.getCurrentProjection(); c.saveCurrentAsBaseline("Before benefits");
        var original = c.getAcceptedProjectionSnapshot();
        var token = c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_WEIGHTED, "a".repeat(64));
        var completion = new AcceptedAnalysisCompletion(c.getAnalysisSessionId(), c.getCurrentFile(), token.kind(), token.ticket(),
                java.time.Instant.now(), "a".repeat(64), "b".repeat(64), AnalysisVersions.algorithms(token.kind()),
                AnalysisVersions.references(token.kind()), com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures.couple(),
                com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures.context());
        assertTrue(c.associateAnalyzer(token, completion));
        c.saveAs(directory.resolve("original-history.json"));
        // Pending associations have placeholder digests until their first durable save.
        var originalAssociation = c.getAnalyzerAssociations(original.id()).getFirst();
        c.getCurrentPlan().setConditionalBenefits(List.of(com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.monthly()));
        c.markModified();
        assertFalse(c.currentProjectionInputsMatch());
        assertFalse(c.currentProjectionDeterministicallyApplicable());
        assertFalse(c.projectionSwapUnavailableReason().isEmpty());
        assertNull(c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_EXHAUSTIVE, "a".repeat(64)));
        set(c, "projectionEngine", throwing());
        c.save(); c.saveAs(directory.resolve("edited-history.json"));
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(c.getCurrentFile());
        copy.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        var retained = copy.getAcceptedProjectionSnapshot();
        assertEquals(original.id(), retained.id());
        assertEquals(original.inputsDocument(), retained.inputsDocument());
        assertEquals(original.inputDigest(), retained.inputDigest());
        assertEquals(original.annual(), retained.annual());
        var restoredAssociation = copy.getAnalyzerAssociations(retained.id()).getFirst();
        assertEquals(originalAssociation.runId(), restoredAssociation.runId());
        assertEquals(originalAssociation.projectionId(), restoredAssociation.projectionId());
        assertEquals(originalAssociation.payloadDigest(), restoredAssociation.payloadDigest());
        assertEquals(originalAssociation.inputFingerprint(), restoredAssociation.inputFingerprint());
        var restoredAnalysis = copy.loadAssociatedAnalyzer(restoredAssociation).toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(completion.payload(), restoredAnalysis.payload());
        assertEquals(completion.context(), restoredAnalysis.context());
        assertEquals(completion.algorithms(), restoredAnalysis.algorithms());
        assertTrue(retained.restoreInputs().getConditionalBenefits().isEmpty());
        assertTrue(copy.getCurrentPlan().getBaseline().getSnapshot().getConditionalBenefits().isEmpty());
        assertEquals(1, copy.getCurrentPlan().getConditionalBenefits().size());
        assertFalse(copy.currentProjectionDeterministicallyApplicable());
        copy.save();
        assertEquals(original.annual(), copy.getAcceptedProjectionSnapshot().annual());
        var frozen = CompleteProjectionSnapshot.freeze(copy.getCurrentPlan());
        var unsupported = new CompleteProjectionSnapshot(retained.version(), UUID.randomUUID(), retained.owner(),
                retained.sourcePlanId(), retained.acceptedAt(), frozen, CompleteProjectionSnapshot.digest(frozen),
                PensionSelectionSemantics.PROVENANCE, retained.annual(), retained.nonInvestable(), retained.summary(), retained.people());
        assertFalse(unsupported.calculationSemanticsCurrent(), "Pension provenance cannot certify conditional-benefit execution");
    }

    @Test void syntheticNonexecutableSnapshotInputsSurviveBothSwapDirectionsWithoutBecomingCurrentCalculations() throws Exception {
        var c = controller(); c.getCurrentProjection();
        var original = c.getAcceptedProjectionSnapshot();
        var candidate = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(c.getCurrentPlan());
        var benefit = com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.monthly();
        candidate.setConditionalBenefits(List.of(benefit));
        var frozen = CompleteProjectionSnapshot.freeze(candidate);
        // Synthetic copy fixture, explicitly not evidence of a computed conditional-benefit result.
        var unsupported = new CompleteProjectionSnapshot(1, UUID.randomUUID(), original.owner(), original.sourcePlanId(), original.acceptedAt(),
                frozen, CompleteProjectionSnapshot.digest(frozen), "Nonexecutable domain-copy fixture", original.annual(),
                original.nonInvestable(), original.summary(), original.people());
        var plan = c.getCurrentPlan();
        plan.setBaseline(new ProjectionBaseline(unsupported.restoreInputs(), java.time.LocalDateTime.now(), "Domain-copy fixture", unsupported.id()));
        plan.installProjectionComponents(Map.of(original.id(), original, unsupported.id(), unsupported));
        plan.setProjectionState(new ProjectionRoleManifest(1, original.owner(),
                new ProjectionRoleManifest.Reference(original.id(), "0".repeat(64)),
                new ProjectionRoleManifest.Reference(unsupported.id(), "0".repeat(64))));
        set(c, "projectionEngine", throwing()); c.saveAs(directory.resolve("copy-fixture.json"));
        var prepared = c.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(c.commitProjectionSwap(prepared));
        assertEquals(List.of(benefit), c.getCurrentPlan().getConditionalBenefits());
        assertEquals(unsupported.inputDigest(), c.getAcceptedProjectionSnapshot().inputDigest());
        assertFalse(c.currentProjectionDeterministicallyApplicable());
        c.save();
        assertEquals(List.of(benefit), new JsonRetirementPlanRepository().load(c.getCurrentFile()).getConditionalBenefits());
        prepared = c.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(c.commitProjectionSwap(prepared));
        assertTrue(c.getCurrentPlan().getConditionalBenefits().isEmpty());
        assertEquals(List.of(benefit), c.getCurrentPlan().getBaseline().getSnapshot().getConditionalBenefits());
        assertEquals(original.id(), c.getAcceptedProjectionSnapshot().id());
        assertEquals(original.annual(), c.getAcceptedProjectionSnapshot().annual());
    }

    @Test void unsupportedExecutionUsesExistingAccessibleStatusWithoutALoadBlockingModal() throws Exception {
        com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.startFx();
        var c = controller();
        c.getCurrentPlan().setConditionalBenefits(List.of(com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.monthly()));
        com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.fx(() -> {
            var window = new com.daviddunn.retirementplanner.ui.MainWindow(c,
                    com.daviddunn.retirementplanner.application.branding.ApplicationBranding.defaults(), null, null);
            var scene = window.createScene();
            var handler = window.getClass().getDeclaredMethod("handleProjectionFailure", Exception.class);
            handler.setAccessible(true);
            handler.invoke(window, new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.UnsupportedExecutionException());
            var notice = (Label) scene.lookup("#projection-funding-notice");
            assertTrue(notice.isVisible()); assertTrue(notice.isManaged());
            assertTrue(notice.isWrapText()); assertTrue(notice.isFocusTraversable());
            assertEquals(com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.MESSAGE, notice.getText());
            assertEquals(notice.getText(), notice.getAccessibleText());
        });
    }

    @Test void legacyPensionResultsRemainReadableSavableAndSwappableButNeverClaimCorrectedApplicability() throws Exception {
        var c = controller();
        var plan = c.getCurrentPlan();
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2027, 67),
                a.getProjectionLengthYears(), a.getProjectionStartDate(), a.getStateTaxAssumptions()));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension(
                "Synthetic survivor", AccountOwnership.PRIMARY, java.time.LocalDate.of(2026, 1, 1), null,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2500")));
        c.getCurrentProjection();
        c.saveCurrentAsBaseline("Legacy input context");
        var fresh = c.getAcceptedProjectionSnapshot();
        assertTrue(fresh.provenance().startsWith(PensionSelectionSemantics.PROVENANCE));
        // Synthetic legacy provenance: numerical evidence is retained exactly, not reinterpreted.
        var legacy = new CompleteProjectionSnapshot(fresh.version(), fresh.id(), fresh.owner(), fresh.sourcePlanId(), fresh.acceptedAt(),
                fresh.inputsDocument(), fresh.inputDigest(), "Accepted normal projection; original build version not recorded",
                fresh.annual(), fresh.nonInvestable(), fresh.summary(), fresh.people());
        plan.installProjectionComponents(Map.of(legacy.id(), legacy));
        var file = directory.resolve("legacy-pension.json");
        c.saveAs(file);
        var copy = controller();
        set(copy, "projectionEngine", throwing());
        copy.open(file);
        copy.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        var originalAnnual = copy.getAcceptedProjectionSnapshot().annual();
        assertEquals(legacy.annual(), originalAnnual);
        assertTrue(copy.currentProjectionInputsMatch(), "Input identity remains distinct from algorithm applicability");
        assertFalse(copy.currentProjectionDeterministicallyApplicable());
        assertTrue(copy.getProjectionSnapshotNotice().contains("Historical/outdated pension-tax"));
        assertNull(copy.prepareAnalyzerAssociation(AnalysisKind.COUPLE_EXHAUSTIVE, "a".repeat(64)));
        assertThrows(IllegalStateException.class, () -> copy.saveCurrentAsBaseline("Must not relabel old semantics"));
        copy.save();
        copy.saveAs(directory.resolve("legacy-pension-copy.json"));
        assertEquals(legacy.id(), copy.getAcceptedProjectionSnapshot().id());
        assertEquals(legacy.inputDigest(), copy.getAcceptedProjectionSnapshot().inputDigest());
        assertEquals(originalAnnual, copy.getAcceptedProjectionSnapshot().annual());
        // An explicit new calculation can coexist with the immutable old baseline.
        set(copy, "projectionEngine", new ProjectionEngine());
        copy.invalidateProjection();
        copy.getCurrentProjection();
        assertTrue(copy.currentProjectionDeterministicallyApplicable());
        var correctedId = copy.getAcceptedProjectionSnapshot().id();
        assertNotEquals(legacy.id(), correctedId);
        copy.save();
        set(copy, "projectionEngine", throwing());
        var prepared = copy.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(copy.commitProjectionSwap(prepared));
        assertEquals(legacy.id(), copy.getAcceptedProjectionSnapshot().id());
        assertFalse(copy.currentProjectionDeterministicallyApplicable());
        assertEquals(originalAnnual, copy.getAcceptedProjectionSnapshot().annual());
        copy.save();
        prepared = copy.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(copy.commitProjectionSwap(prepared));
        assertEquals(correctedId, copy.getAcceptedProjectionSnapshot().id());
        assertTrue(copy.currentProjectionDeterministicallyApplicable());
    }
    @TempDir Path directory;
    final List<ApplicationController> controllers = new ArrayList<>();
    ApplicationController controller() throws Exception { return controller(new JsonRetirementPlanRepository()); }
    ApplicationController controller(JsonRetirementPlanRepository repository) throws Exception {
        var c = new ApplicationController(repository, new JsonApplicationSettingsRepository(directory.resolve("settings-" + controllers.size())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analysis-" + controllers.size()))));
        c.newPlan(BaselineDetailsTest.plan(true)); controllers.add(c); return c;
    }
    @AfterEach void finishWrites() throws Exception {
        for (var c : controllers) {
            c.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
            c.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete()); c.getAnalysisParameters().shutdown();
            var drain = c.getAnalysisResults().drain(Duration.ofSeconds(10)); assertTrue(drain.safeToExit(), drain.toString());
            c.getAnalysisResults().shutdownAfterSuccessfulDrain();
        }
    }
    static ProjectionEngine throwing() {
        return new ProjectionEngine() {
            @Override public Projection project(RetirementPlan p) { throw new AssertionError("Restoration must not project"); }
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw new AssertionError("Restoration must not project"); }
        };
    }

    @Test void noInflationCurrentAndBaselineRestoreExactlyWithoutEngineExecution() throws Exception {
        var c = controller();
        var household = c.getCurrentPlan().getHousehold();
        for (var expense : List.copyOf(household.getExpenses())) household.removeExpense(expense);
        household.addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Fixed nominal spending", new BigDecimal("1234.5600"),
                com.daviddunn.retirementplanner.domain.financial.GrowthCategory.NONE));
        c.invalidateProjection();
        var original = c.getCurrentProjection();
        c.saveCurrentAsBaseline("No Inflation baseline");
        var accepted = c.getAcceptedProjectionSnapshot();
        set(c, "projectionEngine", throwing());
        var file = directory.resolve("no-inflation.json"); c.saveAs(file);
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(file);
        copy.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertNotNull(copy.peekCurrentProjection(), copy.getProjectionSnapshotNotice());
        assertEquals(accepted.id(), copy.getAcceptedProjectionSnapshot().id());
        assertEquals(accepted.inputsDocument(), copy.getAcceptedProjectionSnapshot().inputsDocument());
        assertEquals(accepted.inputDigest(), copy.getAcceptedProjectionSnapshot().inputDigest());
        assertEquals(accepted.chart(), copy.getAcceptedProjectionSnapshot().chart());
        compareFinancialGetters(original, copy.peekCurrentProjection());
        assertEquals(com.daviddunn.retirementplanner.domain.financial.GrowthCategory.NONE,
                copy.getCurrentPlan().getHousehold().getExpenses().getFirst().getGrowthCategory());
        assertEquals(accepted.id(), copy.getCurrentPlan().getBaseline().getProjectionSnapshotId());
        var previousOwner = copy.getCurrentPlan().getPlanId();
        copy.saveAs(directory.resolve("independent.json"));
        assertNotEquals(previousOwner, copy.getCurrentPlan().getPlanId());
        assertEquals(accepted.id(), copy.getAcceptedProjectionSnapshot().id());
        assertEquals(accepted.inputDigest(), copy.getAcceptedProjectionSnapshot().inputDigest());
    }
    static void compareFinancialGetters(Projection original, Projection restored) throws Exception {
        assertEquals(original.size(), restored.size());
        for (int i = 0; i < original.size(); i++) {
            var a = original.getYearAt(i); var b = restored.getYearAt(i);
            for (var getter : ProjectionYear.class.getMethods()) {
                if (!getter.getName().startsWith("get") || getter.getParameterCount() != 0 || !(getter.getReturnType() == BigDecimal.class || getter.getReturnType() == int.class)) continue;
                try { assertEquals(getter.invoke(a), getter.invoke(b), getter.getName()); }
                catch (java.lang.reflect.InvocationTargetException absent) {
                    assertThrows(java.lang.reflect.InvocationTargetException.class, () -> getter.invoke(b), getter.getName());
                }
            }
            assertEquals(a.getSocialSecurityResult(), b.getSocialSecurityResult());
            assertEquals(a.getEndingAccountSnapshots().size(), b.getEndingAccountSnapshots().size());
            for (int j = 0; j < a.getEndingAccountSnapshots().size(); j++) {
                var x = a.getEndingAccountSnapshots().get(j); var y = b.getEndingAccountSnapshots().get(j);
                assertEquals(x.getEndingBalance(), y.getEndingBalance());
                assertEquals(x.getAccount().getType(), y.getAccount().getType());
                assertEquals(x.getAccount().getOwnership(), y.getAccount().getOwnership());
                assertNotSame(x.getAccount(), y.getAccount());
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactInputsAnnualValuesSummaryAndChartSurviveFreshControllerWithoutEngine(boolean couple) throws Exception {
        var c = controller(); c.newPlan(BaselineDetailsTest.plan(couple));
        c.getCurrentPlan().addNonInvestableAsset(new NonInvestableAsset("Frozen home", new BigDecimal("123456.789000"), new BigDecimal("0.0250")));
        var projection = c.getCurrentProjection(); var accepted = c.getAcceptedProjectionSnapshot();
        assertFalse(c.isModified(), "Capturing a completed result does not dirty financial inputs");
        assertTrue(c.currentProjectionInputsMatch());
        assertEquals(new BigDecimal("1000000.0012300"), accepted.restoreInputs().getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
        assertThrows(UnsupportedOperationException.class, () -> accepted.annual().years().clear());
        accepted.restoreInputs().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        assertTrue(accepted.matches(c.getCurrentPlan()));
        c.saveCurrentAsBaseline("Original");
        set(c, "projectionEngine", throwing()); // Capturing, saving and restoring cannot use an engine.
        var file = directory.resolve("original.json"); c.saveAs(file);
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(file);
        set(copy, "projectionSummaryService", new com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummaryService(
                new com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService(),
                new com.daviddunn.retirementplanner.domain.projection.summary.IncomeSummaryService()) {
            @Override public com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary summarize(RetirementPlan plan, Projection p) {
                throw new AssertionError("Restoration must not recalculate income or financial summaries");
            }
        });
        copy.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNotNull(copy.peekCurrentProjection(), copy.getProjectionSnapshotNotice());
        var restored = copy.getAcceptedProjectionSnapshot();
        assertEquals(accepted.id(), restored.id()); assertEquals(accepted.acceptedAt(), restored.acceptedAt());
        assertEquals(accepted.inputsDocument(), restored.inputsDocument()); assertEquals(accepted.summary(), restored.summary());
        assertEquals(accepted.chart(), restored.chart()); assertEquals(accepted.nonInvestable(), restored.nonInvestable());
        compareFinancialGetters(projection, copy.peekCurrentProjection());
        compareFinancialGetters(projection, copy.getBaselineProjection());
        assertEquals(accepted.summary().primarySocialSecurity(), copy.getCurrentProjectionSummary().getIncomeSummary().getPrimarySocialSecurityMonthly());
        assertEquals(couple, copy.getCurrentProjectionSummary().getIncomeSummary().hasSpouse());
        assertFalse(copy.isModified()); assertTrue(copy.currentProjectionInputsMatch());
    }
    @Test void baselineCaptureRejectsStaleInputsAndRetainsOriginalThroughFailuresAndCancellation() throws Exception {
        var c = controller(); assertThrows(IllegalStateException.class, () -> c.saveCurrentAsBaseline("No result"));
        c.getCurrentProjection(); var first = c.getAcceptedProjectionSnapshot();
        set(c, "projectionEngine", throwing()); c.saveCurrentAsBaseline("Accepted");
        assertEquals(first.id(), c.getCurrentPlan().getBaseline().getProjectionSnapshotId());
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1000100"));
        assertFalse(c.currentProjectionInputsMatch());
        assertThrows(IllegalStateException.class, () -> c.saveCurrentAsBaseline("Stale"));
        assertEquals(first.id(), c.getCurrentPlan().getBaseline().getProjectionSnapshotId());
        for (RuntimeException failure : List.of(new CancellationException(), new IllegalStateException("Fatal"))) {
            c.invalidateProjection();
            set(c, "projectionEngine", new ProjectionEngine() { @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw failure; } });
            assertThrows(failure.getClass(), c::getCurrentProjection);
            assertEquals(first.id(), c.getAcceptedProjectionSnapshot().id());
            assertNull(c.peekCurrentProjection());
        }
    }
    @Test void distinctRolesAndYearMappingReconcileWithUnchangedComparisonAndIndependentSaveAs() throws Exception {
        var c = controller(); var plan = c.getCurrentPlan();
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(), 31, java.time.LocalDate.of(2026, 1, 1), a.getStateTaxAssumptions()));
        c.getCurrentProjection(); c.saveCurrentAsBaseline("Original 2056 ending"); var baseline = c.getAcceptedProjectionSnapshot();
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1100000.0012300")); c.markModified(); c.invalidateProjection();
        var comparison = c.compareProjections(); var current = c.getAcceptedProjectionSnapshot();
        assertNotEquals(baseline.id(), current.id());
        var details = c.prepareBaselineDetails(c.peekCurrentProjection()); assertNotNull(details);
        var last = details.baselineChart().years().getLast(); assertEquals(2056, last.year());
        assertEquals(c.getBaselineProjection().getLastYear().getEndingInvestableAssets(), last.values().get(com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric.INVESTABLE_ASSETS));
        assertEquals(c.peekCurrentProjection().getLastYear().getEndingInvestableAssets().subtract(last.values().get(com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric.INVESTABLE_ASSETS)), comparison.getEndingInvestableAssetsChange());
        var file = directory.resolve("a.json"); c.saveAs(file); byte[] original = Files.readAllBytes(file);
        var originalOwner = c.getCurrentPlan().getPlanId(); c.saveAs(directory.resolve("b.json"));
        assertNotEquals(originalOwner, c.getCurrentPlan().getPlanId()); assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(current.id(), c.getAcceptedProjectionSnapshot().id()); assertEquals(current.owner(), c.getAcceptedProjectionSnapshot().sourcePlanId());
        assertEquals(c.getCurrentPlan().getPlanId(), c.getAcceptedProjectionSnapshot().owner().toString());
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(file); copy.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(originalOwner, copy.getAcceptedProjectionSnapshot().owner().toString());
        assertEquals(details.rows(), (copy.compareProjections() != null ? copy.prepareBaselineDetails(copy.peekCurrentProjection()) : null).rows());
    }
    @Test void legacyBaselineSuppressesRebuiltChartAndNoOriginalResultsAreInventedOnOpen() throws Exception {
        var c = controller(); c.getCurrentPlan().setBaseline(ProjectionBaselineFactory.create(c.getCurrentPlan(), "Legacy"));
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1200000"));
        c.compareProjections();
        assertNull(c.prepareBaselineDetails(c.peekCurrentProjection()).baselineChart());
        assertNull(c.getCurrentPlan().getBaseline().getProjectionSnapshotId());
        var file = directory.resolve("legacy.json"); c.saveAs(file);
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(file); copy.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNotNull(copy.peekCurrentProjection()); assertNull(copy.getBaselineProjection());
        assertEquals("Legacy", copy.getCurrentPlan().getBaseline().getDescription());
    }
    @Test void corruptedComponentsStayUnavailableAndCannotDestroyDurableManifest() throws Exception {
        var c = controller(); c.getCurrentProjection(); c.saveCurrentAsBaseline("Original"); var file = directory.resolve("corrupt.json"); c.saveAs(file);
        byte[] planBytes = Files.readAllBytes(file);
        var ref = c.getCurrentPlan().getProjectionState().current();
        var component = directory.resolve(".retirement-planner-projections").resolve(c.getCurrentPlan().getPlanId()).resolve(ref.id() + ".json.gz");
        Files.write(component, new byte[]{1, 2, 3});
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(file); copy.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNull(copy.getCurrentProjection()); assertTrue(copy.getProjectionSnapshotNotice().contains("unavailable"));
        assertThrows(java.io.IOException.class, copy::save); assertArrayEquals(planBytes, Files.readAllBytes(file));
    }
    @Test void metadataIsFinanciallyIsolatedAndRoleVersionsAreStrict() throws Exception {
        var plan = BaselineDetailsTest.plan(true); var service = new AnalysisFingerprintService();
        var inputs = new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE, java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 1), BigDecimal.ZERO, null, null, List.of(), List.of(), null);
        var before = service.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, inputs);
        var mc = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService();
        var settings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(100, 417,
                new BigDecimal("0.05"), new BigDecimal("0.12"));
        var monteCarloBefore = mc.assess(com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService.standalone(plan, settings, null));
        plan.setProjectionState(new ProjectionRoleManifest(1, UUID.fromString(plan.getPlanId()), null, null));
        assertEquals(before, service.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, inputs));
        assertEquals(monteCarloBefore, mc.assess(com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService.standalone(plan, settings, null)));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionRoleManifest(3, UUID.randomUUID(), null, null));
        var copied = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        assertNull(copied.getProjectionState());
    }
    @Test void copiedFileCannotReuseAnotherPathAndDirtyIsNotProjectionFreshness() throws Exception {
        var c = controller(); c.getCurrentProjection();
        c.markModified(); assertTrue(c.currentProjectionInputsMatch());
        var file = directory.resolve("owned.json"); c.saveAs(file); assertFalse(c.isModified());
        var account = c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst();
        account.setCurrentBalance(account.getCurrentBalance().add(BigDecimal.ONE));
        assertFalse(c.currentProjectionInputsMatch()); assertFalse(c.isModified());
        var copied = directory.resolve("copied.json"); Files.copy(file, copied);
        var reopened = controller(); set(reopened, "projectionEngine", throwing()); reopened.open(copied);
        reopened.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNull(reopened.peekCurrentProjection()); assertTrue(reopened.getProjectionSnapshotNotice().contains("unavailable"));
        assertNotNull(reopened.getCurrentPlan().getProjectionState());
    }
    @Test void savedOutdatedInputsRemainHistoricalAndBackupRecoveryRestoresOriginalResults() throws Exception {
        var c = controller();
        c.getCurrentPlan().setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.03"),
                new BigDecimal("0.0250"), 31, java.time.LocalDate.of(2026, 1, 1)));
        c.getCurrentProjection(); c.saveCurrentAsBaseline("Original 31-year baseline");
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1150000"));
        c.invalidateProjection(); var original = c.getCurrentProjection();
        var codec = new AnalysisSnapshotCodec(); long start = System.nanoTime();
        var encoded = codec.encodeProjectionDocument(c.getAcceptedProjectionSnapshot()); long encodedAt = System.nanoTime();
        var decoded = codec.decodeDocument(encoded.bytes(), CompleteProjectionSnapshot.class); long decodedAt = System.nanoTime();
        compareFinancialGetters(original, decoded.annual().restoreProjection()); long restoredAt = System.nanoTime();
        Files.createDirectories(Path.of("target/phase12d-previews"));
        Files.writeString(Path.of("target/phase12d-previews/component-capacity.txt"),
                "Calculated fixture: years=31; accounts=" + decoded.annual().accounts().size()
                + "; rawBytes=" + encoded.rawBytes() + "; gzipBytes=" + encoded.bytes().length
                + "; encodeMs=" + (encodedAt-start)/1_000_000 + "; decodeMs=" + (decodedAt-encodedAt)/1_000_000
                + "; independentReconstructionCheckMs=" + (restoredAt-decodedAt)/1_000_000);
        var file = directory.resolve("outdated.json"); c.saveAs(file);
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1200000")); c.markModified(); c.save();
        var reopened = controller(); set(reopened, "projectionEngine", throwing()); reopened.open(file);
        reopened.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertFalse(reopened.currentProjectionInputsMatch()); assertTrue(reopened.getProjectionSnapshotNotice().contains("Outdated"));
        reopened.compareProjections(); assertTrue(reopened.prepareBaselineDetails(reopened.peekCurrentProjection()).status().toLowerCase().contains("outdated"));
        compareFinancialGetters(original, reopened.peekCurrentProjection());
        var backup = controller(); set(backup, "projectionEngine", throwing()); backup.openRecoveredPlan(file);
        backup.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(backup.isModified()); assertTrue(backup.currentProjectionInputsMatch());
        compareFinancialGetters(original, backup.peekCurrentProjection());
        backup.saveAs(directory.resolve("recovered.json"));
        assertEquals(decoded.id(), backup.getAcceptedProjectionSnapshot().id());
    }
    @Test void queuedRestorationCannotAttachToAnotherPlanOrReplaceANewAcceptedRun() throws Exception {
        var c = controller(); c.getCurrentProjection(); var file = directory.resolve("race.json"); c.saveAs(file);
        for (boolean switchPlan : new boolean[]{false, true}) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var barrier = c.getAnalysisResults().background(() -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return null; });
            assertTrue(entered.await(10, TimeUnit.SECONDS)); c.open(file); var loading = c.getProjectionRestoration();
            if (switchPlan) c.newPlan(BaselineDetailsTest.plan(false)); else c.invalidateProjection();
            var newer = c.getCurrentProjection(); var id = c.getAcceptedProjectionSnapshot().id();
            release.countDown(); barrier.toCompletableFuture().get(10, TimeUnit.SECONDS); loading.toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertSame(newer, c.peekCurrentProjection()); assertEquals(id, c.getAcceptedProjectionSnapshot().id());
        }
    }
    @Test void longDecimalsAndClearBaselineKeepTheCurrentRole() throws Exception {
        var c = controller();
        c.getCurrentPlan().setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.03" + "0".repeat(350)),
                new BigDecimal("0.0250"), 3, java.time.LocalDate.of(2026, 1, 1)));
        var original = c.getCurrentProjection(); c.saveCurrentAsBaseline("Long decimal");
        assertTrue(c.getAcceptedProjectionSnapshot().inputsDocument().contains("0.03" + "0".repeat(350)));
        var file = directory.resolve("long.json"); c.saveAs(file);
        c.getCurrentPlan().setBaseline(null); c.save(); assertNull(c.getCurrentPlan().getProjectionState().baseline());
        var copy = controller(); set(copy, "projectionEngine", throwing()); copy.open(file); copy.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        compareFinancialGetters(original, copy.peekCurrentProjection()); assertNull(copy.getBaselineProjection());
        assertNotNull(copy.getCurrentPlan().getProjectionState().current());
    }
    @ParameterizedTest @ValueSource(strings = {"VERIFY", "REPLACE"})
    void failedPlanCommitPreservesOldPrimaryAndRetryUsesAcceptedMemoryWithoutCalculation(String stage) throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var repository = new JsonRetirementPlanRepository(new AtomicFileWriter((s, p) -> {
            if (fail.get() && s.name().equals(stage)) throw new java.io.IOException("Injected plan transaction failure");
        }), new ObjectMapper());
        var c = controller(repository); c.getCurrentProjection(); c.saveCurrentAsBaseline("A"); var file = directory.resolve("atomic.json"); c.saveAs(file);
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1200000")); c.invalidateProjection(); c.getCurrentProjection();
        byte[] before = Files.readAllBytes(file); var accepted = c.getAcceptedProjectionSnapshot(); fail.set(true);
        assertThrows(java.io.IOException.class, c::save); assertArrayEquals(before, Files.readAllBytes(file));
        assertSame(accepted, c.getAcceptedProjectionSnapshot());
        fail.set(false); set(c, "projectionEngine", throwing()); c.save();
        var reopened = controller(); set(reopened, "projectionEngine", throwing()); reopened.open(file); reopened.getProjectionRestoration().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(accepted.id(), reopened.getAcceptedProjectionSnapshot().id());
    }
}
