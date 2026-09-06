package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Values one already-calculated strategy without recalculating benefits. */
public final class SocialSecurityStrategyValuationCalculator {

    private final SocialSecurityPresentValueCalculator presentValueCalculator;

    public SocialSecurityStrategyValuationCalculator() {
        this(new SocialSecurityPresentValueCalculator());
    }

    SocialSecurityStrategyValuationCalculator(
            SocialSecurityPresentValueCalculator presentValueCalculator) {
        this.presentValueCalculator = Objects.requireNonNull(
                presentValueCalculator,
                "Present-value calculator is required.");
    }

    public SocialSecurityStrategyValuation calculate(
            SocialSecurityLifetimeResult strategyResult,
            LocalDate presentValueBaseDate,
            BigDecimal realDiscountRate) {

        Objects.requireNonNull(strategyResult, "Strategy result is required.");
        Objects.requireNonNull(
                presentValueBaseDate,
                "Present-value base date is required.");
        Objects.requireNonNull(
                realDiscountRate,
                "Real discount rate is required.");
        if (realDiscountRate.compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException(
                    "Real discount rate must be greater than -100%.");
        }

        YearMonth baseMonth = YearMonth.from(presentValueBaseDate);
        if (baseMonth.isAfter(YearMonth.from(
                strategyResult.request().resolvedAnalysisEndDate()))) {
            throw new IllegalArgumentException(
                    "Present-value base date cannot be after the strategy horizon.");
        }

        BigDecimal cumulativeNominal = BigDecimal.ZERO;
        BigDecimal cumulativeReal = BigDecimal.ZERO;
        BigDecimal cumulativePresentValue = BigDecimal.ZERO;
        List<SocialSecurityMonthlyValuation> monthlyValuations =
                new ArrayList<>();

        for (SocialSecurityMonthlyResult month
                : strategyResult.monthlyResults()) {
            BigDecimal nominal = month.householdBenefit();
            BigDecimal real = presentValueCalculator.toBaseDateRealAmount(
                    nominal,
                    strategyResult.request().socialSecurityColaRate(),
                    baseMonth,
                    month.month());
            BigDecimal presentValue = presentValueCalculator.presentValue(
                    real,
                    realDiscountRate,
                    baseMonth,
                    month.month());
            cumulativeNominal = cumulativeNominal.add(nominal);
            cumulativeReal = cumulativeReal.add(real);
            cumulativePresentValue = cumulativePresentValue.add(presentValue);

            monthlyValuations.add(new SocialSecurityMonthlyValuation(
                    month.month(),
                    month,
                    nominal,
                    real,
                    presentValue,
                    cumulativeNominal,
                    cumulativeReal,
                    cumulativePresentValue));
        }

        return new SocialSecurityStrategyValuation(
                strategyResult,
                monthlyValuations,
                strategyResult.householdLifetimeNominalBenefits(),
                cumulativeReal,
                cumulativePresentValue);
    }

    /**
     * Calculates the same reported totals without retaining a second monthly
     * audit list. This is intended for high-volume search aggregation.
     */
    public SocialSecurityStrategyValuationSummary calculateSummary(
            SocialSecurityLifetimeResult strategyResult,
            LocalDate presentValueBaseDate,
            BigDecimal realDiscountRate) {

        Objects.requireNonNull(strategyResult, "Strategy result is required.");
        Objects.requireNonNull(
                presentValueBaseDate,
                "Present-value base date is required.");
        Objects.requireNonNull(
                realDiscountRate,
                "Real discount rate is required.");
        if (realDiscountRate.compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException(
                    "Real discount rate must be greater than -100%.");
        }

        YearMonth baseMonth = YearMonth.from(presentValueBaseDate);
        if (baseMonth.isAfter(YearMonth.from(
                strategyResult.request().resolvedAnalysisEndDate()))) {
            throw new IllegalArgumentException(
                    "Present-value base date cannot be after the strategy horizon.");
        }

        BigDecimal real = BigDecimal.ZERO;
        BigDecimal presentValue = BigDecimal.ZERO;
        for (SocialSecurityMonthlyResult month : strategyResult.monthlyResults()) {
            BigDecimal realAmount = presentValueCalculator.toBaseDateRealAmount(
                    month.householdBenefit(),
                    strategyResult.request().socialSecurityColaRate(),
                    baseMonth,
                    month.month());
            real = real.add(realAmount);
            presentValue = presentValue.add(presentValueCalculator.presentValue(
                    realAmount,
                    realDiscountRate,
                    baseMonth,
                    month.month()));
        }

        return new SocialSecurityStrategyValuationSummary(
                strategyResult.householdLifetimeNominalBenefits(),
                real,
                presentValue,
                strategyResult.primaryTotalOwnRetirementBenefits()
                        .add(strategyResult.primaryTotalSpousalExcessBenefits())
                        .add(strategyResult.primaryTotalSurvivorBenefits()),
                strategyResult.spouseTotalOwnRetirementBenefits()
                        .add(strategyResult.spouseTotalSpousalExcessBenefits())
                        .add(strategyResult.spouseTotalSurvivorBenefits()));
    }

    /**
     * Streams the authoritative monthly engine into compact valuation totals,
     * avoiding lifetime/monthly graph retention in large search workloads.
     */
    public SocialSecurityStrategyValuationSummary calculateSummary(
            SocialSecurityStrategyRequest request,
            LocalDate presentValueBaseDate,
            BigDecimal realDiscountRate) {

        Objects.requireNonNull(request, "Strategy request is required.");
        Objects.requireNonNull(presentValueBaseDate, "Present-value base date is required.");
        Objects.requireNonNull(realDiscountRate, "Real discount rate is required.");
        if (realDiscountRate.compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException(
                    "Real discount rate must be greater than -100%.");
        }
        YearMonth baseMonth = YearMonth.from(presentValueBaseDate);
        if (baseMonth.isAfter(YearMonth.from(request.resolvedAnalysisEndDate()))) {
            throw new IllegalArgumentException(
                    "Present-value base date cannot be after the strategy horizon.");
        }

        SummaryAccumulator accumulator = new SummaryAccumulator();
        new SocialSecurityStrategyCalculator().forEachMonthlyResult(
                request,
                month -> accumulator.add(
                        month,
                        request.socialSecurityColaRate(),
                        realDiscountRate,
                        baseMonth));
        return accumulator.result();
    }

    private final class SummaryAccumulator {

        private BigDecimal nominal = BigDecimal.ZERO;
        private BigDecimal real = BigDecimal.ZERO;
        private BigDecimal presentValue = BigDecimal.ZERO;
        private BigDecimal primary = BigDecimal.ZERO;
        private BigDecimal spouse = BigDecimal.ZERO;

        private void add(
                SocialSecurityMonthlyResult month,
                BigDecimal colaRate,
                BigDecimal discountRate,
                YearMonth baseMonth) {
            BigDecimal realAmount = presentValueCalculator.toBaseDateRealAmount(
                    month.householdBenefit(), colaRate, baseMonth, month.month());
            nominal = nominal.add(month.householdBenefit());
            real = real.add(realAmount);
            presentValue = presentValue.add(presentValueCalculator.presentValue(
                    realAmount, discountRate, baseMonth, month.month()));
            primary = primary.add(month.primarySelectedBenefit());
            spouse = spouse.add(month.spouseSelectedBenefit());
        }

        private SocialSecurityStrategyValuationSummary result() {
            return new SocialSecurityStrategyValuationSummary(
                    nominal, real, presentValue, primary, spouse);
        }
    }
}
