package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator;
import com.daviddunn.retirementplanner.domain.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController.*;

class SocialSecurityAnalyzerJobControllerTest {
    static final class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> queue = new ArrayDeque<>();
        boolean shutdown;
        public void execute(Runnable action) {
            if (shutdown) throw new RejectedExecutionException();
            queue.add(action);
        }
        void run() { queue.remove().run(); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown(); return List.of(); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown && queue.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }

    static final class Fixture implements AutoCloseable {
        final ManualExecutor executor = new ManualExecutor();
        final Queue<Runnable> ui = new ArrayDeque<>();
        final SocialSecurityAnalysisJobCoordinator coordinator = new SocialSecurityAnalysisJobCoordinator(executor);
        final SocialSecurityAnalyzerJobController controller = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
        final List<String> results = new ArrayList<>(List.of("previous"));
        final List<Throwable> failures = new ArrayList<>();
        boolean start(Mode mode) {
            return controller.start(mode, (progress, token) -> "new", results::add, failures::add);
        }
        void drain() { while (!ui.isEmpty()) ui.remove().run(); }
        public void close() { controller.close(); coordinator.close(); }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void allModesShareAdmissionAcrossSessions(Mode mode) {
        try (var f = new Fixture()) {
            var other = new SocialSecurityAnalyzerJobController(f.coordinator, f.ui::add);
            assertTrue(f.start(mode));
            for (Mode competing : Mode.values()) {
                assertFalse(f.start(competing));
                assertFalse(other.start(competing, (p, c) -> "wrong", f.results::add, f.failures::add));
            }
            f.controller.cancel();
            assertEquals(State.CANCELLING, f.controller.state());
            assertTrue(f.coordinator.isBusy());
            assertFalse(f.start(mode));
            f.executor.run(); f.drain();
            assertFalse(f.coordinator.isBusy());
            assertEquals(State.IDLE, f.controller.state());
            assertEquals(List.of("previous"), f.results);
            assertTrue(f.start(mode));
            f.executor.run(); f.drain();
            assertEquals(List.of("previous", "new"), f.results);
            other.close();
        }
    }

    @Test
    void cancelAfterComputationBeforePublicationSuppressesSuccess() {
        try (var f = new Fixture()) {
            f.start(Mode.WEIGHTED); f.executor.run();
            assertEquals(List.of("previous"), f.results);
            f.controller.cancel(); f.drain();
            assertEquals(List.of("previous"), f.results);
            assertEquals("Analysis cancelled", f.controller.status());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void planRevisionInvalidatesEveryMode(Mode mode) {
        try (var f = new Fixture()) {
            f.start(mode); f.executor.run();
            f.controller.invalidate(Change.PLAN); f.drain();
            assertEquals(List.of("previous"), f.results);
            assertTrue(f.failures.isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void candidateRevisionOnlyInvalidatesQuick(Mode mode) {
        try (var f = new Fixture()) {
            f.start(mode); f.executor.run();
            f.controller.invalidate(Change.QUICK_CANDIDATES); f.drain();
            assertEquals(mode == Mode.QUICK ? 1 : 2, f.results.size());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void assumptionsPreserveDeterministicExhaustive(Mode mode) {
        try (var f = new Fixture()) {
            f.start(mode); f.executor.run();
            f.controller.invalidate(Change.ASSUMPTIONS); f.drain();
            assertEquals(mode == Mode.EXHAUSTIVE ? 2 : 1, f.results.size());
        }
    }

    @Test
    void closeRejectsQueuedFailureAndProgress() {
        try (var f = new Fixture()) {
            f.controller.start(Mode.WEIGHTED, (p, c) -> {
                p.onProgress(new AnalysisProgress(AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE, 1, 2));
                throw new IllegalStateException("controlled failure");
            }, value -> fail("closed result"), f.failures::add);
            f.executor.run(); f.controller.close(); f.drain();
            assertEquals(State.CLOSED, f.controller.state());
            assertTrue(f.failures.isEmpty());
            assertFalse(f.coordinator.isBusy());
        }
    }

    @Test
    void progressIsCoalescedAndOnlyPublishedThroughDispatcher() {
        try (var f = new Fixture()) {
            f.controller.start(Mode.WEIGHTED, (p, c) -> {
                for (int i = 0; i <= 5000; i++) {
                    p.onProgress(new AnalysisProgress(AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE, i, 5000));
                }
                assertNull(f.controller.progress().update());
                return "new";
            }, f.results::add, f.failures::add);
            f.executor.run();
            assertTrue(f.ui.size() < 10);
            f.drain();
            assertEquals("Complete", f.controller.status());
            assertEquals(List.of("previous", "new"), f.results);
        }
    }

    @Test
    void fatalFailurePreservesPreviousAndRestoresAdmission() {
        try (var f = new Fixture()) {
            f.controller.<String>start(Mode.QUICK, (p, c) -> { throw new IllegalArgumentException("unsupported"); },
                    f.results::add, f.failures::add);
            f.executor.run(); f.drain();
            assertEquals(1, f.failures.size());
            assertEquals(List.of("previous"), f.results);
            assertEquals(State.IDLE, f.controller.state());
            assertTrue(f.start(Mode.SOCIAL_SECURITY));
            f.executor.run(); f.drain();
        }
    }

    @Test
    void rejectedExecutorDoesNotLeakAdmission() {
        try (var f = new Fixture()) {
            f.executor.shutdown();
            f.start(Mode.QUICK); f.drain();
            assertFalse(f.coordinator.isBusy());
            assertEquals(State.IDLE, f.controller.state());
            assertEquals(1, f.failures.size());
        }
    }
    @Test
    void obsoleteCompletionAndProgressCannotClearNewerJobState() {
        try (var f = new Fixture()) {
            f.start(Mode.QUICK);
            f.executor.run();
            Runnable oldCompletion = f.ui.remove();
            oldCompletion.run();
            long previous = f.controller.generation();
            assertTrue(f.start(Mode.EXHAUSTIVE));
            assertTrue(f.controller.generation() > previous);
            oldCompletion.run();
            assertEquals(State.RUNNING, f.controller.state());
            assertTrue(f.coordinator.isBusy());
            f.executor.run();
            f.drain();
        }
    }

    @Test
    void realWorkerCleanupKeepsAdmissionAndCloseReturnsWithoutWaiting() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var ui = new ConcurrentLinkedQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var cleanup = new CountDownLatch(1);
        try (var coordinator = new SocialSecurityAnalysisJobCoordinator(executor)) {
            var controller = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
            var other = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
            controller.start(Mode.EXHAUSTIVE, (p, c) -> {
                entered.countDown();
                try {
                    assertTrue(cleanup.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    throw new AssertionError(exception);
                }
                assertTrue(c.isCancellationRequested());
                return "discard";
            }, value -> fail("closed publication"), error -> fail(error));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            controller.close();
            controller.close();
            assertEquals(State.CLOSED, controller.state());
            assertTrue(coordinator.isBusy());
            assertFalse(other.start(Mode.QUICK, (p, c) -> "wrong", value -> fail(), error -> fail()));
            cleanup.countDown();
            coordinator.close();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            while (!ui.isEmpty()) ui.remove().run();
            assertFalse(coordinator.isBusy());
            assertTrue(executor.isTerminated());
            other.close();
        } finally {
            cleanup.countDown();
            executor.shutdown();
        }
    }

    @Test
    void queuedProgressAndFailureAreRejectedAfterRevisionAndCancel() {
        for (boolean cancel : List.of(false, true)) {
            try (var f = new Fixture()) {
                f.controller.<String>start(Mode.QUICK, (p, c) -> {
                    p.onProgress(new AnalysisProgress(AnalysisPhase.QUICK_COMPARISON_CANDIDATES, 1, 2));
                    throw new IllegalStateException("obsolete");
                }, f.results::add, f.failures::add);
                f.executor.run();
                if (cancel) f.controller.cancel();
                else f.controller.invalidate(Change.PLAN);
                f.drain();
                assertNull(f.controller.progress().update());
                assertTrue(f.failures.isEmpty());
                assertEquals(List.of("previous"), f.results);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void newSocialSecurityResultOnlyInvalidatesQuickFinancialPublication(Mode mode) {
        try (var f = new Fixture()) {
            f.start(mode);
            f.executor.run();
            f.controller.invalidate(Change.SOCIAL_SECURITY_RESULT);
            f.drain();
            assertEquals(mode == Mode.QUICK ? 1 : 2, f.results.size());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void weightedSettingsDoNotInvalidateOtherModes(Mode mode) {
        try (var f = new Fixture()) {
            f.start(mode);
            f.executor.run();
            f.controller.invalidate(Change.WEIGHTED_SETTINGS);
            f.drain();
            assertEquals(mode == Mode.WEIGHTED ? 1 : 2, f.results.size());
        }
    }}
