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
    Snapshot capture(RetirementPlan plan,
            SocialSecurityMortalityAdjustment primaryAdjustment,
            SocialSecurityMortalityAdjustment spouseAdjustment, LocalDate conditioningDate,
            LocalDate valuationDate, BigDecimal rate, long planRevision, long assumptionsRevision,
            CurrentStrategyBaseline baseline) {
        return capture(plan, null, null, primaryAdjustment, spouseAdjustment, conditioningDate,
                valuationDate, rate, planRevision, assumptionsRevision, baseline);
    }

    static final class Snapshot {
        private final RetirementPlan plan;
        final AnalyzerLongevityAssumptions longevity;
        final LocalDate valuationDate;
        final BigDecimal discountRate;
        final long planRevision;
        final long assumptionsRevision;
        final CurrentStrategyBaseline baseline;

        Snapshot(RetirementPlan source, AnalyzerLongevityAssumptions longevity,
                LocalDate valuationDate, BigDecimal discountRate, long planRevision, long assumptionsRevision,
                CurrentStrategyBaseline baseline) {
            this.plan = new RetirementPlanScenarioCopyService().copy(Objects.requireNonNull(source));
            this.longevity = Objects.requireNonNull(longevity);
            this.valuationDate = Objects.requireNonNull(valuationDate);
            this.discountRate = Objects.requireNonNull(discountRate);
            this.planRevision = planRevision;
            this.assumptionsRevision = assumptionsRevision;
            this.baseline = Objects.requireNonNull(baseline);
        }
    }

    Snapshot capture(RetirementPlan plan, SocialSecurityMortalityCategory primary,
            SocialSecurityMortalityCategory spouse, SocialSecurityMortalityAdjustment primaryAdjustment,
            SocialSecurityMortalityAdjustment spouseAdjustment, LocalDate date, BigDecimal rate,
            long planRevision, long assumptionsRevision) {
        return capture(plan, primary, spouse, primaryAdjustment, spouseAdjustment, date, date, rate,
                planRevision, assumptionsRevision);
    }

    Snapshot capture(RetirementPlan plan, SocialSecurityMortalityCategory primary,
            SocialSecurityMortalityCategory spouse, SocialSecurityMortalityAdjustment primaryAdjustment,
            SocialSecurityMortalityAdjustment spouseAdjustment, LocalDate conditioningDate,
            LocalDate valuationDate, BigDecimal rate, long planRevision, long assumptionsRevision) {
        return capture(plan, primary, spouse, primaryAdjustment, spouseAdjustment, conditioningDate,
                valuationDate, rate, planRevision, assumptionsRevision, CurrentStrategyBaseline.fromPlan(plan));
    }

    Snapshot capture(RetirementPlan plan, SocialSecurityMortalityCategory primary,
            SocialSecurityMortalityCategory spouse, SocialSecurityMortalityAdjustment primaryAdjustment,
            SocialSecurityMortalityAdjustment spouseAdjustment, LocalDate conditioningDate,
            LocalDate valuationDate, BigDecimal rate, long planRevision, long assumptionsRevision,
            CurrentStrategyBaseline baseline) {
        // Compatibility arguments cannot override the authoritative Person values.
        var categories = PersonMortalityCategories.from(plan.getHousehold());
        primary = categories.primary();
        spouse = categories.spouse();
        com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator.validateRate(rate);
        return new Snapshot(plan, new AnalyzerLongevityAssumptions(primary, primaryAdjustment,
                spouse, spouseAdjustment, conditioningDate, SocialSecurityMortalityTables.ssaPeriod2022().metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL),
                valuationDate, rate, planRevision, assumptionsRevision, baseline);
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
        return new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios,
                snapshot.valuationDate, snapshot.discountRate, snapshot.baseline.strategy(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), progress, cancellation);
    }
}
