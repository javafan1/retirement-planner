package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Frozen generation inputs only; retains no mutable plan or Person references.
 * Categories and birth dates can only be captured from the authoritative Persons.
 * Session adjustments are retained in the existing assumptions value type;
 * session conditioning is deliberately replaced by the plan projection start.
 * No claiming elections or financial execution eligibility are required here.
 */
public final class MonteCarloMortalityRequest {

    private final MonteCarloSettings settings;
    private final LocalDate primaryBirthDate;
    private final LocalDate spouseBirthDate;
    private final AnalyzerLongevityAssumptions longevityAssumptions;
    private final SocialSecurityMortalityTable mortalityTable;

    public MonteCarloMortalityRequest(
            RetirementPlan plan,
            MonteCarloSettings settings,
            LongevitySessionSettings sessionSettings) {
        this(plan, settings, sessionSettings, SocialSecurityMortalityTables.ssaPeriod2022());
    }

    public MonteCarloMortalityRequest(
            RetirementPlan plan,
            MonteCarloSettings settings,
            LongevitySessionSettings sessionSettings,
            SocialSecurityMortalityTable mortalityTable) {
        Objects.requireNonNull(plan, "Plan is required.");
        this.settings = Objects.requireNonNull(settings, "Monte Carlo settings are required.");
        Objects.requireNonNull(sessionSettings, "Longevity session settings are required.");
        this.mortalityTable = Objects.requireNonNull(mortalityTable, "Mortality table is required.");
        var household = Objects.requireNonNull(plan.getHousehold(), "Household is required.");
        if (household.getPrimaryPerson() == null || household.getSpouse() == null) {
            throw new IllegalArgumentException("Mortality generation requires a two-person household.");
        }
        primaryBirthDate = Objects.requireNonNull(
                household.getPrimaryPerson().getBirthDate(), "Primary birth date is required.");
        spouseBirthDate = Objects.requireNonNull(
                household.getSpouse().getBirthDate(), "Spouse birth date is required.");
        var categories = PersonMortalityCategories.from(household);
        var planning = Objects.requireNonNull(plan.getPlanningAssumptions(), "Planning assumptions are required.");
        var start = Objects.requireNonNull(planning.getProjectionStartDate(), "Projection start is required.");
        longevityAssumptions = new AnalyzerLongevityAssumptions(
                categories.primary(), sessionSettings.primaryAdjustment(),
                categories.spouse(), sessionSettings.spouseAdjustment(),
                start, mortalityTable.metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
    }

    public MonteCarloSettings settings() {
        return settings;
    }

    public LocalDate primaryBirthDate() {
        return primaryBirthDate;
    }

    public LocalDate spouseBirthDate() {
        return spouseBirthDate;
    }

    public AnalyzerLongevityAssumptions longevityAssumptions() {
        return longevityAssumptions;
    }

    public SocialSecurityMortalityTable mortalityTable() {
        return mortalityTable;
    }
}
