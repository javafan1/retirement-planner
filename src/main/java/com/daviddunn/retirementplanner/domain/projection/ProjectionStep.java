package com.daviddunn.retirementplanner.domain.projection;

public interface ProjectionStep {

    void execute(ProjectionContext context);

}