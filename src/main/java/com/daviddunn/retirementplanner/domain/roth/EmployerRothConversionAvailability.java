package com.daviddunn.retirementplanner.domain.roth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.time.Year;
import java.util.Objects;

/** User-supplied planning availability, not legal verification. Null on an account means legacy unknown. */
@JsonDeserialize(using = EmployerRothConversionAvailability.Decoder.class)
public record EmployerRothConversionAvailability(Status status,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer firstEligibleYear) {
    public enum Status { AVAILABLE, NOT_YET_AVAILABLE }

    public EmployerRothConversionAvailability {
        Objects.requireNonNull(status, "Select employer rollover/conversion availability.");
        if (status == Status.AVAILABLE && firstEligibleYear != null)
            throw new IllegalArgumentException("An Available account cannot have a first eligible year.");
        if (firstEligibleYear != null) {
            try { Year.of(firstEligibleYear); }
            catch (java.time.DateTimeException invalid) { throw new IllegalArgumentException("First eligible year is outside the supported calendar range.", invalid); }
        }
    }

    public boolean availableIn(int calendarYear) {
        return status == Status.AVAILABLE || firstEligibleYear != null && calendarYear >= firstEligibleYear;
    }

    /** Scoped optional-field decoding; all other strict historical validation remains enabled. */
    public static final class Decoder extends JsonDeserializer<EmployerRothConversionAvailability> {
        @Override public EmployerRothConversionAvailability deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            JsonNode node = context.readTree(parser);
            if (!node.isObject() || !node.hasNonNull("status") || !node.get("status").isTextual())
                throw JsonMappingException.from(parser, "Employer availability requires an explicit status.");
            var fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!field.equals("status") && !field.equals("firstEligibleYear"))
                    throw JsonMappingException.from(parser, "Unknown employer availability field: " + field);
            }
            JsonNode year = node.get("firstEligibleYear");
            if (year != null && !year.isNull() && (!year.isIntegralNumber() || !year.canConvertToInt()))
                throw JsonMappingException.from(parser, "First eligible year must be a calendar-year integer.");
            try {
                return new EmployerRothConversionAvailability(Status.valueOf(node.get("status").textValue()),
                        year == null || year.isNull() ? null : year.intValue());
            } catch (RuntimeException invalid) {
                throw JsonMappingException.from(parser, "Invalid employer availability.", invalid);
            }
        }
    }
}
