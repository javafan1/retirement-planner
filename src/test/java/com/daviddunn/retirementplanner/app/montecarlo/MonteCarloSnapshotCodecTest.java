package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.lang.management.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloSnapshotCodecTest {
    private static final Instant START = Instant.parse("2026-10-20T12:00:00Z"), END = START.plusSeconds(10);
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final UUID PLAN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final AnalysisSnapshotCodec codec = new AnalysisSnapshotCodec();
    @TempDir Path directory;

    @Test void allEightFamiliesRetainExactEvidenceAndFrozenPdfInputsAcrossSeeds() throws Exception {
        for (long seed : new long[]{417, 918}) for (boolean couple : new boolean[]{false, true})
            for (boolean mortality : new boolean[]{false, true}) for (boolean paired : new boolean[]{false, true})
                exercise(4, seed, couple, mortality, paired, null);
    }

    @Test void allFundingFailedIsACompleteSerializableResult() throws Exception {
        var plan = MonteCarloCapacityProbeTest.fixture(false);
        plan.getAccountPortfolio().getAccounts().forEach(a -> a.setCurrentBalance(BigDecimal.ZERO));
        var run = new MonteCarloRunService().run(plan, MonteCarloSettings.forPlan(plan, 3, 417, new BigDecimal("0.12")), null,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(3, run.result().fundingFailureCount());
        var payload = MonteCarloSnapshotPresentationMapper.capture(run, plan, START, END);
        var restored = MonteCarloSnapshotPresentationMapper.restoreRun(roundTrip(payload));
        assertEquals(run.result(), restored.result());
    }

    @Test void prefixReferencesAreExactBoundedAndOnlyFromBToA() {
        var a = new History(2027, null, 0, List.of(new BigDecimal("1.00"), new BigDecimal("2.000")));
        var b = new History(2027, 0, 1, List.of(new BigDecimal("3.0000")));
        assertEquals(new BigDecimal("1.00"), b.restore(a).get(2027));
        assertEquals(new BigDecimal("3.0000"), b.restore(a).get(2028));
        assertEquals(0, new History(2027, null, 0, List.of()).length(null));
        assertEquals(2, new History(2027, 0, 2, List.of()).length(a));
        assertThrows(IllegalArgumentException.class, () -> new History(2027, null, -1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new History(2027, 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new History(2027, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new History(2027, 0, Integer.MAX_VALUE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new History(2027, 0, 3, List.of()).length(a));
        assertThrows(IllegalArgumentException.class, () -> b.length(null));
        assertThrows(IllegalArgumentException.class, () -> b.length(b));
        assertThrows(IllegalArgumentException.class, () -> new History(2028, 0, 1, List.of()).length(a));
        var differentScale = History.capture(Map.of(2027, new BigDecimal("1.0")), 2027, a);
        assertNull(differentScale.source());
    }

    @Test void realEmptyAndPartialPrefixesPreserveBothSidesWithoutChangingLiveInputs() throws Exception {
        for (String balance : List.of("0", "20000")) {
            var a = MonteCarloCapacityProbeTest.fixture(false);
            var b = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(a);
            a.getAccountPortfolio().getAccounts().forEach(account -> account.setCurrentBalance(new BigDecimal(balance)));
            var pair = pair(a, b, 3);
            var snapshot = MonteCarloSnapshotPresentationMapper.capture(pair, START, END);
            assertTrue(snapshot.paired().outcomes().stream().allMatch(t -> t.a().failure() != null));
            if (balance.equals("0")) assertEquals(0, snapshot.paired().outcomes().getFirst().a().history().suffix().size());
            else assertTrue(snapshot.paired().outcomes().stream().anyMatch(t -> !t.a().history().suffix().isEmpty()));
            a.getHousehold().getPrimaryPerson().setFirstName("Edited after completion");
            var restored = MonteCarloSnapshotPresentationMapper.restoreComparison(roundTrip(snapshot));
            assertEquals(pair.result().outcomes(), restored.result().outcomes());
            assertEquals(pair.result().summary(), restored.result().summary());
        }
    }

    @Test void frozenMeanScaleIsRestoredWithoutRerunningStatisticalReduction() throws Exception {
        var a = MonteCarloCapacityProbeTest.fixture(false);
        var pair = pair(a, a, 3);
        var s = MonteCarloSnapshotPresentationMapper.capture(pair, START, END);
        var old = s.paired().summary(); var metric = old.investable();
        var exactMean = metric.mean().setScale(metric.mean().scale() + 2);
        var changed = new Metric(metric.count(), metric.percentiles(), exactMean, metric.greater(), metric.equal(), metric.less());
        var summary = new PairSummary(old.states(), old.failuresA(), old.failuresB(), changed, old.netWorth(), old.estate(), old.taxes(), old.annual());
        var altered = withPaired(s, s.settings(), s.request(), new Paired(s.paired().outcomes(), summary));
        assertEquals(exactMean, MonteCarloSnapshotMapping.restoreComparison(roundTrip(altered)).summary().terminalInvestableAssetsDifference().meanDifference().orElseThrow());
        assertEquals(s.paired().outcomes(), altered.paired().outcomes());
    }

    @Test void excessiveReferenceExpansionIsRejectedBeforeDomainMapAllocation() {
        var a = MonteCarloCapacityProbeTest.fixture(false);
        var s = MonteCarloSnapshotPresentationMapper.capture(pair(a, a, 3), START, END);
        var historyA = new History(2027, null, 0, Collections.nCopies(1000, BigDecimal.ZERO));
        var historyB = new History(2027, 0, 1000, List.of());
        var terminal = new MonteCarloMortalityAnalysisResult.TerminalOutcome(LocalDate.of(3026, 12, 31), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var trials = java.util.stream.IntStream.range(0, 751).mapToObj(i -> new PairTrial(i, s.request().fixedLife(),
                new Outcome(historyA, terminal, null), new Outcome(historyB, terminal, null))).toList();
        var request = s.request();
        var expanded = withPaired(s, new Settings(751, s.settings().seed(), s.settings().expectedReturn(), s.settings().volatility(), s.settings().inflation()),
                new Request(request.planA(), request.planB(), request.labelA(), request.labelB(), request.start(), 3026, request.fixedLife(), null),
                new Paired(trials, s.paired().summary()));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> expanded.validate(expanded.kind())).getMessage().contains("observation budget"));
    }

    @Test void decodedBadReferenceAndCountsCannotReachRestoration() throws Exception {
        var a = MonteCarloCapacityProbeTest.fixture(false);
        var s = MonteCarloSnapshotPresentationMapper.capture(pair(a, a, 3), START, END);
        var raw = codec.raw(envelope(s));
        for (String field : List.of("source", "sharedLength")) {
            var node = JSON.readTree(raw);
            var history = (com.fasterxml.jackson.databind.node.ObjectNode) node.at("/payload/paired/outcomes/0/a/history");
            history.put("source", 0); history.put("sharedLength", field.equals("source") ? 1 : Integer.MAX_VALUE);
            var bytes = gzip(JSON.writeValueAsBytes(node));
            assertThrows(IOException.class, () -> codec.decode(bytes));
        }
        var node = JSON.readTree(raw);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.at("/payload/settings")).put("trials", 4);
        var bytes = gzip(JSON.writeValueAsBytes(node));
        assertThrows(IOException.class, () -> codec.decode(bytes));
    }

    @Test void fractionalReferenceVersionIndexSeedAndCountTokensCannotBeCoerced() throws Exception {
        var a = MonteCarloCapacityProbeTest.fixture(false);
        var s = MonteCarloSnapshotPresentationMapper.capture(pair(a, a, 3), START, END);
        var raw = codec.raw(envelope(s));
        var mutations = Map.of("/payload/paired/outcomes/0/b/history/source", 0.5,
                "/payload/paired/outcomes/0/b/history/sharedLength", 50.5,
                "/payload/paired/outcomes/0/index", 0.5, "/payload/version", 1.5,
                "/schemaVersion", 1.5, "/payload/settings/trials", 3.5, "/payload/settings/seed", 417.5);
        for (var entry : mutations.entrySet()) {
            var node = JSON.readTree(raw); int slash = entry.getKey().lastIndexOf('/');
            ((com.fasterxml.jackson.databind.node.ObjectNode) node.at(entry.getKey().substring(0, slash)))
                    .put(entry.getKey().substring(slash + 1), entry.getValue());
            var malformed = gzip(JSON.writeValueAsBytes(node));
            assertThrows(IOException.class, () -> codec.decode(malformed), entry.getKey());
        }
    }

    @Test void equalLookingAccountsRemainIndependentInFrozenPlans() throws Exception {
        var plan = MonteCarloCapacityProbeTest.fixture(false);
        for (int i = 0; i < 2; i++) plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Same display name", com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, new BigDecimal("123.4500")));
        var run = new MonteCarloRunService().run(plan, MonteCarloSettings.forPlan(plan, 2, 417, new BigDecimal("0.12")), null,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var decoded = roundTrip(MonteCarloSnapshotPresentationMapper.capture(run, plan, START, END));
        var accounts = MonteCarloPlanDocument.read(decoded.request().planA()).getAccountPortfolio().getAccounts();
        assertNotSame(accounts.get(3), accounts.get(4));
        assertEquals(new BigDecimal("123.4500"), accounts.get(3).getCurrentBalance());
        assertEquals(new BigDecimal("123.4500"), accounts.get(4).getCurrentBalance());
    }

    @Test void suppliedWorldIdentityIsRequiredAndOpeningDeathRestoresWithoutWorldCallbacks() throws Exception {
        var plan = MonteCarloCapacityProbeTest.fixture(false);
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, new BigDecimal("0.12"));
        var request = MonteCarloMortalityRequest.individual(plan, settings, SocialSecurityMortalityAdjustment.standard());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request, index -> {
            calls.incrementAndGet();
            return new MonteCarloWorld(index, com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath.constant(BigDecimal.ZERO),
                    MonteCarloLifetime.individual(Year.of(2027)), Optional.empty());
        }, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var people = com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold());
        var run = new MonteCarloRun(new MonteCarloRun.Mortality(result), MonteCarloFanModel.from(result, people), people,
                2027, 2026, false, 1000, "Original supplied opening-death world");
        assertThrows(IllegalArgumentException.class, () -> MonteCarloSnapshotPresentationMapper.capture(run, plan, START, END));
        var snapshot = MonteCarloSnapshotPresentationMapper.capture(run, plan, START, END, "a".repeat(64));
        var restored = MonteCarloSnapshotPresentationMapper.restoreRun(roundTrip(snapshot));
        assertEquals(result.outcomes(), restored.mortalityResult().outcomes());
        assertEquals(1, calls.get());
        assertTrue(restored.mortalityResult().annualResults().isEmpty());
        assertEquals("CALLER_SUPPLIED_WORLDS", snapshot.provenance().returns());
        assertEquals("a".repeat(64), snapshot.provenance().suppliedWorldFingerprint());
    }

    @Test void absentSharedSurvivorElectionIsNotReplacedByTheCandidateElection() throws Exception {
        var plan = MonteCarloCapacityProbeTest.fixture(true);
        var sharedPlan = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        var p = sharedPlan.getPlanningAssumptions(); var d = p.getDeathScenarioAssumptions();
        sharedPlan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(p.getEconomicAssumptions(), p.getTaxAssumptions(),
                p.getWithdrawalAssumptions(), new com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions(d.getDeathScenario(), d.getDeathYear(), null, d.getPostDeathExpenseFactor()),
                p.getProjectionLengthYears(), p.getProjectionStartDate(), p.getStateTaxAssumptions()));
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, new BigDecimal("0.12"));
        var shared = new MonteCarloMortalityRequest(sharedPlan, settings, LongevitySessionSettings.defaults(p.getProjectionStartDate()));
        assertTrue(shared.survivorClaimingAge().isEmpty());
        var request = new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("A", plan), new MonteCarloStrategyCandidate("B", plan),
                new MonteCarloStrategyComparisonRequest.Longevity(shared));
        var original = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request);
        var snapshot = MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(original, MonteCarloMode.LONGEVITY_ADJUSTED,
                "No shared survivor election; independent candidate elections remain configured", 1000), START, END);
        var restored = MonteCarloSnapshotMapping.restoreComparison(roundTrip(snapshot));
        assertTrue(((MonteCarloStrategyComparisonRequest.Longevity) restored.request().assumptions()).mortality().survivorClaimingAge().isEmpty());
        assertEquals(original.outcomes(), restored.outcomes()); assertEquals(original.summary(), restored.summary());
        assertEquals(67, restored.request().strategyA().copyForRun().getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
    }

    @Test void snapshotLimitsAndKindGuardsRemainExplicit() {
        assertEquals(33554432, AnalysisSnapshotCodec.MAX_RAW_BYTES);
        assertEquals(16777216, AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES);
        assertEquals(2000000, AnalysisSnapshotCodec.MAX_TOKENS);
        assertEquals(4096, AnalysisSnapshotCodec.MAX_NUMBER_LENGTH);
        assertEquals(80, AnalysisSnapshotCodec.MAX_DEPTH);
        assertEquals(100000, AnalysisSnapshotCodec.MAX_ARRAYS);
        assertEquals(256000, AnalysisSnapshotCodec.MAX_STRING_LENGTH);
        var s = specimen();
        assertThrows(IllegalArgumentException.class, () -> s.validate(AnalysisKind.COUPLE_WEIGHTED));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisFingerprintService().fingerprint(s.kind(),
                MonteCarloCapacityProbeTest.fixture(false), AnalysisFingerprintService.Inputs.none()));
    }

    @Test void shortExponentTokensCannotCreateUnboundedFormattedAmounts() {
        assertEquals(new BigDecimal("1E+100"), MonteCarloSnapshotNumbers.check(new BigDecimal("1E+100")));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloSnapshotNumbers.check(new BigDecimal("1E+2147483647")));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloSnapshotNumbers.check(new BigDecimal("1E-2147483647")));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloPlanDocument.read(
                MonteCarloPlanDocument.capture(MonteCarloCapacityProbeTest.fixture(false)).replace("700000", "1E+2147483647")));
    }

    @Test void oversizedEncodedContentStopsAtTheExistingRawLimit() {
        var s = specimen(); var d = s.display(); var c = d.chart();
        var claims = Collections.nCopies(1000, new Claim(2027, "x".repeat(40000), 67, LocalDate.of(2027, 1, 1)));
        var display = new Display(d.people(), d.primaryName(), d.spouseName(), d.years(), new Chart(c.years(), claims, c.roth(), c.rmd()),
                d.firstYear(), d.lastYear(), d.referenceIncomplete(), d.elapsedNanos(), d.details());
        var oversized = new MonteCarloSnapshot(s.version(), s.mode(), s.hasSpouse(), s.settings(), s.request(), s.provenance(),
                s.fixed(), null, null, display, START, END);
        var failure = assertThrows(IllegalArgumentException.class, () -> envelope(oversized));
        Throwable cause = failure;
        while (cause != null && !(cause instanceof AnalysisSnapshotSaveException)) cause = cause.getCause();
        assertInstanceOf(AnalysisSnapshotSaveException.class, cause);
        assertEquals(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT, ((AnalysisSnapshotSaveException) cause).stage());
    }

    @Test void strictEnvelopeVersionsDigestsAndTrailingDocumentsAreRejected() throws Exception {
        var payload = specimen();
        var envelope = envelope(payload);
        byte[] raw = codec.raw(envelope);
        assertThrows(IOException.class, () -> codec.decode(gzip((new String(raw, StandardCharsets.UTF_8) + " {}").getBytes(StandardCharsets.UTF_8))));
        var node = JSON.readTree(raw);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("payload")).put("version", 200);
        var badVersion = gzip(JSON.writeValueAsBytes(node));
        assertThrows(IOException.class, () -> codec.decode(badVersion));
        node = JSON.readTree(raw);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("payload").get("display")).put("details", "tampered context");
        var badDigest = gzip(JSON.writeValueAsBytes(node));
        assertThrows(IOException.class, () -> codec.decode(badDigest));
        node = JSON.readTree(raw);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("payload")).put("unexpected", true);
        var unknownField = gzip(JSON.writeValueAsBytes(node));
        assertThrows(IOException.class, () -> codec.decode(unknownField));
        var bytes = codec.encode(envelope);
        assertThrows(IOException.class, () -> codec.decode(Arrays.copyOf(bytes, bytes.length / 2)));
    }

    @Test void repositoryRoundTripAndCloneRetainIndependentIdentity() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(directory);
        var snapshot = envelope(specimen());
        repository.save(snapshot);
        var restored = repository.load(PLAN, snapshot.kind()).orElseThrow();
        assertArrayEquals(codec.raw(snapshot), codec.raw(restored));
        var clone = snapshot.clonedTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertTrue(repository.saveIfAbsent(clone));
        assertFalse(repository.saveIfAbsent(clone));
        assertEquals(PLAN, repository.load(clone.planId(), clone.kind()).orElseThrow().originPlanId());
        assertEquals(snapshot, repository.load(PLAN, snapshot.kind()).orElseThrow());
    }

    @Test void longDecimalScaleAndSharedEqualHistoriesSurviveTheProductionEnvelope() throws Exception {
        var base = specimen();
        var trial = base.fixed().outcomes().getFirst();
        var m = trial.metrics(); var longValue = new BigDecimal("0." + "1".repeat(1070));
        var changed = new com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics(longValue, m.totalIncome(), m.totalTaxes(), m.peakAnnualTax(),
                m.endingInvestableAssets(), m.endingNetWorth(), m.afterTaxEstate(), m.lifetimePortfolioWithdrawals(), m.lifetimeRothConversions(),
                m.lifetimeRequiredMinimumDistributions(), m.lifetimeMedicarePremiums(), m.lifetimeHouseholdSocialSecurity(), m.lifetimePrimarySocialSecurity(), m.lifetimeSpouseSocialSecurity());
        var fixed = base.fixed();
        var trials = new ArrayList<>(fixed.outcomes()); trials.set(0, new FixedTrial(0, changed, trial.nonpositiveYear(), trial.failure()));
        var snapshot = new MonteCarloSnapshot(base.version(), base.mode(), base.hasSpouse(), base.settings(), base.request(), base.provenance(),
                new Fixed(trials, fixed.annual(), fixed.terminal(), fixed.reference(), fixed.referenceMetrics(), fixed.referenceFailure()), null, null, base.display(), START, END);
        assertEquals(longValue, MonteCarloSnapshotMapping.restoreFixed(roundTrip(snapshot)).outcomes().getFirst().metrics().orElseThrow().totalInvestmentGrowth());
    }

    @Test
    @EnabledIfSystemProperty(named = "mc.codec.capacity", matches = "true")
    void realProductionCapacityMatrixAndUiMaximum() throws Exception {
        Path output = Path.of("target/mc-production-codec-capacity.tsv");
        Files.writeString(output, "shape\tmortality\tpaired\ttrials\trawBytes\tgzipBytes\ttokens\tmaxNumber\tencodeMs\tdecodeMs\trebuildAndCompareMs\theapUsed\tsummedPoolPeaks\n");
        for (boolean couple : new boolean[]{false, true}) for (boolean mortality : new boolean[]{false, true})
            for (boolean paired : new boolean[]{false, true}) exercise(5000, 417, couple, mortality, paired, output);
        exercise(10000, 417, true, false, true, output);
    }

    @Test
    @EnabledIfSystemProperty(named = "mc.codec.replay", matches = "true")
    void replayAcceptedCapacitySnapshotsWithoutFinancialExecution() throws Exception {
        var output = Path.of("target/mc-production-codec-replay.tsv");
        Files.writeString(output, "file\trawBytes\tgzipBytes\tencodeMs\tdecodeMs\trebuildMs\n");
        var paths = new ArrayList<Path>();
        for (boolean couple : new boolean[]{false, true}) for (boolean mortality : new boolean[]{false, true}) for (boolean paired : new boolean[]{false, true})
            paths.add(Path.of("target/mc-codec-capacity-" + couple + "-" + mortality + "-" + paired + "-5000.json.gz"));
        paths.add(Path.of("target/mc-codec-capacity-true-false-true-10000.json.gz"));
        for (var path : paths) {
            assertTrue(Files.exists(path), "Run the independently calculated capacity proof first.");
            var bytes = Files.readAllBytes(path);
            var accepted = new AnalysisSnapshotCodec().decode(bytes);
            long start = System.nanoTime(); var encoded = codec.encodeMeasured(accepted); long encode = ms(start);
            start = System.nanoTime(); var restored = new AnalysisSnapshotCodec().decode(encoded.bytes()); long decode = ms(start);
            assertEquals(accepted, restored); assertArrayEquals(bytes, encoded.bytes());
            var payload = (MonteCarloSnapshot) restored.payload(); start = System.nanoTime();
            if (payload.mode() == Mode.FIXED || payload.mode() == Mode.LONGEVITY) MonteCarloSnapshotPresentationMapper.restoreRun(payload);
            else MonteCarloSnapshotPresentationMapper.restoreComparison(payload);
            long rebuild = ms(start);
            Files.writeString(output, path.getFileName() + "\t" + encoded.rawBytes() + "\t" + encoded.bytes().length + "\t" + encode + "\t" + decode + "\t" + rebuild + "\n", StandardOpenOption.APPEND);
        }
    }

    private MonteCarloSnapshot specimen() {
        var p = MonteCarloCapacityProbeTest.fixture(false);
        var run = new MonteCarloRunService().run(p, MonteCarloSettings.forPlan(p, 4, 417, new BigDecimal("0.12")), null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        return MonteCarloSnapshotPresentationMapper.capture(run, p, START, END);
    }
    private static MonteCarloStrategyComparisonRun pair(RetirementPlan a, RetirementPlan b, int count) {
        var settings = MonteCarloSettings.forPlan(a, count, 417, new BigDecimal("0.12"));
        var assumptions = new MonteCarloStrategyComparisonRequest.Fixed(settings, a.getPlanningAssumptions().getProjectionStartDate(), 2076,
                new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty())));
        var r = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(new MonteCarloStrategyComparisonRequest(
                new MonteCarloStrategyCandidate("A", a), new MonteCarloStrategyCandidate("B", b), assumptions));
        return new MonteCarloStrategyComparisonRun(r, MonteCarloMode.FIXED_LIFESPAN, "Frozen paired test assumptions", 1000);
    }
    private static MonteCarloSnapshot withPaired(MonteCarloSnapshot s, Settings settings, Request request, Paired paired) {
        return new MonteCarloSnapshot(s.version(), s.mode(), s.hasSpouse(), settings, request, s.provenance(), null, null, paired, s.display(), s.startedAt(), s.completedAt());
    }
    private AnalysisSnapshot envelope(MonteCarloSnapshot s) {
        var context = new SnapshotContext(s.hasSpouse() ? "Couple" : "Single", "Synthetic primary", s.hasSpouse() ? "Synthetic spouse" : null,
                START, List.of(), List.of(), List.of(), Map.of());
        return new AnalysisSnapshot(1, 1, PLAN, s.kind(), s.kind().shape(), UUID.fromString("00000000-0000-0000-0000-000000000003"), END,
                "codec-test", Map.of("analysis-kind", s.kind().name() + "-v1"), Map.of("fixture", "v1"), "1".repeat(64), "2".repeat(64), codec.digest(s, context), s, context, null);
    }
    private MonteCarloSnapshot roundTrip(MonteCarloSnapshot s) throws Exception {
        var original = envelope(s); var bytes = codec.encode(original); var decoded = codec.decode(bytes);
        assertEquals(s, decoded.payload());
        assertArrayEquals(codec.raw(original), codec.raw(decoded));
        assertArrayEquals(bytes, codec.encode(decoded));
        return (MonteCarloSnapshot) decoded.payload();
    }

    private void exercise(int count, long seed, boolean couple, boolean mortality, boolean paired, Path output) throws Exception {
        System.gc(); ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
        var plan = MonteCarloCapacityProbeTest.fixture(couple);
        var settings = MonteCarloSettings.forPlan(plan, count, seed, new BigDecimal("0.12"))
                .withInflation(new MonteCarloInflationSettings(new BigDecimal("0.02"), new BigDecimal("0.0175"), new BigDecimal("-0.02")));
        var request = couple ? new MonteCarloMortalityRequest(plan, settings, LongevitySessionSettings.defaults(plan.getPlanningAssumptions().getProjectionStartDate()), 67)
                : MonteCarloMortalityRequest.individual(plan, settings, SocialSecurityMortalityAdjustment.standard());
        MonteCarloSnapshot snapshot;
        MonteCarloRun originalRun = null;
        MonteCarloStrategyComparisonRun originalPair = null;
        if (paired) {
            var b = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
            b.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2027, new BigDecimal("30000.02"),
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT, com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
            MonteCarloStrategyComparisonRequest.Assumptions assumptions = mortality ? new MonteCarloStrategyComparisonRequest.Longevity(request)
                    : new MonteCarloStrategyComparisonRequest.Fixed(settings, plan.getPlanningAssumptions().getProjectionStartDate(), 2076,
                    couple ? MonteCarloLifetime.couple(com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario.bothSurvive())
                            : new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty())));
            var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(new MonteCarloStrategyComparisonRequest(
                    new MonteCarloStrategyCandidate("Current Plan", plan), new MonteCarloStrategyCandidate("Alternative Roth", b), assumptions));
            originalPair = new MonteCarloStrategyComparisonRun(result, mortality ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN,
                    "Frozen test comparison context: different Roth settings; shared stochastic worlds.", 1000);
            snapshot = MonteCarloSnapshotPresentationMapper.capture(originalPair, START, END);
        } else {
            originalRun = mortality ? new MonteCarloRunService().runMortality(plan, request, AnalysisProgressListener.none(), AnalysisCancellationToken.none())
                    : new MonteCarloRunService().run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
            snapshot = MonteCarloSnapshotPresentationMapper.capture(originalRun, plan, START, END);
        }
        var envelope = envelope(snapshot);
        long start = System.nanoTime(); var encoded = codec.encodeMeasured(envelope); long encodeMs = ms(start);
        start = System.nanoTime(); var decoded = (MonteCarloSnapshot) codec.decode(encoded.bytes()).payload(); long decodeMs = ms(start);
        assertEquals(snapshot, decoded); assertEquals(snapshot.settings(), decoded.settings());
        start = System.nanoTime();
        if (paired) {
            var restored = MonteCarloSnapshotPresentationMapper.restoreComparison(decoded);
            assertEquals(originalPair.result().outcomes(), restored.result().outcomes());
            assertEquals(originalPair.result().summary(), restored.result().summary());
            assertEquals(originalPair.result().request().assumptions().settings(), restored.result().request().assumptions().settings());
            assertEquals(MonteCarloPlanDocument.capture(originalPair.result().request().strategyA().copyForRun()),
                    MonteCarloPlanDocument.capture(restored.result().request().strategyA().copyForRun()));
            assertEquals(MonteCarloPlanDocument.capture(originalPair.result().request().strategyB().copyForRun()),
                    MonteCarloPlanDocument.capture(restored.result().request().strategyB().copyForRun()));
            if (output == null) comparePdf(MonteCarloPdfReportAdapter.from(originalPair), MonteCarloPdfReportAdapter.from(restored));
        } else {
            var restored = MonteCarloSnapshotPresentationMapper.restoreRun(decoded);
            if (!mortality) assertEquals(originalRun.result(), restored.result());
            else {
                assertEquals(originalRun.mortalityResult().outcomes(), restored.mortalityResult().outcomes());
                assertEquals(originalRun.mortalityResult().annualResults(), restored.mortalityResult().annualResults());
                assertEquals(originalRun.mortalityResult().fundingFailureStatistics(), restored.mortalityResult().fundingFailureStatistics());
                assertEquals(originalRun.mortalityResult().request().primary(), restored.mortalityResult().request().primary());
                assertEquals(originalRun.mortalityResult().request().mortalityTable().entries(), restored.mortalityResult().request().mortalityTable().entries());
            }
            assertEquals(originalRun.fan(), restored.fan()); assertEquals(originalRun.people(), restored.people());
            if (output == null) comparePdf(MonteCarloPdfReportAdapter.from(originalRun), MonteCarloPdfReportAdapter.from(restored));
        }
        long rebuildMs = ms(start);
        if (output == null) { assertArrayEquals(codec.encode(envelope), codec.encode(envelope(decoded))); return; }
        long tokens = 0; int max = 0;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(encoded.bytes())); var parser = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(4096).build()).build().createParser(gzip)) {
            while (parser.nextToken() != null) { tokens++; if (parser.currentToken().isNumeric()) max = Math.max(max, parser.getTextLength()); }
        }
        assertTrue(tokens <= AnalysisSnapshotCodec.MAX_TOKENS);
        long peak = ManagementFactory.getMemoryPoolMXBeans().stream().filter(p -> p.getType() == MemoryType.HEAP).mapToLong(p -> p.getPeakUsage().getUsed()).sum();
        String row = couple + "\t" + mortality + "\t" + paired + "\t" + count + "\t" + encoded.rawBytes() + "\t" + encoded.bytes().length
                + "\t" + tokens + "\t" + max + "\t" + encodeMs + "\t" + decodeMs + "\t" + rebuildMs
                + "\t" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() + "\t" + peak;
        Files.writeString(output, row + "\n", StandardOpenOption.APPEND); System.out.println("MC_CODEC_CAPACITY " + row);
        // Synthetic, ignored evidence for later codec-only replay without recalculating trials.
        Files.write(Path.of("target/mc-codec-capacity-" + couple + "-" + mortality + "-" + paired + "-" + count + ".json.gz"), encoded.bytes());
    }
    private static void comparePdf(com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport a,
            com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport b) {
        assertEquals(a.context(), b.context()); assertEquals(a.chart(), b.chart()); assertEquals(a.sections(), b.sections());
    }
    private static byte[] gzip(byte[] raw) throws IOException {
        var bytes = new ByteArrayOutputStream(); try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(raw); } return bytes.toByteArray();
    }
    private static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
