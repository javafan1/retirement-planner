package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class Stage5DCoordinatorTest {
    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "Controlled task did not reach its barrier"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    @Test
    void policyCapsConfigurationProcessorsAndWork() {
        assertEquals(4, LongevityWeightedRepresentativeEvaluationCoordinator.DEFAULT_LIMIT);
        assertEquals(8, LongevityWeightedRepresentativeEvaluationCoordinator.MAXIMUM_LIMIT);
        assertEquals(8, LongevityWeightedRepresentativeEvaluationCoordinator.effectiveWorkers(100,32,81));
        assertEquals(2, LongevityWeightedRepresentativeEvaluationCoordinator.effectiveWorkers(4,2,81));
        assertEquals(1, LongevityWeightedRepresentativeEvaluationCoordinator.effectiveWorkers(4,8,1));
        assertEquals(0, LongevityWeightedRepresentativeEvaluationCoordinator.effectiveWorkers(4,8,0));
        assertThrows(IllegalArgumentException.class, () -> LongevityWeightedRepresentativeEvaluationCoordinator.effectiveWorkers(0,8,81));
    }

    @Test
    void zeroAndOneWorkerAvoidExecutorAndKeepTokenOnOwner() {
        var owner = Thread.currentThread();
        try (var empty = new LongevityWeightedRepresentativeEvaluationCoordinator<Integer>(4,8,List.of(),
                (order,token) -> { fail("No task for empty work"); return null; }, AnalysisCancellationToken.none())) {
            assertTrue(empty.terminated());
        }
        try (var single = new LongevityWeightedRepresentativeEvaluationCoordinator<Integer>(1,8,List.of(1,3),
                (order,token) -> () -> { assertSame(owner,Thread.currentThread()); token.throwIfCancellationRequested(); return order; },
                () -> { assertSame(owner,Thread.currentThread()); return false; })) {
            assertEquals(1,single.representative(1));
            assertEquals(2,single.retry(2));
            assertEquals(3,single.representative(3));
            assertTrue(single.terminated());
        }
    }

    @Test
    void reverseCompletionStillReturnsOriginalPositionsAndReleasesThreads() {
        var finished = Collections.synchronizedList(new ArrayList<Integer>());
        var threads = ConcurrentHashMap.<Thread>newKeySet();
        var gates = new CountDownLatch[]{new CountDownLatch(1),new CountDownLatch(1),new CountDownLatch(1)};
        var owner = Thread.currentThread();
        var coordinator = new LongevityWeightedRepresentativeEvaluationCoordinator<Integer>(4,8,List.of(1,2,3,4),
                (order,token) -> {
                    assertSame(owner,Thread.currentThread());
                    return () -> {
                        threads.add(Thread.currentThread());
                        if (order < 4) await(gates[order-1]);
                        finished.add(order);
                        if (order > 1) gates[order-2].countDown();
                        return order;
                    };
                }, () -> { assertSame(owner,Thread.currentThread()); return false; });
        try (coordinator) {
            for (int order=1;order<=4;order++) assertEquals(order,coordinator.representative(order));
        }
        assertEquals(List.of(4,3,2,1),finished);
        assertTrue(coordinator.terminated());
        assertTrue(threads.stream().noneMatch(Thread::isAlive));
    }

    @Test
    void cancellationBoundsCopiesSkipsQueueAndJoinsActiveTasks() throws Exception {
        var cancel = new AtomicBoolean();
        var prepared = new AtomicInteger();
        var started = new AtomicInteger();
        var active = new CountDownLatch(2);
        var preparedWindow = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var cancellationCaught = new CountDownLatch(1);
        var scope = new AtomicReference<LongevityWeightedRepresentativeEvaluationCoordinator<Integer>>();
        try (var owner = Executors.newSingleThreadExecutor()) {
            var job = owner.submit(() -> {
                var coordinator = new LongevityWeightedRepresentativeEvaluationCoordinator<Integer>(2,8,List.of(1,2,3,4,5,6,7),
                        (order,token) -> {
                            prepared.incrementAndGet(); preparedWindow.countDown();
                            return () -> { started.incrementAndGet(); active.countDown(); await(release); token.throwIfCancellationRequested(); return order; };
                        }, cancel::get);
                scope.set(coordinator);
                try (coordinator) {
                    assertThrows(AnalysisCancelledException.class, () -> coordinator.representative(1));
                    cancellationCaught.countDown();
                }
            });
            try {
                await(active); await(preparedWindow);
                assertEquals(4,prepared.get());
                cancel.set(true);
                await(cancellationCaught);
            } finally { release.countDown(); }
            job.get(10,TimeUnit.SECONDS);
        }
        assertEquals(2,started.get());
        assertEquals(4,prepared.get());
        assertTrue(scope.get().terminated());
    }

    @Test
    void cancellationBeforeSubmissionPreparesNothing() {
        var prepared = new AtomicInteger();
        var coordinator = new LongevityWeightedRepresentativeEvaluationCoordinator<Integer>(4,8,List.of(1,2),
                (order,token) -> { prepared.incrementAndGet(); return () -> order; }, () -> true);
        try (coordinator) { assertThrows(AnalysisCancelledException.class, () -> coordinator.representative(1)); }
        assertEquals(0,prepared.get());
        assertTrue(coordinator.terminated());
    }

    @Test
    void workerRuntimeFailureAndErrorAreNotCancellation() {
        for (boolean error : List.of(false,true)) {
            Throwable expected = error ? new AssertionError("fatal") : new IllegalStateException("worker failure");
            var coordinator = new LongevityWeightedRepresentativeEvaluationCoordinator<Integer>(2,8,List.of(1,2),
                    (order,token) -> () -> { if (expected instanceof Error e) throw e; throw (RuntimeException)expected; }, AnalysisCancellationToken.none());
            try (coordinator) {
                assertSame(expected,assertThrows(expected.getClass(), () -> coordinator.representative(1)));
            }
            assertTrue(coordinator.terminated());
        }
    }

    @Test
    void preparationFailureAlsoClosesPreviouslyStartedWork() {
        var preparedFailure = new IllegalArgumentException("copy failure");
        var coordinator = new LongevityWeightedRepresentativeEvaluationCoordinator<Integer>(2,8,List.of(1,2,3),
                (order,token) -> { if(order==2)throw preparedFailure; return () -> order; }, AnalysisCancellationToken.none());
        try(coordinator) { assertSame(preparedFailure,assertThrows(IllegalArgumentException.class,()->coordinator.representative(1))); }
        assertTrue(coordinator.terminated());
    }
}
