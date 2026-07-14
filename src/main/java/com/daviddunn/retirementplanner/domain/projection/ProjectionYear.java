package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.Objects;

public class ProjectionYear {

    private final int calendarYear;

    private final BigDecimal beginningAssets;

    private final BigDecimal investmentGrowth;

    private final BigDecimal guaranteedIncome;

    private final BigDecimal expenses;

    private final BigDecimal endingAssets;

    public ProjectionYear(
            int calendarYear,
            BigDecimal beginningAssets,
            BigDecimal investmentGrowth,
            BigDecimal guaranteedIncome,
            BigDecimal expenses,
            BigDecimal endingAssets) {

        this.calendarYear = calendarYear;

        this.beginningAssets =
                Objects.requireNonNull(beginningAssets);

        this.investmentGrowth =
                Objects.requireNonNull(investmentGrowth);

        this.guaranteedIncome =
                Objects.requireNonNull(guaranteedIncome);

        this.expenses =
                Objects.requireNonNull(expenses);

        this.endingAssets =
                Objects.requireNonNull(endingAssets);
    }

    public int getCalendarYear() {
        return calendarYear;
    }

    public BigDecimal getBeginningAssets() {
        return beginningAssets;
    }

    public BigDecimal getInvestmentGrowth() {
        return investmentGrowth;
    }

    public BigDecimal getGuaranteedIncome() {
        return guaranteedIncome;
    }

    public BigDecimal getExpenses() {
        return expenses;
    }

    public BigDecimal getEndingAssets() {
        return endingAssets;
    }
}

//import java.math.BigDecimal;
//
//public class ProjectionYear {
//
//    private final int year;
//
//    private final BigDecimal beginningAssets;
//
//    private final BigDecimal investmentGrowth;
//
//    private final BigDecimal guaranteedIncome;
//
//    private final BigDecimal expenses;
//
//    private final BigDecimal endingAssets;
//
//    public int getYear() {
//        return year;
//    }
//
//    public BigDecimal getBeginningAssets() {
//        return beginningAssets;
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
//    public BigDecimal getEndingAssets() {
//        return endingAssets;
//    }
//
//    public ProjectionYear(
//            int year,
//            BigDecimal beginningAssets,
//            BigDecimal investmentGrowth,
//            BigDecimal guaranteedIncome,
//            BigDecimal expenses,
//            BigDecimal endingAssets) {
//
//        this.year = year;
//        this.beginningAssets = beginningAssets;
//        this.investmentGrowth = investmentGrowth;
//        this.guaranteedIncome = guaranteedIncome;
//        this.expenses = expenses;
//        this.endingAssets = endingAssets;
//    }
//
//    // getters...
//}

//public class ProjectionYear {
//
//    private final int year;
//
//    private BigDecimal beginningNetWorth;
//    private BigDecimal endingNetWorth;
//
//    public ProjectionYear(int year) {
//        this.year = year;
//    }
//
//    public int getYear() {
//        return year;
//    }
//
//    public BigDecimal getBeginningNetWorth() {
//        return beginningNetWorth;
//    }
//
//    public void setBeginningNetWorth(BigDecimal beginningNetWorth) {
//        this.beginningNetWorth = beginningNetWorth;
//    }
//
//    public BigDecimal getEndingNetWorth() {
//        return endingNetWorth;
//    }
//
//    public void setEndingNetWorth(BigDecimal endingNetWorth) {
//        this.endingNetWorth = endingNetWorth;
//    }
//}