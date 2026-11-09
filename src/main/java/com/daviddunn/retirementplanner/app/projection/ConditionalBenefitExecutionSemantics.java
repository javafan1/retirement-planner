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
    public static final String ANNUITY_MODEL = "deterministic-nonqualified-annuity-niit-guarded-v3";
    public static final String BASIC_MODEL = "deterministic-basic-niit-not-modeled-v1";
    public static final String ADVANCED_MODEL = "deterministic-advanced-niit-guarded-v1";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private ConditionalBenefitExecutionSemantics() { }

    /** New completion only: compare recorded receipts with authoritative selection before accepting. */
    public static String completed(RetirementPlan frozenPlan, String inputs, RetainedProjectionSnapshot annual, String legacyProvenance) {
        legacyProvenance = ContractualPremiumExecutionSemantics.completed(frozenPlan, inputs, annual, legacyProvenance);
        if (frozenPlan.getConditionalBenefits().isEmpty() && frozenPlan.getFinancialCalculationMode() == null) return legacyProvenance;
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
            if (receiptsPresent || CompleteProjectionSnapshot.hasCalculationModeField(inputs)
                    || annual.years().stream().flatMap(y -> y.sources().stream()).anyMatch(s -> s.calculationMode() != null || s.niit() != null))
                throw new IllegalArgumentException("Financial calculation evidence lacks supported deterministic execution provenance.");
            return;
        }
        var original = CompleteProjectionSnapshot.restoreInputs(inputs);
        if (original.getConditionalBenefits().isEmpty() && original.getFinancialCalculationMode() == null || !provenance.startsWith(prefix(inputs,annual) + PensionSelectionSemantics.PROVENANCE))
            throw new IllegalArgumentException("Unsupported or mismatched conditional execution capability.");
        if (!original.getConditionalBenefits().isEmpty()) ConditionalBenefitAdmission.requireDeterministicState(original.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        if (ConditionalBenefits.hasAnnuity(original.getConditionalBenefits())) NonqualifiedAnnuityAdmission.requireState(
                original.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        validateCashGrowthEvidence(original.getConditionalBenefits(), original.getAccountPortfolio(), annual, original.getFinancialCalculationMode());
        validateRetainedEvidence(original.getConditionalBenefits(), original.getPlanningAssumptions(), annual, original.getFinancialCalculationMode());
    }

    /** Structural original-source validation shared with analyzer history; never reruns evaluation. */
    public static void validateRetainedEvidence(RetirementPlan original, RetainedProjectionSnapshot annual) {
        validateCashGrowthEvidence(original.getConditionalBenefits(), original.getAccountPortfolio(), annual, original.getFinancialCalculationMode());
        validateRetainedEvidence(original.getConditionalBenefits(), original.getPlanningAssumptions(), annual, original.getFinancialCalculationMode());
    }
    private static void validateCashGrowthEvidence(List<ConditionalBenefitDefinition> definitions,
            com.daviddunn.retirementplanner.domain.financial.AccountPortfolio accounts, RetainedProjectionSnapshot annual,
            com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode mode) {
        if (mode == com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.BASIC
                || mode == null && !ConditionalBenefits.hasAnnuity(definitions)) return;
        boolean cash = NonqualifiedAnnuityAdmission.hasOpeningNonqualifiedCash(accounts);
        if (annual.years().stream().anyMatch(y -> y.retainedGrowth().signum() != 0
                || y.growth().signum() != 0 && (cash || y.retainedBeginning().signum() > 0)))
            throw new IllegalArgumentException("Unsupported unclassified nonqualified growth in annuity history.");
    }

    private static void validateRetainedEvidence(List<ConditionalBenefitDefinition> benefits,
            com.daviddunn.retirementplanner.domain.model.PlanningAssumptions assumptions, RetainedProjectionSnapshot annual,
            com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode mode) {
        var definitions = new HashMap<java.util.UUID,ConditionalBenefitDefinition>();
        if (mode != com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.BASIC && benefits.stream().anyMatch(d -> d.annuityTerms() != null && d.annuityTerms().qualification()
                != NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1))
            throw new IllegalArgumentException("Contract-only annuity qualification requires explicit Basic provenance; Advanced requires verified NIIT assumptions.");
        if (ConditionalBenefits.hasAnnuity(benefits)) NonqualifiedAnnuityAdmission.requireOpening(assumptions.getProjectionStartDate());
        benefits.forEach(d -> definitions.put(d.id(),d));
        var lumpEvents = new HashSet<java.util.UUID>();
        var eventDates = new HashMap<java.util.UUID,java.time.LocalDate>();
        var annuityCounts = new HashMap<java.util.UUID,Integer>();
        for (var year : annual.years()) {
            boolean annuities = mode == com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.ADVANCED
                    || mode == null && ConditionalBenefits.hasAnnuity(benefits);
            var policies = year.sources().stream().filter(s -> s.calculationMode() != null).toList();
            if (policies.size() != (mode == null ? 0 : 1) || mode != null && (policies.getFirst().calculationMode() != mode
                    || policies.getFirst().taxYear() == null || policies.getFirst().taxYear() != year.year()))
                throw new IllegalArgumentException("Saved annual policy differs from original calculation mode.");
            var niit = year.sources().stream().map(RetainedProjectionSnapshot.Income::niit).filter(Objects::nonNull).toList();
            if (niit.size() != (annuities ? 1 : 0)) throw new IllegalArgumentException("Missing or misplaced annual NIIT evidence.");
            if (year.sources().stream().anyMatch(s -> s.niit() != null && (s.taxYear() == null || s.taxYear() != year.year())))
                throw new IllegalArgumentException("NIIT evidence belongs to another year.");
            if (annuities) {
                var evidence = niit.getFirst();
                var annuityTaxable = year.sources().stream().map(RetainedProjectionSnapshot.Income::conditionalBenefit)
                        .filter(Objects::nonNull).filter(r -> r.annuityBasis() != null).map(ConditionalBenefitReceipt::taxable)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                var interest = year.sources().stream().filter(s -> s.kind() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST)
                        .map(RetainedProjectionSnapshot.Income::taxable).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                if (evidence.magi().compareTo(year.agi()) != 0 || evidence.tax().compareTo(year.federalTax()) > 0
                        || evidence.taxableAnnuities().compareTo(annuityTaxable) != 0 || evidence.taxableInterest().compareTo(interest) != 0
                        || year.medicare() == null || evidence.filingStatus() != year.medicare().bracket().filingStatus())
                    throw new IllegalArgumentException("Annual NIIT evidence does not match the saved financial facts.");
            }
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
                if (receipt.annuityBasis() != null) {
                    if (!receipt.annuityBasis().terms().basisAsOf().equals(assumptions.getProjectionStartDate())
                            || receipt.annuityBasis().paymentsBefore() != annuityCounts.getOrDefault(receipt.benefitId(),0))
                        throw new IllegalArgumentException("Annuity basis anchor or recovery continuity is inconsistent.");
                    annuityCounts.merge(receipt.benefitId(),receipt.months(),Integer::sum);
                }
                if (receipt.paymentStart().isBefore(assumptions.getProjectionStartDate())
                        || receipt.pattern() == ConditionalBenefitDefinition.PaymentPattern.ONE_TIME_LUMP_SUM && !lumpEvents.add(receipt.benefitId()))
                    throw new IllegalArgumentException("Conditional event is outside the modeled interval or duplicated.");
                var previousEvent = eventDates.putIfAbsent(receipt.benefitId(),receipt.eventDate());
                if (previousEvent != null && !previousEvent.equals(receipt.eventDate()))
                    throw new IllegalArgumentException("Conditional trigger event changed across annual evidence.");
                gross = gross.add(receipt.gross());
                taxable = taxable.add(receipt.taxable());
            }
            if (gross.compareTo(year.income()) > 0 || taxable.compareTo(year.agi()) > 0)
                throw new IllegalArgumentException("Conditional receipts exceed completed cash or taxable income.");
            for (var definition : benefits) if (definition.annuityTerms() != null && !seen.contains(definition.id()))
                throw new IllegalArgumentException("Annuity-enabled year is missing original basis evidence.");
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
            String model = original.getFinancialCalculationMode() != null
                    ? original.getFinancialCalculationMode() == com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.BASIC ? BASIC_MODEL : ADVANCED_MODEL
                    : ConditionalBenefits.hasAnnuity(original.getConditionalBenefits()) ? ANNUITY_MODEL
                    : ConditionalBenefits.hasExcludedPrincipal(original.getConditionalBenefits()) ? LIFE_PRINCIPAL_MODEL : MODEL;
            return KEY + model + "; binding=" + binding + "; ";
        } catch (java.io.IOException invalid) { throw new IllegalArgumentException("Cannot bind deterministic benefit evidence.",invalid); }
    }
}
