package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Objects;

/** Participant facts, not plan qualification or state-tax eligibility conclusions. */
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = RetirementParticipantFacts.Reader.class)
public record RetirementParticipantFacts(
        SeparationStatus separationStatus,
        LocalDate separationDate,
        RetirementConditionsStatus retirementConditionsStatus,
        LocalDate retirementConditionsMetDate,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        LocalDate confirmedPostRbdBeneficiaryRequiredBeginningDate,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Boolean confirmedPostRbdDirectRollover) {

    public enum SeparationStatus { UNKNOWN, NOT_SEPARATED, SEPARATED }
    public enum RetirementConditionsStatus { UNKNOWN, NOT_MET, MET }

    public static final RetirementParticipantFacts UNKNOWN = new RetirementParticipantFacts(
            SeparationStatus.UNKNOWN, null, RetirementConditionsStatus.UNKNOWN, null);

    public RetirementParticipantFacts(SeparationStatus separationStatus, LocalDate separationDate,
            RetirementConditionsStatus retirementConditionsStatus, LocalDate retirementConditionsMetDate) {
        this(separationStatus, separationDate, retirementConditionsStatus, retirementConditionsMetDate, null);
    }

    public RetirementParticipantFacts(SeparationStatus separationStatus, LocalDate separationDate,
            RetirementConditionsStatus retirementConditionsStatus, LocalDate retirementConditionsMetDate,
            LocalDate confirmedPostRbdBeneficiaryRequiredBeginningDate) {
        this(separationStatus, separationDate, retirementConditionsStatus, retirementConditionsMetDate,
                confirmedPostRbdBeneficiaryRequiredBeginningDate, null);
    }

    @JsonCreator
    public RetirementParticipantFacts(
            @JsonProperty("separationStatus") SeparationStatus separationStatus,
            @JsonProperty("separationDate") LocalDate separationDate,
            @JsonProperty("retirementConditionsStatus") RetirementConditionsStatus retirementConditionsStatus,
            @JsonProperty("retirementConditionsMetDate") LocalDate retirementConditionsMetDate,
            @JsonProperty("confirmedPostRbdBeneficiaryRequiredBeginningDate") LocalDate confirmedPostRbdBeneficiaryRequiredBeginningDate,
            @JsonProperty("confirmedPostRbdDirectRollover") Boolean confirmedPostRbdDirectRollover) {
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
        this.confirmedPostRbdBeneficiaryRequiredBeginningDate = confirmedPostRbdBeneficiaryRequiredBeginningDate;
        this.confirmedPostRbdDirectRollover = confirmedPostRbdDirectRollover;
    }

    public static final class Reader extends com.fasterxml.jackson.databind.JsonDeserializer<RetirementParticipantFacts> {
        private static final java.util.Set<String> ORIGINAL_FIELDS = java.util.Set.of(
                "separationStatus", "separationDate", "retirementConditionsStatus", "retirementConditionsMetDate");
        private static final String CONFIRMED_RBD = "confirmedPostRbdBeneficiaryRequiredBeginningDate";
        private static final String CONFIRMED_ROLLOVER = "confirmedPostRbdDirectRollover";

        @Override
        public RetirementParticipantFacts deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            var node = context.readTree(parser);
            if (!node.isObject()) {
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Expected an object for participant facts.");
            }
            if (context.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    && !ORIGINAL_FIELDS.stream().allMatch(node::has)) {
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Missing required participant facts.");
            }
            if (context.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
                for (var key : node.properties()) {
                    if (!ORIGINAL_FIELDS.contains(key.getKey()) && !CONFIRMED_RBD.equals(key.getKey()) && !CONFIRMED_ROLLOVER.equals(key.getKey())) {
                        throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Unknown participant fact: " + key.getKey());
                    }
                }
            }
            return new RetirementParticipantFacts(
                    context.readTreeAsValue(node.path("separationStatus"), SeparationStatus.class),
                    context.readTreeAsValue(node.path("separationDate"), LocalDate.class),
                    context.readTreeAsValue(node.path("retirementConditionsStatus"), RetirementConditionsStatus.class),
                    context.readTreeAsValue(node.path("retirementConditionsMetDate"), LocalDate.class),
                    node.hasNonNull(CONFIRMED_RBD) ? context.readTreeAsValue(node.get(CONFIRMED_RBD), LocalDate.class) : null,
                    node.hasNonNull(CONFIRMED_ROLLOVER) ? context.readTreeAsValue(node.get(CONFIRMED_ROLLOVER), Boolean.class) : null);
        }
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
