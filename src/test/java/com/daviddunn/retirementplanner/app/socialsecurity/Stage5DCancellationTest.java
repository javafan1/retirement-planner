package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.Stage5DCoordinatorTest.await;

@Timeout(30)
class Stage5DCancellationTest {
    @ParameterizedTest
    @ValueSource(strings={"carrier","fallback"})
    void cancellationWaitsForActiveEngineCallsAndStopsAtExistingBoundaries(String boundary) throws Exception {
        var cancel=new AtomicBoolean();
        var engineCalls=new CountDownLatch(2);
        var releaseEngines=new CountDownLatch(1);
        var cancelledOwner=new CountDownLatch(1);
        var queueStarts=new AtomicInteger();
        var scope=new AtomicReference<LongevityWeightedRepresentativeEvaluationCoordinator<Object>>();
        var threads=ConcurrentHashMap.<Thread>newKeySet();
        var plan=boundary.equals("fallback")?LongevityWeightedStrategyEquivalenceTest.youngPlan():Stage4TestPlans.plan();
        var mortality=LongevityContinuationTest.mortality(plan,List.of(2031),boundary.equals("fallback")?List.of(2040,2081):List.of(2040,2050));
        try(var owner=Executors.newSingleThreadExecutor()){
            var job=owner.submit(()->{
                var coordinator=new LongevityWeightedRepresentativeEvaluationCoordinator<Object>(2,8,List.of(1,2,3,4),
                        (order,token)->{
                            var isolated=new RetirementPlanScenarioCopyService().copy(plan);
                            var request=new LongevityWeightedIntegratedStrategyRequest(isolated,Stage4TestPlans.strategy(isolated),mortality,
                                    java.time.LocalDate.of(2029,7,1),new java.math.BigDecimal("0.03"),AnalysisProgressListener.none(),token);
                            return ()->{
                                threads.add(Thread.currentThread());
                                if(order>2)queueStarts.incrementAndGet();
                                var engine=new ProjectionEngine(){
                                    int calls;
                                    @Override public Projection project(com.daviddunn.retirementplanner.domain.model.RetirementPlan p,ProjectionEvaluationContext context){
                                        calls++;
                                        if(calls==(boundary.equals("fallback")?2:1)){
                                            engineCalls.countDown();await(releaseEngines);
                                        }
                                        return super.project(p,context);
                                    }
                                };
                                var session=new LongevityContinuationSession(isolated,request,2,work->{},engine);
                                return session.snapshot(mortality.scenarios().getFirst());
                            };
                        },cancel::get);
                scope.set(coordinator);
                try(coordinator){
                    assertThrows(AnalysisCancelledException.class,()->coordinator.representative(1));
                    cancelledOwner.countDown();
                }
            });
            try{
                await(engineCalls);cancel.set(true);await(cancelledOwner);
                assertFalse(scope.get().terminated(),"Non-interruption-aware engine is still running");
            }finally{releaseEngines.countDown();}
            job.get(10,TimeUnit.SECONDS);
        }
        assertTrue(scope.get().terminated());
        assertEquals(0,queueStarts.get());
        assertTrue(threads.stream().noneMatch(Thread::isAlive));
    }
}
