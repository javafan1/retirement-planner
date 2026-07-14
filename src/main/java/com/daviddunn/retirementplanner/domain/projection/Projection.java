package com.daviddunn.retirementplanner.domain.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Projection {

    private final List<ProjectionYear> years =
            new ArrayList<>();

    public void addYear(ProjectionYear year) {
        years.add(year);
    }

    public List<ProjectionYear> getYears() {
        return Collections.unmodifiableList(years);
    }

    public ProjectionYear getFirstYear() {
        return years.getFirst();
    }

    public ProjectionYear getLastYear() {
        return years.getLast();
    }

    public int getNumberOfYears() {
        return years.size();
    }
}