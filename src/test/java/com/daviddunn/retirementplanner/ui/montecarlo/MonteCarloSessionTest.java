package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloSessionTest {
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> jobs = new ArrayDeque<>();

        @Override
        public void execute(Runnable work) {
            jobs.add(work);
        }

        void drain() {
            while (!jobs.isEmpty()) {
                jobs.remove().run();
            }
        }
    }

    @Test
    void initialRunningProgressCompletedStaleAndDuplicateStates() {
        var worker = new Queue();
        var ui = new Queue();
        var session = new MonteCarloSession(worker, ui::execute);
        var completed = MonteCarloUiFixtures.run("1000");
        assertEquals(MonteCarloSession.State.IDLE, session.state());
        assertTrue(session.start((progress, cancellation) -> {
            progress.onProgress(new AnalysisProgress(AnalysisPhase.MONTE_CARLO_SIMULATIONS, 1, 3));
            progress.onProgress(new AnalysisProgress(AnalysisPhase.MONTE_CARLO_SIMULATIONS, 3, 3));
            return completed;
        }));
        assertTrue(session.busy());
        assertNull(session.result());
        assertFalse(session.start((p, c) -> completed));
        worker.drain();
        assertEquals(MonteCarloSession.State.RUNNING, session.state());
        ui.jobs.remove().run();
        assertEquals(1, session.progress().completedWork());
        ui.drain();
        assertEquals(MonteCarloSession.State.COMPLETED, session.state());
        assertSame(completed, session.result());
        assertEquals(3, session.progress().completedWork());
        session.invalidate();
        assertTrue(session.stale());
        assertSame(completed, session.result());
    }

    @Test
    void cancellationAndPlanInvalidationDiscardQueuedCompletionAndRestoreIdleControls() {
        for (boolean invalidate : new boolean[]{false, true}) {
            var worker = new Queue();
            var ui = new Queue();
            var session = new MonteCarloSession(worker, ui::execute);
            session.start((progress, cancellation) -> MonteCarloUiFixtures.run("1000"));
            worker.drain();
            if (invalidate) {
                session.invalidate();
            } else {
                session.cancel();
            }
            assertEquals(MonteCarloSession.State.CANCELLING, session.state());
            ui.drain();
            assertEquals(MonteCarloSession.State.CANCELLED, session.state());
            assertFalse(session.busy());
            assertNull(session.result());
        }
    }

    @Test
    void cancellationBeforeWorkerAndCloseNeverPublish() {
        var worker = new Queue();
        var ui = new Queue();
        var session = new MonteCarloSession(worker, ui::execute);
        session.start((progress, cancellation) -> {
            fail("Cancelled work must not start");
            return null;
        });
        session.cancel();
        worker.drain();
        ui.drain();
        assertEquals(MonteCarloSession.State.CANCELLED, session.state());
        session.start((progress, cancellation) -> MonteCarloUiFixtures.run("1000"));
        session.close();
        worker.drain();
        ui.drain();
        assertEquals(MonteCarloSession.State.CLOSED, session.state());
        assertNull(session.result());
    }

    @Test
    void unexpectedErrorDiscardsPreviousResultAndRetainsOriginalDiagnostic() {
        var session = new MonteCarloSession(Runnable::run, Runnable::run);
        session.start((p, c) -> MonteCarloUiFixtures.run("1000"));
        var original = new ArithmeticException("Unexpected failure");
        session.start((p, c) -> {
            throw original;
        });
        assertEquals(MonteCarloSession.State.FAILED, session.state());
        assertFalse(session.busy());
        assertNull(session.result());
        assertSame(original, session.failure());
    }

    @Test
    void inputsValidateBoundsWithoutClampingOrBinaryFloatingPoint() {
        var settings = new MonteCarloInputs(5000, "4.50", "12.00", "417").settings();
        assertEquals(0, new BigDecimal("0.045").compareTo(settings.expectedReturn()));
        assertEquals(0, new BigDecimal("0.12").compareTo(settings.returnVolatility()));
        assertEquals(417, settings.seed());
        assertEquals(Long.MIN_VALUE, new MonteCarloInputs(1, "-99", "0", "" + Long.MIN_VALUE).settings().seed());
        for (var invalid : java.util.List.of(
                new MonteCarloInputs(0, "4", "12", "1"),
                new MonteCarloInputs(10001, "4", "12", "1"),
                new MonteCarloInputs(5000, "-100", "12", "1"),
                new MonteCarloInputs(5000, "101", "12", "1"),
                new MonteCarloInputs(5000, "NaN", "12", "1"),
                new MonteCarloInputs(5000, "4", "-1", "1"),
                new MonteCarloInputs(5000, "4", "101", "1"),
                new MonteCarloInputs(5000, "4", "Infinity", "1"),
                new MonteCarloInputs(5000, "4", "12", "9223372036854775808"))) {
            assertThrows(IllegalArgumentException.class, invalid::settings);
        }
    }
}
