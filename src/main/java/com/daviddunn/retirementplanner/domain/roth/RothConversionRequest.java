
package com.daviddunn.retirementplanner.domain.roth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;

import java.math.BigDecimal;
import java.util.Objects;

@com.fasterxml.jackson.annotation.JsonPropertyOrder({"enabled", "startYear", "annualAmount", "stopRule", "strategy",
        "frequency", "customTargetTaxableIncome", "scheduling"})
@JsonDeserialize(using = RothConversionRequest.Reader.class)
public final class RothConversionRequest {

    private final boolean enabled;
    private final int startYear;
    private final BigDecimal annualAmount;
    private final RothConversionStopRule stopRule;
    private final RothConversionFrequency frequency;
    private final RothConversionStrategy strategy;

    private final BigDecimal customTargetTaxableIncome;

    private final Scheduling scheduling;

    public record Scheduling(int schedulingVersion, int conversionEndYear, RothRmdSchedulingPolicy rmdSchedulingPolicy) {
        public Scheduling {
            if (schedulingVersion != 1) throw new IllegalArgumentException("Unsupported Roth scheduling version.");
            if (conversionEndYear < 1900 || conversionEndYear > java.time.Year.MAX_VALUE) {
                throw new IllegalArgumentException("End year is invalid.");
            }
            Objects.requireNonNull(rmdSchedulingPolicy, "RMD scheduling policy is required.");
        }
    }

    public RothConversionRequest(boolean enabled, int startYear, BigDecimal annualAmount, RothConversionStopRule stopRule,
            RothConversionStrategy strategy, RothConversionFrequency frequency, BigDecimal customTargetTaxableIncome) {
        this(enabled, startYear, annualAmount, stopRule, strategy, frequency, customTargetTaxableIncome, null);
    }

    public RothConversionRequest(
            boolean enabled,

            int startYear,

            BigDecimal annualAmount,

            RothConversionStopRule stopRule,

            RothConversionStrategy strategy,

            RothConversionFrequency frequency,

            BigDecimal customTargetTaxableIncome, Scheduling scheduling) {

        if (startYear < 1900) {
            throw new IllegalArgumentException(
                    "Start year is invalid.");
        }

        Objects.requireNonNull(
                annualAmount,
                "Annual amount is required.");

        if (annualAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Annual amount cannot be negative.");
        }

        this.enabled =
                enabled;

        this.startYear =
                startYear;

        this.annualAmount =
                annualAmount;

        this.stopRule =
                stopRule;

        this.frequency =
                Objects.requireNonNull(
                        frequency,
                        "Frequency is required.");

        this.strategy =
                strategy == null
                        ? RothConversionStrategy.FIXED_AMOUNT
                        : strategy;

        validateCustomTargetTaxableIncome(
                this.strategy,
                customTargetTaxableIncome);

        this.customTargetTaxableIncome = customTargetTaxableIncome;
        if (scheduling != null && (scheduling.conversionEndYear() < startYear
                || frequency == RothConversionFrequency.ONE_TIME && scheduling.conversionEndYear() != startYear)) {
            throw new IllegalArgumentException("The Roth schedule requires start at or before end and a single year for one-time conversions.");
        }
        this.scheduling = scheduling;

    }

    public RothConversionRequest(
            boolean enabled,
            int startYear,
            BigDecimal annualAmount,
            RothConversionStopRule stopRule,
            RothConversionStrategy strategy,
            RothConversionFrequency frequency) {

        this(
                enabled,
                startYear,
                annualAmount,
                stopRule,
                strategy,
                frequency,
                null);
    }

    public RothConversionStrategy getStrategy() {
        return strategy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getStartYear() {
        return startYear;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }

    public RothConversionStopRule getStopRule() {
        return stopRule;
    }

    public RothConversionFrequency getFrequency() {
        return frequency;
    }

    public BigDecimal getCustomTargetTaxableIncome() {
        return customTargetTaxableIncome;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Scheduling getScheduling() { return scheduling; }

    public static boolean usesVersionedExecution(RothConversionRequest request) {
        return request != null && request.isEnabled() && request.getScheduling() != null;
    }

    public static void requireExecutable(RothConversionRequest request) {
        if (usesVersionedExecution(request)) {
            throw new SchedulingUnavailable();
        }
    }

    public static final class SchedulingUnavailable extends IllegalArgumentException {
        public SchedulingUnavailable() {
            super("Version-1 Roth scheduling requires a supported Florida Basic normal projection.");
        }
    }

    private record Wire(boolean enabled, int startYear, BigDecimal annualAmount, RothConversionStopRule stopRule,
                        RothConversionStrategy strategy, RothConversionFrequency frequency,
                        BigDecimal customTargetTaxableIncome, Scheduling scheduling) { }

    public static final class Reader extends JsonDeserializer<RothConversionRequest> {
        @Override
        public RothConversionRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedStartObjectToken()) {
                return (RothConversionRequest) context.handleUnexpectedToken(RothConversionRequest.class, parser);
            }
            try (var copy = new TokenBuffer(parser, context)) {
                copy.writeStartObject();
                boolean hasSchedule = false;
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    var name = parser.currentName();
                    parser.nextToken();
                    if ("scheduling".equals(name)) {
                        if (hasSchedule || !parser.isExpectedStartObjectToken()) {
                            throw JsonMappingException.from(parser, "Roth scheduling requires a single explicit object.");
                        }
                        hasSchedule = true;
                        copy.writeFieldName(name);
                        copy.writeObject(readSchedule(parser));
                    } else {
                        copy.writeFieldName(name);
                        copy.copyCurrentStructure(parser);
                    }
                }
                if (parser.currentToken() != JsonToken.END_OBJECT) {
                    throw JsonMappingException.from(parser, "Expected the end of the Roth conversion request.");
                }
                if (!hasSchedule) copy.writeNullField("scheduling");
                copy.writeEndObject();
                try (var buffered = copy.asParser(parser)) {
                    buffered.nextToken();
                    var wire = context.readValue(buffered, Wire.class);
                    return new RothConversionRequest(wire.enabled(), wire.startYear(), wire.annualAmount(), wire.stopRule(),
                            wire.strategy(), wire.frequency(), wire.customTargetTaxableIncome(), wire.scheduling());
                }
            } catch (IllegalArgumentException | NullPointerException invalid) {
                throw JsonMappingException.from(parser, invalid.getMessage(), invalid);
            }
        }

        private static Scheduling readSchedule(JsonParser parser) throws IOException {
            Integer version = null;
            Integer endYear = null;
            RothRmdSchedulingPolicy policy = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var name = parser.currentName();
                var token = parser.nextToken();
                if ("schedulingVersion".equals(name) && version == null && token == JsonToken.VALUE_NUMBER_INT) {
                    version = parser.getIntValue();
                } else if ("conversionEndYear".equals(name) && endYear == null && token == JsonToken.VALUE_NUMBER_INT) {
                    endYear = parser.getIntValue();
                } else if ("rmdSchedulingPolicy".equals(name) && policy == null && token == JsonToken.VALUE_STRING) {
                    policy = RothRmdSchedulingPolicy.valueOf(parser.getText());
                } else {
                    throw JsonMappingException.from(parser, "Invalid Roth scheduling property: " + name);
                }
            }
            if (parser.currentToken() != JsonToken.END_OBJECT || version == null || endYear == null || policy == null) {
                throw JsonMappingException.from(parser, "Roth scheduling requires version, end year, and policy.");
            }
            return new Scheduling(version, endYear, policy);
        }
    }

    private static void validateCustomTargetTaxableIncome(
            RothConversionStrategy strategy,
            BigDecimal customTargetTaxableIncome) {

        if (strategy ==
                RothConversionStrategy
                        .CUSTOM_TAXABLE_INCOME_TARGET) {

            Objects.requireNonNull(
                    customTargetTaxableIncome,
                    "Custom target taxable income is required.");

            if (customTargetTaxableIncome.signum() < 0) {
                throw new IllegalArgumentException(
                        "Custom target taxable income cannot be negative.");
            }

            return;
        }

        if (customTargetTaxableIncome != null) {
            throw new IllegalArgumentException(
                    "Custom target taxable income is only valid "
                            + "for the custom taxable-income strategy.");
        }
    }
}
