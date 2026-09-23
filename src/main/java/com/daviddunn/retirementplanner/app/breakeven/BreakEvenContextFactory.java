package com.daviddunn.retirementplanner.app.breakeven;

import com.daviddunn.retirementplanner.app.socialsecurity.HouseholdLifetimeScenarioMapper;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.util.*;

/** Reuses the weighted analyzer's joint scenarios and annual death-date mapper. No projection engine. */
public final class BreakEvenContextFactory {
    public static final String CONVENTION = "Modeled probability, not a prediction of an individual lifespan. "
            + "Uses the Longevity-Weighted analysis's independent spouse mortality and next complete birthday intervals. "
            + "As in integrated annual scenarios, each death is effective January 1 of its death year. "
            + "Alive in year Y means the modeled death year is later than Y (survival during that entire modeled year). "
            + "Years before the conditioning year are unavailable. Values do not weight the break-even amounts.";

    private final SocialSecurityMortalityTable table;
    public BreakEvenContextFactory() { this(SocialSecurityMortalityTables.ssaPeriod2022()); }
    public BreakEvenContextFactory(SocialSecurityMortalityTable table) { this.table = Objects.requireNonNull(table); }

    public BreakEvenContext create(BreakEvenAnalysisResult result, LongevitySessionSettings settings) {
        List<BreakEvenEvent> events = events(result);
        var b = result.baselineAssumptions();
        var c = result.currentAssumptions();
        if (!sameMortality(b.primary(), c.primary()) || !sameMortality(b.spouse(), c.spouse())) {
            return new BreakEvenContext(events, Map.of(), "Survival probability unavailable because the compared plans use different mortality assumptions (birth dates or mortality categories).");
        }
        if (c.primary().birthDate() == null || c.spouse().birthDate() == null
                || c.primary().mortalityCategory() == null || c.spouse().mortalityCategory() == null) {
            return new BreakEvenContext(events, Map.of(), "Survival probability unavailable: both people need birth dates and mortality categories in Person information.");
        }
        var assumptions = new AnalyzerLongevityAssumptions(category(c.primary().mortalityCategory()),
                settings.primaryAdjustment(), category(c.spouse().mortalityCategory()), settings.spouseAdjustment(),
                settings.conditioningDate(), table.metadata(), SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
        try {
            var prepared = new HouseholdLongevityScenarioFactory(table)
                    .create(c.primary().birthDate(), c.spouse().birthDate(), assumptions);
            List<Integer> years = result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS).years().stream()
                    .map(BreakEvenYearResult::year).filter(year -> year >= settings.conditioningDate().getYear()).toList();
            return new BreakEvenContext(events, survival(prepared, years, c), CONVENTION
                    + "\nConditioning date: " + settings.conditioningDate()
                    + "; primary longevity factor: " + settings.primaryAdjustment().factor().toPlainString()
                    + "; spouse longevity factor: " + settings.spouseAdjustment().factor().toPlainString()
                    + ". Table: " + table.metadata().tableId() + ".");
        } catch (IllegalArgumentException exception) {
            return new BreakEvenContext(events, Map.of(), "Survival probability unavailable: " + exception.getMessage());
        }
    }

    /** Sum the SAME joint probabilities used by weighted integrated analysis after its SAME annual mapping.
     * No new qx or household independence formula is implemented here.
     */
    public static Map<Integer, BreakEvenSurvivalPoint> survival(HouseholdLongevityScenarios prepared,
            List<Integer> years, BreakEvenPlanSummary people) {
        var mapper = new HouseholdLifetimeScenarioMapper();
        Map<Integer, BreakEvenSurvivalPoint> points = new LinkedHashMap<>();
        for (int year : years) {
            BigDecimal primary = BigDecimal.ZERO, spouse = BigDecimal.ZERO, household = BigDecimal.ZERO;
            for (var scenario : prepared.scenarios()) {
                var annual = mapper.map(scenario);
                boolean p = annual.primaryDeathYear().orElseThrow().getValue() > year;
                boolean s = annual.spouseDeathYear().orElseThrow().getValue() > year;
                if (p) primary = primary.add(scenario.jointProbability());
                if (s) spouse = spouse.add(scenario.jointProbability());
                if (p || s) household = household.add(scenario.jointProbability());
            }
            points.put(year, new BreakEvenSurvivalPoint(year, people.primary().ageIn(year), people.spouse().ageIn(year),
                    primary, spouse, household));
        }
        return Map.copyOf(points);
    }

    public static List<BreakEvenEvent> events(BreakEvenAnalysisResult result) {
        if (result.comparableYearCount() == 0) return List.of();
        List<BreakEvenEvent> events = new ArrayList<>();
        for (AccountOwnership owner : List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE)) {
            var baseline = owner == AccountOwnership.PRIMARY ? result.baselineAssumptions().primary() : result.baselineAssumptions().spouse();
            var current = owner == AccountOwnership.PRIMARY ? result.currentAssumptions().primary() : result.currentAssumptions().spouse();
            Map<Integer, List<BreakEvenEvent.Election>> grouped = new TreeMap<>();
            addElection(grouped, baseline, BreakEvenEvent.PlanScope.BASELINE, result);
            addElection(grouped, current, BreakEvenEvent.PlanScope.CURRENT, result);
            grouped.forEach((year, elections) -> events.add(new BreakEvenEvent(year, owner,
                    elections.stream().anyMatch(e -> e.plan() == BreakEvenEvent.PlanScope.CURRENT) ? current.name() : baseline.name(), elections)));
        }
        events.sort(Comparator.comparingInt(BreakEvenEvent::year).thenComparing(BreakEvenEvent::person));
        return List.copyOf(events);
    }
    private static void addElection(Map<Integer, List<BreakEvenEvent.Election>> grouped,
            BreakEvenPlanSummary.PersonSummary person, BreakEvenEvent.PlanScope scope, BreakEvenAnalysisResult result) {
        if (person.retirementClaimDate() == null || person.retirementClaimingAge() == null) return;
        int year = person.retirementClaimDate().getYear();
        if (year < result.comparisonStartYear() || year > result.comparisonEndYear()) return;
        grouped.computeIfAbsent(year, ignored -> new ArrayList<>()).add(
                new BreakEvenEvent.Election(scope, person.retirementClaimingAge(), person.retirementClaimDate()));
    }
    private static boolean sameMortality(BreakEvenPlanSummary.PersonSummary b, BreakEvenPlanSummary.PersonSummary c) {
        return Objects.equals(b.birthDate(), c.birthDate()) && b.mortalityCategory() == c.mortalityCategory();
    }
    private static SocialSecurityMortalityCategory category(MortalityCategory value) {
        return value == MortalityCategory.MALE ? SocialSecurityMortalityCategory.MALE : SocialSecurityMortalityCategory.FEMALE;
    }
}
