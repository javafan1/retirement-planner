package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.fasterxml.jackson.core.*;
import static org.junit.jupiter.api.Assertions.*;

class ExhaustiveSnapshotTest {
    final AnalysisSnapshotCodec codec = new AnalysisSnapshotCodec();
    static final Instant START = Instant.parse("2026-10-20T12:00:00Z");

    static RetirementPlan plan(int years) {
        var plan = FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.MI);
        plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.CHECKING, "Funding", AccountOwnership.JOINT,
                new BigDecimal("15000000.0000")));
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), years,
                a.getProjectionStartDate(), a.getStateTaxAssumptions()));
        return plan;
    }

    static ExhaustiveSnapshot payload(IntegratedSocialSecurityCompleteStrategySearchResult result) {
        var first = result.rankedSuccessfulEntries().getFirst();
        var report = new ExhaustiveSnapshot.Report("Synthetic household", "Primary", "Spouse", START,
                List.of(new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section(
                        "Frozen financial assumptions", List.of("Return: 0.0400", "Start: 2027-07-01", "State: MI"))),
                "Original persisted current strategy");
        var reference = new ExhaustiveSnapshot.CrossReference(ExhaustiveSnapshot.ReferenceStatus.AVAILABLE,
                "Original SS recommendation", UUID.fromString("00000000-0000-0000-0000-000000000123"), "a".repeat(64), 7L,
                WeightedSnapshots.Strategy.from(first.strategy()), new BigDecimal("345678.0012300"),
                first.generationOrder(), first.metrics().orElseThrow().afterTaxEstate(), first.afterTaxEstateRank().orElseThrow(),
                new BigDecimal("0.000000"));
        var groups = new ArrayList<ExhaustiveSnapshot.Group>();
        for (var entry : result.rankedSuccessfulEntries()) {
            if (groups.stream().noneMatch(g -> sameGroup(result.entries().get(g.representativeOrder() - 1).metrics().orElseThrow(), entry.metrics().orElseThrow())))
                groups.add(new ExhaustiveSnapshot.Group(entry.generationOrder(), (int) result.rankedSuccessfulEntries().stream()
                        .filter(e -> sameGroup(entry.metrics().orElseThrow(), e.metrics().orElseThrow())).count()));
            if (groups.size() == 20) break;
        }
        return ExhaustiveSnapshot.capture(result, Duration.ofSeconds(123, 123456789), 20,
                groups, report, reference, START.plusSeconds(124),
                new ExhaustiveSnapshot.Selection(first.generationOrder(), WeightedSnapshots.Strategy.from(first.strategy()),
                        "FUTURE_DOLLAR_ESTATE", "heat-map"));
    }

    static boolean sameGroup(ProjectionMetrics a, ProjectionMetrics b) {
        return List.of(a.afterTaxEstate(), a.endingInvestableAssets(), a.totalTaxes(), a.lifetimePortfolioWithdrawals(), a.lifetimeHouseholdSocialSecurity())
                .equals(List.of(b.afterTaxEstate(), b.endingInvestableAssets(), b.totalTaxes(), b.lifetimePortfolioWithdrawals(), b.lifetimeHouseholdSocialSecurity()));
    }

    static ProjectionMetrics differences(ProjectionMetrics candidate, ProjectionMetrics baseline) throws Exception {
        var fields = ProjectionMetrics.class.getRecordComponents();
        Object[] values = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            var a = (BigDecimal) fields[i].getAccessor().invoke(candidate);
            var b = (BigDecimal) fields[i].getAccessor().invoke(baseline);
            values[i] = a == null && b == null ? null : a.subtract(b);
        }
        return ProjectionMetrics.class.getDeclaredConstructor(Collections.nCopies(14, BigDecimal.class).toArray(Class<?>[]::new)).newInstance(values);
    }

    AnalysisSnapshot envelope(ExhaustiveSnapshot p) throws Exception {
        var context = new SnapshotContext("Synthetic household", "Primary", "Spouse", START,
                List.of(), List.of(), List.of(), Map.of());
        return new AnalysisSnapshot(1, 1, UUID.fromString("00000000-0000-0000-0000-000000000456"),
                AnalysisKind.COUPLE_EXHAUSTIVE, AnalysisKind.Shape.COUPLE, UUID.randomUUID(), p.completedAt(), "test",
                AnalysisVersions.algorithms(AnalysisKind.COUPLE_EXHAUSTIVE), AnalysisVersions.references(AnalysisKind.COUPLE_EXHAUSTIVE),
                "b".repeat(64), "c".repeat(64), codec.digest(p, context), p, context, null);
    }

    static IntegratedSocialSecurityCompleteStrategySearchResult actualResult() {
        var plan = plan(4);
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        return new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(
                new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(62, 67, 70), List.of(62, 67),
                        standard.primarySurvivorCandidates().subList(0, 2), standard.spouseSurvivorCandidates().subList(0, 2),
                        IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 20));
    }

    @Test void actualSearchPreservesEveryEntryBaselineProjectionAndFrozenContext(@TempDir Path dir) throws Exception {
        var result = actualResult();
        var snapshot = envelope(payload(result));
        var bytes = codec.encode(snapshot);
        var copy = codec.decode(bytes);
        assertEquals(snapshot, copy);
        assertArrayEquals(bytes, codec.encode(copy));
        var restored = ((ExhaustiveSnapshot) copy.payload()).restore();
        assertEquals(result.rankingMeasure(), restored.rankingMeasure());
        assertEquals(result.currentPlanRank(), restored.currentPlanRank());
        assertEquals(result.entries().size(), restored.entries().size());
        assertEquals(20, restored.entries().stream().filter(e -> e.retainedDetail().isPresent()).count());
        RetainedProjectionCodecTest.assertGraph(result.currentPlanBaseline(), restored.currentPlanBaseline(), new IdentityHashMap<>(), "baseline");
        for (int i = 0; i < result.entries().size(); i++) {
            var e = result.entries().get(i); var a = restored.entries().get(i);
            assertEquals(e.generationOrder(), a.generationOrder()); assertEquals(e.strategy(), a.strategy());
            assertEquals(e.afterTaxEstateRank(), a.afterTaxEstateRank()); assertEquals(e.metrics(), a.metrics());
            assertEquals(e.differencesFromCurrentPlan(), a.differencesFromCurrentPlan()); assertEquals(e.failure(), a.failure());
            RetainedProjectionCodecTest.assertGraph(e.retainedDetail(), a.retainedDetail(), new IdentityHashMap<>(), "detail");
        }
        assertEquals(result.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList(),
                restored.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList());
        for (var ranked : restored.rankedSuccessfulEntries())
            assertSame(ranked, restored.entries().stream().filter(e -> e.generationOrder() == ranked.generationOrder()).findFirst().orElseThrow());
        var repo = new JsonAnalysisSnapshotRepository(dir);
        repo.save(snapshot);
        assertEquals(snapshot, repo.load(snapshot.planId(), snapshot.kind()).orElseThrow());
        assertEquals("345678.0012300", ((ExhaustiveSnapshot) copy.payload()).socialSecurityReference().expectedPresentValue().toPlainString());
    }

    /** Test-only record copying allows negative fixtures without weakening production constructors. */
    @SuppressWarnings("unchecked")
    static <T> T replace(T record, String field, Object value) throws Exception {
        var fields = record.getClass().getRecordComponents();
        Object[] args = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) args[i] = fields[i].getName().equals(field) ? value : fields[i].getAccessor().invoke(record);
        return (T) record.getClass().getDeclaredConstructor(Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType)
                .toArray(Class<?>[]::new)).newInstance(args);
    }

    @ParameterizedTest @ValueSource(strings = {"duplicateRank", "missingRank", "wrongRank", "wrongOrder", "wrongDetail", "selection", "group", "crossReference", "orphan"})
    void rejectsMalformedReferencesWithoutRepairingOrRecalculating(String problem) throws Exception {
        var original = payload(actualResult());
        ExhaustiveSnapshot bad;
        var entries = new ArrayList<>(original.entries());
        switch (problem) {
            case "duplicateRank" -> { var ids = new ArrayList<>(original.rankedOrders()); ids.add(ids.getFirst()); bad = replace(original, "rankedOrders", ids); }
            case "missingRank" -> bad = replace(original, "rankedOrders", original.rankedOrders().subList(1, original.rankedOrders().size()));
            case "wrongRank" -> { entries.set(0, replace(entries.getFirst(), "rank", 999)); bad = replace(original, "entries", entries); }
            case "wrongOrder" -> { Collections.swap(entries, 0, 1); bad = replace(original, "entries", entries); }
            case "wrongDetail" -> {
                var detailed = original.entries().stream().filter(e -> e.detail() != null).toList();
                var first = detailed.getFirst();
                entries.set(first.order() - 1, replace(first, "detail", detailed.getLast().detail()));
                bad = replace(original, "entries", entries);
            }
            case "selection" -> bad = replace(original, "selection", new ExhaustiveSnapshot.Selection(99999,
                    original.selection().strategy(), "FUTURE_DOLLAR_ESTATE", "heat-map"));
            case "group" -> bad = replace(original, "displayedGroups", List.of(new ExhaustiveSnapshot.Group(99999, 1)));
            case "crossReference" -> bad = replace(original, "socialSecurityReference", replace(original.socialSecurityReference(), "matchingOrder", 99999));
            case "orphan" -> {
                var baseline = original.baseline();
                var annual = baseline.years().getFirst();
                var balances = new ArrayList<>(annual.balances());
                balances.set(0, new RetainedProjectionSnapshot.Balance(99999, balances.getFirst().endingBalance()));
                var years = new ArrayList<>(baseline.years()); years.set(0, replace(annual, "balances", balances));
                bad = replace(original, "baseline", replace(baseline, "years", years));
            }
            default -> throw new AssertionError(problem);
        }
        assertThrows(RuntimeException.class, bad::restore);
    }

    @Test void unavailableCrossReferenceAndUnselectedPresentationAreExplicit() throws Exception {
        var p = payload(actualResult());
        for (var status : List.of(ExhaustiveSnapshot.ReferenceStatus.NO_CURRENT_SOCIAL_SECURITY_RESULT,
                ExhaustiveSnapshot.ReferenceStatus.OUTSIDE_SEARCH_UNIVERSE)) {
            boolean absent = status == ExhaustiveSnapshot.ReferenceStatus.NO_CURRENT_SOCIAL_SECURITY_RESULT;
            var reference = new ExhaustiveSnapshot.CrossReference(status, "Unavailable when completed",
                    absent ? null : p.socialSecurityReference().sourceRunId(), null, null,
                    absent ? null : p.socialSecurityReference().recommendedStrategy(),
                    absent ? null : new BigDecimal("123.00000"), null, null, null, null);
            var frozen = replace(replace(p, "socialSecurityReference", reference), "selection", null);
            var envelope = envelope(frozen);
            assertEquals(envelope, codec.decode(codec.encode(envelope)));
        }
    }

    @Test void allFourteenMetricsPreserveLongExactDecimalsAndIndependentScales() throws Exception {
        var p = payload(actualResult());
        var fields = ProjectionMetrics.class.getRecordComponents();
        assertEquals(14, fields.length);
        Object[] values = new Object[14];
        for (int i = 0; i < values.length; i++) values[i] = new BigDecimal("1." + "0".repeat(i + 1) + "12300");
        values[0] = new BigDecimal("1." + "0".repeat(1063) + "12300");
        var metrics = ProjectionMetrics.class.getDeclaredConstructor(Collections.nCopies(14, BigDecimal.class).toArray(Class<?>[]::new)).newInstance(values);
        var entries = new ArrayList<>(p.entries());
        entries.set(0, replace(entries.getFirst(), "differences", metrics));
        var snapshot = envelope(replace(p, "entries", entries));
        var restored = ((ExhaustiveSnapshot) codec.decode(codec.encode(snapshot)).payload()).restore();
        assertEquals(metrics, restored.entries().getFirst().differencesFromCurrentPlan().orElseThrow());
    }

    @Test void corruptedDigestAndWrongPayloadKindAreRejected() throws Exception {
        var snapshot = envelope(payload(actualResult()));
        var raw = new String(codec.raw(snapshot), java.nio.charset.StandardCharsets.UTF_8);
        var changed = raw.replace("345678.0012300", "345678.0012301");
        assertNotEquals(raw, changed);
        assertThrows(java.io.IOException.class, () -> codec.decode(RetainedProjectionCodecTest.gzip(changed)));
        assertThrows(IllegalArgumentException.class, () -> snapshot.payload().validate(AnalysisKind.COUPLE_WEIGHTED));
        var inconsistent = replace(snapshot, "completedAt", snapshot.completedAt().plusSeconds(1));
        assertThrows(java.io.IOException.class, () -> codec.encode(inconsistent));
    }

    @Test void clonePreservesFrozenPayloadAndAssessesOnlyDeterministicDependencies() throws Exception {
        var source = envelope(payload(actualResult()));
        var plan = plan(4);
        var fingerprints = new AnalysisFingerprintService();
        source = replace(source, "inputFingerprint", fingerprints.fingerprint(source.kind(), plan, AnalysisFingerprintService.Inputs.none()));
        source = replace(source, "contextFingerprint", fingerprints.effectiveContextFingerprint(source.kind(), plan));
        var destination = source.clonedTo(UUID.randomUUID());
        assertEquals(source.planId(), destination.originPlanId());
        assertSame(source.payload(), destination.payload());
        assertEquals(source.payloadDigest(), destination.payloadDigest());
        assertEquals(source.completedAt(), destination.completedAt());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new AnalysisCompatibilityPolicy().assessCopy(destination, plan).state());
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("999999.00100"));
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, new AnalysisCompatibilityPolicy().assessCopy(destination, plan).state());
        assertEquals(destination, codec.decode(codec.encode(destination)));
    }

    @Test void completeUniverseCapacityAndTieFailureFidelity() throws Exception {
        // Storage-scale fixture: actual supported elections and real 40-year baseline/detail graph.
        // Twenty actual detailed evaluations; other compact outcomes repeat those metrics to stress ties.
        // This bounded storage fixture is not a claim that all 5,184 strategies were financially evaluated.
        var plan = plan(40);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1960, 2, 1));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1962, 6, 1));
        plan.getAccountPortfolio().getAccounts().forEach(a -> a.setOpeningRmdAccountData(null));
        for (var person : List.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse())) {
            for (var source : List.copyOf(person.getIncomeSources())) if (source instanceof com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome ss) {
                person.replaceIncomeSource(ss, new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(ss.getName(),
                        ss.getOwnership(), person.getBirthDate().plusYears(67), null, ss.getFullRetirementMonthlyBenefit(),
                        67, BigDecimal.ZERO, 2026));
            }
        }
        var baseline = new IntegratedSocialSecurityStrategyEvaluator().evaluateCurrentStrategy(plan);
        var strategies = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan).strategies();
        assertEquals(5184, strategies.size());
        var entries = new ArrayList<IntegratedSocialSecurityCompleteStrategySearchEntry>();
        var details = new ArrayList<IntegratedSocialSecurityStrategyResult>();
        for (int i = 0; i < 20; i++) details.add(new IntegratedSocialSecurityStrategyEvaluator().evaluate(plan, strategies.get(i)));
        var metricOrder = new ArrayList<Integer>();
        for (int i = 0; i < strategies.size() - 1; i++) metricOrder.add(i);
        metricOrder.sort(Comparator.comparing((Integer i) -> details.get(i % 20).metrics().afterTaxEstate()).reversed());
        var ranks = new HashMap<Integer, Integer>();
        BigDecimal previous = null; int rank = 0;
        for (int position = 0; position < metricOrder.size(); position++) {
            int i = metricOrder.get(position);
            var value = details.get(i % 20).metrics().afterTaxEstate();
            if (previous == null || previous.compareTo(value) != 0) rank = position + 1;
            ranks.put(i, rank); previous = value;
        }
        for (int i = 0; i < strategies.size(); i++) {
            var s = strategies.get(i);
            if (i == strategies.size() - 1) {
                entries.add(new IntegratedSocialSecurityCompleteStrategySearchEntry(i + 1, s, OptionalInt.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(s,
                        "Synthetic retained candidate failure", IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION))));
            } else {
                var detail = i < 20 ? details.get(i) : null;
                var metrics = details.get(i % 20).metrics();
                entries.add(new IntegratedSocialSecurityCompleteStrategySearchEntry(i + 1, s, OptionalInt.of(ranks.get(i)),
                        Optional.of(metrics), Optional.of(differences(metrics, baseline.metrics())), Optional.ofNullable(detail), Optional.empty()));
            }
        }
        var result = new IntegratedSocialSecurityCompleteStrategySearchResult(baseline, IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,
                entries, metricOrder.stream().map(entries::get).toList(), OptionalInt.empty());
        var snapshot = envelope(payload(result));
        long start = System.nanoTime();
        var encoded = codec.encodeMeasured(snapshot);
        long encodedAt = System.nanoTime();
        var decoded = codec.decode(encoded.bytes());
        long decodedAt = System.nanoTime();
        var restored = ((ExhaustiveSnapshot) decoded.payload()).restore();
        long restoredAt = System.nanoTime();
        assertEquals(snapshot, decoded);
        assertEquals(20, restored.entries().stream().filter(e -> e.retainedDetail().isPresent()).count());
        assertEquals(1, restored.failedStrategyCount());
        assertEquals(5183, restored.successfulStrategyCount());
        assertEquals(40, restored.currentPlanBaseline().projection().size());
        int tokens = 0, arrays = 0, depth = 0, maxDepth = 0, maxNumber = 0;
        try (var gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(encoded.bytes()));
                var parser = new JsonFactory().createParser(gzip)) {
            for (JsonToken t; (t = parser.nextToken()) != null;) {
                tokens++;
                if (t == JsonToken.START_ARRAY) arrays++;
                if (t == JsonToken.START_ARRAY || t == JsonToken.START_OBJECT) maxDepth = Math.max(maxDepth, ++depth);
                if (t == JsonToken.END_ARRAY || t == JsonToken.END_OBJECT) depth--;
                if (t.isNumeric()) maxNumber = Math.max(maxNumber, parser.getTextLength());
            }
        }
        System.out.printf(Locale.ROOT, "EXHAUSTIVE_CAPACITY candidates=%d years=%d retained=20 raw=%d gzip=%d tokens=%d arrays=%d depth=%d maxNumber=%d encodeMs=%.3f decodeMs=%.3f reconstructMs=%.3f%n",
                strategies.size(), baseline.projection().size(), encoded.rawBytes(), encoded.bytes().length, tokens, arrays, maxDepth,
                maxNumber, (encodedAt-start)/1e6, (decodedAt-encodedAt)/1e6, (restoredAt-decodedAt)/1e6);
    }
}
