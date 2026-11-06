package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitFingerprintAdmissionTest {
    private String integrated(RetirementPlan plan) {
        return new AnalysisFingerprintService().fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none());
    }
    private String monteCarlo(RetirementPlan plan) {
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
        var assessment = new MonteCarloFingerprintService().forAcceptance(MonteCarloFingerprintService.standalone(plan, settings, null));
        assertTrue(assessment.valid(), assessment.problem()); return assessment.fingerprint();
    }

    @Test void financialIdentityIncludesSemanticsButExcludesOnlyDisplayIdentityAndOrder() {
        var plan = plan(true);
        var original = integrated(plan); var originalMc = monteCarlo(plan);
        plan.setConditionalBenefits(List.of());
        assertEquals(original, integrated(plan)); assertEquals(originalMc, monteCarlo(plan));
        plan.setConditionalBenefits(List.of(monthly()));
        var financial = integrated(plan); var mc = monteCarlo(plan);
        assertNotEquals(original, financial); assertNotEquals(originalMc, mc);
        for (var equivalent : List.of(change(monthly(), "label", "Renamed"), change(monthly(), "id", new UUID(1, 1)),
                change(monthly(), "annualEscalationRate", new BigDecimal(".02")))) {
            plan.setConditionalBenefits(List.of(equivalent));
            assertEquals(financial, integrated(plan)); assertEquals(mc, monteCarlo(plan));
        }
        for (var material : List.of(change(monthly(), "grossAmount", new BigDecimal("2501")),
                change(monthly(), "recipient", PersonReference.SPOUSE),
                change(monthly(), "trigger", new Trigger(TriggerType.AGE, null, PersonReference.PRIMARY, 75)),
                change(monthly(), "notBefore", LocalDate.of(2033, 1, 1)),
                change(monthly(), "endDate", LocalDate.of(2040, 1, 1)),
                change(monthly(), "coverageEnd", LocalDate.of(2040, 1, 1)),
                change(monthly(), "annualEscalationRate", new BigDecimal(".03")))) {
            plan.setConditionalBenefits(List.of(material));
            assertNotEquals(financial, integrated(plan)); assertNotEquals(mc, monteCarlo(plan));
        }
        var duplicate = change(monthly(), "id", new UUID(0, 2));
        var distinct = change(duplicate, "grossAmount", BigDecimal.TEN);
        plan.setConditionalBenefits(List.of(monthly(), duplicate));
        assertNotEquals(financial, integrated(plan)); assertNotEquals(mc, monteCarlo(plan));
        plan.setConditionalBenefits(List.of(monthly(), distinct));
        var two = integrated(plan); var twoMc = monteCarlo(plan);
        plan.setConditionalBenefits(List.of(distinct, monthly()));
        assertEquals(two, integrated(plan)); assertEquals(twoMc, monteCarlo(plan));
        var level = change(monthly(), "annualEscalationRate", BigDecimal.ZERO);
        plan.setConditionalBenefits(List.of(level));
        var recurring = integrated(plan);
        plan.setConditionalBenefits(List.of(change(level, "paymentPattern", PaymentPattern.ONE_TIME_LUMP_SUM)));
        assertNotEquals(recurring, integrated(plan));
        var survivor = change(change(monthly(), "recipient", PersonReference.SPOUSE), "trigger",
                new Trigger(TriggerType.DEATH, null, PersonReference.PRIMARY, null));
        plan.setConditionalBenefits(List.of(survivor));
        var incomeCategory = integrated(plan);
        plan.setConditionalBenefits(List.of(change(survivor, "category", Category.DEATH_BENEFIT)));
        assertNotEquals(incomeCategory, integrated(plan));
    }

    @Test void expenseEnabledIdentityKeepsBothCandidateBenefitSemanticsAndIgnoresTheirOpaqueMetadata() {
        var plan = plan(true);
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Stochastic expense",
                new BigDecimal("1000"), com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL, null, null,
                com.daviddunn.retirementplanner.domain.financial.ExpenseType.RECURRING, new BigDecimal(".17")));
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
        var service = new MonteCarloFingerprintService();
        plan.setBaseline(ProjectionBaselineFactory.create(plan, "Zero benefits"));
        plan.setConditionalBenefits(List.of(monthly()));
        var standalone = MonteCarloFingerprintService.standalone(plan, settings, null);
        assertEquals(MonteCarloFingerprintService.Contract.BENEFITS_STANDALONE_V5, service.acceptanceContract(standalone));
        var original = service.forAcceptance(standalone);
        assertTrue(original.valid());
        var pair = MonteCarloStrategyComparisonRunService.capture(plan, settings, MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67");
        var ordered = service.forAcceptance(MonteCarloFingerprintService.comparison(pair.request()));
        assertTrue(ordered.valid());
        assertEquals(MonteCarloFingerprintService.Contract.PAIRED_BENEFITS_V6,
                service.acceptanceContract(MonteCarloFingerprintService.comparison(pair.request())));
        plan.setConditionalBenefits(List.of(change(change(monthly(), "id", new UUID(1, 2)), "label", "Renamed only")));
        assertEquals(original.fingerprint(), service.forAcceptance(MonteCarloFingerprintService.standalone(plan, settings, null)).fingerprint());
        pair = MonteCarloStrategyComparisonRunService.capture(plan, settings, MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67");
        assertEquals(ordered.fingerprint(), service.forAcceptance(MonteCarloFingerprintService.comparison(pair.request())).fingerprint());
        plan.setBaseline(ProjectionBaselineFactory.create(plan, "Benefit-bearing baseline"));
        plan.setConditionalBenefits(List.of());
        pair = MonteCarloStrategyComparisonRunService.capture(plan, settings, MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67");
        var reversed = service.forAcceptance(MonteCarloFingerprintService.comparison(pair.request()));
        assertTrue(reversed.valid()); assertNotEquals(ordered.fingerprint(), reversed.fingerprint());
    }

    @Test void everyMonteCarloHouseholdModeCapturesBothCandidatesAndExecutesWithExplicitCapability() {
        for (boolean couple : new boolean[]{false, true}) for (var mode : MonteCarloMode.values()) {
            for (boolean benefitInBaseline : new boolean[]{false, true}) {
                var plan = plan(couple);
                if (benefitInBaseline) plan.setConditionalBenefits(List.of(monthly()));
                plan.setBaseline(ProjectionBaselineFactory.create(plan, "Original assumptions"));
                plan.setConditionalBenefits(benefitInBaseline ? List.of() : List.of(monthly()));
                var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
                var paired = MonteCarloStrategyComparisonRunService.capture(plan, settings, mode, "1", "1", "67", "67");
                var fingerprints = new MonteCarloFingerprintService();
                var inputs = MonteCarloFingerprintService.comparison(paired.request());
                assertTrue(fingerprints.forAcceptance(inputs).valid());
                var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(paired.request(), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
                assertEquals(!benefitInBaseline, result.benefitExecution().candidates().getFirst().enabled());
                assertEquals(benefitInBaseline, result.benefitExecution().candidates().get(1).enabled());
                assertNotNull(new MonteCarloStrategyComparisonRunService().run(paired, AnalysisProgressListener.none(), AnalysisCancellationToken.none()).result().benefitExecution());
            }
            var plan = plan(couple); plan.setConditionalBenefits(List.of(monthly()));
            var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
            var analyzer = new MonteCarloAnalyzer();
            if (mode == MonteCarloMode.FIXED_LIFESPAN) {
                assertNotNull(analyzer.analyze(plan, settings).benefitExecution());
                assertNotNull(new MonteCarloRunService().run(plan,settings,null,AnalysisProgressListener.none(),AnalysisCancellationToken.none()).result().benefitExecution());
            } else {
                var mortality = new MonteCarloMortalityRequest(plan, settings,
                        com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings.defaults(
                                plan.getPlanningAssumptions().getProjectionStartDate()), 67);
                assertTrue(new MonteCarloFingerprintService().forAcceptance(MonteCarloFingerprintService.standalone(plan, settings, mortality)).valid());
                assertNotNull(analyzer.analyzeMortality(plan, mortality).benefitExecution());
                assertNotNull(new MonteCarloRunService().runMortality(plan,mortality,AnalysisProgressListener.none(),AnalysisCancellationToken.none()).mortalityResult().benefitExecution());
            }
        }
    }

    @Test void verifiedDeterministicAndIntegratedEntriesAcceptDefinitions() {
        for (boolean couple : new boolean[]{false, true}) {
            var plan = plan(couple); plan.setConditionalBenefits(List.of(change(monthly(), "grossAmount", BigDecimal.ZERO)));
            var engine = new ProjectionEngine();
            assertNotNull(engine.project(plan));
            assertNotNull(engine.project(plan, ProjectionEvaluationContext.empty()));
            assertNotNull(new EstateAtSecondDeathCalculator().calculateOpening(plan, plan.getPlanningAssumptions().getProjectionStartDate()));
            assertNotNull(new IntegratedSocialSecurityStrategyEvaluator().evaluateCurrentStrategy(plan));
            if (couple) {
                assertTrue(new IntegratedSocialSecurityStrategyComparisonService().compare(plan, List.of()).entries().isEmpty());
                var grid = new IntegratedRetirementClaimingGridCalculator();
                assertEquals(1, grid.calculate(new IntegratedRetirementClaimingGridRequest(plan, List.of(67), List.of(67), grid.survivorPolicy(plan))).cells().size());
                var strategy = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
                assertEquals(1, new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(
                        new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(67), List.of(67),
                                List.of(strategy.primarySurvivorElection()), List.of(strategy.spouseSurvivorElection()),
                                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 1)).entries().size());
            }
        }
    }

    @Test void socialSecurityOnlyCalculationsIgnoreFullPlanBenefitsWhileIntegratedMortalityExecutesThem() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var mortality = SinglePersonMortalityAnalysisTest.mortality(plan, "1");
        var service = new AnalysisFingerprintService();
        var inputs = new AnalysisFingerprintService.Inputs(BigDecimal.ONE, null, LocalDate.of(2027,1,1),
                LocalDate.of(2027,1,1), new BigDecimal(".02"), null, null, List.of(), List.of(), null);
        var before = service.fingerprint(AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY, plan, inputs);
        var results = SinglePersonMortalityAnalysisTest.run(plan, mortality, ".02", SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY);
        plan.setConditionalBenefits(List.of(monthly()));
        assertEquals(before, service.fingerprint(AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY, plan, inputs));
        assertEquals(results.entries(), SinglePersonMortalityAnalysisTest.run(plan, mortality, ".02", SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY).entries());
        assertEquals(9, SinglePersonMortalityAnalysisTest.run(plan, mortality, ".02", SinglePersonMortalityAnalysis.Mode.INTEGRATED).entries().size());
    }

    @Test void weightedAndContinuationOpeningEstateShortcutsPreserveOpeningEstateWithoutPayments() {
        var plan = plan(true);
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        var strategy = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan).strategies().getFirst();
        var household = plan.getHousehold();
        // Both deaths at the opening date would otherwise require no ProjectionEngine call.
        var probability = new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityProbability(66, BigDecimal.ONE);
        var scenarios = com.daviddunn.retirementplanner.domain.socialsecurity.analysis.PreparedLongevityTestSupport.create(
                household.getPrimaryPerson().getBirthDate(), household.getSpouse().getBirthDate(), start,
                List.of(probability), List.of(new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityProbability(64, BigDecimal.ONE)));
        plan.setConditionalBenefits(List.of(monthly()));
        var request = new LongevityWeightedIntegratedStrategyRequest(plan, strategy, scenarios, start, BigDecimal.ZERO);
        var reference = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(request);
        assertEquals(0, reference.actualProjectionRunCount());
        assertEquals(new EstateAtSecondDeathCalculator().calculateOpening(plan, start).nominalAfterTaxEstate(), reference.expectedNominalEstateAtSecondDeath());
        assertEquals(reference.scenarioOutcomes(), new LongevityWeightedContinuationEvaluator().evaluate(request).scenarioOutcomes());
        var comparison = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(strategy), scenarios, start, BigDecimal.ZERO);
        assertEquals(new LongevityWeightedIntegratedStrategyComparisonService().compareExact(comparison).orderedEntries(),
                new LongevityWeightedIntegratedStrategyComparisonService().compare(comparison).orderedEntries());
    }

    @Test void legacyArtifactsRemainReadableButCannotClaimConditionalBenefitExecution() throws Exception {
        var plan = plan(false);
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO);
        var run = new MonteCarloRunService().run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var completed = java.time.Instant.parse("2026-11-06T12:00:00Z");
        var original = MonteCarloSnapshotPresentationMapper.capture(run, plan, completed, completed);
        original.validate(original.kind());
        var before = JSON.writeValueAsString(original);
        com.fasterxml.jackson.databind.node.ObjectNode node = JSON.valueToTree(original);
        var frozen = JSON.readValue(original.request().planA(), RetirementPlan.class);
        frozen.setConditionalBenefits(List.of(monthly()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("request")).put("planA", JSON.writeValueAsString(frozen));
        var unsupported = JSON.treeToValue(node, MonteCarloSnapshot.class);
        assertDoesNotThrow(() -> unsupported.validate(unsupported.kind()));
        assertThrows(IllegalArgumentException.class, unsupported::requireInterpretable);
        assertThrows(IllegalArgumentException.class, () -> MonteCarloSnapshotPresentationMapper.restoreRun(unsupported));
        assertEquals(before, JSON.writeValueAsString(original));
    }
}
