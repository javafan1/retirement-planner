
package com.daviddunn.retirementplanner.domain.projection;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

public class ProjectionContext {

    private RetirementPlan plan;

    private Projection projection;

    private ProjectionYear previousYear;

    private ProjectionYear currentYear;

    public ProjectionContext(RetirementPlan plan,
                             Projection projection) {

        this.plan = plan;
        this.projection = projection;
    }

    public RetirementPlan getPlan() {
        return plan;
    }

    public Projection getProjection() {
        return projection;
    }

    public ProjectionYear getPreviousYear() {
        return previousYear;
    }

    public void setPreviousYear(ProjectionYear previousYear) {
        this.previousYear = previousYear;
    }

    public ProjectionYear getCurrentYear() {
        return currentYear;
    }

    public void setCurrentYear(ProjectionYear currentYear) {
        this.currentYear = currentYear;
    }
}