package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Frozen job inputs. No reference to the caller's mutable plan is retained or exposed. */
public final class LongevityWeightedIntegratedStrategyComparisonRequest {
    private final RetirementPlan frozenPlan;
    private final List<SocialSecurityHouseholdClaimingStrategy> candidates;
    private final HouseholdLongevityScenarios longevityScenarios;
    private final LocalDate valuationDate;
    private final BigDecimal realDiscountRate;
    private final Optional<SocialSecurityHouseholdClaimingStrategy> baselineStrategy;
    private final LongevityWeightedDetailRetentionPolicy detailRetention;
    private final AnalysisProgressListener progressListener;
    private final AnalysisCancellationToken cancellationToken;

    public LongevityWeightedIntegratedStrategyComparisonRequest(RetirementPlan sourcePlan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates,
            HouseholdLongevityScenarios longevityScenarios, LocalDate valuationDate, BigDecimal realDiscountRate) {
        this(sourcePlan, candidates, longevityScenarios, valuationDate, realDiscountRate, Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), AnalysisProgressListener.none(),
                AnalysisCancellationToken.none());
    }

    public LongevityWeightedIntegratedStrategyComparisonRequest(RetirementPlan sourcePlan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates,
            HouseholdLongevityScenarios longevityScenarios, LocalDate valuationDate, BigDecimal realDiscountRate,
            Optional<SocialSecurityHouseholdClaimingStrategy> baselineStrategy,
            LongevityWeightedDetailRetentionPolicy detailRetention,
            AnalysisProgressListener progressListener, AnalysisCancellationToken cancellationToken) {
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "Ordered candidates are required."));
        this.longevityScenarios = Objects.requireNonNull(longevityScenarios, "Prepared mortality is required.");
        this.valuationDate = Objects.requireNonNull(valuationDate, "Analyzer PV base date is required.");
        EstatePresentValueCalculator.validateRate(realDiscountRate);
        this.realDiscountRate = realDiscountRate;
        this.baselineStrategy = Objects.requireNonNull(baselineStrategy, "Baseline optional is required.");
        this.detailRetention = Objects.requireNonNull(detailRetention);
        this.progressListener = Objects.requireNonNull(progressListener);
        this.cancellationToken = Objects.requireNonNull(cancellationToken);
        if (detailRetention.selectedCandidateOrders().stream().anyMatch(order -> order > this.candidates.size())) {
            throw new IllegalArgumentException("Selected detail order is outside the candidate list.");
        }
        frozenPlan = new RetirementPlanScenarioCopyService().copy(Objects.requireNonNull(sourcePlan));
        IntegratedSocialSecurityStrategyEvaluator.requireAdvancedPlan(frozenPlan);
        EstatePresentValueCalculator.validateRate(frozenPlan.getPlanningAssumptions()
                .getEconomicAssumptions().getGeneralInflationRate());
        if (!frozenPlan.getHousehold().getPrimaryPerson().getBirthDate().equals(longevityScenarios.primary().request().dateOfBirth())
                || !frozenPlan.getHousehold().getSpouse().getBirthDate().equals(longevityScenarios.spouse().request().dateOfBirth())) {
            throw new IllegalArgumentException("Prepared mortality dates of birth must match the plan household.");
        }
        baselineStrategy.ifPresent(strategy -> validateCompleteStrategy(frozenPlan, strategy));
    }

    /** Explicit opt-in to the persisted shared survivor policy; never invents the deterministic placeholder. */
    public static SocialSecurityHouseholdClaimingStrategy explicitCurrentStrategy(RetirementPlan plan) {
        Objects.requireNonNull(plan);
        if (plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge() == null) {
            throw new IllegalArgumentException("Weighted current-strategy comparison requires an explicit complete "
                    + "survivor policy or a supplied complete baseline strategy; no default survivor age is used.");
        }
        var strategy = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
        validateCompleteStrategy(plan, strategy);
        return strategy;
    }

    static void validateCompleteStrategy(RetirementPlan plan, SocialSecurityHouseholdClaimingStrategy strategy) {
        IntegratedSocialSecurityStrategyEvaluator.validateStrategy(plan, Objects.requireNonNull(strategy));
        if (strategy.primarySurvivorElection().claimDate().isBefore(SocialSecuritySurvivorBenefitCalculator
                .calculateEarliestSurvivorClaimDate(plan.getHousehold().getPrimaryPerson().getBirthDate()))
                || strategy.spouseSurvivorElection().claimDate().isBefore(SocialSecuritySurvivorBenefitCalculator
                .calculateEarliestSurvivorClaimDate(plan.getHousehold().getSpouse().getBirthDate()))) {
            throw new IllegalArgumentException("Complete survivor elections must be on or after the applicable earliest claiming date.");
        }
    }

    RetirementPlan newPlanCopy() { return new RetirementPlanScenarioCopyService().copy(frozenPlan); }
    public List<SocialSecurityHouseholdClaimingStrategy> candidates() { return candidates; }
    public HouseholdLongevityScenarios longevityScenarios() { return longevityScenarios; }
    public LocalDate valuationDate() { return valuationDate; }
    public BigDecimal realDiscountRate() { return realDiscountRate; }
    public Optional<SocialSecurityHouseholdClaimingStrategy> baselineStrategy() { return baselineStrategy; }
    public LongevityWeightedDetailRetentionPolicy detailRetention() { return detailRetention; }
    public AnalysisProgressListener progressListener() { return progressListener; }
    public AnalysisCancellationToken cancellationToken() { return cancellationToken; }
}
