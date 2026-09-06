package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.income.FullRetirementAge;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.income.SurvivorFullRetirementAgeCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Ordinary aged-widow(er) rules for the isolated monthly analyzer.
 * Special disability, child-in-care, divorced-survivor, family-maximum,
 * earnings-test, and SSA internal dime-rounding rules are outside its scope.
 */
public final class SocialSecuritySurvivorBenefitCalculator {

    private static final BigDecimal MAXIMUM_EARLY_REDUCTION =
            new BigDecimal("0.285");
    private static final BigDecimal RIB_LIMIT_FLOOR =
            new BigDecimal("0.825");

    private SocialSecuritySurvivorBenefitCalculator() {
    }

    public static LocalDate calculateEarliestSurvivorClaimDate(
            LocalDate birthDate) {

        return SocialSecurityRetirementDateCalculator
                .effectiveBirthDate(birthDate)
                .plusYears(60);
    }

    public static LocalDate calculateSurvivorFullRetirementDate(
            LocalDate birthDate) {

        LocalDate effectiveBirthDate =
                SocialSecurityRetirementDateCalculator
                        .effectiveBirthDate(birthDate);
        FullRetirementAge survivorFra =
                SurvivorFullRetirementAgeCalculator.determine(
                        effectiveBirthDate);

        return effectiveBirthDate
                .plusYears(survivorFra.years())
                .plusMonths(survivorFra.months());
    }

    public static BigDecimal calculateReductionFactor(
            LocalDate survivorBirthDate,
            YearMonth survivorEntitlementMonth) {

        Objects.requireNonNull(
                survivorBirthDate,
                "Survivor birth date is required.");
        Objects.requireNonNull(
                survivorEntitlementMonth,
                "Survivor entitlement month is required.");

        YearMonth age60Month = YearMonth.from(
                calculateEarliestSurvivorClaimDate(
                        survivorBirthDate));
        YearMonth survivorFraMonth = YearMonth.from(
                calculateSurvivorFullRetirementDate(
                        survivorBirthDate));

        if (survivorEntitlementMonth.isBefore(age60Month)) {
            throw new IllegalArgumentException(
                    "Survivor entitlement month cannot be before age 60.");
        }
        if (!survivorEntitlementMonth.isBefore(survivorFraMonth)) {
            return BigDecimal.ONE;
        }

        long possibleEarlyMonths = ChronoUnit.MONTHS.between(
                age60Month,
                survivorFraMonth);
        long monthsEarly = ChronoUnit.MONTHS.between(
                survivorEntitlementMonth,
                survivorFraMonth);

        BigDecimal reduction = MAXIMUM_EARLY_REDUCTION
                .multiply(BigDecimal.valueOf(monthsEarly))
                .divide(
                        BigDecimal.valueOf(possibleEarlyMonths),
                        12,
                        RoundingMode.HALF_UP);

        return BigDecimal.ONE.subtract(reduction);
    }

    /**
     * Determines the worker-history basis before the survivor's own
     * claiming-age reduction. Amounts must be expressed in the same payment
     * month's dollars.
     */
    public static WorkerBasis calculateDeceasedWorkerBasis(
            BigDecimal projectedWorkerFraBenefit,
            LocalDate workerBirthDate,
            LocalDate workerRetirementClaimDate,
            YearMonth workerDeathMonth) {

        requireNonNegative(
                projectedWorkerFraBenefit,
                "Projected worker FRA benefit");
        Objects.requireNonNull(
                workerBirthDate,
                "Worker birth date is required.");
        Objects.requireNonNull(
                workerRetirementClaimDate,
                "Worker retirement claim date is required.");
        Objects.requireNonNull(
                workerDeathMonth,
                "Worker death month is required.");

        YearMonth workerClaimMonth = YearMonth.from(
                workerRetirementClaimDate);
        YearMonth workerFraMonth = YearMonth.from(
                SocialSecurityRetirementDateCalculator
                        .calculateFullRetirementDate(
                                workerBirthDate));

        if (workerClaimMonth.isBefore(workerDeathMonth)) {
            BigDecimal claimedFactor =
                    com.daviddunn.retirementplanner.domain.income
                            .SocialSecurityBenefitCalculator
                            .calculateRetirementBenefitFactor(
                                    workerBirthDate,
                                    workerRetirementClaimDate);

            if (workerClaimMonth.isBefore(workerFraMonth)) {
                BigDecimal widowLimit = projectedWorkerFraBenefit
                        .multiply(claimedFactor.max(RIB_LIMIT_FLOOR))
                        .setScale(2, RoundingMode.HALF_UP);
                return new WorkerBasis(
                        projectedWorkerFraBenefit,
                        widowLimit);
            }

            BigDecimal delayedBasis = projectedWorkerFraBenefit
                    .multiply(claimedFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            return new WorkerBasis(delayedBasis, delayedBasis);
        }

        YearMonth age70Month = YearMonth.from(
                SocialSecurityRetirementDateCalculator
                        .calculateLatestRetirementClaimDate(
                                workerBirthDate));
        YearMonth creditEndMonth = workerDeathMonth.isBefore(age70Month)
                ? workerDeathMonth
                : age70Month;
        int accruedDelayedMonths = Math.max(
                Math.toIntExact(
                        ChronoUnit.MONTHS.between(
                                workerFraMonth,
                                creditEndMonth)),
                0);

        BigDecimal delayedFactor = BigDecimal.ONE.add(
                BigDecimal.valueOf(accruedDelayedMonths)
                        .multiply(BigDecimal.valueOf(2))
                        .divide(
                                BigDecimal.valueOf(300),
                                10,
                                RoundingMode.HALF_UP));

        BigDecimal accruedBasis = projectedWorkerFraBenefit
                .multiply(delayedFactor)
                .setScale(2, RoundingMode.HALF_UP);
        return new WorkerBasis(accruedBasis, accruedBasis);
    }

    public static Calculation calculate(
            WorkerBasis deceasedWorkerBasis,
            LocalDate survivorBirthDate,
            YearMonth survivorEntitlementMonth) {

        Objects.requireNonNull(
                deceasedWorkerBasis,
                "Deceased worker basis is required.");
        BigDecimal factor = calculateReductionFactor(
                survivorBirthDate,
                survivorEntitlementMonth);
        BigDecimal amount = deceasedWorkerBasis.unreducedBasis()
                .multiply(factor)
                .min(deceasedWorkerBasis.maximumPayableBenefit())
                .setScale(2, RoundingMode.HALF_UP);

        return new Calculation(
                deceasedWorkerBasis.unreducedBasis(),
                factor,
                amount);
    }

    private static void requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(amount, description + " is required.");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }
    }

    public record Calculation(
            BigDecimal deceasedWorkerBasis,
            BigDecimal survivorReductionFactor,
            BigDecimal survivorBenefit) {
    }

    /**
     * The unreduced base and any RIB-LIM ceiling. They differ only when the
     * deceased worker claimed a reduced retirement benefit.
     */
    public record WorkerBasis(
            BigDecimal unreducedBasis,
            BigDecimal maximumPayableBenefit) {

        public WorkerBasis {
            requireNonNegative(unreducedBasis, "Unreduced survivor basis");
            requireNonNegative(
                    maximumPayableBenefit,
                    "Maximum payable survivor benefit");
        }
    }
}
