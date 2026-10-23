package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.AccountFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisFingerprintTest {
    final AnalysisFingerprintService service = new AnalysisFingerprintService();
    AnalysisFingerprintService.Inputs inputs(String factor, String rate, int count, boolean baseline) {
        var candidate = SnapshotFixtures.couple().couple().entries().getFirst().strategy();
        return new AnalysisFingerprintService.Inputs(new BigDecimal(factor), BigDecimal.ONE,
                LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), new BigDecimal(rate), count,
                SnapshotFixtures.HASH, List.of(new AnalysisFingerprintService.Candidate(candidate, 1,
                        new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue(
                                new BigDecimal("150.00"), new BigDecimal("140.00"), new BigDecimal("100.00400"),
                                new BigDecimal("90.00"), new BigDecimal("60.00"), 2))),
                baseline ? List.of(new SnapshotContext.Section("baseline", List.of("Primary survivor 66"))) : List.of(), null);
    }
    RetirementPlan plan(AnalysisKind kind) {
        return kind.shape() == AnalysisKind.Shape.COUPLE ? LongevityWeightedAnalysisRequestFactoryTest.plan() : SinglePersonMortalityAnalysisTest.plan();
    }
    boolean assertsMonteCarloDelegationGuard(AnalysisKind kind, RetirementPlan plan, AnalysisFingerprintService.Inputs inputs) {
        if (!kind.isMonteCarlo()) return false;
        var failure = assertThrows(IllegalArgumentException.class, () -> service.fingerprint(kind, plan, inputs));
        assertTrue(failure.getMessage().contains("Monte Carlo"));
        assertFalse(service.assess(kind, plan, inputs, null).valid());
        return true;
    }
    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void deterministicIndependentOfIdentityNamesAndObjectGraphs(AnalysisKind kind) {
        var p = plan(kind); var i = inputs("1.00", "0.0100", 10, false);
        if (assertsMonteCarloDelegationGuard(kind, p, i)) return;
        var digest = service.fingerprint(kind, p, i);
        p.assignPlanId(UUID.randomUUID()); p.getHousehold().getPrimaryPerson().setFirstName("Renamed");
        assertEquals(digest, service.fingerprint(kind, p, i));
        var copied = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(p);
        assertEquals(digest, service.fingerprint(kind, copied, i));
        p.getHousehold().getPrimaryPerson().setBirthDate(p.getHousehold().getPrimaryPerson().getBirthDate().plusDays(1));
        assertNotEquals(digest, service.fingerprint(kind, p, i));
    }
    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void mortalityCandidateBaselineDependencySeparation(AnalysisKind kind) {
        var p = plan(kind);
        if (assertsMonteCarloDelegationGuard(kind, p, inputs("1.00", "0.0100", 10, false))) return;
        var original = service.fingerprint(kind, p, inputs("1.00", "0.0100", 10, false));
        var mortalityEdit = service.fingerprint(kind, p, inputs("0.90", "0.0200", 10, false));
        boolean mortalityMode = Set.of(AnalysisKind.COUPLE_SOCIAL_SECURITY, AnalysisKind.COUPLE_WEIGHTED,
                AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY, AnalysisKind.INDIVIDUAL_WEIGHTED).contains(kind);
        assertEquals(mortalityMode, !original.equals(mortalityEdit));
        assertEquals(kind == AnalysisKind.COUPLE_QUICK, !original.equals(service.fingerprint(kind, p, inputs("1.00", "0.0100", 5, false))));
        assertEquals(kind == AnalysisKind.COUPLE_WEIGHTED, !original.equals(service.fingerprint(kind, p, inputs("1.00", "0.0100", 10, true))));
        assertEquals(original, service.fingerprint(kind, p, inputs("1.00", "0.0100", 10, false)));
    }
    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void accountBalancesMatterOnlyToIntegratedModesAndOrderIsPreserved(AnalysisKind kind) {
        var p = plan(kind); var i = inputs("1.00", "0.0100", 10, false);
        if (assertsMonteCarloDelegationGuard(kind, p, i)) return;
        var a = AccountFactory.create(AccountType.BROKERAGE, "first", AccountOwnership.PRIMARY, new BigDecimal("123.4500"));
        var b = AccountFactory.create(AccountType.ROTH_IRA, "second", AccountOwnership.PRIMARY, new BigDecimal("456.7800"));
        p.getAccountPortfolio().addAccount(a); p.getAccountPortfolio().addAccount(b);
        var original = service.fingerprint(kind, p, i);
        a.setCurrentBalance(new BigDecimal("123.4501"));
        boolean integrated = kind != AnalysisKind.COUPLE_SOCIAL_SECURITY && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY;
        assertEquals(integrated, !original.equals(service.fingerprint(kind, p, i)));
        a.setCurrentBalance(new BigDecimal("123.4500")); assertEquals(original, service.fingerprint(kind, p, i));
        p.getAccountPortfolio().removeAccount(a); p.getAccountPortfolio().addAccount(a);
        assertEquals(integrated, !original.equals(service.fingerprint(kind, p, i)));
    }
    @Test void canonicalMapOrderScaleAndInvalidPendingInput() throws Exception {
        var mapper = new ObjectMapper();
        assertEquals(service.canonicalDigest(mapper.readTree("{\"b\":2,\"a\":1}")), service.canonicalDigest(mapper.readTree("{\"a\":1,\"b\":2}")));
        var p = plan(AnalysisKind.COUPLE_SOCIAL_SECURITY);
        assertNotEquals(service.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, p, inputs("1.0", "0.01", 10, false)),
                service.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, p, inputs("1.00", "0.01", 10, false)));
        var invalid = service.assess(AnalysisKind.COUPLE_SOCIAL_SECURITY, p, inputs("1", "0.01", 10, false), "unfinished date");
        assertFalse(invalid.valid()); assertNull(invalid.fingerprint());
    }
    @Test void compatibilitySeparatesVersionReferenceAndInputValidity() {
        var s = SnapshotFixtures.snapshot(UUID.randomUUID()); var policy = new AnalysisCompatibilityPolicy();
        var same = new AnalysisFingerprintService.Assessment(s.inputFingerprint(), s.contextFingerprint(), null);
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, policy.assess(s, same, s.algorithms(), s.references()).state());
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, policy.assess(s, same, Map.of("weighted", "v2"), s.references()).state());
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, policy.assess(s, same, s.algorithms(), Map.of()).state());
        assertEquals(AnalysisCompatibilityPolicy.State.INPUTS_NEED_ATTENTION, policy.assess(s, new AnalysisFingerprintService.Assessment(null, null, "invalid"), s.algorithms(), s.references()).state());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, policy.assess(s, same, s.algorithms(), s.references()).state());
        assertEquals(AnalysisCompatibilityPolicy.State.NO_SAVED_RESULTS, policy.assess(null, same, Map.of(), Map.of()).state());
    }
    @Test void copiedSnapshotCurrencyIsEvaluatedRatherThanAssumed() {
        var p = plan(AnalysisKind.COUPLE_WEIGHTED); var payload = SnapshotFixtures.couple(); var context = SnapshotFixtures.context();
        var metadata = payload.couple().metadata(); var m = metadata.longevityAssumptions();
        var inputs = new AnalysisFingerprintService.Inputs(m.primaryAdjustment().factor(), m.spouseAdjustment().factor(),
                m.mortalityBaseDate(), metadata.valuationDate(), metadata.realDiscountRate(), null, null,
                List.of(), context.baseline().fingerprintSections(), null);
        var snapshot = new AnalysisSnapshot(1, 1, UUID.fromString(p.getPlanId()), AnalysisKind.COUPLE_WEIGHTED,
                AnalysisKind.Shape.COUPLE, UUID.randomUUID(), java.time.Instant.now(), "test",
                AnalysisVersions.algorithms(AnalysisKind.COUPLE_WEIGHTED), AnalysisVersions.references(AnalysisKind.COUPLE_WEIGHTED),
                service.fingerprint(AnalysisKind.COUPLE_WEIGHTED, p, inputs), service.effectiveContextFingerprint(AnalysisKind.COUPLE_WEIGHTED, p),
                new com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec().digest(payload, context), payload, context, null);
        var copy = snapshot.clonedTo(UUID.randomUUID());
        var policy = new AnalysisCompatibilityPolicy();
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, policy.assessCopy(copy, p).state());
        p.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.BROKERAGE, "new", AccountOwnership.PRIMARY, BigDecimal.ONE));
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, policy.assessCopy(copy, p).state());
        assertEquals(snapshot.completedAt(), copy.completedAt()); assertEquals(snapshot.planId(), copy.originPlanId());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {
            "economicAssumptions.expectedAnnualInvestmentReturn", "economicAssumptions.generalInflationRate",
            "economicAssumptions.healthcareInflationRate", "economicAssumptions.socialSecurityColaRate",
            "taxAssumptions.federalTaxBracketGrowthRate", "taxAssumptions.standardDeductionGrowthRate",
            "deathScenarioAssumptions.postDeathExpenseFactor"})
    void economicAndTaxFieldDependenciesAreModeSpecific(String path) throws Exception {
        var mapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
        for (AnalysisKind kind : AnalysisKind.values()) {
            var original = plan(kind); var next = inputs("1.00", "0.0100", 10, false);
            var old = kind.isMonteCarlo() ? null : service.fingerprint(kind, original, next);
            var root = mapper.readTree(mapper.writeValueAsBytes(original));
            var names = path.split("\\.");
            ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("planningAssumptions").get(names[0]))
                    .put(names[1], new BigDecimal("0.07100"));
            var changed = mapper.treeToValue(root, RetirementPlan.class);
            if (kind.isMonteCarlo()) {
                assertTrue(assertsMonteCarloDelegationGuard(kind, original, next));
                assertTrue(assertsMonteCarloDelegationGuard(kind, changed, next));
                continue;
            }
            boolean ssOnly = kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY;
            assertEquals(!ssOnly || path.endsWith("socialSecurityColaRate"), !old.equals(service.fingerprint(kind, changed, next)), kind + ":" + path);
            if (kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY && path.endsWith("generalInflationRate"))
                assertNotEquals(service.effectiveContextFingerprint(kind, original), service.effectiveContextFingerprint(kind, changed));
        }
    }
    @Test void spendingAndOpeningRmdFactsAffectIntegratedButNotSsOnly() {
        var p = plan(AnalysisKind.COUPLE_WEIGHTED); var next = inputs("1.00", "0.0100", 10, false);
        var ss = service.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, p, next);
        var weighted = service.fingerprint(AnalysisKind.COUPLE_WEIGHTED, p, next);
        p.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("spending", new BigDecimal("1000.1200")));
        assertNotEquals(weighted, service.fingerprint(AnalysisKind.COUPLE_WEIGHTED, p, next));
        assertEquals(ss, service.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, p, next));
        var account = AccountFactory.create(AccountType.TRADITIONAL_IRA, "IRA", AccountOwnership.PRIMARY, new BigDecimal("100000.00"));
        p.getAccountPortfolio().addAccount(account); weighted = service.fingerprint(AnalysisKind.COUPLE_WEIGHTED, p, next);
        account.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2026, new BigDecimal("110000.0000"), new BigDecimal("123.4500")));
        assertNotEquals(weighted, service.fingerprint(AnalysisKind.COUPLE_WEIGHTED, p, next));
        assertEquals(ss, service.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, p, next));
    }
}
