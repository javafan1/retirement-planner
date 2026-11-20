package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.surplusPlan;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasFundingProvenanceTest {
    @Test
    void oldTargetProofTuplesRoundTripWithTheirOwnDependencyAndAreHistorical() throws Exception {
        var plan = selectArkansas(surplusPlan("1000000", 1));
        plan.getAccountPortfolio().addAccount(new RothIRA("Target history destination", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, BigDecimal.ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        var completed = assertInstanceOf(ProjectionExecutionResult.Completed.class,
                ProjectionEngine.forArkansasProduction().projectArkansasWithOutcome(plan, () -> false, ignored -> {}));
        var current = CompleteProjectionSnapshot.captureArkansas(UUID.randomUUID(), plan, completed.projection(), Instant.EPOCH);
        var annual = current.annual();
        var state = annual.arkansas();
        assertTrue(current.calculationSemanticsCurrent());
        assertEquals("AR-TARGET-P3D2G-1", state.dependencies().get("arkansas-roth-target"));
        var codec = new RetainedProjectionCodec();
        var json = new ObjectMapper();

        for (var policies : List.of(RetainedProjectionSnapshot.Arkansas.V1, RetainedProjectionSnapshot.Arkansas.CAPPED_FUNDING)) {
            var oldPolicyAnnual = new RetainedProjectionSnapshot(annual.schemaVersion(), annual.accounts(), annual.years(),
                    annual.metrics(), annual.strategy(), annual.advancedSocialSecurityPathUsed(), annual.warnings(),
                    new RetainedProjectionSnapshot.Arkansas(state.version(), state.jurisdiction(), state.projectionAlgorithm(), policies, state.years()));
            var oldBytes = codec.encode(oldPolicyAnnual).bytes();
            var restored = codec.decode(oldBytes);
            assertArrayEquals(oldBytes, codec.encode(restored).bytes());
            assertEquals(state.years(), restored.arkansas().years());
            var old = new CompleteProjectionSnapshot(current.version(), current.id(), current.owner(), current.sourcePlanId(),
                    current.acceptedAt(), current.inputsDocument(), current.inputDigest(), current.provenance(), restored,
                    current.nonInvestable(), current.summary(), current.people());
            assertFalse(old.calculationSemanticsCurrent());
            assertEquals(current.id(), old.id());
            assertEquals(current.inputDigest(), old.inputDigest());
            assertNotEquals(ArkansasAnalysisProvenance.annualDigest(current), ArkansasAnalysisProvenance.annualDigest(old));

            var dependency = new ArkansasAnalysisProvenance.Dependency(old.id(), old.inputDigest(),
                    ArkansasAnalysisProvenance.annualDigest(old), 1, 0, 1, true);
            var oldEvidence = new ArkansasAnalysisProvenance(1, StateTaxJurisdiction.AR, List.of(dependency), policies, List.of());
            var parsed = json.readValue(json.writeValueAsBytes(oldEvidence), ArkansasAnalysisProvenance.class);
            assertEquals(oldEvidence, parsed);
            assertFalse(parsed.policiesCurrent());
            parsed.validateDependency(old);
            assertThrows(IllegalArgumentException.class, () -> parsed.validateDependency(current));
        }
        var incompatible = new java.util.HashMap<>(RetainedProjectionSnapshot.Arkansas.V1);
        incompatible.put("arkansas-roth-target", "AR-TARGET-P3D2G-1");
        assertThrows(IllegalArgumentException.class, () -> new RetainedProjectionSnapshot.Arkansas(state.version(),
                state.jurisdiction(), state.projectionAlgorithm(), incompatible, state.years()));
    }

    @Test
    void legacyFundingEvidenceRemainsCanonicalAndCannotBeRelabeledAsCurrent() throws Exception {
        var plan = selectArkansas(surplusPlan("1000000", 1));
        var completed = assertInstanceOf(ProjectionExecutionResult.Completed.class,
                ProjectionEngine.forArkansasProduction().projectArkansasWithOutcome(plan, () -> false, ignored -> {}));
        var current = CompleteProjectionSnapshot.captureArkansas(UUID.randomUUID(), plan, completed.projection(), Instant.EPOCH);
        assertEquals(ArkansasValidationTax.reviewedVersions(), current.annual().arkansas().versions());
        assertEquals("AR-FUNDING-P3D2E-1", current.annual().arkansas().versions().get("arkansas-tax-funding"));

        // The discontinuity fixture has fully resolved receipts and the same search with either proof.
        var newer = current.annual();
        var arkansas = newer.arkansas();
        var earlier = new RetainedProjectionSnapshot(newer.schemaVersion(), newer.accounts(), newer.years(),
                newer.metrics(), newer.strategy(), newer.advancedSocialSecurityPathUsed(), newer.warnings(),
                new RetainedProjectionSnapshot.Arkansas(arkansas.version(), arkansas.jurisdiction(),
                        arkansas.projectionAlgorithm(), RetainedProjectionSnapshot.Arkansas.V1, arkansas.years()));
        var codec = new RetainedProjectionCodec();
        var bytes = codec.encode(earlier).bytes();
        var restored = codec.decode(bytes);
        assertArrayEquals(bytes, codec.encode(restored).bytes());
        assertEquals(earlier, restored);
        var legacy = new CompleteProjectionSnapshot(current.version(), current.id(), current.owner(), current.sourcePlanId(),
                current.acceptedAt(), current.inputsDocument(), current.inputDigest(), current.provenance(), restored,
                current.nonInvestable(), current.summary(), current.people());
        assertEquals(current.id(), legacy.id());
        assertEquals(current.inputDigest(), legacy.inputDigest());
        assertEquals(current.annual().years(), legacy.annual().years());
        assertTrue(legacy.matches(plan));
        assertFalse(legacy.calculationSemanticsCurrent());
        assertTrue(current.calculationSemanticsCurrent());
        assertNotEquals(ArkansasAnalysisProvenance.annualDigest(current), ArkansasAnalysisProvenance.annualDigest(legacy));

        var dependency = new ArkansasAnalysisProvenance.Dependency(legacy.id(), legacy.inputDigest(),
                ArkansasAnalysisProvenance.annualDigest(legacy), 1, 0, 1, false);
        var evidence = new ArkansasAnalysisProvenance(1, StateTaxJurisdiction.AR, List.of(dependency),
                earlier.arkansas().dependencies(), List.of());
        var json = new ObjectMapper();
        var decoded = json.readValue(json.writeValueAsBytes(evidence), ArkansasAnalysisProvenance.class);
        assertEquals(evidence, decoded);
        assertFalse(decoded.policiesCurrent());
        decoded.validateDependency(legacy);
        assertThrows(IllegalArgumentException.class, () -> decoded.validateDependency(current));

        var relabeled = new ArkansasAnalysisProvenance(1, StateTaxJurisdiction.AR, List.of(dependency),
                newer.arkansas().dependencies(), List.of());
        assertThrows(IllegalArgumentException.class, () -> relabeled.validateDependency(legacy));
        assertThrows(IllegalArgumentException.class, () -> new ArkansasAnalysisProvenance(1, StateTaxJurisdiction.AR,
                List.of(dependency), Map.of("arkansas-income-tax-rules", "AR-2026-P2-1",
                        "arkansas-tax-funding", "AR-FUNDING-UNREVIEWED"), List.of()));
    }
}
