package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** One complete strategy. valuationDate is the analyzer PV base date, not mortality conditioning. */
public record LongevityWeightedIntegratedStrategyRequest(
        RetirementPlan sourcePlan,
        SocialSecurityHouseholdClaimingStrategy strategy,
        HouseholdLongevityScenarios longevityScenarios,
        LocalDate valuationDate,
        BigDecimal realDiscountRate,
        AnalysisProgressListener progressListener,
        AnalysisCancellationToken cancellationToken) {
    public LongevityWeightedIntegratedStrategyRequest {
        Objects.requireNonNull(sourcePlan, "Source plan is required.");
        Objects.requireNonNull(strategy, "Complete strategy is required.");
        Objects.requireNonNull(longevityScenarios, "Prepared mortality scenarios are required.");
        Objects.requireNonNull(valuationDate, "Analyzer present-value base date is required.");
        EstatePresentValueCalculator.validateRate(realDiscountRate);
        Objects.requireNonNull(progressListener, "Progress listener is required.");
        Objects.requireNonNull(cancellationToken, "Cancellation token is required.");
    }

    public LongevityWeightedIntegratedStrategyRequest(RetirementPlan sourcePlan,
            SocialSecurityHouseholdClaimingStrategy strategy, HouseholdLongevityScenarios longevityScenarios,
            LocalDate valuationDate, BigDecimal realDiscountRate) {
        this(sourcePlan, strategy, longevityScenarios, valuationDate, realDiscountRate,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }
}
