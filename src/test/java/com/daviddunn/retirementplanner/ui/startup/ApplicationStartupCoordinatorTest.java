package com.daviddunn.retirementplanner.ui.startup;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationStartupCoordinatorTest {
    @Test void workRunsOffCallerAndCompletionIsExplicitlyDispatchedExactlyOnce() throws Exception {
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var caller = Thread.currentThread();
        var runs = new AtomicInteger();
        var accepted = new AtomicInteger();
        try (var coordinator = new ApplicationStartupCoordinator(callbacks::add)) {
            coordinator.start(() -> { assertNotSame(caller, Thread.currentThread()); runs.incrementAndGet(); return 7; },
                    accepted::set, error -> fail(error));
            var callback = callbacks.poll(5, TimeUnit.SECONDS); assertNotNull(callback);
            assertEquals(0, accepted.get()); callback.run();
            assertEquals(7, accepted.get()); assertEquals(1, runs.get()); assertTrue(callbacks.isEmpty());
            assertThrows(IllegalStateException.class, () -> coordinator.start(() -> 1, ignored -> {}, ignored -> {}));
        }
    }
    @Test void failureNeverPublishesSuccess() throws Exception {
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var failed = new AtomicReference<Throwable>();
        try (var coordinator = new ApplicationStartupCoordinator(callbacks::add)) {
            coordinator.start(() -> { throw new IllegalStateException("fixture"); }, ignored -> fail(), failed::set);
            var callback = callbacks.poll(5, TimeUnit.SECONDS); assertNotNull(callback); callback.run();
            assertInstanceOf(IllegalStateException.class, failed.get());
        }
    }
    @Test void queuedCompletionCannotOpenWindowAfterShutdown() throws Exception {
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var called = new AtomicBoolean();
        var coordinator = new ApplicationStartupCoordinator(callbacks::add);
        coordinator.start(() -> 1, ignored -> called.set(true), ignored -> called.set(true));
        var callback = callbacks.poll(5, TimeUnit.SECONDS); assertNotNull(callback);
        coordinator.close(); callback.run(); assertFalse(called.get());
    }
    @Test void lateUncooperativePreparationCannotPublishAfterClose() throws Exception {
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1); var done = new CountDownLatch(1);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var coordinator = new ApplicationStartupCoordinator(callbacks::add);
        coordinator.start(() -> {
            entered.countDown();
            while (finish.getCount() != 0) { try { finish.await(); } catch (InterruptedException ignored) { } }
            done.countDown(); return 1;
        }, ignored -> fail(), ignored -> fail());
        assertTrue(entered.await(5, TimeUnit.SECONDS)); coordinator.close(); finish.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNull(callbacks.poll(200, TimeUnit.MILLISECONDS));
        assertThrows(IllegalStateException.class, () -> coordinator.start(() -> 1, ignored -> {}, ignored -> {}));
    }
}
