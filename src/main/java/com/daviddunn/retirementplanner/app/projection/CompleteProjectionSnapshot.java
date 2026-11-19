package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.noninvestable.*;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.summary.*;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatistics;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Immutable accepted data. Restoration constructs detached graphs and never invokes an engine. */
public record CompleteProjectionSnapshot(int version, UUID id, UUID owner, UUID sourcePlanId, Instant acceptedAt,
        String inputsDocument, String inputDigest, String provenance, RetainedProjectionSnapshot annual,
        List<NonInvestableYear> nonInvestable, Summary summary, BreakEvenPlanSummary people) {
    public static final int VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                    .maxNumberLength(4096).maxNestingDepth(80).maxStringLength(256000).build())
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public CompleteProjectionSnapshot {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported complete projection schema.");
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(sourcePlanId); Objects.requireNonNull(acceptedAt);
        Objects.requireNonNull(inputsDocument); Objects.requireNonNull(inputDigest); Objects.requireNonNull(provenance);
        Objects.requireNonNull(annual); Objects.requireNonNull(summary); Objects.requireNonNull(people);
        nonInvestable = List.copyOf(nonInvestable);
        if (inputsDocument.length() > 256_000 || annual.years().isEmpty() || annual.years().size() > 1000
                || annual.accounts().size() > 1000 || nonInvestable.size() != annual.years().size())
            throw new IllegalArgumentException("Incomplete or oversized projection context.");
        if (!digest(inputsDocument).equals(inputDigest)) throw new IllegalArgumentException("Projection input digest mismatch.");
        ConditionalBenefitExecutionSemantics.validate(inputsDocument,annual,provenance);
        ContractualPremiumExecutionSemantics.validate(inputsDocument,annual,provenance);
        if (annual.arkansas() != null) {
            var frozen = restoreInputs(inputsDocument);
            validateArkansasInputs(frozen, annual);
            if (hasArkansasProductionStamp(provenance) && frozen.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                    != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AR) {
                throw new IllegalArgumentException("Arkansas production provenance requires Arkansas inputs.");
            }
        }
        for (int i = 0; i < annual.years().size(); i++) {
            if (nonInvestable.get(i).year() != annual.years().get(i).year()
                    || (i > 0 && annual.years().get(i).year() != annual.years().get(i - 1).year() + 1))
                throw new IllegalArgumentException("Projection year mismatch.");
        }
    }

    public boolean arkansasHistory() {
        return annual.arkansas() != null;
    }

    private static final String ARKANSAS_PRODUCTION = "Arkansas production projection v1";

    private static boolean hasArkansasProductionStamp(String provenance) {
        String stamp = PensionSelectionSemantics.PROVENANCE + ARKANSAS_PRODUCTION;
        return provenance.endsWith(stamp) || provenance.contains(stamp + ContractualPremiumExecutionSemantics.KEY);
    }

    /** Controlled acceptance of an already completed gated projection. Ordinary projection capture remains separate. */
    public static CompleteProjectionSnapshot captureArkansas(UUID owner, RetirementPlan frozenPlan,
            Projection projection, Instant acceptedAt) {
        var retained = RetainedProjectionSnapshot.captureArkansas(projection);
        var inputs = freeze(frozenPlan);
        var nonInvestable = new NonInvestableAssetProjectionService().project(frozenPlan.getNonInvestableAssets(),
                projection.getStartYear(), projection.getEndYear());
        var summary = new ProjectionSummaryService(
                new com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService(),
                new IncomeSummaryService()).summarize(frozenPlan, projection);
        return new CompleteProjectionSnapshot(VERSION, UUID.randomUUID(), owner, owner, acceptedAt, inputs, digest(inputs),
                ConditionalBenefitExecutionSemantics.completed(frozenPlan, inputs, retained,
                        PensionSelectionSemantics.PROVENANCE + (ProjectionEngine.selectsArkansas(frozenPlan)
                                ? ARKANSAS_PRODUCTION : "Controlled Arkansas projection")),
                retained, nonInvestable.stream().map(NonInvestableYear::capture).toList(),
                Summary.capture(summary), BreakEvenPlanSummary.from(frozenPlan));
    }

    private static void validateArkansasInputs(RetirementPlanSnapshot inputs, RetainedProjectionSnapshot annual) {
        for (int i = 0; i < annual.years().size(); i++) {
            var year = annual.years().get(i);
            var evidence = annual.arkansas().years().get(i).sourceEvidence();
            for (var pension : evidence.pensions()) {
                if (pension.sourceIndex() >= year.sources().size()) {
                    throw new IllegalArgumentException("Arkansas pension reference is outside the retained source list.");
                }
                var source = year.sources().get(pension.sourceIndex());
                findOriginalPension(inputs.getHousehold(), source.description(), source.pension(), pension.origin());
            }
        }
    }

    /** Source occurrence in the original frozen member and income-source order. */
    public static int findOriginalPension(com.daviddunn.retirementplanner.domain.model.Household household,
            String name, RetainedProjectionSnapshot.PensionFacts terms,
            com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin origin) {
        var pensions = household.members().stream().flatMap(person -> person.getIncomeSources().stream())
                .filter(com.daviddunn.retirementplanner.domain.income.Pension.class::isInstance)
                .map(com.daviddunn.retirementplanner.domain.income.Pension.class::cast).toList();
        int match = -1;
        for (int i = 0; i < pensions.size(); i++) {
            var pension = pensions.get(i);
            if (pension.getSourceOrigin() == origin && pension.getName().equals(name)
                    && new RetainedProjectionSnapshot.PensionFacts(pension.getOwnership(), pension.getMonthlyBenefit(),
                        pension.getSurvivorMonthlyBenefit(), pension.getAnnualColaRate(), pension.getStartDate(), pension.getEndDate(),
                        pension.getRetirementParticipantFacts(), pension.getPlanClassification()).equals(terms)) {
                if (match != -1) throw new IllegalArgumentException("Ambiguous original Arkansas pension reference.");
                match = i;
            }
        }
        if (match == -1) throw new IllegalArgumentException("Arkansas pension provenance does not identify an original frozen pension.");
        return match;
    }

    public static String freeze(RetirementPlan plan) {
        var inputs = new RetirementPlanSnapshot(plan.getHousehold(), plan.getAccountPortfolio(),
                plan.getPlanningAssumptions(), plan.getRothConversionRequest(), plan.getNonInvestableAssets(), plan.getConditionalBenefits());
        if (plan.getFinancialCalculationMode() != null) inputs.setFinancialCalculationMode(plan.getFinancialCalculationMode());
        return freeze(inputs);
    }
    public static String freeze(RetirementPlanSnapshot snapshot) {
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (Exception failure) { throw new IllegalArgumentException("Cannot freeze projection inputs.", failure); }
    }
    public static String digest(String document) {
        return AnalysisSnapshotCodec.sha256(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static boolean hasCalculationModeField(String document) {
        try {
            JsonNode node = JSON.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(document);
            return node.has("financialCalculationMode");
        } catch (java.io.IOException invalid) {
            throw new IllegalArgumentException("Invalid original calculation inputs.", invalid);
        }
    }
    public RetirementPlanSnapshot restoreInputs() {
        return restoreInputs(inputsDocument);
    }

    /**
     * Resolves display assumptions from this accepted projection's digest-validated frozen inputs.
     * The denormalized people metadata is retained for its historical presentation contract; the
     * reduction policy is sourced only from the associated input document.
     */
    public BreakEvenPlanSummary restoreBreakEvenAssumptions() {
        var frozenInputs = restoreInputs();
        var frozenPolicy = BreakEvenPlanSummary.from(frozenInputs).benefitReductionPolicy();
        if (people.benefitReductionPolicy() != null
                && !people.benefitReductionPolicy().equals(frozenPolicy)) {
            throw new IllegalArgumentException("Projection reduction metadata does not match its frozen inputs.");
        }
        return people.withBenefitReductionPolicy(frozenPolicy);
    }

    public static RetirementPlanSnapshot restoreInputs(String document) {
        try { return JSON.readValue(document, RetirementPlanSnapshot.class); }
        catch (Exception failure) { throw new IllegalArgumentException("Invalid projection inputs.", failure); }
    }
    public boolean matches(RetirementPlan plan) { return inputDigest.equals(digest(freeze(plan))); }
    /** Separate from exact configured-input identity, which still permits historical swaps. */
    public boolean calculationSemanticsCurrent() {
        if (arkansasHistory()) {
            return hasArkansasProductionStamp(provenance)
                    && annual.arkansas().projectionAlgorithm().equals(ProjectionEngine.ALGORITHM_VERSION)
                    && annual.arkansas().versions().equals(ArkansasValidationTax.reviewedVersions());
        }
        // A pension-selection stamp cannot attest to an unimplemented benefit capability.
        // Decode property names rather than searching raw JSON (which may escape them).
        try { if (restoreInputs().getFinancialCalculationMode() != null || !restoreInputs().getConditionalBenefits().isEmpty())
            return ConditionalBenefitExecutionSemantics.current(inputsDocument,annual,provenance); }
        catch (RuntimeException unavailable) { return false; }
        if (provenance.startsWith(PensionSelectionSemantics.PROVENANCE)) return true;
        if (provenance.startsWith(PensionSelectionSemantics.KEY + "=")) return false;
        try {
            var original = restoreInputs();
            return !PensionSelectionSemantics.potentiallyAffected(original.getHousehold(), original.getPlanningAssumptions());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
    public boolean matchesDeterministicInputs(RetirementPlan plan) {
        return sameDeterministicInputs(inputsDocument, freeze(plan));
    }
    public static boolean sameDeterministicInputs(String original, String current) {
        try {
            var exact = JSON.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
            var before = exact.readTree(original);
            var after = exact.readTree(current);
            DeterministicExpenseInputs.removeVolatility(before);
            DeterministicExpenseInputs.removeVolatility(after);
            return JSON.writeValueAsString(before).equals(JSON.writeValueAsString(after));
        } catch (Exception failure) {
            throw new IllegalArgumentException("Cannot assess deterministic inputs.", failure);
        }
    }
    public CompleteProjectionSnapshot ownedBy(UUID identity) {
        return new CompleteProjectionSnapshot(version, id, identity, sourcePlanId, acceptedAt, inputsDocument, inputDigest,
                provenance, annual, nonInvestable, summary, people);
    }
    public List<NonInvestableAssetProjection> restoreNonInvestable() {
        return nonInvestable.stream().map(NonInvestableYear::restore).toList();
    }
    public ProjectionChartModel chart() {
        var projection = annual.restoreProjection();
        var accounts = projection.getYears().stream().flatMap(y -> y.getEndingAccountSnapshots().stream())
                .map(s -> s.getAccount()).distinct().toList();
        return ProjectionChartModel.from(projection.getYears(), restoreNonInvestable(), people, accounts);
    }
    public record Asset(String name, BigDecimal value) {
        public Asset { Objects.requireNonNull(name); Objects.requireNonNull(value); }
    }
    public record NonInvestableYear(int year, List<Asset> assets, BigDecimal total) {
        public NonInvestableYear { assets = List.copyOf(assets); Objects.requireNonNull(total); }
        public static NonInvestableYear capture(NonInvestableAssetProjection source) {
            return new NonInvestableYear(source.getCalendarYear(), source.getAssetValues().stream()
                    .map(a -> new Asset(a.getAssetName(), a.getProjectedValue())).toList(), source.getTotalValue());
        }
        NonInvestableAssetProjection restore() {
            return new NonInvestableAssetProjection(year, assets.stream()
                    .map(a -> new NonInvestableAssetValue(a.name(), a.value())).toList(), total);
        }
    }
    public record Summary(BigDecimal ending, BigDecimal lowest, BigDecimal primarySocialSecurity,
            BigDecimal spouseSocialSecurity, BigDecimal primaryPension, BigDecimal spousePension) {
        public Summary {
            Objects.requireNonNull(ending); Objects.requireNonNull(lowest);
            Objects.requireNonNull(primarySocialSecurity); Objects.requireNonNull(primaryPension);
            if ((spouseSocialSecurity == null) != (spousePension == null))
                throw new IllegalArgumentException("Incomplete spouse income summary.");
        }
        public static Summary capture(ProjectionSummary source) {
            var income = source.getIncomeSummary();
            return new Summary(source.getStatistics().getEndingInvestableAssets(), source.getStatistics().getLowestInvestableAssets(),
                    income.getPrimarySocialSecurityMonthly(), income.getSpouseSocialSecurityMonthly(),
                    income.getPrimaryPensionMonthly(), income.getSpousePensionMonthly());
        }
        public ProjectionSummary restore(Projection projection) {
            var income = spouseSocialSecurity == null ? new IncomeSummary(primarySocialSecurity, primaryPension)
                    : new IncomeSummary(primarySocialSecurity, spouseSocialSecurity, primaryPension, spousePension);
            return new ProjectionSummary(projection, new ProjectionStatistics(ending, lowest), income);
        }
    }
}
