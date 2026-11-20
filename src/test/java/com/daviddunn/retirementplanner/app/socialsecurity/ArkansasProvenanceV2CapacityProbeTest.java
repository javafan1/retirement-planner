package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasProvenanceV2CapacityProbeTest {
    @Test void representativeDrawdownRetainsAndRestoresEveryPensionAttribution() throws Exception {
        var plan = fixture();
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var bounded = new IntegratedSocialSecurityCompleteStrategySearchRequest(plan,
                List.of(62, 64, 66, 68, 70), List.of(62, 64, 66, 68, 70),
                List.of(standard.primarySurvivorCandidates().getFirst(), standard.primarySurvivorCandidates().getLast()),
                List.of(standard.spouseSurvivorCandidates().getFirst(), standard.spouseSurvivorCandidates().getLast()),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 0);
        var scenarios = LongevityContinuationTest.mortality(plan, List.of(2037, 2043, 2049, 2055), List.of(2038, 2044, 2050, 2056));
        var cancellation = AnalysisCancellationToken.none();
        var configured = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, bounded.strategies(), scenarios,
                LocalDate.of(2030, 1, 1), new BigDecimal("0.03"), Optional.of(configured),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), AnalysisProgressListener.none(), cancellation);
        var accepted = (ProjectionExecutionResult.Completed) ProjectionEngine.forArkansasProduction()
                .projectArkansasWithOutcome(plan, cancellation, ignored -> { });
        var source = CompleteProjectionSnapshot.captureArkansas(UUID.randomUUID(), plan, accepted.projection(), Instant.now());
        var kind = AnalysisKind.COUPLE_WEIGHTED;
        var identity = UUID.randomUUID();
        var date = Instant.now();
        var baseline = new SnapshotContext.Baseline(List.of(), WeightedSnapshots.Strategy.from(configured), "");
        var context = new SnapshotContext("Capacity fixture", "Primary", "Spouse", date, List.of(), List.of(), List.of(), Map.of(), baseline);
        var mortality = scenarios.assumptions();
        var fingerprint = new AnalysisFingerprintService().assess(kind, plan, new AnalysisFingerprintService.Inputs(
                mortality.primaryAdjustment().factor(), mortality.spouseAdjustment().factor(), mortality.mortalityBaseDate(),
                request.valuationDate(), request.realDiscountRate(), null, null, List.of(), baseline.fingerprintSections(), null), null);
        var codec = new AnalysisSnapshotCodec();
        long beforeMemory = memory();
        long workStart = System.nanoTime();
        var run = ArkansasAnalysisProvenance.runWeightedProductionV2(source, identity, fingerprint.fingerprint(), cancellation,
                comparison -> comparison.compare(request), value -> new WeightedSnapshots.Payload(WeightedSnapshots.Couple.from(value), null));
        long workEnd = System.nanoTime();
        long afterMemory = memory();
        var completion = new AcceptedAnalysisCompletion(source.owner(), null, kind, identity, date,
                fingerprint.fingerprint(), fingerprint.contextFingerprint(), AnalysisVersions.calculatedAlgorithms(kind, run.payload()),
                AnalysisVersions.references(kind), run.payload(), context).withArkansasExecution(run.evidence());
        var original = completion.presentationSnapshot(codec);
        long encodeStart = System.nanoTime();
        var bytes = codec.encode(original);
        long decodeStart = System.nanoTime();
        var restored = codec.decode(bytes);
        long validationStart = System.nanoTime();
        restored.arkansasExecution().validateDependency(source);
        var result = restored.payload().couple().restore();
        long validationEnd = System.nanoTime();
        assertEquals(original, restored);
        var v2 = restored.arkansasExecution().executionEvidence();
        assertEquals(100, result.completedStrategyCount());
        assertEquals(182, v2.executions().size());
        assertEquals(174, v2.facts().size());
        assertEquals(18_616, v2.executions().stream().mapToInt(execution -> v2.pensionVectors().get(execution.pensionVector()).size()).sum());
        assertEquals(416, v2.weightedAttribution().candidates().stream().mapToInt(ArkansasWeightedAttribution.Candidate::scenarioCount).sum());
        assertEquals(2, v2.facts().stream().filter(fact -> fact.recipient() != fact.originalOwner()).map(fact -> fact.recipient()).distinct().count());
        byte[] raw = codec.raw(original);
        assertArrayEquals(raw, codec.raw(restored));
        System.out.printf(Locale.ROOT, "strategies=100 worlds=16 executed=182 years=4654 logical=18616 dictionary=174 "
                + "scenarioLinks=416 physicalVectors=%d encodedPensionReferences=%d rawBytes=%d compressedBytes=%d workerMs=%.3f encodeMs=%.3f decodeMs=%.3f "
                + "restoreValidateMs=%.3f heapBeforeMiB=%.3f heapAfterMiB=%.3f%n",
                v2.pensionVectors().size(), v2.pensionVectors().stream().mapToInt(List::size).sum(),
                raw.length, bytes.length, (workEnd-workStart)/1e6, (decodeStart-encodeStart)/1e6,
                (validationStart-decodeStart)/1e6, (validationEnd-validationStart)/1e6, beforeMemory/1048576.0, afterMemory/1048576.0);

        var mapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        var document = (ObjectNode) mapper.readTree(raw);
        ((ObjectNode) document.get("arkansasExecution")).put("version", 17);
        assertThrows(IOException.class, () -> decodeModified(codec, mapper, document));

        var changed = (ObjectNode) mapper.readTree(raw);
        var evidence = changed.path("arkansasExecution").path("executionEvidence");
        var links = (ArrayNode) evidence.path("pensionVectors").get(0);
        var originalFirst = links.get(0);
        links.set(0, mapper.getNodeFactory().numberNode(v2.facts().size()));
        assertThrows(IOException.class, () -> decodeModified(codec, mapper, changed));
        links.set(0, originalFirst);
        links.insert(1, originalFirst);
        assertThrows(IOException.class, () -> decodeModified(codec, mapper, changed));
        links.remove(1);
        links.remove(links.size() - 1);
        assertThrows(IOException.class, () -> decodeModified(codec, mapper, changed));

        var oversized = (ObjectNode) mapper.readTree(raw);
        var facts = (ArrayNode) oversized.path("arkansasExecution").path("executionEvidence").path("facts");
        var firstFact = facts.get(0);
        while (facts.size() <= ArkansasAnalysisEvidenceV2.MAX_FACTS) facts.add(firstFact);
        assertTrue(assertThrows(IOException.class, () -> decodeModified(codec, mapper, oversized)).getMessage().contains("collection limit"));

        var carrier = v2.executions().getFirst();
        var vector = v2.pensionVectors().getFirst();
        var firstFacts = v2.facts().subList(0, vector.size());
        int withBudget = ArkansasAnalysisEvidenceV2.MAX_LOGICAL_RECEIPTS / vector.size();
        assertTrue(withBudget < ArkansasAnalysisEvidenceV2.MAX_EXECUTIONS);
        var repeated = new ArrayList<ArkansasAnalysisEvidenceV2.Execution>(withBudget + 1);
        for (int index = 0; index <= withBudget; index++) {
            repeated.add(new ArkansasAnalysisEvidenceV2.Execution(index, carrier.candidate(), carrier.primaryDeath(),
                    carrier.spouseDeath(), carrier.firstYear(), carrier.requestedLastYear(), carrier.completedYears(),
                    carrier.shortage(), carrier.annualDigest(), 0, carrier.pensionDigest()));
        }
        var boundaryGraph = new ArkansasAnalysisEvidenceV2(identity, fingerprint.fingerprint(), v2.resultDigest(), firstFacts,
                List.of(vector), repeated.subList(0, withBudget), null, null, null, v2.weightedAttribution());
        assertEquals(withBudget, boundaryGraph.executions().size());
        assertThrows(ArkansasAnalysisEvidenceV2.BudgetExceeded.class, () -> new ArkansasAnalysisEvidenceV2(
                identity, fingerprint.fingerprint(), v2.resultDigest(), firstFacts, List.of(vector), repeated,
                null, null, null, v2.weightedAttribution()));
        assertThrows(ArkansasAnalysisEvidenceV2.BudgetExceeded.class, () -> new ArkansasAnalysisEvidenceV2(
                identity, fingerprint.fingerprint(), v2.resultDigest(), firstFacts,
                Collections.nCopies(ArkansasAnalysisEvidenceV2.MAX_REFERENCES / vector.size() + 1, vector),
                List.of(carrier), null, null, null, v2.weightedAttribution()));
        assertThrows(ArkansasAnalysisEvidenceV2.BudgetExceeded.class, () -> new ArkansasAnalysisEvidenceV2(
                identity, fingerprint.fingerprint(), v2.resultDigest(), List.of(),
                Collections.nCopies(ArkansasAnalysisEvidenceV2.MAX_PENSION_VECTORS + 1, List.of()),
                List.of(carrier), null, null, null, v2.weightedAttribution()));

        assertArrayEquals(raw, codec.raw(codec.decode(bytes)));
    }

    @Test void compactMortalityAndArchiveLimitsFailBeforeReconstruction() throws Exception {
        var plan = fixture();
        var strategy = WeightedSnapshots.Strategy.from(new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan));
        int maximum = ArkansasWeightedAttribution.MAX_WORLDS;
        var links = Base64.getEncoder().encodeToString(new byte[maximum * Short.BYTES]);
        var digest = "0".repeat(64);
        var candidate = new ArkansasWeightedAttribution.Candidate(1, strategy, null, maximum, links, digest, digest, null, null);
        assertEquals(maximum, candidate.scenarioCount());
        assertThrows(IllegalArgumentException.class, () ->
                new ArkansasWeightedAttribution.Candidate(1, strategy, null, maximum + 1, links, digest, digest, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new ArkansasWeightedAttribution.Candidate(1, strategy, null, maximum, links.substring(1), digest, digest, null, null));
        assertThrows(ArkansasAnalysisEvidenceV2.BudgetExceeded.class, () -> new ArkansasWeightedAttribution(
                List.of(), null, Collections.nCopies(ArkansasWeightedAttribution.MAX_SCENARIO_LINKS / maximum + 1, candidate)));
        var codec = new AnalysisSnapshotCodec();
        assertTrue(assertThrows(IOException.class, () -> codec.decode(new byte[AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES + 1]))
                .getMessage().contains("Compressed snapshot"));
        var inflated = new ByteArrayOutputStream();
        byte[] zeros = new byte[4096];
        try (var zip = new GZIPOutputStream(inflated)) {
            for (int remainder = AnalysisSnapshotCodec.MAX_RAW_BYTES + 1; remainder > 0; remainder -= zeros.length) {
                zip.write(zeros, 0, Math.min(remainder, zeros.length));
            }
        }
        assertTrue(assertThrows(IOException.class, () -> codec.decode(inflated.toByteArray())).getMessage().contains("Uncompressed snapshot"));
    }

    @Test void simultaneousDeathsRetainTheLaterGenuineBothAliveCarrier() throws Exception {
        var plan = fixture();
        var mortality = LongevityContinuationTest.mortality(plan, List.of(2037, 2043), List.of(2037, 2043));
        var configured = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
        var cancellation = AnalysisCancellationToken.none();
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(configured), mortality,
                start, BigDecimal.ZERO, Optional.of(configured), LongevityWeightedDetailRetentionPolicy.aggregateOnly(),
                AnalysisProgressListener.none(), cancellation);
        var projection = (ProjectionExecutionResult.Completed) ProjectionEngine.forArkansasProduction()
                .projectArkansasWithOutcome(plan, cancellation, ignored -> { });
        var time = Instant.now();
        var source = CompleteProjectionSnapshot.captureArkansas(UUID.randomUUID(), plan, projection.projection(), time);
        var kind = AnalysisKind.COUPLE_WEIGHTED;
        var id = UUID.randomUUID();
        var baseline = new SnapshotContext.Baseline(List.of(), WeightedSnapshots.Strategy.from(configured), "");
        var context = new SnapshotContext("Simultaneous deaths", "Primary", "Spouse", time, List.of(), List.of(), List.of(), Map.of(), baseline);
        var fingerprints = new AnalysisFingerprintService().assess(kind, plan,
                new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE, start, start, BigDecimal.ZERO,
                        null, null, List.of(), baseline.fingerprintSections(), null), null);
        var run = ArkansasAnalysisProvenance.runWeightedProductionV2(source, id, fingerprints.fingerprint(), cancellation,
                comparison -> comparison.compare(request), result -> new WeightedSnapshots.Payload(WeightedSnapshots.Couple.from(result), null));
        var completion = new AcceptedAnalysisCompletion(source.owner(), null, kind, id, time,
                fingerprints.fingerprint(), fingerprints.contextFingerprint(), AnalysisVersions.calculatedAlgorithms(kind, run.payload()),
                AnalysisVersions.references(kind), run.payload(), context).withArkansasExecution(run.evidence());
        var codec = new AnalysisSnapshotCodec();
        var restored = codec.decode(codec.encode(completion.presentationSnapshot(codec)));
        var evidence = restored.arkansasExecution().executionEvidence();
        var proof = evidence.weightedAttribution();
        var earlier = simultaneousWorldIndex(proof.worlds(), 2037);
        var sourceCandidate = proof.candidates().getFirst();
        var links = java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(sourceCandidate.executionLinks()));
        int ordinal = Short.toUnsignedInt(links.getShort(earlier * Short.BYTES)) - 1;
        var carrier = evidence.executions().get(ordinal);
        assertEquals(2043, carrier.primaryDeath());
        assertEquals(2043, carrier.spouseDeath());
        restored.arkansasExecution().validateDependency(source);
    }

    private static int simultaneousWorldIndex(List<ArkansasWeightedAttribution.World> worlds, int year) {
        for (int index = 0; index < worlds.size(); index++) {
            if (worlds.get(index).primaryDeath() == year && worlds.get(index).spouseDeath() == year) return index;
        }
        throw new AssertionError("Missing simultaneous mortality world.");
    }

    private static void decodeModified(AnalysisSnapshotCodec codec, ObjectMapper mapper, ObjectNode tree) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(out)) { mapper.writeValue(zip, tree); }
        codec.decode(out.toByteArray());
    }

    private static long memory() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private static RetirementPlan fixture() {
        var plan = Stage4TestPlans.plan();
        var old = plan.getPlanningAssumptions();
        var opening = LocalDate.of(2030, 1, 1);
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, 65), 25, opening,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.AR)));
        for (var owner : List.of(PRIMARY, SPOUSE)) {
            var person = plan.getHousehold().peopleByOwner().get(owner);
            person.getIncomeSources().stream().filter(Pension.class::isInstance).toList().forEach(person::removeIncomeSource);
            for (int variant = 0; variant < 2; variant++) {
                var pension = new Pension(owner.name() + " Pension " + (variant + 1), owner, opening, null,
                        new BigDecimal(variant == 0 ? "3400" : "2700"), new BigDecimal("0.02"),
                        new BigDecimal(variant == 0 ? "2650" : "2100"));
                pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
                person.addIncomeSource(pension);
            }
        }
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Spouse IRA", SPOUSE, new BigDecimal("420000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Spouse Roth", SPOUSE, new BigDecimal("130000")));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, BigDecimal.ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        return plan;
    }
}
