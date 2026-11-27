package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.noninvestable.*;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.summary.*;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatistics;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Immutable accepted data. Display restoration constructs detached graphs from certified evidence. */
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
        boolean versionedRoth = validateRothScheduling(inputsDocument, annual, provenance);
        SpousalInheritancePlanningEvidence.validate(inputsDocument, annual, provenance);
        String calculationProvenance = SpousalInheritancePlanningEvidence.calculationProvenance(provenance);
        ConditionalBenefitExecutionSemantics.validate(inputsDocument,annual,calculationProvenance);
        ContractualPremiumExecutionSemantics.validate(inputsDocument,annual,calculationProvenance);
        if (annual.arkansas() != null) {
            var frozen = restoreInputs(inputsDocument);
            validateArkansasInputs(frozen, annual);
            validateArkansasLifePrincipal(frozen, annual, calculationProvenance);
            boolean regularOnly = annual.arkansas().hasRegularTaxOnlyPolicy();
            if (regularOnly != frozen.getPlanningAssumptions().getStateTaxAssumptions().usesArkansasRegularTaxOnly()
                    || regularOnly && (!hasArkansasProductionStamp(calculationProvenance)
                        || frozen.getFinancialCalculationMode() == FinancialCalculationMode.ADVANCED)) {
                throw new IllegalArgumentException("Arkansas low-income modeling policy differs from the frozen inputs or route.");
            }
            if (hasArkansasProductionStamp(calculationProvenance) && frozen.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                    != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AR) {
                throw new IllegalArgumentException("Arkansas production provenance requires Arkansas inputs.");
            }
        }
        for (int i = 0; i < annual.years().size(); i++) {
            if (nonInvestable.get(i).year() != annual.years().get(i).year()
                    || (i > 0 && annual.years().get(i).year() != annual.years().get(i - 1).year() + 1))
                throw new IllegalArgumentException("Projection year mismatch.");
        }
        if (versionedRoth || SpousalInheritancePlanningEvidence.applied(annual)) {
            validateSpousalIraRmd(inputsDocument, annual, versionedRoth);
        }
    }

    private static final String ROTH_POLICY = "; roth-scheduling=FL-BASIC-ROTH-SCHEDULING-V1/";

    public static String rothSchedulingStamp(
            com.daviddunn.retirementplanner.domain.roth.RothConversionRequest request,
            String inputs, RetainedProjectionSnapshot annual) {
        if (!com.daviddunn.retirementplanner.domain.roth.RothConversionRequest.usesVersionedExecution(request)) return "";
        return ROTH_POLICY + request.getScheduling().rmdSchedulingPolicy() + "/"
                + new com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec()
                        .executionDigest(java.util.List.of(digest(inputs), annual));
    }

    private static boolean validateRothScheduling(String inputsDocument, RetainedProjectionSnapshot annual, String provenance) {
        try {
            var request = JSON.readTree(inputsDocument).path("rothConversionRequest").has("scheduling")
                    ? restoreInputs(inputsDocument).getRothConversionRequest() : null;
            var stamp = rothSchedulingStamp(request, inputsDocument, annual);
            int marker = provenance.indexOf(ROTH_POLICY);
            if (stamp.isEmpty() ? marker >= 0 : marker < 0 || marker != provenance.lastIndexOf(ROTH_POLICY)
                    || !provenance.substring(marker).startsWith(stamp)) {
                throw new IllegalArgumentException("Missing or mismatched versioned Roth scheduling identity.");
            }
            return !stamp.isEmpty();
        } catch (java.io.IOException invalid) {
            throw new IllegalArgumentException("Invalid frozen projection inputs.", invalid);
        }
    }

    private static void validateSpousalIraRmd(String inputsDocument, RetainedProjectionSnapshot annual, boolean versionedRoth) {
        var frozen = executionPlan(restoreInputs(inputsDocument));
        int year = annual.years().getFirst().year();
        var mismatch = com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable.Reason.HISTORICAL_EVIDENCE_MISMATCH;
        try {
            // The accepted rows cannot prove their own debits. Independently reproduce the scoped calculation
            // from the frozen opening graph and compare every retained field, including years without a debit.
            var engine = new ProjectionEngine();
            if (versionedRoth && !matchesVersionedRoute(frozen, annual)) {
                throw new IllegalArgumentException("Retained inheritance policy differs from normal execution.");
            }
            var outcome = versionedRoth ? engine.projectNormalWithRothScheduling(frozen) : switch (annual.schemaVersion()) {
                case RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_OWNER_SCHEMA -> engine.projectSpousalPostRbd401kOwner(frozen);
                case RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA -> engine.projectSpousalPostRbd401k(frozen);
                case RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA -> engine.projectSpousalIraDeathYear(frozen);
                case RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA -> engine.projectSpousalSuccessorOrdinary(frozen);
                case RetainedProjectionSnapshot.SPOUSAL_401K_ROLLOVER_SCHEMA -> engine.projectSpousal401kRollover(frozen);
                case RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_IRA_SCHEMA -> engine.projectSpousalBeneficiaryIraRmd(frozen);
                case RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_401K_SCHEMA -> engine.projectSpousalBeneficiary401kRmd(frozen);
                default -> engine.projectSpousalIraRmd(frozen);
            };
            if (outcome instanceof com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult.Completed done
                    && switch (annual.schemaVersion()) {
                        case RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_OWNER_SCHEMA ->
                                RetainedProjectionSnapshot.captureSpousalPostRbd401kOwner(done.projection()).equals(annual);
                        case RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA ->
                                RetainedProjectionSnapshot.captureSpousalPostRbd401k(done.projection()).equals(annual);
                        case RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA ->
                                RetainedProjectionSnapshot.captureSpousalIraDeathYear(done.projection()).equals(annual);
                        case RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA ->
                                RetainedProjectionSnapshot.captureSpousalSuccessorOrdinary(done.projection()).equals(annual);
                        case RetainedProjectionSnapshot.SPOUSAL_401K_ROLLOVER_SCHEMA ->
                                RetainedProjectionSnapshot.captureSpousal401kRollover(done.projection()).equals(annual);
                        case RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_IRA_SCHEMA ->
                                RetainedProjectionSnapshot.captureSpousalBeneficiaryIraRmd(done.projection()).equals(annual);
                        case RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_401K_SCHEMA ->
                                RetainedProjectionSnapshot.captureSpousalBeneficiary401kRmd(done.projection()).equals(annual);
                        default -> (versionedRoth ? RetainedProjectionSnapshot.capture(done.projection())
                                : RetainedProjectionSnapshot.captureSpousalIraRmd(done.projection())).equals(annual);
                    }) return;
        } catch (java.util.concurrent.CancellationException canceled) {
            throw canceled;
        } catch (RuntimeException failure) {
            var invalid = new com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable(year, mismatch,
                    "Frozen inputs do not support the recorded applied IRA calculation.");
            invalid.initCause(failure);
            throw invalid;
        }
        throw new com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable(year, mismatch,
                "Annual obligations, debits or financial totals differ from the frozen calculation.");
    }

    private static boolean matchesVersionedRoute(RetirementPlan plan, RetainedProjectionSnapshot annual) {
        int schema = annual.schemaVersion();
        if (ProjectionEngine.requestsSpousalPostRbd401kOwner(plan))
            return schema == RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_OWNER_SCHEMA;
        if (ProjectionEngine.requestsSpousalPostRbd401k(plan))
            return schema == RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA;
        if (ProjectionEngine.requestsSpousalIraDeathYear(plan))
            return schema == RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA;
        if (ProjectionEngine.requestsAppliedSpousalIraRmd(plan))
            return schema == RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA;
        return schema == RetainedProjectionSnapshot.SCHEMA || schema == RetainedProjectionSnapshot.AVAILABILITY_SCHEMA;
    }

    public RetirementPlan restoreExecutionPlan() {
        return executionPlan(restoreInputs());
    }

    private static RetirementPlan executionPlan(RetirementPlanSnapshot input) {
        var plan = new RetirementPlan(input.getHousehold(), input.getAccountPortfolio(), input.getPlanningAssumptions(),
                input.getRothConversionRequest(), input.getNonInvestableAssets());
        plan.setConditionalBenefits(input.getConditionalBenefits());
        if (input.getFinancialCalculationMode() != null) plan.setFinancialCalculationMode(input.getFinancialCalculationMode());
        return plan;
    }

    private static void validateArkansasLifePrincipal(RetirementPlanSnapshot inputs, RetainedProjectionSnapshot annual,
            String provenance) {
        var assumptions = inputs.getPlanningAssumptions();
        var definitions = inputs.getConditionalBenefits();
        boolean scoped = annual.arkansas().hasLifePrincipalPolicy();
        if (scoped != (assumptions.getStateTaxAssumptions().jurisdiction() == StateTaxJurisdiction.AR
                && !definitions.isEmpty())) {
            throw new IllegalArgumentException("Arkansas life insurance policy does not match the original inputs.");
        }
        if (!scoped) return;
        if (!hasArkansasProductionStamp(provenance) || !provenance.startsWith(ConditionalBenefitExecutionSemantics.KEY)) {
            throw new IllegalArgumentException("Arkansas life insurance requires deterministic execution provenance.");
        }
        ConditionalBenefitAdmission.requireArkansasLifePrincipal(
                inputs.getHousehold(), definitions, inputs.getFinancialCalculationMode());
        var opening = assumptions.getProjectionStartDate();
        if (annual.years().getFirst().year() != opening.getYear()
                || annual.years().size() != assumptions.getProjectionLengthYears()) {
            throw new IllegalArgumentException("Arkansas life insurance requires the original deterministic horizon.");
        }
        var deaths = EffectiveHouseholdDeathView.resolve(inputs.getHousehold(), assumptions.getDeathScenarioAssumptions(),
                Optional.empty());
        var evaluator = new ConditionalBenefitEvaluator();
        for (int i = 0; i < annual.years().size(); i++) {
            var year = annual.years().get(i);
            var evidence = annual.arkansas().years().get(i).sourceEvidence();
            var recordedDeaths = new EnumMap<AccountOwnership, LocalDate>(AccountOwnership.class);
            for (var owner : inputs.getHousehold().peopleByOwner().keySet()) {
                deaths.deathDate(owner).filter(date -> date.getYear() <= year.year())
                        .ifPresent(date -> recordedDeaths.put(owner, date));
            }
            var expected = evaluator.evaluate(definitions,
                    BenefitEvaluationContext.capture(inputs.getHousehold(), deaths, opening, year.year()));
            var actual = year.sources().stream().map(RetainedProjectionSnapshot.Income::conditionalBenefit)
                    .filter(Objects::nonNull).toList();
            var firstReceipt = i == 0 ? opening : LocalDate.of(year.year(), 1, 1);
            if (!expected.receipts().equals(actual) || !recordedDeaths.equals(evidence.modeledDeaths())
                    || !firstReceipt.equals(evidence.firstProjectedReceipt())) {
                throw new IllegalArgumentException("Arkansas life insurance evidence differs from original death or payment facts.");
            }
            var guaranteed = year.sources().stream().filter(source -> source.kind() == Kind.PENSION)
                    .map(RetainedProjectionSnapshot.Income::gross)
                    .reduce(year.socialSecurity().householdBenefit().add(expected.gross()), BigDecimal::add);
            if (guaranteed.compareTo(year.settlement().guaranteedIncome()) != 0) {
                throw new IllegalArgumentException("Arkansas life insurance is not reconciled to retained household cash sources.");
            }
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
                SpousalInheritancePlanningEvidence.completed(frozenPlan.getPlanningAssumptions().getEconomicAssumptions().getSpousalInheritance(),
                        inputs, retained, ConditionalBenefitExecutionSemantics.completed(frozenPlan, inputs, retained,
                        PensionSelectionSemantics.PROVENANCE + (ProjectionEngine.selectsArkansas(frozenPlan)
                                ? ARKANSAS_PRODUCTION : "Controlled Arkansas projection"))),
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

    public String rothSchedulingDisclosure() {
        if (!provenance.contains(ROTH_POLICY)) return "";
        var request = restoreInputs().getRothConversionRequest();
        var schedule = request.getScheduling();
        String target = switch (request.getStrategy()) {
            case FIXED_AMOUNT -> " $" + request.getAnnualAmount().toPlainString();
            case CUSTOM_TAXABLE_INCOME_TARGET -> " $" + request.getCustomTargetTaxableIncome().toPlainString();
            default -> "";
        };
        return "Applied Roth schedule " + request.getStartYear() + "–" + schedule.conversionEndYear()
                + ". " + schedule.rmdSchedulingPolicy().label() + ". " + request.getFrequency()
                + " " + request.getStrategy().name().replace('_', ' ') + target + ".";
    }

    public String inheritancePlanningDisclosure() {
        return SpousalInheritancePlanningEvidence.disclosure(inputsDocument, annual);
    }
    public boolean spousalIraRmdApplied() {
        return SpousalInheritancePlanningEvidence.applied(annual);
    }
    public com.daviddunn.retirementplanner.domain.projection.Projection restoreProjection() {
        return spousalIraRmdApplied() ? annual.restoreSpousalIraRmd(this) : annual.restoreProjection();
    }
    /** Separate from exact configured-input identity, which still permits historical swaps. */
    public boolean calculationSemanticsCurrent() {
        String calculationProvenance = SpousalInheritancePlanningEvidence.calculationProvenance(provenance);
        if (arkansasHistory()) {
            var reviewed = ArkansasValidationTax.reviewedVersions();
            if (annual.arkansas().hasLifePrincipalPolicy()) {
                reviewed = ArkansasValidationTax.withLifePrincipalPolicy(reviewed);
            }
            if (annual.arkansas().hasRegularTaxOnlyPolicy()) {
                reviewed = ArkansasValidationTax.withRegularTaxOnlyPolicy(reviewed);
            }
            return hasArkansasProductionStamp(calculationProvenance)
                    && annual.arkansas().projectionAlgorithm().equals(ProjectionEngine.ALGORITHM_VERSION)
                    && annual.arkansas().versions().equals(reviewed);
        }
        // A pension-selection stamp cannot attest to an unimplemented benefit capability.
        // Decode property names rather than searching raw JSON (which may escape them).
        try {
            var original = restoreInputs();
            if (original.getPlanningAssumptions().getEconomicAssumptions().getSpousalInheritance() != null) {
                var plan = executionPlan(original);
                if (ProjectionEngine.requestsSpousalPostRbd401kOwner(plan)) {
                    if (annual.schemaVersion() != RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_OWNER_SCHEMA) return false;
                } else if (ProjectionEngine.requestsSpousalPostRbd401k(plan)) {
                    if (annual.schemaVersion() != RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA) return false;
                } else if (ProjectionEngine.requestsSpousalIraDeathYear(plan)) {
                    if (annual.schemaVersion() != RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA) return false;
                } else if (annual.schemaVersion() != RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA
                        && ProjectionEngine.requestsAppliedSpousalIraRmd(plan)) return false;
            }
            if (original.getPlanningAssumptions().getStateTaxAssumptions().usesArkansasRegularTaxOnly()) return false;
            if (original.getFinancialCalculationMode() != null || !original.getConditionalBenefits().isEmpty()) {
                return ConditionalBenefitExecutionSemantics.current(inputsDocument,annual,calculationProvenance);
            }
        }
        catch (RuntimeException unavailable) { return false; }
        if (calculationProvenance.startsWith(PensionSelectionSemantics.PROVENANCE)) return true;
        if (calculationProvenance.startsWith(PensionSelectionSemantics.KEY + "=")) return false;
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
        var projection = restoreProjection();
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
