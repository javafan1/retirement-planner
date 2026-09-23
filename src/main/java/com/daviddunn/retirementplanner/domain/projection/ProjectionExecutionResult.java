package com.daviddunn.retirementplanner.domain.projection;

import java.util.List;
import java.util.Objects;

/**
 * Immutable completed-year list; no fabricated failing year. Account metadata retains
 * ordinary ProjectionYear reference semantics. Monte Carlo retains only scalar values.
 */
public sealed interface ProjectionExecutionResult {

    enum Status {
        COMPLETED,
        INSUFFICIENT_FUNDS
    }

    Status status();

    List<ProjectionYear> completedYears();

    record Completed(List<ProjectionYear> completedYears) implements ProjectionExecutionResult {

        public Completed {
            completedYears = List.copyOf(completedYears);
        }

        @Override
        public Status status() {
            return Status.COMPLETED;
        }

        public Projection projection() {
            var result = new Projection();
            completedYears.forEach(result::addYear);
            return result;
        }
    }

    record InsufficientFunds(
            List<ProjectionYear> completedYears,
            FundingFailure fundingFailure) implements ProjectionExecutionResult {

        public InsufficientFunds {
            completedYears = List.copyOf(completedYears);
            Objects.requireNonNull(fundingFailure);
        }

        @Override
        public Status status() {
            return Status.INSUFFICIENT_FUNDS;
        }
    }
}
