package com.daviddunn.retirementplanner.domain.tax.state;

import java.util.Objects;

/** Bounded evaluation failure: neither insolvency nor a successful zero-tax projection. */
public final class StateTaxUnavailableException extends RuntimeException {
    private final ProductionStateTaxResult result;
    private final int taxYear;

    public StateTaxUnavailableException(ProductionStateTaxResult result, int taxYear) {
        super("State income tax unavailable for " + Objects.requireNonNull(result).jurisdiction()
                + " in " + taxYear + " (" + result.unavailableReasons() + "): "
                + String.join("; ", result.limitations()));
        if (result.calculationStatus() != StateTaxCalculationStatus.UNSUPPORTED) {
            throw new IllegalArgumentException("Unavailable failure requires an unsupported result.");
        }
        this.result = result;
        this.taxYear = taxYear;
    }

    public ProductionStateTaxResult result() { return result; }
    public int taxYear() { return taxYear; }

    public static void requireCalculated(ProductionStateTaxResult result, int taxYear) {
        if (result.calculationStatus() == StateTaxCalculationStatus.UNSUPPORTED) {
            throw new StateTaxUnavailableException(result, taxYear);
        }
    }
}
