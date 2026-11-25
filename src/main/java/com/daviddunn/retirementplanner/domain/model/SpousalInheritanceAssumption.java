package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/** A prospective plan-level choice. An omitted assumption identifies legacy inputs. */
public record SpousalInheritanceAssumption(int version, Treatment treatment) {

    public static final int VERSION = 1;

    public enum Treatment {
        SPOUSE_AS_OWNER("SPOUSE AS OWNER"),
        SPOUSE_AS_BENEFICIARY("SPOUSE AS BENEFICIARY");

        private final String label;

        Treatment(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public SpousalInheritanceAssumption {
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported spousal inheritance assumption version.");
        }
        Objects.requireNonNull(treatment, "Spousal inheritance treatment is required.");
    }

    public SpousalInheritanceAssumption(Treatment treatment) {
        this(VERSION, treatment);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SpousalInheritanceAssumption fromWire(JsonNode node) {
        if (!node.isObject() || node.size() != 2 || !node.path("version").isIntegralNumber()
                || !node.path("version").canConvertToInt() || !node.path("treatment").isTextual()) {
            throw new IllegalArgumentException("Invalid spousal inheritance assumption.");
        }
        return new SpousalInheritanceAssumption(node.get("version").intValue(),
                Treatment.valueOf(node.get("treatment").textValue()));
    }
}
