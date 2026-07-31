package com.daviddunn.retirementplanner.domain.income;

public record FullRetirementAge(
        int years,
        int months) {

    public FullRetirementAge {

        if (years < 66 || years > 67) {
            throw new IllegalArgumentException(
                    "Years must be 66 or 67.");
        }

        if (months < 0 || months > 11) {
            throw new IllegalArgumentException(
                    "Months must be between 0 and 11.");
        }
    }

    @Override
    public String toString() {

        return months == 0
                ? years + " years"
                : years + " years " + months + " months";
    }
}