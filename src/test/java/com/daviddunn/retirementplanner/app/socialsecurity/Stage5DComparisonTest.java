package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.Stage5DSequentialCharacterizationTest.sameResult;
import static com.daviddunn.retirementplanner.app.socialsecurity.Stage5DCoordinatorTest.await;

@Timeout(30)
class Stage5DComparisonTest {
    static LongevityWeightedIntegratedStrategyComparisonRequest detailed(RetirementPlan plan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates, HouseholdLongevityScenarios mortality,
            AnalysisProgressListener progress, AnalysisCancellationToken token) {
        return new LongevityWeightedIntegratedStrategyComparisonRequest(plan,candidates,mortality,
                LocalDate.of(2029,7,1),new BigDecimal("0.03"),Optional.of(Stage4TestPlans.strategy(plan)),
                new LongevityWeightedDetailRetentionPolicy(true,Set.of(2)),progress,token);
    }

    @ParameterizedTest
    @ValueSource(ints={1,2,4,6,8})
    void completeEqualityWithDuplicatesBaselineSelectedMemberAndExactScales(int workers) throws Exception {
        var plan=Stage4TestPlans.plan();
        var a=strategy(plan,67,67);
        var candidates=List.of(a,strategy(plan,67,67),strategy(plan,62,62),strategy(plan,70,70),a);
        var owner=Thread.currentThread();
        var progress=new ArrayList<AnalysisProgress>();
        var req=detailed(plan,candidates,scenarios(plan),p->{assertSame(owner,Thread.currentThread());progress.add(p);},
                ()->{assertSame(owner,Thread.currentThread());return false;});
        var service=new LongevityWeightedIntegratedStrategyComparisonService(workers);
        var expected=service.compareSequential(req);
        progress.clear();
        var actual=service.compare(req);
        sameResult(expected,actual);
        for(int i=0;i<candidates.size();i++)assertSame(candidates.get(i),actual.orderedEntries().get(i).strategy());
        assertEquals(List.of(0,1,2,3,4,5,6),progress.stream().filter(p->p.phase()==AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON)
                .map(AnalysisProgress::completedWork).toList());
        assertTrue(progress.stream().allMatch(p->p.fractionComplete()>=0&&p.fractionComplete()<=1));
    }

    @ParameterizedTest
    @ValueSource(ints={1,2,4,8})
    void youngerFailuresCarrierFallbackBaselineAndEveryRetryMatch(int workers) throws Exception {
        var plan=LongevityWeightedStrategyEquivalenceTest.youngPlan();
        var a=Stage4TestPlans.strategy(plan);
        var req=detailed(plan,List.of(a,a,strategy(plan,70,70)),
                LongevityContinuationTest.mortality(plan,List.of(2031),List.of(2040,2081)),
                AnalysisProgressListener.none(),AnalysisCancellationToken.none());
        var service=new LongevityWeightedIntegratedStrategyComparisonService(workers);
        var actual=service.compare(req);
        sameResult(service.compareSequential(req),actual);
        assertTrue(actual.work().continuation().failedCarriers()>0);
        assertTrue(actual.work().continuation().fallbackIndependentRuns()>0);
        assertEquals(List.of(0,1,2,3),actual.failures().stream().map(e->e.inputOrder()).toList());
    }

    @ParameterizedTest
    @ValueSource(ints={2,4,8})
    void validationFailureDoesNotPreventLaterSuccess(int workers) throws Exception {
        var plan=Stage4TestPlans.plan();var valid=strategy(plan,67,67);
        var invalid=new SocialSecurityHouseholdClaimingStrategy(62,67,valid.primaryRetirementClaimDate(),valid.spouseRetirementClaimDate(),valid.primarySurvivorElection(),valid.spouseSurvivorElection());
        var req=request(plan,List.of(valid,invalid,strategy(plan,70,70),invalid));
        var service=new LongevityWeightedIntegratedStrategyComparisonService(workers);
        var result=service.compare(req);
        sameResult(service.compareSequential(req),result);
        assertEquals(List.of(2,4),result.failures().stream().map(e->e.inputOrder()).toList());
        assertEquals(2,result.completedStrategyCount());
    }

    @Test
    void successfulRetryNeverReplacesFailedRepresentative() {
        var plan=Stage4TestPlans.plan();var a=strategy(plan,67,67);
        var progress=new ArrayList<AnalysisProgress>();
        var calls=Collections.synchronizedList(new ArrayList<Integer>());
        var service=new LongevityWeightedIntegratedStrategyComparisonService(2,input->{
            calls.add(input.order());
            var value=LongevityWeightedIntegratedStrategyComparisonService.evaluateRepresentative(input);
            if(input.order()!=1)return value;
            var failure=new IntegratedSocialSecurityStrategyEvaluationFailure(input.request().strategy(),"controlled failure",
                    IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION);
            return new LongevityWeightedIntegratedStrategyComparisonService.RepresentativeResult(
                    new LongevityWeightedIntegratedStrategyComparisonEntry(1,a,Optional.empty(),Optional.of(failure),Optional.empty(),OptionalInt.empty(),Optional.empty(),Optional.empty()),value.work());
        });
        var req=new LongevityWeightedIntegratedStrategyComparisonRequest(plan,List.of(a,a,a,strategy(plan,70,70)),
                scenarios(plan),LocalDate.of(2029,7,1),new BigDecimal("0.03"),Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(),progress::add,AnalysisCancellationToken.none());
        var result=service.compare(req);
        assertEquals(Set.of(1,2,3,4),new HashSet<>(calls));
        assertEquals(4,calls.size());
        assertEquals(4,result.work().continuationEvaluations());
        assertEquals(0,result.evaluationsAvoided());
        assertEquals(1,result.failedStrategyCount());
        assertEquals(3,result.completedStrategyCount());
        assertEquals(List.of(1),result.failures().stream().map(e->e.inputOrder()).toList());
        assertEquals(List.of(0,1,2,3,4),progress.stream()
                .filter(p->p.phase()==AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON)
                .map(AnalysisProgress::completedWork).toList());
    }

    @Test
    void controlledReverseCompletionKeepsRankingAndCallbackOrder() throws Exception {
        if(Runtime.getRuntime().availableProcessors()<2)return;
        var plan=Stage4TestPlans.plan();
        var owner=Thread.currentThread();
        var progress=new ArrayList<AnalysisProgress>();
        var req=detailed(plan,List.of(strategy(plan,62,62),strategy(plan,70,70)),scenarios(plan),p->{
            assertSame(owner,Thread.currentThread());progress.add(p);
        },AnalysisCancellationToken.none());
        var second=new CountDownLatch(1);var completion=Collections.synchronizedList(new ArrayList<Integer>());
        var service=new LongevityWeightedIntegratedStrategyComparisonService(2,input->{
            if(input.order()==1)await(second);
            var result=LongevityWeightedIntegratedStrategyComparisonService.evaluateRepresentative(input);
            completion.add(input.order());
            if(input.order()==2)second.countDown();
            return result;
        });
        var expected=service.compareSequential(req);
        progress.clear();
        sameResult(expected,service.compare(req));
        assertEquals(List.of(2,1),completion);
        assertEquals(List.of(0,1,2,3),progress.stream()
                .filter(p->p.phase()==AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON)
                .map(AnalysisProgress::completedWork).toList());
        assertTrue(progress.stream().allMatch(p->p.fractionComplete()>=0&&p.fractionComplete()<=1));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void retentionNearLimitAndAggregateOnlyAreIndependentOfWorkers(boolean detail) throws Exception {
        var plan=Stage4TestPlans.plan();
        var candidates=LongevityWeightedEquivalenceBenchmarkTest.universe(plan).subList(0,20);
        var selection=new HashSet<Integer>();for(int i=2;i<=20;i++)selection.add(i);
        var req=new LongevityWeightedIntegratedStrategyComparisonRequest(plan,candidates,scenarios(plan),LocalDate.of(2029,7,1),new BigDecimal("0.03"),
                Optional.of(Stage4TestPlans.strategy(plan)),detail?new LongevityWeightedDetailRetentionPolicy(true,selection):LongevityWeightedDetailRetentionPolicy.aggregateOnly(),AnalysisProgressListener.none(),AnalysisCancellationToken.none());
        var service=new LongevityWeightedIntegratedStrategyComparisonService(4);
        var result=service.compare(req);sameResult(service.compareSequential(req),result);
        assertEquals(detail?40:0,result.retainedDetailedScenarioOutcomeCount());
        assertThrows(IllegalArgumentException.class,()->new LongevityWeightedDetailRetentionPolicy(true,java.util.stream.IntStream.rangeClosed(1,20).boxed().collect(java.util.stream.Collectors.toSet())));
    }

    @ParameterizedTest
    @ValueSource(strings={"before","final"})
    void cancellationBeforeSubmissionOrAtPublicationReturnsNothing(String boundary) {
        var plan=Stage4TestPlans.plan();var flag=new AtomicBoolean(boundary.equals("before"));var calls=new AtomicInteger();
        var req=detailed(plan,List.of(strategy(plan,62,62),strategy(plan,70,70)),scenarios(plan),p->{
            if(p.phase()==AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON&&p.completedWork()==p.totalWork())flag.set(true);
        },flag::get);
        var service=new LongevityWeightedIntegratedStrategyComparisonService(4,input->{calls.incrementAndGet();return LongevityWeightedIntegratedStrategyComparisonService.evaluateRepresentative(input);});
        assertThrows(AnalysisCancelledException.class,()->service.compare(req));
        if(boundary.equals("before"))assertEquals(0,calls.get());
    }

    @Test
    void graphOwnershipRmdIdentityAndFrozenSourceArePreserved() throws Exception {
        var plan=Stage4TestPlans.plan();var sourceJson=Stage4TestPlans.json(plan);
        var req=request(plan,List.of(strategy(plan,62,62),strategy(plan,70,70)));
        var inputs=Collections.synchronizedList(new ArrayList<LongevityWeightedIntegratedStrategyComparisonService.RepresentativeInput>());
        var service=new LongevityWeightedIntegratedStrategyComparisonService(2,input->{inputs.add(input);return LongevityWeightedIntegratedStrategyComparisonService.evaluateRepresentative(input);});
        var expected=service.compareSequential(req);sameResult(expected,service.compare(req));
        var a=inputs.get(0).request().sourcePlan();var b=inputs.get(1).request().sourcePlan();
        assertNotSame(a,b);assertNotSame(a.getHousehold(),b.getHousehold());
        for(var account:a.getAccountPortfolio().getAccounts())for(var other:b.getAccountPortfolio().getAccounts())assertNotSame(account,other);
        var snapshot=RmdBalanceSnapshot.from(LocalDate.of(2029,12,31),ProjectedPortfolio.from(a.getAccountPortfolio()));
        assertThrows(IllegalArgumentException.class,()->snapshot.getBalance(b.getAccountPortfolio().getAccounts().getFirst()));
        var beforeB=Stage4TestPlans.json(b);a.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ONE);
        assertEquals(beforeB,Stage4TestPlans.json(b));assertEquals(sourceJson,Stage4TestPlans.json(plan));
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.TEN);
        sameResult(expected,service.compare(req));
        assertEquals(5,plan.getPlanningAssumptions().getProjectionLengthYears());
    }

    @Test
    void concurrentJobsAndDeterministicAnalysisStayIsolatedAcrossRepeatedRuns() throws Exception {
        var plan=Stage4TestPlans.plan();var other=Stage4TestPlans.plan();var json=Stage4TestPlans.json(plan);
        var req=request(plan,List.of(strategy(plan,62,62),strategy(plan,70,70)));
        var req2=request(other,req.candidates());var service=new LongevityWeightedIntegratedStrategyComparisonService(2);
        var expected=service.compareSequential(req);
        var deterministic=new ProjectionEngine().project(plan).getFinalInvestableAssets();
        for(int i=0;i<3;i++){
            var gate=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(3)){
                var first=pool.submit(()->{await(gate);return service.compare(req);});
                var second=pool.submit(()->{await(gate);return service.compare(req2);});
                var third=pool.submit(()->{await(gate);return new ProjectionEngine().project(plan).getFinalInvestableAssets();});
                gate.countDown();sameResult(expected,first.get());sameResult(expected,second.get());assertEquals(deterministic,third.get());
            }
        }
        assertEquals(json,Stage4TestPlans.json(plan));assertEquals(json,Stage4TestPlans.json(other));
    }

    @Test
    void scaleDifferentExactTiesKeepCompetitionRanksAndOriginalOrder() {
        var plan=Stage4TestPlans.plan();
        var candidates=List.of(strategy(plan,62,62),strategy(plan,67,67),strategy(plan,70,70));
        var service=new LongevityWeightedIntegratedStrategyComparisonService(4,input->{
            var value=LongevityWeightedIntegratedStrategyComparisonService.evaluateRepresentative(input);
            var e=value.entry();var a=e.aggregate().orElseThrow();
            var pv=new BigDecimal(input.order()==1?"100.0":input.order()==2?"100.00":"90.00");
            var aggregate=new LongevityWeightedStrategyAggregate(pv,a.expectedNominalEstateAtSecondDeath(),a.minimumNominalScenarioEstate(),
                    a.maximumNominalScenarioEstate(),a.totalEvaluatedProbability(),a.originalScenarioCount(),a.actualProjectionRunCount());
            return new LongevityWeightedIntegratedStrategyComparisonService.RepresentativeResult(
                    new LongevityWeightedIntegratedStrategyComparisonEntry(e.inputOrder(),e.strategy(),Optional.of(aggregate),e.failure(),e.scenarioDetails(),
                            e.rank(),e.pvDifferenceFromBaseline(),e.nominalDifferenceFromBaseline()),value.work());
        });
        var result=service.compare(request(plan,candidates));
        assertEquals(List.of(1,1,3),result.orderedEntries().stream().map(e->e.rank().orElseThrow()).toList());
        assertEquals(List.of(1,2,3),result.rankedSuccessfulEntries().stream().map(e->e.inputOrder()).toList());
        assertEquals(new BigDecimal("100.0"),result.orderedEntries().get(0).aggregate().orElseThrow().expectedPvAfterTaxEstate());
        assertEquals(new BigDecimal("100.00"),result.orderedEntries().get(1).aggregate().orElseThrow().expectedPvAfterTaxEstate());
    }

    @Test
    void simultaneousStructuredFailuresKeepInputIdentityAndLaterSuccess() {
        var plan=Stage4TestPlans.plan();
        var second=new CountDownLatch(1);
        var candidates=List.of(strategy(plan,62,62),strategy(plan,67,67),strategy(plan,70,70));
        var service=new LongevityWeightedIntegratedStrategyComparisonService(2,input->{
            if(input.order()==1&&Runtime.getRuntime().availableProcessors()>1)await(second);
            var value=LongevityWeightedIntegratedStrategyComparisonService.evaluateRepresentative(input);
            if(input.order()==2)second.countDown();
            if(input.order()==3)return value;
            var e=value.entry();
            var failure=new IntegratedSocialSecurityStrategyEvaluationFailure(e.strategy(),"Failure at "+input.order(),
                    IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION);
            return new LongevityWeightedIntegratedStrategyComparisonService.RepresentativeResult(
                    new LongevityWeightedIntegratedStrategyComparisonEntry(e.inputOrder(),e.strategy(),Optional.empty(),Optional.of(failure),Optional.empty(),
                            OptionalInt.empty(),Optional.empty(),Optional.empty()),value.work());
        });
        var result=service.compare(request(plan,candidates));
        assertEquals(List.of(1,2),result.failures().stream().map(e->e.inputOrder()).toList());
        assertSame(candidates.get(1),result.failures().get(1).failure().orElseThrow().strategy());
        assertEquals(1,result.completedStrategyCount());
        assertEquals(3,result.rankedSuccessfulEntries().getFirst().inputOrder());
    }

    @Test
    void callbackFailureCleansUpOutstandingWorkers() {
        var plan=Stage4TestPlans.plan();var threads=ConcurrentHashMap.<Thread>newKeySet();
        var failure=new IllegalStateException("Callback failed");
        var req=new LongevityWeightedIntegratedStrategyComparisonRequest(plan,
                List.of(strategy(plan,62,62),strategy(plan,67,67),strategy(plan,70,70)),scenarios(plan),LocalDate.of(2029,7,1),new BigDecimal("0.03"),
                Optional.empty(),LongevityWeightedDetailRetentionPolicy.aggregateOnly(),p->{
                    if(p.phase()==AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON&&p.completedWork()==1)throw failure;
                },AnalysisCancellationToken.none());
        var owner=Thread.currentThread();
        var service=new LongevityWeightedIntegratedStrategyComparisonService(2,input->{
            if(Thread.currentThread()!=owner)threads.add(Thread.currentThread());
            return LongevityWeightedIntegratedStrategyComparisonService.evaluateRepresentative(input);
        });
        assertSame(failure,assertThrows(IllegalStateException.class,()->service.compare(req)));
        assertTrue(threads.stream().noneMatch(Thread::isAlive));
    }
}
