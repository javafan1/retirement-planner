package com.daviddunn.retirementplanner.app.socialsecurity.results;

public enum AnalysisKind {
    COUPLE_SOCIAL_SECURITY(Shape.COUPLE),
    COUPLE_QUICK(Shape.COUPLE),
    COUPLE_EXHAUSTIVE(Shape.COUPLE),
    COUPLE_WEIGHTED(Shape.COUPLE),
    INDIVIDUAL_DETERMINISTIC(Shape.SINGLE),
    INDIVIDUAL_SOCIAL_SECURITY(Shape.SINGLE),
    INDIVIDUAL_WEIGHTED(Shape.SINGLE),
    COUPLE_MONTE_CARLO_FIXED(Shape.COUPLE, true),
    COUPLE_MONTE_CARLO_LONGEVITY(Shape.COUPLE, true),
    COUPLE_MONTE_CARLO_PAIRED_FIXED(Shape.COUPLE, true),
    COUPLE_MONTE_CARLO_PAIRED_LONGEVITY(Shape.COUPLE, true),
    INDIVIDUAL_MONTE_CARLO_FIXED(Shape.SINGLE, true),
    INDIVIDUAL_MONTE_CARLO_LONGEVITY(Shape.SINGLE, true),
    INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED(Shape.SINGLE, true),
    INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY(Shape.SINGLE, true);

    public enum Shape { SINGLE, COUPLE }
    private final Shape shape;
    private final boolean monteCarlo;
    AnalysisKind(Shape shape) { this(shape, false); }
    AnalysisKind(Shape shape, boolean monteCarlo) { this.shape = shape; this.monteCarlo = monteCarlo; }
    public Shape shape() { return shape; }
    public boolean isMonteCarlo() { return monteCarlo; }
}
