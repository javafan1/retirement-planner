package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Read-only translation from current plan data to an immutable analyzer request. */
public final class SocialSecurityStrategyAnalysisRequestFactory {

    private static final List<Integer> RETIREMENT_AGES =
            List.of(62, 63, 64, 65, 66, 67, 68, 69, 70);

    public SocialSecurityStrategyAnalysisContext create(
            RetirementPlan plan,
            SocialSecurityMortalityCategory primaryCategory,
            SocialSecurityMortalityCategory spouseCategory,
            BigDecimal realDiscountRate,
            LocalDate presentValueBaseDate) {
        return create(plan, primaryCategory, spouseCategory,
                SocialSecurityMortalityAdjustment.standard(),
                SocialSecurityMortalityAdjustment.standard(),
                realDiscountRate, presentValueBaseDate);
    }

    public SocialSecurityStrategyAnalysisContext create(
            RetirementPlan plan,
            SocialSecurityMortalityCategory primaryCategory,
            SocialSecurityMortalityCategory spouseCategory,
            SocialSecurityMortalityAdjustment primaryAdjustment,
            SocialSecurityMortalityAdjustment spouseAdjustment,
            BigDecimal realDiscountRate,
            LocalDate presentValueBaseDate) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        if (primaryCategory == null) {
            throw new IllegalArgumentException("Primary mortality category is required.");
        }
        if (spouseCategory == null) {
            throw new IllegalArgumentException("Spouse mortality category is required.");
        }
        Objects.requireNonNull(primaryAdjustment, "Primary mortality adjustment is required.");
        Objects.requireNonNull(spouseAdjustment, "Spouse mortality adjustment is required.");
        Objects.requireNonNull(realDiscountRate, "Real discount rate is required.");
        Objects.requireNonNull(presentValueBaseDate, "PV base date is required.");

        Person primary = requirePerson(
                plan.getHousehold().getPrimaryPerson(), "Primary");
        Person spouse = requirePerson(plan.getHousehold().getSpouse(), "Spouse");
        SocialSecurityIncome primarySource = source(primary, AccountOwnership.PRIMARY, "Primary");
        SocialSecurityIncome spouseSource = source(spouse, AccountOwnership.SPOUSE, "Spouse");
        requireBenefit(primarySource, "Primary");
        requireBenefit(spouseSource, "Spouse");

        SocialSecurityMortalityTable table = SocialSecurityMortalityTables.ssaPeriod2022();
        SocialSecurityMortalityDistributionProvider provider =
                new SocialSecurityMortalityDistributionProvider(table);
        SocialSecurityMortalityDistribution primaryMortality = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        primary.getBirthDate(), presentValueBaseDate, primaryCategory,
                        primaryAdjustment))
                .distribution();
        SocialSecurityMortalityDistribution spouseMortality = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        spouse.getBirthDate(), presentValueBaseDate, spouseCategory,
                        spouseAdjustment))
                .distribution();
        LocalDate primaryDeath = SocialSecurityDeathDateCalculator.calculateDeathDate(
                primary.getBirthDate(), primaryMortality.deathAges().getLast());
        LocalDate spouseDeath = SocialSecurityDeathDateCalculator.calculateDeathDate(
                spouse.getBirthDate(), spouseMortality.deathAges().getLast());

        SocialSecurityStrategyRequest base = new SocialSecurityStrategyRequest(
                presentValueBaseDate,
                null,
                election(primarySource, primary, AccountOwnership.PRIMARY),
                election(spouseSource, spouse, AccountOwnership.SPOUSE),
                SocialSecuritySurvivorBenefitCalculator.calculateEarliestSurvivorClaimDate(
                        primary.getBirthDate()),
                SocialSecuritySurvivorBenefitCalculator.calculateEarliestSurvivorClaimDate(
                        spouse.getBirthDate()),
                primaryDeath,
                spouseDeath,
                plan.getPlanningAssumptions().getSocialSecurityColaRate());
        SocialSecurityMortalityWeightedClaimingGridRequest grid =
                new SocialSecurityMortalityWeightedClaimingGridRequest(
                        base,
                        RETIREMENT_AGES,
                        RETIREMENT_AGES,
                        primaryMortality,
                        spouseMortality,
                        presentValueBaseDate,
                        presentValueBaseDate,
                        realDiscountRate);
        return new SocialSecurityStrategyAnalysisContext(
                new SocialSecuritySurvivorClaimingOptimizationRequest(grid),
                primary.getFullName(),
                spouse.getFullName(),
                primarySource.getFullRetirementMonthlyBenefit(),
                spouseSource.getFullRetirementMonthlyBenefit(),
                primarySource.getBenefitValuationYear(),
                spouseSource.getBenefitValuationYear(),
                primarySource.getStartDate(),
                spouseSource.getStartDate(),
                primaryCategory,
                spouseCategory,
                primaryAdjustment,
                spouseAdjustment,
                table.metadata());
    }

    private static Person requirePerson(Person person, String owner) {
        if (person == null) {
            throw new IllegalArgumentException(
                    "The household Social Security strategy analyzer currently requires both a primary person and spouse.");
        }
        if (person.getBirthDate() == null) {
            throw new IllegalArgumentException(owner + " birth date is required.");
        }
        return person;
    }

    private static SocialSecurityIncome source(
            Person person,
            AccountOwnership ownership,
            String owner) {
        List<SocialSecurityIncome> sources = person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast)
                .filter(source -> source.getOwnership() == ownership)
                .toList();
        if (sources.isEmpty()) {
            throw new IllegalArgumentException(owner + " Social Security source is required.");
        }
        if (sources.size() > 1) {
            throw new IllegalArgumentException(owner + " must have exactly one Social Security source.");
        }
        return sources.getFirst();
    }

    private static void requireBenefit(SocialSecurityIncome source, String owner) {
        if (source.getFullRetirementMonthlyBenefit() == null
                || source.getFullRetirementMonthlyBenefit().signum() <= 0) {
            throw new IllegalArgumentException(owner + " Social Security FRA benefit is required.");
        }
        if (source.getBenefitValuationYear() <= 0) {
            throw new IllegalArgumentException(owner + " benefit valuation year is required.");
        }
    }

    private static SocialSecurityClaimingElection election(
            SocialSecurityIncome source,
            Person person,
            AccountOwnership ownership) {
        return new SocialSecurityClaimingElection(
                ownership,
                person.getBirthDate(),
                source.getFullRetirementMonthlyBenefit(),
                source.getBenefitValuationYear(),
                source.getStartDate());
    }
}
