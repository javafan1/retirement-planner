package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitCalculator;
import com.daviddunn.retirementplanner.domain.projection.CompoundGrowthService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Calculates own-retirement entitlement month by month from immutable inputs.
 *
 * Conventions for this foundation:
 * - The analysis and end calendar months are both included.
 * - The retirement claim month is the first entitlement month.
 * - A person is treated as deceased for the entire calendar month containing
 *   their death date; exact SSA death-payment rules are deferred.
 * - COLA is applied annually by payment calendar year relative to the benefit
 *   valuation year, matching the planner's existing annual convention.
 * - Modern deemed filing uses each person's retirement claim date as their
 *   filing date for both own and spouse benefits.
 * - Normal spouse excess is available only while both spouses are alive and
 *   only after both retirement claim dates have been reached.
 * - A survivor filing date is independent from the own-retirement filing
 *   date. Survivor entitlement can begin in the modeled death month.
 * - Once both entitlements have been filed, payment is own retirement plus
 *   only the positive survivor excess; two full benefits are never stacked.
 */
public final class SocialSecurityStrategyCalculator {

    private final CompoundGrowthService compoundGrowthService =
            new CompoundGrowthService();

    public SocialSecurityLifetimeResult calculate(
            SocialSecurityStrategyRequest request) {

        Objects.requireNonNull(request, "Strategy request is required.");

        List<SocialSecurityMonthlyResult> monthlyResults =
                new ArrayList<>();
        forEachMonthlyResult(request, monthlyResults::add);

        List<SocialSecurityAnnualResult> annualResults =
                aggregateAnnualResults(monthlyResults);

        BigDecimal primaryLifetimeOwn = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::primaryOwnBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spouseLifetimeOwn = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::spouseOwnBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal primaryLifetimeSpousal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::primarySpousalExcessBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spouseLifetimeSpousal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::spouseSpousalExcessBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal primaryLifetimeSurvivor = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::primarySurvivorBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spouseLifetimeSurvivor = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::spouseSurvivorBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal householdLifetime = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::householdBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SocialSecurityLifetimeResult(
                request,
                monthlyResults,
                annualResults,
                primaryLifetimeOwn,
                primaryLifetimeSpousal,
                primaryLifetimeSurvivor,
                spouseLifetimeOwn,
                spouseLifetimeSpousal,
                spouseLifetimeSurvivor,
                householdLifetime);
    }

    /** Streams the authoritative monthly results without retaining aggregates. */
    void forEachMonthlyResult(
            SocialSecurityStrategyRequest request,
            Consumer<SocialSecurityMonthlyResult> consumer) {
        Objects.requireNonNull(request, "Strategy request is required.");
        Objects.requireNonNull(consumer, "Monthly result consumer is required.");
        PreparedElection primary = prepare(request.primaryElection());
        PreparedElection spouse = prepare(request.spouseElection());
        YearMonth firstMonth = YearMonth.from(request.analysisDate());
        YearMonth lastMonth = YearMonth.from(request.resolvedAnalysisEndDate());

        for (YearMonth month = firstMonth;
             !month.isAfter(lastMonth);
             month = month.plusMonths(1)) {

            boolean primaryAlive = isAlive(
                    month,
                    request.primaryDeathDate());
            boolean spouseAlive = isAlive(
                    month,
                    request.spouseDeathDate());

            BigDecimal primaryOwn = ownBenefit(
                    primary,
                    month,
                    primaryAlive,
                    request.socialSecurityColaRate());
            BigDecimal spouseOwn = ownBenefit(
                    spouse,
                    month,
                    spouseAlive,
                    request.socialSecurityColaRate());

            boolean bothAlive = primaryAlive && spouseAlive;

            BigDecimal primarySpousalExcess = spousalExcessBenefit(
                    primary,
                    spouse,
                    month,
                    bothAlive,
                    request.socialSecurityColaRate());
            BigDecimal spouseSpousalExcess = spousalExcessBenefit(
                    spouse,
                    primary,
                    month,
                    bothAlive,
                    request.socialSecurityColaRate());

            BigDecimal primarySurvivorTotal = survivorBenefit(
                    primary,
                    spouse,
                    month,
                    primaryAlive,
                    spouseAlive,
                    request.primarySurvivorClaimDate(),
                    request.spouseDeathDate(),
                    request.socialSecurityColaRate());
            BigDecimal spouseSurvivorTotal = survivorBenefit(
                    spouse,
                    primary,
                    month,
                    spouseAlive,
                    primaryAlive,
                    request.spouseSurvivorClaimDate(),
                    request.primaryDeathDate(),
                    request.socialSecurityColaRate());

            BigDecimal primarySurvivorExcess = primarySurvivorTotal
                    .subtract(primaryOwn)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal spouseSurvivorExcess = spouseSurvivorTotal
                    .subtract(spouseOwn)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal primarySelected = primaryOwn.add(
                    primarySpousalExcess).add(
                    primarySurvivorExcess);
            BigDecimal spouseSelected = spouseOwn.add(
                    spouseSpousalExcess).add(
                    spouseSurvivorExcess);

            consumer.accept(new SocialSecurityMonthlyResult(
                            month,
                            lifeState(primaryAlive, spouseAlive),
                            primaryAlive,
                            spouseAlive,
                            primaryOwn,
                            primarySpousalExcess,
                            primarySurvivorExcess,
                            activeBenefitTypes(
                                    primaryOwn,
                                    primarySpousalExcess,
                                    primarySurvivorExcess),
                            primarySelected,
                            spouseOwn,
                            spouseSpousalExcess,
                            spouseSurvivorExcess,
                            activeBenefitTypes(
                                    spouseOwn,
                                    spouseSpousalExcess,
                                    spouseSurvivorExcess),
                            spouseSelected,
                            primarySelected.add(spouseSelected)));
        }
    }

    private PreparedElection prepare(
            SocialSecurityClaimingElection election) {

        BigDecimal claimingAdjustedMonthlyBenefit =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        election.fullRetirementMonthlyBenefit(),
                        election.birthDate(),
                        election.retirementClaimDate());

        return new PreparedElection(
                election,
                claimingAdjustedMonthlyBenefit);
    }

    private BigDecimal ownBenefit(
            PreparedElection prepared,
            YearMonth paymentMonth,
            boolean alive,
            BigDecimal colaRate) {

        if (!alive || paymentMonth.isBefore(
                YearMonth.from(
                        prepared.election().retirementClaimDate()))) {
            return BigDecimal.ZERO;
        }

        int colaYears = Math.max(
                paymentMonth.getYear()
                        - prepared.election().benefitValuationYear(),
                0);

        return compoundGrowthService.project(
                        prepared.claimingAdjustedMonthlyBenefit(),
                        colaRate,
                        colaYears)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal spousalExcessBenefit(
            PreparedElection claimant,
            PreparedElection worker,
            YearMonth paymentMonth,
            boolean bothAlive,
            BigDecimal colaRate) {

        YearMonth claimantClaimMonth = YearMonth.from(
                claimant.election().retirementClaimDate());
        YearMonth workerClaimMonth = YearMonth.from(
                worker.election().retirementClaimDate());

        if (!bothAlive
                || paymentMonth.isBefore(claimantClaimMonth)
                || paymentMonth.isBefore(workerClaimMonth)) {
            return BigDecimal.ZERO;
        }

        YearMonth spouseEntitlementMonth =
                claimantClaimMonth.isAfter(workerClaimMonth)
                        ? claimantClaimMonth
                        : workerClaimMonth;

        BigDecimal claimantFraBenefit = projectedFraBenefit(
                claimant,
                paymentMonth,
                colaRate);
        BigDecimal workerFraBenefit = projectedFraBenefit(
                worker,
                paymentMonth,
                colaRate);

        return SocialSecuritySpousalBenefitCalculator
                .calculateMonthlyExcessBenefit(
                        claimantFraBenefit,
                        workerFraBenefit,
                        claimant.election().birthDate(),
                        spouseEntitlementMonth);
    }

    private BigDecimal projectedFraBenefit(
            PreparedElection prepared,
            YearMonth paymentMonth,
            BigDecimal colaRate) {

        int colaYears = Math.max(
                paymentMonth.getYear()
                        - prepared.election().benefitValuationYear(),
                0);

        return compoundGrowthService.project(
                        prepared.election()
                                .fullRetirementMonthlyBenefit(),
                        colaRate,
                        colaYears)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal survivorBenefit(
            PreparedElection survivor,
            PreparedElection deceasedWorker,
            YearMonth paymentMonth,
            boolean survivorAlive,
            boolean workerAlive,
            LocalDate intendedSurvivorClaimDate,
            LocalDate workerDeathDate,
            BigDecimal colaRate) {

        if (!survivorAlive
                || workerAlive
                || intendedSurvivorClaimDate == null
                || workerDeathDate == null) {
            return BigDecimal.ZERO;
        }

        YearMonth workerDeathMonth = YearMonth.from(workerDeathDate);
        YearMonth intendedClaimMonth = YearMonth.from(
                intendedSurvivorClaimDate);
        YearMonth entitlementMonth = intendedClaimMonth.isAfter(
                workerDeathMonth)
                ? intendedClaimMonth
                : workerDeathMonth;

        if (paymentMonth.isBefore(entitlementMonth)) {
            return BigDecimal.ZERO;
        }

        BigDecimal projectedWorkerFraBenefit = projectedFraBenefit(
                deceasedWorker,
                paymentMonth,
                colaRate);
        SocialSecuritySurvivorBenefitCalculator.WorkerBasis workerBasis =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateDeceasedWorkerBasis(
                                projectedWorkerFraBenefit,
                                deceasedWorker.election().birthDate(),
                                deceasedWorker.election()
                                        .retirementClaimDate(),
                                workerDeathMonth);

        return SocialSecuritySurvivorBenefitCalculator.calculate(
                        workerBasis,
                        survivor.election().birthDate(),
                        entitlementMonth)
                .survivorBenefit();
    }

    private boolean isAlive(
            YearMonth month,
            LocalDate deathDate) {

        return deathDate == null
                || month.isBefore(YearMonth.from(deathDate));
    }

    private SocialSecurityHouseholdLifeState lifeState(
            boolean primaryAlive,
            boolean spouseAlive) {

        if (primaryAlive && spouseAlive) {
            return SocialSecurityHouseholdLifeState.BOTH_ALIVE;
        }
        if (primaryAlive) {
            return SocialSecurityHouseholdLifeState.PRIMARY_ONLY;
        }
        if (spouseAlive) {
            return SocialSecurityHouseholdLifeState.SPOUSE_ONLY;
        }
        return SocialSecurityHouseholdLifeState.NEITHER_ALIVE;
    }

    private Set<SocialSecurityBenefitType> activeBenefitTypes(
            BigDecimal ownBenefit,
            BigDecimal spousalExcessBenefit,
            BigDecimal survivorBenefit) {

        java.util.EnumSet<SocialSecurityBenefitType> result =
                java.util.EnumSet.noneOf(
                        SocialSecurityBenefitType.class);
        if (ownBenefit.signum() > 0) {
            result.add(SocialSecurityBenefitType.OWN_RETIREMENT);
        }
        if (spousalExcessBenefit.signum() > 0) {
            result.add(SocialSecurityBenefitType.SPOUSAL);
        }
        if (survivorBenefit.signum() > 0) {
            result.add(SocialSecurityBenefitType.SURVIVOR);
        }
        return result.isEmpty()
                ? Set.of(SocialSecurityBenefitType.NONE)
                : Set.copyOf(result);
    }

    private List<SocialSecurityAnnualResult> aggregateAnnualResults(
            List<SocialSecurityMonthlyResult> monthlyResults) {

        Map<Integer, List<SocialSecurityMonthlyResult>> byYear =
                new LinkedHashMap<>();

        for (SocialSecurityMonthlyResult result : monthlyResults) {
            byYear.computeIfAbsent(
                            result.month().getYear(),
                            ignored -> new ArrayList<>())
                    .add(result);
        }

        return byYear.entrySet().stream()
                .map(entry -> SocialSecurityAnnualResult
                        .fromMonthlyResults(
                                entry.getKey(),
                                entry.getValue()))
                .toList();
    }

    private record PreparedElection(
            SocialSecurityClaimingElection election,
            BigDecimal claimingAdjustedMonthlyBenefit) {
    }
}
