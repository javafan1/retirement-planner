package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeInsuranceMonteCarloTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    static final Instant START=Instant.parse("2026-11-06T21:30:00Z");
    static ConditionalBenefitDefinition principalFor(RetirementPlan plan) {
        var node=LifeInsurancePrincipalTest.document();
        node.put("coverageStart",plan.getPlanningAssumptions().getProjectionStartDate().toString());
        node.putNull("coverageEnd");
        try { return BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class); }
        catch (Exception invalid) { throw new IllegalArgumentException(invalid); }
    }
    static RetirementPlan prepared(boolean expenses) {
        var plan=plan(true); var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES,a.getProjectionStartDate().getYear()+1,67),
                a.getProjectionLengthYears(),a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        plan.setConditionalBenefits(List.of(principalFor(plan)));
        if (expenses) StandaloneExpenseCapabilityTest.enable(plan,".25");
        return plan;
    }
    static void roundTrip(MonteCarloSnapshot payload) throws Exception {
        assertEquals(payload.paired()==null?6:7,payload.version());
        assertEquals(payload.paired()==null?7:8,payload.fingerprintVersion());
        assertEquals(2,payload.benefitExecution().version());
        var saved=StandaloneExpenseCapabilityTest.envelope(payload); var codec=new AnalysisSnapshotCodec();
        assertEquals(saved,codec.decode(codec.encode(saved)));
        var policy=new MonteCarloFingerprintService();
        var assessment=policy.compatibility(saved,new MonteCarloFingerprintService.Inputs(payload.kind(),payload.settings(),payload.request()));
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,assessment.state());
        assertTrue(assessment.explanation().contains("life insurance principal contract v"+payload.fingerprintVersion()));
    }

    @ParameterizedTest @CsvSource({"false,false","false,true","true,false","true,true"})
    void coupleStandaloneUsesAuthoritativeScenarioCashAndVersionedCapability(boolean longevity,boolean expenses) throws Exception {
        var plan=prepared(expenses); var settings=MonteCarloSettings.forPlan(plan,2,417,new BigDecimal(".12"));
        var service=new MonteCarloRunService();
        var run=longevity?service.runMortality(plan,mortality(plan,settings),PROGRESS,NEVER):service.run(plan,settings,null,PROGRESS,NEVER);
        var again=longevity?service.runMortality(plan,mortality(plan,settings),PROGRESS,NEVER):service.run(plan,settings,null,PROGRESS,NEVER);
        if(longevity) assertEquals(run.mortalityResult().outcomes(),again.mortalityResult().outcomes());
        else assertEquals(run.result(),again.result());
        roundTrip(MonteCarloSnapshotPresentationMapper.capture(run,plan,START,START.plusSeconds(1)));
        int first=plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        for(int index=0;index<2;index++) {
            var world=longevity?new MonteCarloWorldGenerator(mortality(plan,settings)).generate(index):null;
            int last=longevity?world.lifetime().terminalDeathYear()-1:first+plan.getPlanningAssumptions().getProjectionLengthYears()-1;
            if(last<first) continue;
            var economic=longevity?world.economicPath():new MonteCarloScenarioGenerator().generate(first,last,settings,index);
            var context=longevity?ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(last)
                    .withSurvivorClaimingAge(mortality(plan,settings).survivorClaimingAge().orElseThrow()):ProjectionEvaluationContext.empty();
            var inflation=longevity?world.inflationPath():new MonteCarloInflationGenerator().generate(first,last,settings,index);
            if(inflation.isPresent()) context=context.withInflationPath(inflation.orElseThrow());
            if(expenses) context=InternalExpenseRun.configured(plan).apply(plan,context,settings.seed(),index,first,last);
            var direct=new ProjectionEngine().projectWithOutcome(plan,context,economic);
            var annual=new TreeMap<Integer,BigDecimal>(); direct.completedYears().forEach(y->annual.put(y.getCalendarYear(),y.getEndingInvestableAssets()));
            if(longevity) assertEquals(annual,run.mortalityResult().outcomes().get(index).annualInvestableAssets());
            else if(direct instanceof ProjectionExecutionResult.Completed completed)
                assertEquals(new com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator().calculate(plan,completed.projection()),
                        run.result().outcomes().get(index).metrics().orElseThrow());
            for(var year:direct.completedYears()) for(var source:year.getIncomeSources()) if(source.conditionalBenefit().isPresent()) {
                var receipt=source.conditionalBenefit().orElseThrow(); assertEquals(0,receipt.taxable().signum());
                assertEquals(receipt.gross(),receipt.excluded());
            }
        }
    }

    static Stream<Arguments> pairs() {
        return Stream.of(false,true).flatMap(l->Stream.of("CURRENT","BASELINE","IDENTICAL","DIFFERENT","TAXABLE","COVERAGE","REVERSE")
                .map(c->Arguments.of(l,c)));
    }
    @ParameterizedTest @MethodSource("pairs")
    void pairedCandidatesRemainIndependentWithPositiveExpenses(boolean longevity,String configuration) throws Exception {
        var a=prepared(true); var b=new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(a);
        if(configuration.equals("CURRENT")) b.setConditionalBenefits(List.of());
        if(configuration.equals("BASELINE")) a.setConditionalBenefits(List.of());
        if(configuration.equals("DIFFERENT")) b.setConditionalBenefits(List.of(BenefitFixtures.change(b.getConditionalBenefits().getFirst(),"grossAmount",new BigDecimal("450000"))));
        if(configuration.equals("TAXABLE") || configuration.equals("REVERSE")) {
            var node=BenefitFixtures.JSON.valueToTree(b.getConditionalBenefits().getFirst());
            if(configuration.equals("TAXABLE")) { ((com.fasterxml.jackson.databind.node.ObjectNode)node).remove("lifeInsuranceQualification");
                ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("taxCharacter","FULLY_TAXABLE_ORDINARY_NON_PENSION"); }
            else { ((com.fasterxml.jackson.databind.node.ObjectNode)node.get("trigger")).put("person","SPOUSE");
                ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("recipient","PRIMARY"); }
            b.setConditionalBenefits(List.of(BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class)));
        }
        if(configuration.equals("COVERAGE")) b.setConditionalBenefits(List.of(BenefitFixtures.change(b.getConditionalBenefits().getFirst(),"coverageEnd",
                b.getPlanningAssumptions().getProjectionStartDate().plusYears(1).withDayOfYear(1))));
        var settings=MonteCarloSettings.forPlan(a,2,417,new BigDecimal(".12"));
        int last=a.getPlanningAssumptions().getProjectionStartDate().getYear()+a.getPlanningAssumptions().getProjectionLengthYears()-1;
        var request=longevity?comparison(a,b,settings,true,last):new MonteCarloStrategyComparisonRequest(
                new MonteCarloStrategyCandidate("Current",a),new MonteCarloStrategyCandidate("Baseline",b),
                new MonteCarloStrategyComparisonRequest.Fixed(settings,a.getPlanningAssumptions().getProjectionStartDate(),last,
                        MonteCarloLifetime.couple(new HouseholdLifetimeScenario(Optional.of(Year.of(a.getPlanningAssumptions().getProjectionStartDate().getYear()+1)),Optional.empty()))));
        var analyzer=new MonteCarloStrategyComparisonAnalyzer();
        var result=analyzer.analyzeComparison(request,PROGRESS,NEVER);
        assertEquals(result.outcomes(),analyzer.analyzeComparison(request,PROGRESS,NEVER).outcomes());
        if(configuration.equals("IDENTICAL")) assertEquals(0,result.summary().terminalAfterTaxEstateDifference().meanDifference().orElseThrow().signum());
        var payload=MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,
                longevity?MonteCarloMode.LONGEVITY_ADJUSTED:MonteCarloMode.FIXED_LIFESPAN,"Original",0),START,START.plusSeconds(1));
        roundTrip(payload);
        assertEquals(a.getConditionalBenefits(),MonteCarloPlanDocument.read(payload.request().planA()).getConditionalBenefits());
        assertEquals(b.getConditionalBenefits(),MonteCarloPlanDocument.read(payload.request().planB()).getConditionalBenefits());
        assertThrows(IllegalArgumentException.class,()->new MonteCarloSnapshot(5,payload.mode(),true,payload.settings(),payload.request(),payload.provenance(),
                null,null,payload.paired(),payload.display(),START,START.plusSeconds(1),null,payload.pairedExpenseExecution(),payload.benefitExecution()));
        if (!payload.request().planA().equals(payload.request().planB()))
            assertThrows(IllegalArgumentException.class,()->payload.benefitExecution().validate(payload.kind(),payload.request().planB(),payload.request().planA()));
    }

    @Test void legacyCapabilityCannotAuthenticateExcludedDefinitionsAndStreamsAreUnchanged() {
        var p=prepared(true); var capability=ConditionalBenefitAnalysisExecution.capture(AnalysisKind.COUPLE_MONTE_CARLO_FIXED,p);
        assertThrows(IllegalArgumentException.class,()->new ConditionalBenefitAnalysisExecution(1,ConditionalBenefitAnalysisExecution.MODEL,
                capability.family(),capability.scenarioProtocol(),capability.candidates()));
        var settings=MonteCarloSettings.forPlan(p,2,417,new BigDecimal(".12"));
        var before=new MonteCarloWorldGenerator(mortality(p,settings)); p.setConditionalBenefits(List.of());
        var after=new MonteCarloWorldGenerator(mortality(p,settings));
        for(int i=0;i<2;i++) { assertEquals(before.generate(i).lifetime(),after.generate(i).lifetime());
            assertEquals(before.generate(i).economicPath().annualReturns(),after.generate(i).economicPath().annualReturns()); }
        assertFalse(new MonteCarloFingerprintService().assess(MonteCarloFingerprintService.standalone(prepared(true),settings,null),
                MonteCarloFingerprintService.Contract.BENEFITS_STANDALONE_V5).valid());
    }

    @Test void excludedCapabilityStorageFailureRetainsOldResultAndRetryNeverSimulates() throws Exception {
        var plan=prepared(true); var settings=MonteCarloSettings.forPlan(plan,2,417,new BigDecimal(".12"));
        var run=new MonteCarloRunService().run(plan,settings,null,PROGRESS,NEVER);
        var payload=MonteCarloSnapshotPresentationMapper.capture(run,plan,START,START.plusSeconds(1));
        assertEquals(6,payload.version()); assertEquals(7,payload.fingerprintVersion());
        var repository=new MonteCarloResultLifecycleTest.Memory(); var store=new AnalysisResultStore(repository);
        var ui=new MonteCarloResultLifecycleTest.Ui();
        var binding=new MonteCarloResultLifecycle.Binding(UUID.randomUUID(),directory.resolve("synthetic.json"),payload.kind());
        var lifecycle=new MonteCarloResultLifecycle<Object>(store,ui,
                saved->{ fail("Retry Save must neither restore nor rerun a completed excluded result"); return null; },r->{},()->{},()->{});
        try {
            lifecycle.bind(binding); lifecycle.assess(()->()->new MonteCarloFingerprintService.Inputs(payload.kind(),payload.settings(),payload.request()));
            lifecycle.accept(MonteCarloResultLifecycle.completion(binding,payload)); ui.until(()->lifecycle.status().contains("SAVED"));
            var previous=repository.load(binding.planId(),payload.kind()).orElseThrow(); repository.fail=true;
            var replacement=MonteCarloResultLifecycle.completion(binding,payload); lifecycle.accept(replacement); ui.until(lifecycle::retryAvailable);
            assertEquals(previous,repository.load(binding.planId(),payload.kind()).orElseThrow());
            assertSame(payload,store.inMemory(new AnalysisResultStore.Slot(binding.planId(),payload.kind())).orElseThrow().payload());
            repository.fail=false; lifecycle.retry(); lifecycle.close();
            assertEquals(AnalysisResultStore.Durability.SAVED,store.whenSettled(new AnalysisResultStore.Slot(binding.planId(),payload.kind()))
                    .toCompletableFuture().get(15,java.util.concurrent.TimeUnit.SECONDS).durability());
            assertEquals(replacement.runId(),repository.load(binding.planId(),payload.kind()).orElseThrow().runId());
            assertEquals(payload,repository.load(binding.planId(),payload.kind()).orElseThrow().payload());
        } finally { lifecycle.close(); MonteCarloResultLifecycleTest.close(store); }
    }
}
