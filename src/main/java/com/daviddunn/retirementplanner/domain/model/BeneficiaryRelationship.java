package com.daviddunn.retirementplanner.domain.model;

public enum BeneficiaryRelationship {

    SPOUSE("Spouse"),
    CHILD("Child"),
    SIBLING("Sibling"),
    OTHER("Other");

    private final String displayName;

    BeneficiaryRelationship(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}