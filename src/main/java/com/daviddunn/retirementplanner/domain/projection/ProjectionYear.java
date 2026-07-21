
package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectionYear {

    // Timeline
    private final int projectionYear;
    private final int calendarYear;

    // Assets
    private final BigDecimal beginningInvestableAssets;
    private final BigDecimal investmentGrowth;

    // Income
    private final BigDecimal guaranteedIncome;

    // Expenses
    private final BigDecimal projectedExpenses;

    // Ending Balance
    private final BigDecimal endingInvestableAssets;

    public ProjectionYear(
            int projectionYear,
            int calendarYear,
            BigDecimal beginningInvestableAssets,
            BigDecimal investmentGrowth,
            BigDecimal guaranteedIncome,
            BigDecimal projectedExpenses,
            BigDecimal endingInvestableAssets) {

        this.projectionYear = projectionYear;
        this.calendarYear = calendarYear;

        this.beginningInvestableAssets =
                Objects.requireNonNull(
                        beginningInvestableAssets,
                        "Beginning assets are required.");

        this.investmentGrowth =
                Objects.requireNonNull(
                        investmentGrowth,
                        "Investment growth is required.");

        this.guaranteedIncome =
                Objects.requireNonNull(
                        guaranteedIncome,
                        "Total income is required.");

        this.projectedExpenses =
                Objects.requireNonNull(
                        projectedExpenses,
                        "Projected expenses are required.");

        this.endingInvestableAssets =
                Objects.requireNonNull(
                        endingInvestableAssets,
                        "Ending assets are required.");
    }

    public int getProjectionYear() {
        return projectionYear;
    }

    public int getCalendarYear() {
        return calendarYear;
    }

    public BigDecimal getBeginningInvestableAssets() {
        return beginningInvestableAssets;
    }

    public BigDecimal getInvestmentGrowth() {
        return investmentGrowth;
    }

    public BigDecimal getGuaranteedIncome() {
        return guaranteedIncome;
    }

    public BigDecimal getProjectedExpenses() {
        return projectedExpenses;
    }

    public BigDecimal getEndingInvestableAssets() {
        return endingInvestableAssets;
    }

    @Override
    public String toString() {
        return "ProjectionYear{" +
                "projectionYear=" + projectionYear +
                ", calendarYear=" + calendarYear +
                ", beginningInvestableAssets=" + beginningInvestableAssets +
                ", investmentGrowth=" + investmentGrowth +
                ", totalIncome=" + guaranteedIncome +
                ", projectedExpenses=" + projectedExpenses +
                ", endingInvestableAssets=" + endingInvestableAssets +
                '}';
    }
}

//package com.daviddunn.retirementplanner.domain.projection;
//
//import java.math.BigDecimal;
//import java.util.Objects;

//public class ProjectionYear {
//
//    private final int calendarYear;
//
//    private final BigDecimal beginningInvestableAssets;
//
//    private final BigDecimal investmentGrowth;
//
//    private final BigDecimal guaranteedIncome;
//
//    private final BigDecimal expenses;
//
//    private final BigDecimal endingInvestableAssets;
//
//    public ProjectionYear(
//            int calendarYear,
//            BigDecimal beginningInvestableAssets,
//            BigDecimal investmentGrowth,
//            BigDecimal guaranteedIncome,
//            BigDecimal expenses,
//            BigDecimal endingInvestableAssets) {

//        this.calendarYear = calendarYear;
//
//        this.beginningInvestableAssets =
//                Objects.requireNonNull(beginningInvestableAssets);
//
//        this.investmentGrowth =
//                Objects.requireNonNull(investmentGrowth);
//
//        this.guaranteedIncome =
//                Objects.requireNonNull(guaranteedIncome);
//
//        this.expenses =
//                Objects.requireNonNull(expenses);
//
//        this.endingInvestableAssets =
//                Objects.requireNonNull(endingInvestableAssets);
//    }
//
//    public int getCalendarYear() {
//        return calendarYear;
//    }
//
//    public BigDecimal getBeginningInvestableAssets() {
//        return beginningInvestableAssets;
//    }
//
//    public BigDecimal getInvestmentGrowth() {
//        return investmentGrowth;
//    }
//
//    public BigDecimal getGuaranteedIncome() {
//        return guaranteedIncome;
//    }
//
//    public BigDecimal getExpenses() {
//        return expenses;
//    }
//
//    public BigDecimal getEndingInvestableAssets() {
//        return endingInvestableAssets;
//    }
//}
