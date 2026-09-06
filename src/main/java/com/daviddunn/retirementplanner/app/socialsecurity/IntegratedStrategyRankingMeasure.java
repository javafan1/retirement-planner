package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;

import java.math.BigDecimal;

/** Explicit deterministic metric used to order complete integrated strategies. */
public enum IntegratedStrategyRankingMeasure {
    AFTER_TAX_ESTATE;

    BigDecimal value(ProjectionMetrics metrics) {
        return metrics.afterTaxEstate();
    }
}
