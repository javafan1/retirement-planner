package com.daviddunn.retirementplanner.domain.model;

/** Tax modeling policy only. Both modes permit every application feature. */
public enum FinancialCalculationMode {
    BASIC("Basic", "NIIT Not Modeled. Net Investment Income Tax (NIIT) is not included in projected federal taxes."),
    ADVANCED("Advanced", "NIIT is calculated for supported income sources. Unsupported investment-income configurations may prevent a projection from completing.");

    private final String label;
    private final String disclosure;

    FinancialCalculationMode(String label, String disclosure) {
        this.label = label;
        this.disclosure = disclosure;
    }

    public String label() { return label; }
    public String disclosure() { return disclosure; }
    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public static FinancialCalculationMode fromWire(String value) {
        return valueOf(java.util.Objects.requireNonNull(value, "Calculation mode is required."));
    }
    @Override public String toString() { return label; }
}
