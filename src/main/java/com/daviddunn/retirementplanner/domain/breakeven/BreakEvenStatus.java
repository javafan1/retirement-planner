package com.daviddunn.retirementplanner.domain.breakeven;

public enum BreakEvenStatus {
    BREAK_EVEN_REACHED,
    NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD,
    CURRENT_ALREADY_AHEAD,
    IDENTICAL,
    CROSSOVER_NOT_SUSTAINED,
    NO_COMPARABLE_YEARS
}
