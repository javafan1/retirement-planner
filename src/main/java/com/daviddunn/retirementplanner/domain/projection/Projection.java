package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Projection {

    private final List<ProjectionYear> years =
            new ArrayList<>();

    public void addYear(ProjectionYear year) {
        years.add(Objects.requireNonNull(year, "year"));
    }

    public List<ProjectionYear> getYears() {
        return List.copyOf(years);
    }

    public ProjectionYear getYearAt(int index) {
        return years.get(index);
    }

    public ProjectionYear getFirstYear() {
        return years.getFirst();
    }

    public ProjectionYear getLastYear() {
        return years.getLast();
    }

    public int getStartYear() {
        return getFirstYear().getCalendarYear();
    }

    public int getEndYear() {
        return getLastYear().getCalendarYear();
    }

    public int getYearsProjected() {
        return years.size();
    }

    public BigDecimal getFinalInvestableAssets() {
        return getLastYear().getEndingInvestableAssets();
    }

    public BigDecimal getHighestInvestableAssets() {

        BigDecimal highest = BigDecimal.ZERO;

        for (ProjectionYear year : years) {

            if (year.getEndingInvestableAssets()
                    .compareTo(highest) > 0) {

                highest = year.getEndingInvestableAssets();
            }
        }

        return highest;
    }

    public BigDecimal getLowestInvestableAssets() {

        if (years.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal lowest =
                getFirstYear().getEndingInvestableAssets();

        for (ProjectionYear year : years) {

            if (year.getEndingInvestableAssets()
                    .compareTo(lowest) < 0) {

                lowest = year.getEndingInvestableAssets();
            }
        }

        return lowest;
    }

    public boolean depletedPortfolio() {

        return getFinalInvestableAssets()
                .compareTo(BigDecimal.ZERO) <= 0;
    }

    public int size() {
        return years.size();
    }

    public boolean isEmpty() {
        return years.isEmpty();
    }
}