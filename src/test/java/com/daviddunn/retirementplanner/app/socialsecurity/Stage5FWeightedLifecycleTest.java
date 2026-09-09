package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController.*;

@Timeout(30)
class Stage5FWeightedLifecycleTest {
    @org.junit.jupiter.api.Test
    void cancellationAfterFinancialCompletionBeforeDispatchSuppressesPublication() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var ui = new ConcurrentLinkedQueue<Runnable>();
        try (var coordinator = new SocialSecurityAnalysisJobCoordinator(executor)) {
            var controller = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
            var plan = Stage4TestPlans.plan();
            var frozen = request(plan, List.of(strategy(plan, 67, 67)));
            var published = new ArrayList<LongevityWeightedIntegratedStrategyComparisonResult>();
            assertTrue(controller.start(Mode.WEIGHTED,
                    (progress, token) -> new LongevityWeightedIntegratedStrategyComparisonService(2).compare(frozen),
                    published::add, failure -> fail(failure)));
            executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
            assertTrue(published.isEmpty());
            controller.cancel();
            while (!ui.isEmpty()) ui.remove().run();
            assertTrue(published.isEmpty());
            assertEquals("Analysis cancelled", controller.status());
            assertFalse(coordinator.isBusy());
            controller.close();
        } finally {
            executor.shutdown(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @CsvSource({"LONGEVITY_STRATEGY_EQUIVALENCE,false", "LONGEVITY_INTEGRATED_COMPARISON,false",
            "LONGEVITY_STRATEGY_EQUIVALENCE,true", "LONGEVITY_INTEGRATED_COMPARISON,true"})
    void cancellationAndCloseAtRealServicePhasesPreservePreviousAndReleaseOnlyAfterCleanup(
            AnalysisPhase phase, boolean close) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var ui = new ConcurrentLinkedQueue<Runnable>();
        var boundary = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        try (var coordinator = new SocialSecurityAnalysisJobCoordinator(executor)) {
            var controller = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
            var plan = Stage4TestPlans.plan();
            var frozen = request(plan, List.of(strategy(plan, 67, 67), strategy(plan, 70, 70)));
            var previous = new LongevityWeightedIntegratedStrategyComparisonService(2).compare(frozen);
            var retained = new java.util.concurrent.atomic.AtomicReference<>(previous);
            assertTrue(controller.start(Mode.WEIGHTED, (progress, token) -> {
                var adapted = new LongevityWeightedIntegratedStrategyComparisonRequest(frozen.newPlanCopy(), frozen.candidates(),
                        frozen.longevityScenarios(), frozen.valuationDate(), frozen.realDiscountRate(), frozen.baselineStrategy(),
                        frozen.detailRetention(), update -> {
                            progress.onProgress(update);
                            if (update.phase() == phase && update.completedWork() > 0 && boundary.getCount() > 0) {
                                boundary.countDown();
                                try { assertTrue(resume.await(10, TimeUnit.SECONDS)); }
                                catch (InterruptedException failure) { throw new AssertionError(failure); }
                            }
                        }, token);
                return new LongevityWeightedIntegratedStrategyComparisonService(2).compare(adapted);
            }, retained::set, failure -> fail(failure)));
            assertTrue(boundary.await(10, TimeUnit.SECONDS));
            if (close) controller.close(); else controller.cancel();
            assertTrue(coordinator.isBusy());
            assertFalse(controller.start(Mode.WEIGHTED, (p, c) -> previous, retained::set, failure -> fail(failure)));
            resume.countDown();
            executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
            while (!ui.isEmpty()) ui.remove().run();
            assertFalse(coordinator.isBusy());
            assertSame(previous, retained.get());
            assertEquals(close ? State.CLOSED : State.IDLE, controller.state());
            if (!close) {
                assertEquals("Analysis cancelled", controller.status());
                assertTrue(controller.start(Mode.WEIGHTED, (p, c) -> previous, retained::set, failure -> fail(failure)));
            }
            controller.close();
        } finally {
            resume.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
