package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitMonteCarloExecutionTest {
    static final Instant START = Instant.parse("2026-11-06T17:00:00Z");
    static Stream<Arguments> standaloneMatrix() {
        return Stream.of(true, false).flatMap(c -> Stream.of(true, false).flatMap(l ->
                Stream.of(true, false).map(e -> Arguments.of(c, l, e))));
    }
    static Stream<Arguments> pairedMatrix() {
        return Stream.of(true, false).flatMap(c -> Stream.of(true, false).flatMap(l -> Stream.of(true, false).flatMap(e ->
                Stream.of("CURRENT", "BASELINE", "IDENTICAL", "DIFFERENT").map(b -> Arguments.of(c, l, e, b)))));
    }
    static ConditionalBenefitDefinition scheduled(RetirementPlan p, String amount) {
        var definition = BenefitFixtures.change(BenefitFixtures.monthly(), "trigger",
                new Trigger(TriggerType.FIXED_DATE, p.getPlanningAssumptions().getProjectionStartDate(), null, null));
        return BenefitFixtures.change(BenefitFixtures.change(definition, "grossAmount", new BigDecimal(amount)), "annualEscalationRate", BigDecimal.ZERO);
    }
    static List<ConditionalBenefitDefinition> benefits(RetirementPlan p, String amount) {
        var result = new ArrayList<ConditionalBenefitDefinition>(); result.add(scheduled(p, amount));
        if (p.getHousehold().hasSpouse()) {
            var survivor = BenefitFixtures.change(scheduled(p, "2500"), "id", new UUID(0, 2));
            survivor = BenefitFixtures.change(survivor, "recipient", PersonReference.SPOUSE);
            survivor = BenefitFixtures.change(survivor, "trigger", new Trigger(TriggerType.DEATH, null, PersonReference.PRIMARY, null));
            survivor = BenefitFixtures.change(survivor, "label", "Survivor monthly");
            result.add(survivor);
            var lump = BenefitFixtures.change(survivor, "id", new UUID(0, 3));
            lump = BenefitFixtures.change(lump, "grossAmount", new BigDecimal("400000"));
            lump = BenefitFixtures.change(lump, "paymentPattern", PaymentPattern.ONE_TIME_LUMP_SUM);
            lump = BenefitFixtures.change(lump, "label", "Covered first-death lump");
            lump = BenefitFixtures.change(lump, "coverageStart", LocalDate.of(2027, 1, 1));
            result.add(BenefitFixtures.change(lump, "coverageEnd", LocalDate.of(2070, 1, 1)));
        }
        return List.copyOf(result);
    }

    @ParameterizedTest @MethodSource("standaloneMatrix")
    void generatedStandaloneMatchesDirectFinancialWorldsAndRestores(boolean couple, boolean longevity, boolean expenses) throws Exception {
        var p = plan(couple);
        if (expenses) StandaloneExpenseCapabilityTest.enable(p, ".25");
        p.setConditionalBenefits(benefits(p, "1200"));
        String original = MonteCarloPlanDocument.capture(p);
        var settings = MonteCarloSettings.forPlan(p, 3, 417, new BigDecimal(".12"));
        var service = new MonteCarloRunService();
        var r = longevity ? service.runMortality(p, mortality(p, settings), PROGRESS, NEVER)
                : service.run(p, settings, null, PROGRESS, NEVER);
        var again = longevity ? service.runMortality(p, mortality(p, settings), PROGRESS, NEVER)
                : service.run(p, settings, null, PROGRESS, NEVER);
        if (longevity) {
            assertEquals(r.mortalityResult().outcomes(), again.mortalityResult().outcomes());
            assertEquals(r.mortalityResult().annualResults(), again.mortalityResult().annualResults());
            assertEquals(r.mortalityResult().benefitExecution(), again.mortalityResult().benefitExecution());
        } else assertEquals(r.result(), again.result());
        int first = p.getPlanningAssumptions().getProjectionStartDate().getYear();
        for (int i = 0; i < 3; i++) {
            var world = longevity ? new MonteCarloWorldGenerator(mortality(p, settings)).generate(i) : null;
            int last = longevity ? world.lifetime().terminalDeathYear() - 1 : first + p.getPlanningAssumptions().getProjectionLengthYears() - 1;
            if (last < first) continue;
            var economic = longevity ? world.economicPath() : new MonteCarloScenarioGenerator().generate(first, last, settings, i);
            var ctx = longevity ? ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(last)
                    : ProjectionEvaluationContext.empty();
            if (longevity && couple) ctx = ctx.withSurvivorClaimingAge(mortality(p, settings).survivorClaimingAge().orElseThrow());
            var inflation = longevity ? world.inflationPath() : new MonteCarloInflationGenerator().generate(first, last, settings, i);
            if (inflation.isPresent()) ctx = ctx.withInflationPath(inflation.orElseThrow());
            if (expenses) ctx = InternalExpenseRun.configured(p).apply(p, ctx, settings.seed(), i, first, last);
            var actual = new ProjectionEngine().projectWithOutcome(p, ctx, economic);
            var annual = new TreeMap<Integer, BigDecimal>();
            actual.completedYears().forEach(y -> annual.put(y.getCalendarYear(), y.getEndingInvestableAssets()));
            if (longevity) assertEquals(annual, r.mortalityResult().outcomes().get(i).annualInvestableAssets());
            else if (actual instanceof ProjectionExecutionResult.Completed completed)
                assertEquals(new ProjectionMetricsCalculator().calculate(p, completed.projection()), r.result().outcomes().get(i).metrics().orElseThrow());
            assertReceipts(actual.completedYears(), p, ctx);
        }
        var payload = MonteCarloSnapshotPresentationMapper.capture(r, p, START, START.plusSeconds(1));
        assertEquals(4, payload.version()); assertEquals(5, payload.fingerprintVersion());
        var codec = new AnalysisSnapshotCodec(); var saved = StandaloneExpenseCapabilityTest.envelope(payload);
        var restored = (MonteCarloSnapshot) codec.decode(codec.encode(saved)).payload();
        assertEquals(payload, restored);
        var rebuilt = MonteCarloSnapshotPresentationMapper.restoreRun(restored);
        if (longevity) {
            assertEquals(r.mortalityResult().outcomes(), rebuilt.mortalityResult().outcomes());
            assertEquals(r.mortalityResult().annualResults(), rebuilt.mortalityResult().annualResults());
            assertEquals(r.mortalityResult().benefitExecution(), rebuilt.mortalityResult().benefitExecution());
        } else assertEquals(r.result(), rebuilt.result());
        assertTrue(rebuilt.planDetails().contains("fully taxable ordinary non-pension"));
        p.setConditionalBenefits(List.of(scheduled(p, "99999")));
        assertEquals(payload, restored); assertEquals(original, payload.request().planA());
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, new MonteCarloFingerprintService().compatibility(saved,
                MonteCarloFingerprintService.standalone(p, settings, longevity ? mortality(p, settings) : null)).state());
    }

    @ParameterizedTest @MethodSource("pairedMatrix")
    void pairedCandidatesKeepTheirOwnBenefitsAndSharedWorlds(boolean couple, boolean longevity, boolean expenses, String configuration) throws Exception {
        var a = plan(couple); var b = plan(couple);
        if (expenses) { StandaloneExpenseCapabilityTest.enable(a, ".25"); StandaloneExpenseCapabilityTest.enable(b, ".25"); }
        if (!configuration.equals("BASELINE")) a.setConditionalBenefits(benefits(a, "1200"));
        if (!configuration.equals("CURRENT")) b.setConditionalBenefits(benefits(b, configuration.equals("DIFFERENT") ? "800" : "1200"));
        var settings = MonteCarloSettings.forPlan(a, 2, 417, new BigDecimal(".12"));
        int last = a.getPlanningAssumptions().getProjectionStartDate().getYear() + a.getPlanningAssumptions().getProjectionLengthYears() - 1;
        var request = comparison(a, b, settings, longevity, last);
        var analyzer = new MonteCarloStrategyComparisonAnalyzer();
        var actual = analyzer.analyzeComparison(request);
        assertEquals(actual, analyzer.analyzeComparison(request));
        assertEquals(!configuration.equals("BASELINE"), actual.benefitExecution().candidates().getFirst().enabled());
        assertEquals(!configuration.equals("CURRENT"), actual.benefitExecution().candidates().get(1).enabled());
        for (var pair : actual.outcomes()) {
            if (configuration.equals("IDENTICAL")) assertEquals(pair.outcomeA(), pair.outcomeB());
            var world = request.worldSource().apply(pair.scenarioIndex());
            assertEquals(world.lifetime(), pair.lifetime());
            for (var side : List.of(a, b)) {
                int first = request.assumptions().start().getYear(); int end = longevity ? world.lifetime().terminalDeathYear() - 1 : last;
                if (end < first) continue;
                var ctx = ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(end);
                if (couple && longevity) ctx = ctx.withSurvivorClaimingAge(side.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
                if (world.inflationPath().isPresent()) ctx = ctx.withInflationPath(world.inflationPath().orElseThrow());
                if (expenses) ctx = InternalExpenseRun.configured(side).apply(side, ctx, settings.seed(), pair.scenarioIndex(), first, end);
                var direct = new ProjectionEngine().projectWithOutcome(side, ctx, world.economicPath());
                var annual = new TreeMap<Integer, BigDecimal>(); direct.completedYears().forEach(y -> annual.put(y.getCalendarYear(), y.getEndingInvestableAssets()));
                assertEquals(annual, (side == a ? pair.outcomeA() : pair.outcomeB()).annualInvestableAssets());
                assertReceipts(direct.completedYears(), side, ctx);
            }
        }
        if (configuration.equals("IDENTICAL"))
            assertEquals(0, actual.summary().terminalAfterTaxEstateDifference().meanDifference().orElseThrow().signum());
        var run = new MonteCarloStrategyComparisonRun(actual, longevity ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN, "Original comparison", 0);
        var payload = MonteCarloSnapshotPresentationMapper.capture(run, START, START.plusSeconds(1));
        assertEquals(5, payload.version()); assertEquals(6, payload.fingerprintVersion());
        var codec = new AnalysisSnapshotCodec(); var saved = StandaloneExpenseCapabilityTest.envelope(payload);
        var restored = (MonteCarloSnapshot) codec.decode(codec.encode(saved)).payload();
        var rebuilt = MonteCarloSnapshotMapping.restoreComparison(restored);
        assertEquals(actual.outcomes(), rebuilt.outcomes()); assertEquals(actual.summary(), rebuilt.summary());
        assertEquals(actual.benefitExecution(), rebuilt.benefitExecution());
        assertTrue(run.details().contains("Current:")); assertTrue(run.details().contains("Baseline:"));
    }

    static void assertReceipts(List<ProjectionYear> years, RetirementPlan plan, ProjectionEvaluationContext context) {
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), plan.getPlanningAssumptions().getDeathScenarioAssumptions(), context.householdLifetimeScenario());
        var lumps = new HashSet<UUID>();
        for (var row : years) {
            var receipts = row.getIncomeSources().stream().flatMap(s -> s.conditionalBenefit().stream()).toList();
            var expected = new ConditionalBenefitEvaluator().evaluate(plan.getConditionalBenefits(), BenefitEvaluationContext.capture(
                    plan.getHousehold(), deaths, plan.getPlanningAssumptions().getProjectionStartDate(), row.getCalendarYear()));
            assertEquals(expected.receipts(), receipts);
            for (var receipt : receipts) {
                assertEquals(receipt.gross(), receipt.taxable());
                if (receipt.pattern() == PaymentPattern.ONE_TIME_LUMP_SUM) assertTrue(lumps.add(receipt.benefitId()));
            }
        }
    }

    @Test void configuredBenefitsDoNotChangeWorldsAndSuppliedWorldsRemainRejected() {
        var p = plan(true); var settings = MonteCarloSettings.forPlan(p, 3, 417, new BigDecimal(".12"));
        var before = new MonteCarloWorldGenerator(mortality(p, settings));
        p.setConditionalBenefits(benefits(p, "1200"));
        var after = new MonteCarloWorldGenerator(mortality(p, settings));
        for (int i = 0; i < 3; i++) {
            assertEquals(before.generate(i).lifetime(), after.generate(i).lifetime());
            assertEquals(before.generate(i).economicPath().annualReturns(), after.generate(i).economicPath().annualReturns());
        }
        assertThrows(UnsupportedOperationException.class, () -> new MonteCarloAnalyzer().analyzeMortality(p, mortality(p, settings),
                i -> { fail("Supplied world must not be invoked"); return null; }, PROGRESS, NEVER));
        var request = comparison(p, p, settings, false, 2029);
        assertThrows(UnsupportedOperationException.class, () -> new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request,
                i -> { fail("Supplied world must not be invoked"); return null; }, PROGRESS, NEVER));
    }

    @Test void anInjectedEngineCannotPublishAResultThatIgnoresConfiguredBenefits() {
        var p=plan(true);p.setConditionalBenefits(benefits(p,"1200"));
        var settings=MonteCarloSettings.forPlan(p,1,417,BigDecimal.ZERO);
        var ignored=new ProjectionEngine(){
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan,ProjectionEvaluationContext context,ProjectionEconomicPath path){
                var copy=new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
                copy.setConditionalBenefits(List.of());return super.projectWithOutcome(copy,context,path);
            }
        };
        var failed=assertThrows(MonteCarloExecutionException.class,()->new MonteCarloAnalyzer(ignored).analyze(p,settings));
        assertTrue(failed.getCause().getMessage().contains("every applicable conditional benefit"));
        assertThrows(MonteCarloStrategyComparisonAnalyzer.ExecutionException.class,()->new MonteCarloStrategyComparisonAnalyzer(ignored)
                .analyzeComparison(comparison(p,p,settings,false,2029)));
        assertFalse(p.getConditionalBenefits().isEmpty());
    }
}
