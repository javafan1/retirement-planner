package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Objects;

/** Participant facts, not plan qualification or state-tax eligibility conclusions. */
public record RetirementParticipantFacts(
        SeparationStatus separationStatus,
        LocalDate separationDate,
        RetirementConditionsStatus retirementConditionsStatus,
        LocalDate retirementConditionsMetDate) {

    public enum SeparationStatus { UNKNOWN, NOT_SEPARATED, SEPARATED }
    public enum RetirementConditionsStatus { UNKNOWN, NOT_MET, MET }

    public static final RetirementParticipantFacts UNKNOWN = new RetirementParticipantFacts(
            SeparationStatus.UNKNOWN, null, RetirementConditionsStatus.UNKNOWN, null);

    @JsonCreator
    public RetirementParticipantFacts(
            @JsonProperty("separationStatus") SeparationStatus separationStatus,
            @JsonProperty("separationDate") LocalDate separationDate,
            @JsonProperty("retirementConditionsStatus") RetirementConditionsStatus retirementConditionsStatus,
            @JsonProperty("retirementConditionsMetDate") LocalDate retirementConditionsMetDate) {
        this.separationStatus = Objects.requireNonNullElse(separationStatus, SeparationStatus.UNKNOWN);
        this.retirementConditionsStatus = Objects.requireNonNullElse(
                retirementConditionsStatus, RetirementConditionsStatus.UNKNOWN);
        if (separationDate != null && this.separationStatus != SeparationStatus.SEPARATED) {
            throw new IllegalArgumentException("A separation date requires Separated status.");
        }
        if (retirementConditionsMetDate != null
                && this.retirementConditionsStatus != RetirementConditionsStatus.MET) {
            throw new IllegalArgumentException("A conditions-met date requires Met status.");
        }
        this.separationDate = separationDate;
        this.retirementConditionsMetDate = retirementConditionsMetDate;
    }

    /** Undated affirmative facts do not establish their timing in a multi-year projection. */
    public SeparationStatus separationStatusOn(LocalDate boundary) {
        Objects.requireNonNull(boundary);
        if (separationStatus != SeparationStatus.SEPARATED) return separationStatus;
        if (separationDate == null) return SeparationStatus.UNKNOWN;
        return boundary.isBefore(separationDate) ? SeparationStatus.NOT_SEPARATED : SeparationStatus.SEPARATED;
    }

    public RetirementConditionsStatus retirementConditionsStatusOn(LocalDate boundary) {
        Objects.requireNonNull(boundary);
        if (retirementConditionsStatus != RetirementConditionsStatus.MET) return retirementConditionsStatus;
        if (retirementConditionsMetDate == null) return RetirementConditionsStatus.UNKNOWN;
        return boundary.isBefore(retirementConditionsMetDate)
                ? RetirementConditionsStatus.NOT_MET : RetirementConditionsStatus.MET;
    }
}
