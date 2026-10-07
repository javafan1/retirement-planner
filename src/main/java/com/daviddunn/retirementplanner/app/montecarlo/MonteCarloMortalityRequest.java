package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Frozen generation inputs and optional shared survivor execution election; no mutable plan or Person references.
 * Categories and birth dates can only be captured from the authoritative Persons.
 * Session adjustments are retained in the existing assumptions value type;
 * session conditioning is deliberately replaced by the plan projection start.
 * Generation-only couple requests may omit the survivor election; couple execution requires it.
 * Individual requests contain only primary mortality inputs and never contain a survivor election.
 * The election never participates in mortality or market sampling.
 */
public final class MonteCarloMortalityRequest {

    private final MonteCarloSettings settings;
    private final LocalDate primaryBirthDate;
    private final Optional<LocalDate> spouseBirthDate;
    private final Optional<AnalyzerLongevityAssumptions> longevityAssumptions;
    private final SocialSecurityMortalityDistributionRequest primary;
    private final SocialSecurityMortalityTable mortalityTable;
    private final Optional<Integer> survivorClaimingAge;

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
        // Compatibility callers freeze an available plan election; absence remains valid for generation only.
        this(plan, settings, sessionSettings, mortalityTable, Optional.ofNullable(plan.getPlanningAssumptions()
                .getDeathScenarioAssumptions().getSurvivorClaimingAge()));
    }

    /** Explicit analysis-session election; independent of the saved plan death scenario. */
    public MonteCarloMortalityRequest(RetirementPlan plan, MonteCarloSettings settings,
                                     LongevitySessionSettings sessionSettings, int survivorClaimingAge) {
        this(plan, settings, sessionSettings, SocialSecurityMortalityTables.ssaPeriod2022(), Optional.of(survivorClaimingAge));
    }

    public MonteCarloMortalityRequest(RetirementPlan plan, MonteCarloSettings settings,
                                     LongevitySessionSettings sessionSettings, SocialSecurityMortalityTable mortalityTable,
                                     int survivorClaimingAge) {
        this(plan, settings, sessionSettings, mortalityTable, Optional.of(survivorClaimingAge));
    }

    private MonteCarloMortalityRequest(RetirementPlan plan, MonteCarloSettings settings,
                                      LongevitySessionSettings sessionSettings, SocialSecurityMortalityTable mortalityTable,
                                      Optional<Integer> survivorClaimingAge) {
        Objects.requireNonNull(plan, "Plan is required.");
        this.settings = Objects.requireNonNull(settings, "Monte Carlo settings are required.");
        Objects.requireNonNull(sessionSettings, "Longevity session settings are required.");
        this.mortalityTable = Objects.requireNonNull(mortalityTable, "Mortality table is required.");
        this.survivorClaimingAge = plan.getHousehold().hasSpouse() ? Objects.requireNonNull(survivorClaimingAge) : Optional.empty();
        survivorClaimingAge.ifPresent(com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy::validateClaimingAge);
        var household = Objects.requireNonNull(plan.getHousehold(), "Household is required.");
        primaryBirthDate = Objects.requireNonNull(
                household.getPrimaryPerson().getBirthDate(), "Primary birth date is required.");
        spouseBirthDate = household.spouse().map(person -> Objects.requireNonNull(
                person.getBirthDate(), "Spouse birth date is required."));
        this.survivorClaimingAge.ifPresent(age -> {
            try {
                primaryBirthDate.plusYears(age);
                spouseBirthDate.orElseThrow().plusYears(age);
            } catch (java.time.DateTimeException invalid) {
                throw new IllegalArgumentException("Survivor Social Security claiming age must produce valid dates for both people.", invalid);
            }
        });
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        var person = household.getPrimaryPerson();
        if (person.getMortalityCategory() == null) throw new IllegalArgumentException("Primary mortality category is required.");
        var category = switch (person.getMortalityCategory()) {
            case MALE -> SocialSecurityMortalityCategory.MALE;
            case FEMALE -> SocialSecurityMortalityCategory.FEMALE;
        };
        primary = new SocialSecurityMortalityDistributionRequest(primaryBirthDate, start, category, sessionSettings.primaryAdjustment());
        if (household.hasSpouse()) {
            var categories = PersonMortalityCategories.from(household);
            longevityAssumptions = Optional.of(new AnalyzerLongevityAssumptions(
                    categories.primary(), sessionSettings.primaryAdjustment(),
                    categories.spouse(), sessionSettings.spouseAdjustment(), start, mortalityTable.metadata(),
                    SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL));
        } else {
            longevityAssumptions = Optional.empty();
        }
    }

    /** Individual inputs contain no spouse adjustment or survivor election. */
    public static MonteCarloMortalityRequest individual(RetirementPlan plan, MonteCarloSettings settings,
            SocialSecurityMortalityAdjustment primaryAdjustment) {
        return new MonteCarloMortalityRequest(plan, settings, primaryAdjustment, SocialSecurityMortalityTables.ssaPeriod2022());
    }

    private MonteCarloMortalityRequest(RetirementPlan plan, MonteCarloSettings settings,
            SocialSecurityMortalityAdjustment primaryAdjustment, SocialSecurityMortalityTable table) {
        if (plan.getHousehold().hasSpouse()) throw new IllegalArgumentException("Individual request requires one person.");
        this.settings = Objects.requireNonNull(settings);
        this.mortalityTable = Objects.requireNonNull(table);
        var person = plan.getHousehold().getPrimaryPerson();
        primaryBirthDate = Objects.requireNonNull(person.getBirthDate());
        if (person.getMortalityCategory() == null) throw new IllegalArgumentException("Primary mortality category is required.");
        var category = switch (person.getMortalityCategory()) {
            case MALE -> SocialSecurityMortalityCategory.MALE;
            case FEMALE -> SocialSecurityMortalityCategory.FEMALE;
        };
        primary = new SocialSecurityMortalityDistributionRequest(primaryBirthDate,
                plan.getPlanningAssumptions().getProjectionStartDate(), category, primaryAdjustment);
        spouseBirthDate = Optional.empty();
        longevityAssumptions = Optional.empty();
        survivorClaimingAge = Optional.empty();
    }

    public boolean hasSpouse() { return spouseBirthDate.isPresent(); }
    public SocialSecurityMortalityDistributionRequest primary() { return primary; }
    public LocalDate conditioningDate() { return primary.mortalityBaseDate(); }
    public MonteCarloSettings settings() {
        return settings;
    }

    public Optional<Integer> survivorClaimingAge() {
        return survivorClaimingAge;
    }

    public LocalDate primaryBirthDate() {
        return primaryBirthDate;
    }

    public LocalDate spouseBirthDate() {
        return spouseBirthDate.orElseThrow(() -> new IllegalStateException("Individual request has no spouse."));
    }

    public AnalyzerLongevityAssumptions longevityAssumptions() {
        return longevityAssumptions.orElseThrow(() -> new IllegalStateException("Individual request has no couple assumptions."));
    }

    public SocialSecurityMortalityTable mortalityTable() {
        return mortalityTable;
    }
}
