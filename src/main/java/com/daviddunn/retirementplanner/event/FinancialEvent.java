package com.daviddunn.retirementplanner.event;

import java.time.LocalDate;

public abstract class FinancialEvent {

    private final LocalDate date;

    private final String description;

    protected FinancialEvent(LocalDate date,
                             String description) {

        this.date = date;
        this.description = description;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }
}
