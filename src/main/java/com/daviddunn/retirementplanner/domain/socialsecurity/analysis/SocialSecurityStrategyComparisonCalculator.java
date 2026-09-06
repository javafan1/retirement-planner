package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares exactly two deterministic gross Social Security strategies.
 * Positive differences always mean Strategy A is ahead.
 */
public final class SocialSecurityStrategyComparisonCalculator {

    private final SocialSecurityStrategyCalculator strategyCalculator;
    private final SocialSecurityStrategyValuationCalculator valuationCalculator;
    private final SocialSecurityBreakEvenCalculator breakEvenCalculator;

    public SocialSecurityStrategyComparisonCalculator() {
        this(
                new SocialSecurityStrategyCalculator(),
                new SocialSecurityStrategyValuationCalculator(),
                new SocialSecurityBreakEvenCalculator());
    }

    SocialSecurityStrategyComparisonCalculator(
            SocialSecurityStrategyCalculator strategyCalculator,
            SocialSecurityStrategyValuationCalculator valuationCalculator,
            SocialSecurityBreakEvenCalculator breakEvenCalculator) {

        this.strategyCalculator = Objects.requireNonNull(
                strategyCalculator,
                "Strategy calculator is required.");
        this.valuationCalculator = Objects.requireNonNull(
                valuationCalculator,
                "Strategy valuation calculator is required.");
        this.breakEvenCalculator = Objects.requireNonNull(
                breakEvenCalculator,
                "Break-even calculator is required.");
    }

    public SocialSecurityStrategyComparisonResult calculate(
            SocialSecurityStrategyComparisonRequest request) {

        Objects.requireNonNull(request, "Comparison request is required.");

        SocialSecurityLifetimeResult resultA = strategyCalculator.calculate(
                request.strategyA());
        SocialSecurityLifetimeResult resultB = strategyCalculator.calculate(
                request.strategyB());
        SocialSecurityStrategyValuation valuationA =
                valuationCalculator.calculate(
                        resultA,
                        request.presentValueBaseDate(),
                        request.realDiscountRate());
        SocialSecurityStrategyValuation valuationB =
                valuationCalculator.calculate(
                        resultB,
                        request.presentValueBaseDate(),
                        request.realDiscountRate());

        List<SocialSecurityMonthlyValuation> monthsA =
                valuationA.monthlyValuations();
        List<SocialSecurityMonthlyValuation> monthsB =
                valuationB.monthlyValuations();
        if (monthsA.size() != monthsB.size()) {
            throw new IllegalArgumentException(
                    "Strategy monthly timelines must contain the same number of months.");
        }

        List<SocialSecurityMonthlyComparison> comparisons =
                new ArrayList<>();

        for (int index = 0; index < monthsA.size(); index++) {
            SocialSecurityMonthlyValuation monthA = monthsA.get(index);
            SocialSecurityMonthlyValuation monthB = monthsB.get(index);
            if (!monthA.month().equals(monthB.month())) {
                throw new IllegalArgumentException(
                        "Strategy monthly timelines are not aligned at "
                                + monthA.month() + ".");
            }

            BigDecimal nominalA = monthA.nominalBenefit();
            BigDecimal nominalB = monthB.nominalBenefit();
            BigDecimal realA = monthA.realBenefit();
            BigDecimal realB = monthB.realBenefit();
            BigDecimal presentValueA = monthA.presentValueContribution();
            BigDecimal presentValueB = monthB.presentValueContribution();

            comparisons.add(new SocialSecurityMonthlyComparison(
                    monthA.month(),
                    monthA.strategyMonth(),
                    monthB.strategyMonth(),
                    nominalA,
                    nominalB,
                    nominalA.subtract(nominalB),
                    realA,
                    realB,
                    realA.subtract(realB),
                    presentValueA,
                    presentValueB,
                    presentValueA.subtract(presentValueB),
                    monthA.cumulativeNominalBenefit(),
                    monthB.cumulativeNominalBenefit(),
                    monthA.cumulativeNominalBenefit().subtract(
                            monthB.cumulativeNominalBenefit()),
                    monthA.cumulativeRealBenefit(),
                    monthB.cumulativeRealBenefit(),
                    monthA.cumulativeRealBenefit().subtract(
                            monthB.cumulativeRealBenefit()),
                    monthA.cumulativePresentValue(),
                    monthB.cumulativePresentValue(),
                    monthA.cumulativePresentValue().subtract(
                            monthB.cumulativePresentValue())));
        }

        BigDecimal nominalDifference = valuationA
                .nominalLifetimeBenefits()
                .subtract(valuationB.nominalLifetimeBenefits());
        BigDecimal realDifference = valuationA.realLifetimeBenefits()
                .subtract(valuationB.realLifetimeBenefits());
        BigDecimal presentValueDifference = valuationA.presentValue()
                .subtract(valuationB.presentValue());

        if (!comparisons.isEmpty()
                && nominalDifference.compareTo(
                comparisons.getLast().cumulativeNominalDifference()) != 0) {
            throw new IllegalStateException(
                    "Nominal lifetime totals must reconcile to the monthly comparison.");
        }

        return new SocialSecurityStrategyComparisonResult(
                request,
                valuationA,
                valuationB,
                comparisons,
                nominalDifference,
                realDifference,
                presentValueDifference,
                winner(nominalDifference),
                winner(realDifference),
                winner(presentValueDifference),
                breakEvenCalculator.calculate(comparisons));
    }

    private StrategyComparisonWinner winner(BigDecimal difference) {
        return difference.signum() > 0
                ? StrategyComparisonWinner.STRATEGY_A
                : difference.signum() < 0
                ? StrategyComparisonWinner.STRATEGY_B
                : StrategyComparisonWinner.TIE;
    }
}
