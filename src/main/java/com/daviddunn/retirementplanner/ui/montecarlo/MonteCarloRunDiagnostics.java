package com.daviddunn.retirementplanner.ui.montecarlo;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Bounded run-stage metadata only: never messages, paths, inputs, identifiers or Throwable logging. */
final class MonteCarloRunDiagnostics {
    static final Logger LOG = Logger.getLogger(MonteCarloRunDiagnostics.class.getName());

    enum Event { VIEW_READY, CLICK_ENTERED, VALIDATION_PASSED, ADMISSION_REJECTED, PREFLIGHT_FAILED,
        WORKER_SUBMITTED, SESSION_CHANGED, FEEDBACK_LAYOUT, VIEW_CLOSED }
    enum Category { NONE, VALIDATION, UNSUPPORTED_CONFIGURATION, PREPARATION, WORKER, EXECUTOR }

    record State(MonteCarloMode mode, MonteCarloSession.State before, MonteCarloSession.State after,
            boolean runEnabled, boolean validationPassed, boolean workerSubmitted,
            boolean statusUpdated, boolean statusVisible) { }

    static String format(Event event, State state, Category category, Throwable failure) {
        return "MonteCarloRun event=" + event + " mode=" + state.mode()
                + " before=" + state.before() + " after=" + state.after()
                + " runEnabled=" + state.runEnabled() + " validationPassed=" + state.validationPassed()
                + " workerSubmitted=" + state.workerSubmitted() + " statusUpdated=" + state.statusUpdated()
                + " statusVisible=" + state.statusVisible() + " category=" + category
                + " exception=" + (failure == null ? "NONE" : failure.getClass().getName());
    }

    static void record(Event event, State state, Category category, Throwable failure) {
        try {
            LOG.log(failure == null ? Level.INFO : Level.WARNING, format(event, state, category, failure));
        } catch (RuntimeException unavailable) {
            // A logging handler must never prevent a run or hide its original failure.
        }
    }

    private MonteCarloRunDiagnostics() { }
}
