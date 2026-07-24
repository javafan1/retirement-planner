package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.Objects;

public final class InheritedAccountInformation {

    private final LocalDate originalOwnerDateOfBirth;
    private final LocalDate originalOwnerDateOfDeath;
    private final BeneficiaryRelationship beneficiaryRelationship;

    @JsonCreator
    public InheritedAccountInformation(

            @JsonProperty("originalOwnerDateOfBirth")
            LocalDate originalOwnerDateOfBirth,

            @JsonProperty("originalOwnerDateOfDeath")
            LocalDate originalOwnerDateOfDeath,

            @JsonProperty("beneficiaryRelationship")
            BeneficiaryRelationship beneficiaryRelationship) {

        this.originalOwnerDateOfBirth =
                Objects.requireNonNull(
                        originalOwnerDateOfBirth,
                        "Original owner date of birth is required.");

        this.originalOwnerDateOfDeath =
                Objects.requireNonNull(
                        originalOwnerDateOfDeath,
                        "Original owner date of death is required.");

        this.beneficiaryRelationship =
                Objects.requireNonNull(
                        beneficiaryRelationship,
                        "Beneficiary relationship is required.");

        if (!originalOwnerDateOfDeath.isAfter(
                originalOwnerDateOfBirth)) {

            throw new IllegalArgumentException(
                    "Original owner date of death must be after date of birth.");
        }
    }

    public LocalDate getOriginalOwnerDateOfBirth() {
        return originalOwnerDateOfBirth;
    }

    public LocalDate getOriginalOwnerDateOfDeath() {
        return originalOwnerDateOfDeath;
    }

    public BeneficiaryRelationship getBeneficiaryRelationship() {
        return beneficiaryRelationship;
    }
}