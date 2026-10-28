package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.noninvestable.*;
import com.daviddunn.retirementplanner.domain.projection.Projection;
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
        for (int i = 0; i < annual.years().size(); i++) {
            if (nonInvestable.get(i).year() != annual.years().get(i).year()
                    || (i > 0 && annual.years().get(i).year() != annual.years().get(i - 1).year() + 1))
                throw new IllegalArgumentException("Projection year mismatch.");
        }
    }

    public static String freeze(RetirementPlan plan) {
        return freeze(new RetirementPlanSnapshot(plan.getHousehold(), plan.getAccountPortfolio(),
                plan.getPlanningAssumptions(), plan.getRothConversionRequest(), plan.getNonInvestableAssets()));
    }
    public static String freeze(RetirementPlanSnapshot snapshot) {
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (Exception failure) { throw new IllegalArgumentException("Cannot freeze projection inputs.", failure); }
    }
    public static String digest(String document) {
        return AnalysisSnapshotCodec.sha256(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public RetirementPlanSnapshot restoreInputs() {
        return restoreInputs(inputsDocument);
    }
    public static RetirementPlanSnapshot restoreInputs(String document) {
        try { return JSON.readValue(document, RetirementPlanSnapshot.class); }
        catch (Exception failure) { throw new IllegalArgumentException("Invalid projection inputs.", failure); }
    }
    public boolean matches(RetirementPlan plan) { return inputDigest.equals(digest(freeze(plan))); }
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
