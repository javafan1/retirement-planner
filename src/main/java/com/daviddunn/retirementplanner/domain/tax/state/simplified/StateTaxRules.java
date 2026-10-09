package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Validated, immutable research rules. Dataset readiness is not legal certification. */
public record StateTaxRules(
        String jurisdiction,
        int taxYear,
        String datasetVersion,
        String readiness,
        String taxBase,
        String system,
        BigDecimal rate,
        Map<FilingStatus, List<Bracket>> brackets,
        Map<FilingStatus, BigDecimal> filingThreshold,
        Map<FilingStatus, BigDecimal> standardDeduction,
        String exemptionType,
        Map<FilingStatus, BigDecimal> exemptions,
        Map<FilingStatus, BigDecimal> exemptionCeiling,
        Map<IncomeType, String> incomeTreatment,
        List<Adjustment> adjustments,
        List<Senior> seniors,
        List<Surcharge> surcharges,
        List<Boundary> boundaries,
        List<String> limitations) {

    public StateTaxRules {
        brackets = brackets.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        filingThreshold = Map.copyOf(filingThreshold);
        standardDeduction = Map.copyOf(standardDeduction);
        exemptions = Map.copyOf(exemptions);
        exemptionCeiling = Map.copyOf(exemptionCeiling);
        incomeTreatment = Map.copyOf(incomeTreatment);
        adjustments = List.copyOf(adjustments);
        seniors = List.copyOf(seniors);
        surcharges = List.copyOf(surcharges);
        boundaries = List.copyOf(boundaries);
        limitations = List.copyOf(limitations);
    }

    public enum IncomeType {
        SOCIAL_SECURITY, TRADITIONAL_IRA, EMPLOYER_RETIREMENT_PLAN, PENSION,
        ROTH_CONVERSION, QUALIFIED_ROTH_DISTRIBUTION, INTEREST,
        ORDINARY_DIVIDEND, QUALIFIED_DIVIDEND,
        SHORT_TERM_CAPITAL_GAIN, LONG_TERM_CAPITAL_GAIN
    }

    public enum PensionSource {
        PRIVATE, FEDERAL_GOVERNMENT, NEW_YORK_STATE_LOCAL_GOVERNMENT,
        OTHER_GOVERNMENT, UNKNOWN
    }

    public record Bracket(BigDecimal from, BigDecimal to, BigDecimal rate) { }

    public record Band(
            BigDecimal from, BigDecimal to, String method,
            Map<FilingStatus, BigDecimal> amount) {
        public Band {
            amount = Map.copyOf(amount);
        }
    }

    public record Adjustment(
            String id, String scope, List<IncomeType> appliesTo,
            String qualification, BigDecimal minimumAge, List<String> alternativeEvents,
            List<PensionSource> pensionSources, String method,
            Map<FilingStatus, BigDecimal> maximum,
            List<Band> ageBands, List<Band> incomeBands,
            String group, int priority, boolean coordinatedLimit,
            Map<FilingStatus, BigDecimal> federalAgiMaximum,
            boolean inclusiveIncomeBands, boolean zeroAboveIncomeBands) {
        public Adjustment {
            appliesTo = List.copyOf(appliesTo);
            alternativeEvents = List.copyOf(alternativeEvents);
            pensionSources = List.copyOf(pensionSources);
            maximum = Map.copyOf(maximum);
            ageBands = List.copyOf(ageBands);
            incomeBands = List.copyOf(incomeBands);
            federalAgiMaximum = Map.copyOf(federalAgiMaximum);
        }
    }

    /** Explicit supported-scenario boundary; amounts and eligibility remain in the dataset. */
    public record Boundary(String id, String stage, String basis, String trigger,
                           Map<FilingStatus, BigDecimal> maximum,
                           BigDecimal minimumAge, List<IncomeType> incomeTypes,
                           List<PensionSource> pensionSources, String qualification, String reason) {
        public Boundary {
            maximum = Map.copyOf(maximum);
            incomeTypes = List.copyOf(incomeTypes);
            pensionSources = List.copyOf(pensionSources);
        }
    }

    public record Senior(String id, BigDecimal minimumAge, BigDecimal amount,
                         boolean subjectToCeiling) { }

    public record Surcharge(String id, Map<FilingStatus, BigDecimal> threshold,
                            BigDecimal rate) {
        public Surcharge {
            threshold = Map.copyOf(threshold);
        }
    }
}
