package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.Stage5DSequentialCharacterizationTest.sameResult;

/** Opt-in complete-result oracle and warmed benchmark of the actual production coordinator. */
class Stage5DRepresentativeBenchmarkTest {
    static long gcTime(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}
    static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}

    @Test
    @EnabledIfSystemProperty(named="stage5d.fullBenchmark",matches="true")
    void fullUniverseEqualsSequentialAtEveryWorkerLimit() throws Exception {
        var plan=Stage4TestPlans.plan();var source=Stage4TestPlans.json(plan);
        var candidates=LongevityWeightedEquivalenceBenchmarkTest.universe(plan);
        var scenarios=LongevityWeightedEquivalenceBenchmarkTest.productionScenarios(plan);
        var financial=new AtomicBoolean();var peak=new AtomicLong();var gcStart=new AtomicLong();var countStart=new AtomicLong();var cpuStart=new AtomicLong();
        var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        var heap=ManagementFactory.getMemoryMXBean();
        var request=new LongevityWeightedIntegratedStrategyComparisonRequest(plan,candidates,scenarios,LocalDate.of(2029,7,1),new BigDecimal("0.03"),
                Optional.empty(),LongevityWeightedDetailRetentionPolicy.aggregateOnly(),p->{
                    if(p.phase()==AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON&&p.completedWork()==0){
                        peak.set(heap.getHeapMemoryUsage().getUsed());gcStart.set(gcTime());countStart.set(gcCount());cpuStart.set(os.getProcessCpuTime());financial.set(true);
                    }
                },AnalysisCancellationToken.none());
        System.out.println("Stage5D processors="+Runtime.getRuntime().availableProcessors()+" maxHeap="+Runtime.getRuntime().maxMemory());
        System.out.println("Stage5D sequential WARMUP");
        var reference=new LongevityWeightedIntegratedStrategyComparisonService().compareSequential(request);
        assertEquals(81,reference.equivalencePlan().orElseThrow().equivalenceGroupCount());
        assertEquals(9396,reference.work().projectionEngineRuns());
        for(int workers:new int[]{0,1,2,4,6,8,8,6,4,2,1,0}){
            financial.set(false);
            System.gc();
            var monitor=Executors.newSingleThreadScheduledExecutor();
            monitor.scheduleAtFixedRate(()->{if(financial.get())peak.accumulateAndGet(heap.getHeapMemoryUsage().getUsed(),Math::max);},0,20,TimeUnit.MILLISECONDS);
            LongevityWeightedIntegratedStrategyComparisonResult result;
            try{
                var service=new LongevityWeightedIntegratedStrategyComparisonService(Math.max(1,workers));
                result=workers==0?service.compareSequential(request):service.compare(request);
            }finally{financial.set(false);monitor.shutdown();assertTrue(monitor.awaitTermination(10,TimeUnit.SECONDS));}
            long gc=gcTime()-gcStart.get();long count=gcCount()-countStart.get();double cpu=(os.getProcessCpuTime()-cpuStart.get())/1e9;
            sameResult(reference,result);
            assertEquals(5184,result.completedStrategyCount());assertEquals(0,result.failedStrategyCount());
            for(int i=0;i<candidates.size();i++)assertSame(candidates.get(i),result.orderedEntries().get(i).strategy());
            double seconds=result.elapsedTime().minus(result.equivalencePlan().orElseThrow().elapsedTime()).toNanos()/1e9;
            System.out.println("Stage5D MEASURE workers="+workers+" financialSeconds="+seconds+" planningSeconds="+result.equivalencePlan().orElseThrow().elapsedTime().toNanos()/1e9
                    +" totalSeconds="+result.elapsedTime().toNanos()/1e9+" cpuSeconds="+cpu+" averageCores="+cpu/seconds+" gcMs="+gc+" gcCollections="+count
                    +" sampledPeakMiB="+peak.get()/1048576.0+" engineRuns="+result.work().projectionEngineRuns()+" successes="+result.completedStrategyCount()+" failures="+result.failedStrategyCount()+" completeEquality=true");
        }
        assertEquals(source,Stage4TestPlans.json(plan));
    }
}
