package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Captures on FX; mortality preparation and complete enumeration run under analyzer admission. */
final class LongevityWeightedAnalysisRequestFactory {
    static final class Snapshot {
        private final RetirementPlan plan;
        final AnalyzerLongevityAssumptions longevity;
        final LocalDate valuationDate;
        final BigDecimal discountRate;
        final long planRevision;
        final long assumptionsRevision;

        Snapshot(RetirementPlan source, AnalyzerLongevityAssumptions longevity,
                LocalDate valuationDate, BigDecimal discountRate, long planRevision, long assumptionsRevision) {
            this.plan = new RetirementPlanScenarioCopyService().copy(Objects.requireNonNull(source));
            this.longevity = Objects.requireNonNull(longevity);
            this.valuationDate = Objects.requireNonNull(valuationDate);
            this.discountRate = Objects.requireNonNull(discountRate);
            this.planRevision = planRevision;
            this.assumptionsRevision = assumptionsRevision;
        }
    }

    Snapshot capture(RetirementPlan plan, SocialSecurityMortalityCategory primary,
            SocialSecurityMortalityCategory spouse, SocialSecurityMortalityAdjustment primaryAdjustment,
            SocialSecurityMortalityAdjustment spouseAdjustment, LocalDate date, BigDecimal rate,
            long planRevision, long assumptionsRevision) {
        if (primary == null || spouse == null) {
            throw new IllegalArgumentException("Choose both mortality categories.");
        }
        com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator.validateRate(rate);
        return new Snapshot(plan, new AnalyzerLongevityAssumptions(primary, primaryAdjustment,
                spouse, spouseAdjustment, date, SocialSecurityMortalityTables.ssaPeriod2022().metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL),
                date, rate, planRevision, assumptionsRevision);
    }

    LongevityWeightedIntegratedStrategyComparisonRequest create(Snapshot snapshot,
            AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        var plan = snapshot.plan;
        var universe = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        var scenarios = new HouseholdLongevityScenarioFactory(SocialSecurityMortalityTables.ssaPeriod2022())
                .create(primary.getBirthDate(), spouse.getBirthDate(), snapshot.longevity);
        List<SocialSecurityHouseholdClaimingStrategy> candidates = new ArrayList<>();
        for (int primaryAge : universe.primaryRetirementAges()) {
            for (int spouseAge : universe.spouseRetirementAges()) {
                for (var primarySurvivor : universe.primarySurvivorCandidates()) {
                    for (var spouseSurvivor : universe.spouseSurvivorCandidates()) {
                        cancellation.throwIfCancellationRequested();
                        candidates.add(new SocialSecurityHouseholdClaimingStrategy(primaryAge, spouseAge,
                                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(primary.getBirthDate(), primaryAge),
                                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(spouse.getBirthDate(), spouseAge),
                                primarySurvivor, spouseSurvivor));
                    }
                }
            }
        }
        Optional<SocialSecurityHouseholdClaimingStrategy> baseline =
                plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge() == null
                        ? Optional.empty()
                        : Optional.of(LongevityWeightedIntegratedStrategyComparisonRequest.explicitCurrentStrategy(plan));
        return new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios,
                snapshot.valuationDate, snapshot.discountRate, baseline,
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), progress, cancellation);
    }
}
