package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalBenefitIntegratedExecutionTest {
    public static RetirementPlan plan() {
        var p = BenefitFixtures.plan(true);
        var a = p.getPlanningAssumptions();
        p.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2027, 67), 4, a.getProjectionStartDate(), a.getStateTaxAssumptions()));
        p.setConditionalBenefits(benefits(true));
        return p;
    }
    static List<ConditionalBenefitDefinition> benefits(boolean primaryDies) {
        var recipient = primaryDies ? PersonReference.SPOUSE : PersonReference.PRIMARY;
        var insured = primaryDies ? PersonReference.PRIMARY : PersonReference.SPOUSE;
        var b = BenefitFixtures.change(BenefitFixtures.monthly(), "recipient", recipient);
        b = BenefitFixtures.change(b, "trigger", new Trigger(TriggerType.DEATH, null, insured, null));
        b = BenefitFixtures.change(b, "grossAmount", new BigDecimal("2500"));
        b = BenefitFixtures.change(b, "annualEscalationRate", BigDecimal.ZERO);
        b = BenefitFixtures.change(b, "label", "Original survivor monthly");
        var lump = BenefitFixtures.change(b, "id", new UUID(0, 2));
        lump = BenefitFixtures.change(lump, "paymentPattern", PaymentPattern.ONE_TIME_LUMP_SUM);
        lump = BenefitFixtures.change(lump, "grossAmount", new BigDecimal("400000"));
        lump = BenefitFixtures.change(lump, "label", "Original covered first-death lump");
        lump = BenefitFixtures.change(lump, "coverageStart", LocalDate.of(2027, 1, 1));
        lump = BenefitFixtures.change(lump, "coverageEnd", LocalDate.of(2027, 1, 1));
        return List.of(b, lump);
    }
    public static HouseholdLongevityScenarios mortality(RetirementPlan p, int primaryYear, int spouseYear) {
        var h = p.getHousehold();
        return PreparedLongevityTestSupport.create(h.getPrimaryPerson().getBirthDate(), h.getSpouse().getBirthDate(),
                p.getPlanningAssumptions().getProjectionStartDate(),
                List.of(new SocialSecurityMortalityProbability(primaryYear - h.getPrimaryPerson().getBirthDate().getYear(), BigDecimal.ONE)),
                List.of(new SocialSecurityMortalityProbability(spouseYear - h.getSpouse().getBirthDate().getYear(), BigDecimal.ONE)));
    }

    @Test void claimingStrategiesQuickGridAndSearchRetainExactDefinitionsAndReceipts() throws Exception {
        var p = plan(); String original = Stage4TestPlans.json(p);
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator(); var current = evaluator.extractCurrentStrategy(p);
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(p, List.of(67, 68), List.of(67),
                List.of(current.primarySurvivorElection()), List.of(current.spouseSurvivorElection()), IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 2);
        var search = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        assertEquals(2, search.entries().size()); assertEquals(2, search.rankedSuccessfulEntries().size());
        var quick = new IntegratedSocialSecurityStrategyComparisonService().compare(p, request.strategies());
        assertEquals(2, quick.entries().size());
        var gridCalculator = new IntegratedRetirementClaimingGridCalculator();
        var grid = gridCalculator.calculate(new IntegratedRetirementClaimingGridRequest(p, List.of(67, 68), List.of(67), gridCalculator.survivorPolicy(p)));
        assertEquals(2, grid.cells().size());
        var baseline = evaluator.evaluateCurrentStrategy(p);
        var expected = baseline.projection().getYears().stream().map(y -> y.getIncomeSources().stream()
                .flatMap(s -> s.conditionalBenefit().stream()).toList()).toList();
        assertEquals(new BigDecimal("430000.00"), expected.get(1).stream().map(ConditionalBenefitReceipt::gross).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("30000.00"), expected.get(2).stream().map(ConditionalBenefitReceipt::gross).reduce(BigDecimal.ZERO, BigDecimal::add));
        for (var entry : search.entries()) {
            assertTrue(entry.successful());
            assertEquals(expected, entry.retainedDetail().orElseThrow().projection().getYears().stream().map(y -> y.getIncomeSources().stream()
                    .flatMap(s -> s.conditionalBenefit().stream()).toList()).toList());
            var direct = evaluator.evaluate(p, entry.strategy());
            assertEquals(direct.metrics(), entry.metrics().orElseThrow());
        }
        assertEquals(original, Stage4TestPlans.json(p));
    }

    @ParameterizedTest @CsvSource({"2027,2029,true,460000", "2029,2027,false,460000", "2028,2029,true,30000", "2027,2027,true,0", "2026,2026,true,0"})
    void weightedReferenceMatchesExplicitLifetimeAndRetainsTerminalBoundary(int primaryYear, int spouseYear,
            boolean primaryDies, String expectedReceipts) {
        var p = plan(); p.setConditionalBenefits(benefits(primaryDies));
        var strategy = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(p).strategies().getFirst();
        var scenarios = mortality(p, primaryYear, spouseYear); var opening = p.getPlanningAssumptions().getProjectionStartDate();
        var request = new LongevityWeightedIntegratedStrategyRequest(p, strategy, scenarios, opening, BigDecimal.ZERO);
        var reference = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(request);
        var continuation = new LongevityWeightedContinuationEvaluator().evaluate(request);
        assertEquals(reference.scenarioOutcomes(), continuation.scenarioOutcomes());
        assertEquals(BigDecimal.ONE, reference.totalEvaluatedProbability());
        int last = Math.max(primaryYear, spouseYear) - 1;
        if (last < opening.getYear()) {
            assertEquals(0, reference.actualProjectionRunCount());
            assertEquals(new EstateAtSecondDeathCalculator().calculateOpening(p, opening).nominalAfterTaxEstate(), reference.expectedNominalEstateAtSecondDeath());
            return;
        }
        var life = new HouseholdLifetimeScenario(Optional.of(Year.of(primaryYear)), Optional.of(Year.of(spouseYear)));
        var projection = new ProjectionEngine().project(p, ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, life).withExactEndingYear(last));
        var receipts = projection.getYears().stream().flatMap(y -> y.getIncomeSources().stream()).flatMap(s -> s.conditionalBenefit().stream()).toList();
        assertEquals(0, new BigDecimal(expectedReceipts).compareTo(receipts.stream().map(ConditionalBenefitReceipt::gross).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(projection.getYears().getLast().getAfterTaxEstateValue(), reference.expectedNominalEstateAtSecondDeath());
        assertEquals(LocalDate.of(last, 12, 31), reference.scenarioOutcomes().getFirst().estateSnapshot().balanceDate());
        assertEquals(receipts.stream().filter(r -> r.pattern() == PaymentPattern.ONE_TIME_LUMP_SUM).count(),
                receipts.stream().filter(r -> r.pattern() == PaymentPattern.ONE_TIME_LUMP_SUM).map(ConditionalBenefitReceipt::benefitId).distinct().count());
    }

    @Test void weightedComparisonDisablesOnlyUnprovenBenefitReuseAndPreservesProbability() {
        var p = plan(); var strategy = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(p).strategies().getFirst();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(p, List.of(strategy, strategy), mortality(p, 2027, 2029),
                p.getPlanningAssumptions().getProjectionStartDate(), BigDecimal.ZERO);
        var service = new LongevityWeightedIntegratedStrategyComparisonService();
        var actual = service.compare(request); var reference = service.compareExact(request);
        assertTrue(actual.equivalencePlan().isEmpty()); assertEquals(0, actual.evaluationsAvoided());
        assertEquals(0, actual.work().continuationEvaluations());
        assertEquals(reference.orderedEntries(), actual.orderedEntries());
        assertEquals(BigDecimal.ONE, actual.metadata().originalScenarioProbability());
        assertEquals(2, actual.completedStrategyCount());
        assertEquals(actual.orderedEntries().getFirst().aggregate(), actual.orderedEntries().get(1).aggregate());
        assertThrows(UnsupportedOperationException.class,()->new LongevityWeightedPrefixEquivalencePlanner().plan(request));
        assertThrows(UnsupportedOperationException.class,()->new LongevityWeightedStrategyEquivalencePlanner().plan(request));
    }

    @Test void singlePersonIntegratedAndMortalitySupportDateAndAgeWithoutChangingSocialSecurityOnly() {
        var p = SinglePersonMortalityAnalysisTest.plan();
        var mortality = SinglePersonMortalityAnalysisTest.mortality(p, "1");
        var ss = SinglePersonMortalityAnalysisTest.run(p, mortality, ".02", SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY);
        var b = BenefitFixtures.change(BenefitFixtures.monthly(), "trigger", new Trigger(TriggerType.AGE, null, PersonReference.PRIMARY, 65));
        p.setConditionalBenefits(List.of(b));
        assertEquals(ss.entries(), SinglePersonMortalityAnalysisTest.run(p, mortality, ".02", SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY).entries());
        var weighted = SinglePersonMortalityAnalysisTest.run(p, mortality, ".02", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        assertEquals(9, weighted.entries().size()); assertTrue(weighted.projectionCount() > 0);
        var deterministic = SinglePersonIntegratedAnalysis.calculate(IntegratedSocialSecurityCompleteStrategySearchRequest.standard(p),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(9, deterministic.result().rankedSuccessfulEntries().size());
    }
}
