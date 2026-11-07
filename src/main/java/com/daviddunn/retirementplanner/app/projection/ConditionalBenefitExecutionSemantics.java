package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;

/** Deterministic capability, independent of B0 pension and all analyzer/MC algorithm versions. */
public final class ConditionalBenefitExecutionSemantics {
    public static final String KEY = "conditional-benefits=";
    public static final String MODEL = "deterministic-annual-v1";
    public static final String LIFE_PRINCIPAL_MODEL = "deterministic-life-principal-v2";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private ConditionalBenefitExecutionSemantics() { }

    /** New completion only: compare recorded receipts with authoritative selection before accepting. */
    public static String completed(RetirementPlan frozenPlan, String inputs, RetainedProjectionSnapshot annual, String legacyProvenance) {
        if (frozenPlan.getConditionalBenefits().isEmpty()) return legacyProvenance;
        ConditionalBenefitAdmission.requireDeterministicSupported(frozenPlan);
        var deaths = EffectiveHouseholdDeathView.resolve(frozenPlan.getHousehold(), frozenPlan.getPlanningAssumptions().getDeathScenarioAssumptions(), Optional.empty());
        for (var year : annual.years()) {
            var expected = new ConditionalBenefitEvaluator().evaluate(frozenPlan.getConditionalBenefits(), BenefitEvaluationContext.capture(
                    frozenPlan.getHousehold(), deaths, frozenPlan.getPlanningAssumptions().getProjectionStartDate(), year.year()));
            var actual = year.sources().stream().map(RetainedProjectionSnapshot.Income::conditionalBenefit).filter(Objects::nonNull).toList();
            if (!expected.receipts().equals(actual)) throw new IllegalArgumentException("Completed projection did not execute every applicable conditional benefit.");
        }
        String provenance = prefix(inputs,annual) + legacyProvenance;
        validate(inputs,annual,provenance);
        return provenance;
    }

    /** Restored evidence only: never evaluates a payment, projects or changes historical data. */
    public static void validate(String inputs, RetainedProjectionSnapshot annual, String provenance) {
        boolean tagged = provenance.startsWith(KEY);
        boolean receiptsPresent = annual.years().stream().flatMap(y -> y.sources().stream()).anyMatch(i -> i.conditionalBenefit() != null);
        if (!tagged) {
            if (receiptsPresent) throw new IllegalArgumentException("Conditional receipts lack supported deterministic execution provenance.");
            return;
        }
        var original = CompleteProjectionSnapshot.restoreInputs(inputs);
        if (original.getConditionalBenefits().isEmpty() || !provenance.startsWith(prefix(inputs,annual) + PensionSelectionSemantics.PROVENANCE))
            throw new IllegalArgumentException("Unsupported or mismatched conditional execution capability.");
        ConditionalBenefitAdmission.requireDeterministicState(original.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        validateRetainedEvidence(original.getConditionalBenefits(), original.getPlanningAssumptions(), annual);
    }

    /** Structural original-source validation shared with analyzer history; never reruns evaluation. */
    public static void validateRetainedEvidence(RetirementPlan original, RetainedProjectionSnapshot annual) {
        validateRetainedEvidence(original.getConditionalBenefits(), original.getPlanningAssumptions(), annual);
    }

    private static void validateRetainedEvidence(List<ConditionalBenefitDefinition> benefits,
            com.daviddunn.retirementplanner.domain.model.PlanningAssumptions assumptions, RetainedProjectionSnapshot annual) {
        var definitions = new HashMap<java.util.UUID,ConditionalBenefitDefinition>();
        benefits.forEach(d -> definitions.put(d.id(),d));
        var lumpEvents = new HashSet<java.util.UUID>();
        var eventDates = new HashMap<java.util.UUID,java.time.LocalDate>();
        for (var year : annual.years()) {
            if (year.state() == null || year.state().jurisdiction() != assumptions.getStateTaxAssumptions().jurisdiction()
                    || year.state().status() != com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus.CALCULATED
                    || year.settlement().guaranteedIncome().compareTo(year.income()) != 0
                    || year.settlement().incomeTaxes().compareTo(year.federalTax().add(year.stateTax())) != 0)
                throw new IllegalArgumentException("Conditional result lacks reconciled cash/tax state evidence.");
            var seen = new HashSet<java.util.UUID>();
            java.math.BigDecimal gross = java.math.BigDecimal.ZERO;
            java.math.BigDecimal taxable = java.math.BigDecimal.ZERO;
            for (var source : year.sources()) {
                var receipt = source.conditionalBenefit();
                if (receipt == null) continue;
                var definition = definitions.get(receipt.benefitId());
                if (definition == null || receipt.year() != year.year() || !seen.add(receipt.benefitId()))
                    throw new IllegalArgumentException("Conditional receipt ownership/year/identity mismatch.");
                receipt.validateDefinition(definition);
                if (receipt.paymentStart().isBefore(assumptions.getProjectionStartDate())
                        || receipt.months() == 0 && !lumpEvents.add(receipt.benefitId()))
                    throw new IllegalArgumentException("Conditional event is outside the modeled interval or duplicated.");
                var previousEvent = eventDates.putIfAbsent(receipt.benefitId(),receipt.eventDate());
                if (previousEvent != null && !previousEvent.equals(receipt.eventDate()))
                    throw new IllegalArgumentException("Conditional trigger event changed across annual evidence.");
                gross = gross.add(receipt.gross());
                taxable = taxable.add(receipt.taxable());
            }
            if (gross.compareTo(year.income()) > 0 || taxable.compareTo(year.agi()) > 0)
                throw new IllegalArgumentException("Conditional receipts exceed completed cash or taxable income.");
        }
    }
    public static boolean current(String inputs, RetainedProjectionSnapshot annual, String provenance) {
        if (!provenance.startsWith(KEY)) return false;
        try { validate(inputs,annual,provenance); return true; }
        catch (RuntimeException invalid) { return false; }
    }
    private static String prefix(String inputs, RetainedProjectionSnapshot annual) {
        try {
            var binding = AnalysisSnapshotCodec.sha256(JSON.writeValueAsBytes(List.of(CompleteProjectionSnapshot.digest(inputs),annual)));
            var original = CompleteProjectionSnapshot.restoreInputs(inputs);
            String model = ConditionalBenefits.hasExcludedPrincipal(original.getConditionalBenefits()) ? LIFE_PRINCIPAL_MODEL : MODEL;
            return KEY + model + "; binding=" + binding + "; ";
        } catch (java.io.IOException invalid) { throw new IllegalArgumentException("Cannot bind deterministic benefit evidence.",invalid); }
    }
}
