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
        LocalDate primaryDeath = deathDate(death, DeathScenario.PRIMARY_DIES);
        LocalDate spouseDeath = deathDate(death, DeathScenario.SPOUSE_DIES);
        SocialSecurityHouseholdClaimingStrategy override =
                evaluationContext.socialSecurityStrategy().orElse(null);
        if (override != null) {
            validateOverride(primary, spouse, override);
        }
        LocalDate primarySurvivorClaim = override != null
                ? applicableSurvivorClaim(primaryDeath, spouseDeath,
                        override.primarySurvivorElection().claimDate(), true)
                : survivorClaimDate(death, DeathScenario.SPOUSE_DIES, primary);
        LocalDate spouseSurvivorClaim = override != null
                ? applicableSurvivorClaim(primaryDeath, spouseDeath,
                        override.spouseSurvivorElection().claimDate(), false)
                : survivorClaimDate(death, DeathScenario.PRIMARY_DIES, spouse);

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

        Map<Integer, HouseholdSocialSecurityResult> results = new LinkedHashMap<>();
        for (SocialSecurityAnnualResult annual :
                strategyCalculator.calculate(request).annualResults()) {
            results.put(annual.calendarYear(), fromAnnual(annual));
        }
        return Map.copyOf(results);
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

    /** Existing production death-year semantics: deceased from January 1. */
    private static LocalDate deathDate(
            DeathScenarioAssumptions assumptions,
            DeathScenario ownerDeathScenario) {
        return assumptions.getDeathScenario() == ownerDeathScenario
                ? LocalDate.of(assumptions.getDeathYear(), 1, 1)
                : null;
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
