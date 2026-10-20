package com.daviddunn.retirementplanner.app.socialsecurity.results;

public enum AnalysisKind {
    COUPLE_SOCIAL_SECURITY(Shape.COUPLE),
    COUPLE_QUICK(Shape.COUPLE),
    COUPLE_EXHAUSTIVE(Shape.COUPLE),
    COUPLE_WEIGHTED(Shape.COUPLE),
    INDIVIDUAL_DETERMINISTIC(Shape.SINGLE),
    INDIVIDUAL_SOCIAL_SECURITY(Shape.SINGLE),
    INDIVIDUAL_WEIGHTED(Shape.SINGLE);

    public enum Shape { SINGLE, COUPLE }
    private final Shape shape;
    AnalysisKind(Shape shape) { this.shape = shape; }
    public Shape shape() { return shape; }
}
