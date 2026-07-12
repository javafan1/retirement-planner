package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;

public class ProjectionYear {

    private final int year;

    private BigDecimal beginningNetWorth;
    private BigDecimal endingNetWorth;

    public ProjectionYear(int year) {
        this.year = year;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getBeginningNetWorth() {
        return beginningNetWorth;
    }

    public void setBeginningNetWorth(BigDecimal beginningNetWorth) {
        this.beginningNetWorth = beginningNetWorth;
    }

    public BigDecimal getEndingNetWorth() {
        return endingNetWorth;
    }

    public void setEndingNetWorth(BigDecimal endingNetWorth) {
        this.endingNetWorth = endingNetWorth;
    }
}