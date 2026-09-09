package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;

import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class Stage5EWeightedAdapterTest {

    @Test
    void realWeightedServiceUsesAdmissionDispatchPhasesCancellationAndStaleGuards() throws Exception {
        for (String outcome : List.of("success", "cancel", "stale")) {
            var executor = Executors.newSingleThreadExecutor();
            var ui = new ConcurrentLinkedQueue<Runnable>();
            var boundary = new CountDownLatch(1);
            var resume = new CountDownLatch(1);
            try (var coordinator = new SocialSecurityAnalysisJobCoordinator(executor)) {
                var controller = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
                var other = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
                var plan = Stage4TestPlans.plan();
                var frozen = request(plan, List.of(strategy(plan, 67, 67), strategy(plan, 70, 70)));
                var values = new ArrayList<LongevityWeightedIntegratedStrategyComparisonResult>();
                var phases = new CopyOnWriteArrayList<AnalysisPhase>();
                assertTrue(controller.start(Mode.WEIGHTED, (progress, token) -> {
                    var adapted = new LongevityWeightedIntegratedStrategyComparisonRequest(
                            frozen.newPlanCopy(), frozen.candidates(), frozen.longevityScenarios(),
                            frozen.valuationDate(), frozen.realDiscountRate(), frozen.baselineStrategy(),
                            frozen.detailRetention(), update -> {
                                phases.add(update.phase());
                                progress.onProgress(update);
                                if (outcome.equals("cancel") && boundary.getCount() > 0) {
                                    boundary.countDown();
                                    try {
                                        assertTrue(resume.await(5, TimeUnit.SECONDS));
                                    } catch (InterruptedException exception) {
                                        throw new AssertionError(exception);
                                    }
                                }
                            }, token);
                    // Existing Stage 5D seam: recovery financial execution is limited to two workers.
                    return new LongevityWeightedIntegratedStrategyComparisonService(2).compare(adapted);
                }, values::add, error -> fail(error)));
                assertFalse(other.start(Mode.QUICK, (p1, c) -> "wrong", value -> fail(), error -> fail()));
                if (outcome.equals("cancel")) {
                    assertTrue(boundary.await(5, TimeUnit.SECONDS));
                    controller.cancel();
                    assertEquals(State.CANCELLING, controller.state());
                    assertTrue(coordinator.isBusy());
                    resume.countDown();
                }
                executor.submit(() -> { }).get(20, TimeUnit.SECONDS);
                assertTrue(values.isEmpty(), "No worker publication");
                if (outcome.equals("stale")) controller.invalidate(Change.PLAN);
                while (!ui.isEmpty()) ui.remove().run();
                assertEquals(State.IDLE, controller.state());
                assertFalse(coordinator.isBusy());
                assertEquals(outcome.equals("success") ? 1 : 0, values.size());
                if (outcome.equals("success")) {
                    assertTrue(phases.contains(AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE));
                    assertTrue(phases.contains(AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON));
                    assertEquals("Complete", controller.status());
                    Stage5DSequentialCharacterizationTest.sameResult(
                            new LongevityWeightedIntegratedStrategyComparisonService(2).compareSequential(frozen),
                            values.getFirst());
                }
                controller.close();
                other.close();
            } finally {
                resume.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }
}