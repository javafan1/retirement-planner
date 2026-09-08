package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityIncomeCalculator;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitSelection;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityAnnualResult;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityClaimingElection;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityStrategyCalculator;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityStrategyRequest;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projection-facing adapter for the authoritative monthly Social Security
 * strategy calculator. One lifetime calculation is indexed by calendar year
 * for a projection run. The legacy annual calculator is retained only for
 * plan shapes the advanced two-person modern-cohort engine cannot represent.
 */
public final class SocialSecurityProjectionIncomeProvider {

    private static final LocalDate MODERN_COHORT_START = LocalDate.of(1954, 1, 2);

    private final SocialSecurityStrategyCalculator strategyCalculator;
    private final HouseholdSocialSecurityIncomeCalculator legacyCalculator;

    public SocialSecurityProjectionIncomeProvider() {
        this(new SocialSecurityStrategyCalculator(),
                new HouseholdSocialSecurityIncomeCalculator());
    }

    SocialSecurityProjectionIncomeProvider(
            SocialSecurityStrategyCalculator strategyCalculator,
            HouseholdSocialSecurityIncomeCalculator legacyCalculator) {
        this.strategyCalculator = Objects.requireNonNull(strategyCalculator);
        this.legacyCalculator = Objects.requireNonNull(legacyCalculator);
    }

    public Map<Integer, HouseholdSocialSecurityResult> calculate(
            RetirementPlan plan,
            int firstCalendarYear,
            int lastCalendarYear) {
        return calculate(plan, firstCalendarYear, lastCalendarYear,
                ProjectionEvaluationContext.empty());
    }

    public Map<Integer, HouseholdSocialSecurityResult> calculate(
            RetirementPlan plan,
            int firstCalendarYear,
            int lastCalendarYear,
            ProjectionEvaluationContext evaluationContext) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Objects.requireNonNull(evaluationContext, "Projection evaluation context is required.");
        return calculate(plan, firstCalendarYear, lastCalendarYear, evaluationContext,
                EffectiveHouseholdDeathView.resolve(plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                        evaluationContext.householdLifetimeScenario()));
    }

    Map<Integer, HouseholdSocialSecurityResult> calculate(
            RetirementPlan plan,
            int firstCalendarYear,
            int lastCalendarYear,
            ProjectionEvaluationContext evaluationContext,
            EffectiveHouseholdDeathView deathView) {
        return calculate(plan, firstCalendarYear, lastCalendarYear, evaluationContext, deathView, null);
    }

    /** Job-local proof support. Ordinary projections never use this caller-owned cache. */
    public Map<Integer, HouseholdSocialSecurityResult> calculateForEquivalence(
            RetirementPlan plan, int firstCalendarYear, int lastCalendarYear,
            ProjectionEvaluationContext context,
            Map<SocialSecurityStrategyCalculator.ScheduleKey, Map<Integer, HouseholdSocialSecurityResult>> cache) {
        Objects.requireNonNull(cache);
        return calculate(plan, firstCalendarYear, lastCalendarYear, context,
                EffectiveHouseholdDeathView.resolve(plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                        context.householdLifetimeScenario()), cache);
    }

    private Map<Integer, HouseholdSocialSecurityResult> calculate(
            RetirementPlan plan, int firstCalendarYear, int lastCalendarYear,
            ProjectionEvaluationContext evaluationContext, EffectiveHouseholdDeathView deathView,
            Map<SocialSecurityStrategyCalculator.ScheduleKey, Map<Integer, HouseholdSocialSecurityResult>> cache) {
        return calculate(plan, firstCalendarYear, lastCalendarYear, evaluationContext, deathView, cache, false);
    }

    /** Runs the identical finite-request construction/validation without generating another schedule. */
    public void validateForContinuation(RetirementPlan plan, int firstCalendarYear, int lastCalendarYear,
            ProjectionEvaluationContext context) {
        Objects.requireNonNull(context);
        if (context.householdLifetimeScenario().isEmpty()) {
            throw new IllegalArgumentException("Continuation validation requires a lifetime scenario.");
        }
        calculate(plan, firstCalendarYear, lastCalendarYear, context,
                EffectiveHouseholdDeathView.resolve(plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                        context.householdLifetimeScenario()), null, true);
    }

    private Map<Integer, HouseholdSocialSecurityResult> calculate(
            RetirementPlan plan, int firstCalendarYear, int lastCalendarYear,
            ProjectionEvaluationContext evaluationContext, EffectiveHouseholdDeathView deathView,
            Map<SocialSecurityStrategyCalculator.ScheduleKey, Map<Integer, HouseholdSocialSecurityResult>> cache,
            boolean validationOnly) {
        return calculate(plan, firstCalendarYear, lastCalendarYear, evaluationContext, deathView,
                cache, validationOnly, null);
    }

    /** Validated finite inputs for exact SS prefix proofs. Does not calculate benefits or cache results. */
    public SocialSecurityStrategyCalculator.ScheduleKey validatedKeyForEquivalence(
            RetirementPlan plan, int firstCalendarYear, int lastCalendarYear,
            ProjectionEvaluationContext context) {
        Objects.requireNonNull(context);
        if (context.householdLifetimeScenario().isEmpty()) {
            throw new IllegalArgumentException("Equivalence coverage validation requires a lifetime scenario.");
        }
        SocialSecurityStrategyCalculator.ScheduleKey[] result = new SocialSecurityStrategyCalculator.ScheduleKey[1];
        calculate(plan, firstCalendarYear, lastCalendarYear, context,
                EffectiveHouseholdDeathView.resolve(plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                        context.householdLifetimeScenario()), null, true, key -> result[0] = key);
        return Objects.requireNonNull(result[0]);
    }

    private Map<Integer, HouseholdSocialSecurityResult> calculate(
            RetirementPlan plan, int firstCalendarYear, int lastCalendarYear,
            ProjectionEvaluationContext evaluationContext, EffectiveHouseholdDeathView deathView,
            Map<SocialSecurityStrategyCalculator.ScheduleKey, Map<Integer, HouseholdSocialSecurityResult>> cache,
            boolean validationOnly,
            java.util.function.Consumer<SocialSecurityStrategyCalculator.ScheduleKey> validatedKey) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Objects.requireNonNull(evaluationContext,
                "Projection evaluation context is required.");
        if (firstCalendarYear <= 0 || lastCalendarYear < firstCalendarYear) {
            throw new IllegalArgumentException("Invalid Social Security projection year range.");
        }

        Household household = plan.getHousehold();
        Person primary = household.getPrimaryPerson();
        Person spouse = household.getSpouse();
        List<SocialSecurityIncome> primarySources = sources(primary, AccountOwnership.PRIMARY);
        List<SocialSecurityIncome> spouseSources = sources(spouse, AccountOwnership.SPOUSE);

        if (!supportsAdvanced(primary, spouse, primarySources, spouseSources)) {
            if (evaluationContext.householdLifetimeScenario().isPresent()) {
                throw new IllegalArgumentException(
                        "A lifetime scenario requires the advanced two-person Social Security path; "
                                + "legacy compatibility fallback is unavailable.");
            }
            if (evaluationContext.socialSecurityStrategy().isPresent()) {
                throw new IllegalArgumentException(
                        "A complete Social Security strategy override requires the advanced "
                                + "two-person projection path; compatibility fallback is unavailable.");
            }
            return calculateLegacy(plan, firstCalendarYear, lastCalendarYear);
        }

        SocialSecurityIncome primarySource = primarySources.getFirst();
        SocialSecurityIncome spouseSource = spouseSources.getFirst();
        DeathScenarioAssumptions death = plan.getPlanningAssumptions()
                .getDeathScenarioAssumptions();
        LocalDate primaryDeath = deathView.deathDate(AccountOwnership.PRIMARY).orElse(null);
        LocalDate spouseDeath = deathView.deathDate(AccountOwnership.SPOUSE).orElse(null);
        boolean lifetimeOverride = evaluationContext.householdLifetimeScenario().isPresent();
        if (lifetimeOverride) {
            primaryDeath = withinHorizon(primaryDeath, lastCalendarYear);
            spouseDeath = withinHorizon(spouseDeath, lastCalendarYear);
        }
        SocialSecurityHouseholdClaimingStrategy override =
                evaluationContext.socialSecurityStrategy().orElse(null);
        if (override != null) {
            validateOverride(primary, spouse, override);
        }
        LocalDate primarySurvivorClaim;
        LocalDate spouseSurvivorClaim;
        if (lifetimeOverride) {
            primarySurvivorClaim = lifetimeSurvivorClaim(primary, primaryDeath, spouseDeath,
                    override == null ? null : override.primarySurvivorElection().claimDate(),
                    death.getSurvivorClaimingAge(), firstCalendarYear, lastCalendarYear);
            spouseSurvivorClaim = lifetimeSurvivorClaim(spouse, spouseDeath, primaryDeath,
                    override == null ? null : override.spouseSurvivorElection().claimDate(),
                    death.getSurvivorClaimingAge(), firstCalendarYear, lastCalendarYear);
        } else {
            primarySurvivorClaim = override != null
                    ? applicableSurvivorClaim(primaryDeath, spouseDeath,
                            override.primarySurvivorElection().claimDate(), true)
                    : survivorClaimDate(death, DeathScenario.SPOUSE_DIES, primary);
            spouseSurvivorClaim = override != null
                    ? applicableSurvivorClaim(primaryDeath, spouseDeath,
                            override.spouseSurvivorElection().claimDate(), false)
                    : survivorClaimDate(death, DeathScenario.PRIMARY_DIES, spouse);
        }
        SocialSecurityStrategyRequest request = new SocialSecurityStrategyRequest(
                LocalDate.of(firstCalendarYear, 1, 1),
                LocalDate.of(lastCalendarYear, 12, 31),
                election(primary, primarySource, AccountOwnership.PRIMARY,
                        override == null ? primarySource.getStartDate()
                                : override.primaryRetirementClaimDate()),
                election(spouse, spouseSource, AccountOwnership.SPOUSE,
                        override == null ? spouseSource.getStartDate()
                                : override.spouseRetirementClaimDate()),
                primarySurvivorClaim,
                spouseSurvivorClaim,
                primaryDeath,
                spouseDeath,
                plan.getPlanningAssumptions().getSocialSecurityColaRate());

        if (validationOnly) {
            if (validatedKey != null) validatedKey.accept(SocialSecurityStrategyCalculator.scheduleKey(request));
            return Map.of();
        }
        var key = cache == null ? null : SocialSecurityStrategyCalculator.scheduleKey(request);
        if (cache != null && cache.containsKey(key)) {
            return cache.get(key);
        }
        Map<Integer, HouseholdSocialSecurityResult> results = new LinkedHashMap<>();
        for (SocialSecurityAnnualResult annual :
                strategyCalculator.calculate(request).annualResults()) {
            results.put(annual.calendarYear(), fromAnnual(annual));
        }
        var immutable = Map.copyOf(results);
        if (cache != null) {
            cache.put(key, immutable);
        }
        return immutable;
    }

    public boolean supportsAdvancedPath(RetirementPlan plan) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Household household = plan.getHousehold();
        return supportsAdvanced(
                household.getPrimaryPerson(), household.getSpouse(),
                sources(household.getPrimaryPerson(), AccountOwnership.PRIMARY),
                sources(household.getSpouse(), AccountOwnership.SPOUSE));
    }

    private Map<Integer, HouseholdSocialSecurityResult> calculateLegacy(
            RetirementPlan plan,
            int firstCalendarYear,
            int lastCalendarYear) {
        Map<Integer, HouseholdSocialSecurityResult> results = new LinkedHashMap<>();
        for (int year = firstCalendarYear; year <= lastCalendarYear; year++) {
            results.put(year, legacyCalculator.calculate(
                    plan.getHousehold(), LocalDate.of(year, 12, 31),
                    plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                    plan.getPlanningAssumptions().getSocialSecurityColaRate()));
        }
        return Map.copyOf(results);
    }

    private static boolean supportsAdvanced(
            Person primary,
            Person spouse,
            List<SocialSecurityIncome> primarySources,
            List<SocialSecurityIncome> spouseSources) {
        return primary != null && spouse != null
                && primary.getBirthDate() != null && spouse.getBirthDate() != null
                && !primary.getBirthDate().isBefore(MODERN_COHORT_START)
                && !spouse.getBirthDate().isBefore(MODERN_COHORT_START)
                && primarySources.size() == 1 && spouseSources.size() == 1;
    }

    private static List<SocialSecurityIncome> sources(
            Person person,
            AccountOwnership ownership) {
        if (person == null) {
            return List.of();
        }
        return person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast)
                .filter(source -> source.getOwnership() == ownership)
                .toList();
    }

    private static SocialSecurityClaimingElection election(
            Person person,
            SocialSecurityIncome source,
            AccountOwnership ownership,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(
                ownership, person.getBirthDate(),
                source.getFullRetirementMonthlyBenefit(),
                source.getBenefitValuationYear(), claimDate);
    }

    private static LocalDate applicableSurvivorClaim(
            LocalDate primaryDeath,
            LocalDate spouseDeath,
            LocalDate candidateDate,
            boolean primaryIsSurvivor) {
        LocalDate otherDeath = primaryIsSurvivor ? spouseDeath : primaryDeath;
        return otherDeath == null ? null : candidateDate;
    }

    private static void validateOverride(
            Person primary,
            Person spouse,
            SocialSecurityHouseholdClaimingStrategy strategy) {
        validateRetirementElection(primary, strategy.primaryRetirementAge(),
                strategy.primaryRetirementClaimDate(), "Primary");
        validateRetirementElection(spouse, strategy.spouseRetirementAge(),
                strategy.spouseRetirementClaimDate(), "Spouse");
    }

    private static void validateRetirementElection(
            Person person,
            int age,
            LocalDate claimDate,
            String owner) {
        if (age < 62 || age > 70) {
            throw new IllegalArgumentException(
                    owner + " retirement claim age must be between 62 and 70.");
        }
        LocalDate expected = SocialSecurityRetirementDateCalculator
                .calculateRetirementClaimDate(person.getBirthDate(), age);
        if (!expected.equals(claimDate)) {
            throw new IllegalArgumentException(
                    owner + " retirement claim date does not match the supplied age and DOB.");
        }
    }

    private static LocalDate withinHorizon(LocalDate death, int lastYear) {
        return death != null && death.getYear() <= lastYear ? death : null;
    }

    private static LocalDate lifetimeSurvivorClaim(
            Person claimant,
            LocalDate claimantDeath,
            LocalDate otherDeath,
            LocalDate explicitClaim,
            Integer persistedAge,
            int firstYear,
            int lastYear) {
        LocalDate earliest = com.daviddunn.retirementplanner.domain.socialsecurity.analysis.
                SocialSecuritySurvivorBenefitCalculator.calculateEarliestSurvivorClaimDate(claimant.getBirthDate());
        LocalDate firstPossible = LocalDate.of(firstYear, 1, 1);
        if (otherDeath == null) {
            return null;
        }
        if (otherDeath.isAfter(firstPossible)) {
            firstPossible = otherDeath;
        }
        if (earliest.isAfter(firstPossible)) {
            firstPossible = earliest;
        }
        if (firstPossible.isAfter(LocalDate.of(lastYear, 12, 31))
                || (claimantDeath != null && !firstPossible.isBefore(claimantDeath))) {
            return null;
        }
        if (explicitClaim == null && persistedAge == null) {
            throw new IllegalArgumentException(
                    "Lifetime scenario survivor behavior requires a persisted survivor claiming age "
                            + "or a complete Social Security strategy override.");
        }
        LocalDate claim = explicitClaim != null
                ? explicitClaim : claimant.getBirthDate().plusYears(persistedAge);
        return claimantDeath != null && !claim.isBefore(claimantDeath) ? null : claim;
    }
    /** Persisted integer survivor age maps to that survivor's exact birthday. */
    private static LocalDate survivorClaimDate(
            DeathScenarioAssumptions assumptions,
            DeathScenario otherOwnerDeathScenario,
            Person survivor) {
        return assumptions.getDeathScenario() == otherOwnerDeathScenario
                ? survivor.getBirthDate().plusYears(
                        assumptions.getSurvivorClaimingAge())
                : null;
    }

    private static HouseholdSocialSecurityResult fromAnnual(
            SocialSecurityAnnualResult annual) {
        BigDecimal primarySurvivorCandidate = annual.primarySurvivorBenefits().signum() > 0
                ? annual.primarySelectedBenefits() : BigDecimal.ZERO;
        BigDecimal spouseSurvivorCandidate = annual.spouseSurvivorBenefits().signum() > 0
                ? annual.spouseSelectedBenefits() : BigDecimal.ZERO;
        return new HouseholdSocialSecurityResult(
                annual.primaryOwnBenefits(),
                annual.spouseOwnBenefits(),
                annual.primarySpousalExcessBenefits(),
                annual.spouseSpousalExcessBenefits(),
                primarySurvivorCandidate,
                spouseSurvivorCandidate,
                selection(annual.primarySelectedBenefits(),
                        annual.primarySurvivorBenefits()),
                selection(annual.spouseSelectedBenefits(),
                        annual.spouseSurvivorBenefits()),
                annual.householdBenefits());
    }

    private static SocialSecurityBenefitSelection selection(
            BigDecimal selected,
            BigDecimal survivorExcess) {
        if (selected.signum() == 0) {
            return SocialSecurityBenefitSelection.NONE;
        }
        return survivorExcess.signum() > 0
                ? SocialSecurityBenefitSelection.SURVIVOR
                : SocialSecurityBenefitSelection.OWN;
    }
}
