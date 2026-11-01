package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmployerRothAvailabilityTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Set<AccountOwnership> OWNERS = Set.of(AccountOwnership.PRIMARY);

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"TRADITIONAL_401K", "TRADITIONAL_403B"})
    void exactCalendarBoundaryAndOtherSourcesRemainUsable(AccountType type) {
        var employer = RothEligibilityExecutionTest.account(type, AccountOwnership.PRIMARY, new BigDecimal("100000.00"));
        employer.setRothConversionAvailability(new EmployerRothConversionAvailability(
                EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2030));
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("10000.00"));
        var roth = new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO);
        var portfolio = RothEligibilityExecutionTest.portfolio(employer, ira, roth);
        var converter = new ProjectedPortfolioRothConverter();
        assertEquals(new BigDecimal("10000.00"), converter.getMaximumConvertibleAmount(portfolio, OWNERS, 2029));
        assertEquals(new BigDecimal("110000.00"), converter.getMaximumConvertibleAmount(portfolio, OWNERS, 2030));
        var early = converter.convertHousehold(portfolio, new BigDecimal("50000.00"), OWNERS, 2029);
        assertEquals(new BigDecimal("10000.00"), early.getTotalConversion());
        assertSame(ira, early.getAllocations().getFirst().sourceAccount());
        var ready = converter.convertHousehold(portfolio, new BigDecimal("50000.00"), OWNERS, 2030);
        assertSame(employer, ready.getAllocations().getFirst().sourceAccount());
        assertEquals(new BigDecimal("50000.00"), ready.getPortfolio().getBalance(employer));
        assertEquals(new BigDecimal("50000.00"), ready.getPortfolio().getBalance(roth));
        assertEquals(portfolio.getTotalBalance(), ready.getPortfolio().getTotalBalance());
        assertThrows(IllegalArgumentException.class, () -> converter.getMaximumConvertibleAmount(portfolio));
        assertThrows(IllegalArgumentException.class, () -> converter.convertHousehold(portfolio, BigDecimal.ONE));
        assertEquals(new BigDecimal("100000.00"), employer.getCurrentBalance());
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"TRADITIONAL_401K", "TRADITIONAL_403B"})
    void unavailableWithoutYearNeverUnlocksAndLegacyUnknownStillExecutes(AccountType type) {
        var source = RothEligibilityExecutionTest.account(type, AccountOwnership.PRIMARY, new BigDecimal("100000"));
        var roth = new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO);
        var portfolio = RothEligibilityExecutionTest.portfolio(source, roth);
        var converter = new ProjectedPortfolioRothConverter();
        assertEquals(new BigDecimal("100000"), converter.getMaximumConvertibleAmount(portfolio));
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(
                EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, null));
        for (int year : List.of(2026, 2030, 2100)) {
            assertEquals(0, converter.getMaximumConvertibleAmount(portfolio, OWNERS, year).signum());
            assertEquals(0, converter.convertHousehold(portfolio, BigDecimal.TEN, OWNERS, year).getTotalConversion().signum());
            assertThrows(IllegalStateException.class, () -> converter.convert(portfolio, AccountOwnership.PRIMARY, BigDecimal.TEN, year));
        }
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.AVAILABLE, null));
        assertEquals(new BigDecimal("100000"), converter.getMaximumConvertibleAmount(portfolio));
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"OTHER\"}",
            "{\"status\":1}", "{\"status\":\"AVAILABLE\",\"firstEligibleYear\":2030}",
            "{\"status\":\"NOT_YET_AVAILABLE\",\"firstEligibleYear\":2030.1}",
            "{\"status\":\"NOT_YET_AVAILABLE\",\"firstEligibleYear\":\"2030\"}",
            "{\"status\":\"AVAILABLE\",\"extra\":true}"})
    void malformedExplicitAssumptionsAreRejected(String document) {
        assertThrows(Exception.class, () -> json.readValue(document, EmployerRothConversionAvailability.class));
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"TRADITIONAL_401K", "TRADITIONAL_403B"}, mode = EnumSource.Mode.EXCLUDE)
    void unsupportedAccountTypesRejectProperty(AccountType type) {
        var account = RothEligibilityExecutionTest.account(type, AccountOwnership.PRIMARY, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> account.setRothConversionAvailability(
                new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.AVAILABLE, null)));
    }

    @Test void annualExecutionResolvesEachYearAndPreservesRmdOrder() {
        var plan = RothEligibilityFinancialTest.plan(false, AccountType.TRADITIONAL_401K, AccountType.ROTH_IRA,
                RothConversionStrategy.FIXED_AMOUNT);
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(
                EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2027));
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(assumptions.getEconomicAssumptions(), assumptions.getTaxAssumptions(),
                assumptions.getWithdrawalAssumptions(), assumptions.getDeathScenarioAssumptions(), 3,
                assumptions.getProjectionStartDate()).withStateTaxAssumptions(assumptions.getStateTaxAssumptions()));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, new BigDecimal("1000.00"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        var original = CompleteProjectionSnapshot.freeze(plan);
        var projection = new ProjectionEngine().project(plan);
        assertEquals(0, projection.getYearAt(0).getRothConversion().signum());
        assertEquals(0, new BigDecimal("1000").compareTo(projection.getYearAt(1).getRothConversion()));
        assertEquals(0, new BigDecimal("1000").compareTo(projection.getYearAt(2).getRothConversion()));
        assertEquals(original, CompleteProjectionSnapshot.freeze(plan));
        plan.getHousehold().getPrimaryPerson().setBirthDate(java.time.LocalDate.of(1950, 1, 1));
        source.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                2026, source.getCurrentBalance(), BigDecimal.ZERO));
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, null));
        var rmdYear = new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(rmdYear.getRequiredMinimumDistribution().signum() > 0);
        assertEquals(0, rmdYear.getRothConversion().signum());
    }

    @Test void schemaOneStaysExactAndSchemaTwoSurvivesStrictRoundTripSaveAndIndependentSaveAs() throws Exception {
        var plan = RothEligibilityFinancialTest.plan(true, AccountType.TRADITIONAL_401K, AccountType.ROTH_IRA,
                RothConversionStrategy.FIXED_AMOUNT);
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        String legacy = CompleteProjectionSnapshot.freeze(plan);
        assertFalse(legacy.contains("rothConversionAvailability"));
        source.setRothConversionAvailability(null);
        assertEquals(legacy, CompleteProjectionSnapshot.freeze(plan));
        var codec = new RetainedProjectionCodec();
        var old = RetainedProjectionSnapshot.capture(new ProjectionEngine().project(plan));
        assertEquals(1, old.schemaVersion());
        var oldBytes = codec.encode(old).bytes();
        assertEquals(old, codec.decode(oldBytes));
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2027));
        var configured = CompleteProjectionSnapshot.freeze(plan);
        assertNotEquals(CompleteProjectionSnapshot.digest(legacy), CompleteProjectionSnapshot.digest(configured));
        var retained = RetainedProjectionSnapshot.capture(new ProjectionEngine().project(plan));
        assertEquals(2, retained.schemaVersion());
        var decoded = codec.decode(codec.encode(retained).bytes());
        assertEquals(retained, RetainedProjectionSnapshot.capture(decoded.restoreProjection()));
        assertEquals(old, codec.decode(oldBytes));
        var repository = new JsonRetirementPlanRepository();
        repository.save(plan, directory.resolve("original.json"));
        var loaded = repository.load(directory.resolve("original.json"));
        assertEquals(configured, CompleteProjectionSnapshot.freeze(loaded));
        repository.saveWithIdentity(loaded, directory.resolve("copy.json"), UUID.randomUUID());
        assertEquals(configured, CompleteProjectionSnapshot.freeze(repository.load(directory.resolve("copy.json"))));
        var raw = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(codec.encode(retained).bytes())).readAllBytes();
        var node = new ObjectMapper().readTree(raw);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("schemaVersion", 1);
        var corrupted = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(corrupted)) { gzip.write(new ObjectMapper().writeValueAsBytes(node)); }
        assertThrows(Exception.class, () -> codec.decode(corrupted.toByteArray()));
    }

    @Test void availabilityChangesFinancialButNotSocialSecurityOnlyFingerprints() {
        var plan = com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.plan(false);
        var source = new Traditional401K("Employer", AccountOwnership.PRIMARY, BigDecimal.TEN);
        plan.getAccountPortfolio().addAccount(source);
        var service = new AnalysisFingerprintService();
        var inputs = new AnalysisFingerprintService.Inputs(BigDecimal.ONE, null,
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 1), BigDecimal.ZERO,
                null, null, List.of(), List.of(), null);
        String ss = service.fingerprint(AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY, plan, inputs);
        String deterministic = service.fingerprint(AnalysisKind.INDIVIDUAL_DETERMINISTIC, plan, AnalysisFingerprintService.Inputs.none());
        var mc = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService();
        var settings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(2, 123, BigDecimal.ZERO, BigDecimal.ZERO);
        String stochastic = mc.assess(com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService.standalone(plan, settings, null)).fingerprint();
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2030));
        assertEquals(ss, service.fingerprint(AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY, plan, inputs));
        assertNotEquals(deterministic, service.fingerprint(AnalysisKind.INDIVIDUAL_DETERMINISTIC, plan, AnalysisFingerprintService.Inputs.none()));
        assertNotNull(stochastic);
        assertNotEquals(stochastic, mc.assess(com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService.standalone(plan, settings, null)).fingerprint());
    }

    @Test void persistedCompleteSnapshotRetainsSchemaTwoWithoutRecalculation() throws Exception {
        var plan = RothEligibilityFinancialTest.plan(false, AccountType.TRADITIONAL_403B, AccountType.ROTH_IRA,
                RothConversionStrategy.FIXED_AMOUNT);
        plan.getAccountPortfolio().getAccounts().getFirst().setRothConversionAvailability(
                new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2030));
        var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(
                new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(directory.resolve("settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analyses"))));
        com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(controller, "currentPlan", plan);
        controller.getCurrentProjection();
        var original = controller.getAcceptedProjectionSnapshot();
        assertNotNull(original); assertEquals(2, original.annual().schemaVersion());
        controller.saveAs(directory.resolve("accepted.json"));
        var loaded = new JsonRetirementPlanRepository().load(directory.resolve("accepted.json"));
        var restored = new ProjectionSnapshotComponents().load(directory.resolve("accepted.json"), loaded.getProjectionState())
                .get(loaded.getProjectionState().current().id());
        assertEquals(original.id(), restored.id());
        assertEquals(original.annual(), restored.annual());
        assertEquals(original.inputsDocument(), restored.inputsDocument());
        assertEquals(original.inputDigest(), restored.inputDigest());
        assertEquals(0, restored.annual().restoreProjection().getYearAt(0).getRothConversion().signum());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void seededMonteCarloUsesFrozenAvailabilityWithoutMutatingInputs(boolean couple) {
        var plan = RothEligibilityFinancialTest.plan(couple, AccountType.TRADITIONAL_401K, AccountType.ROTH_IRA,
                RothConversionStrategy.FIXED_AMOUNT);
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2030));
        String original = CompleteProjectionSnapshot.freeze(plan);
        var settings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(3, 431, BigDecimal.ZERO, new BigDecimal("0.02"));
        var engine = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer();
        var first = engine.analyze(plan, settings);
        var again = engine.analyze(plan, settings);
        assertEquals(first.outcomes(), again.outcomes());
        for (var outcome : first.outcomes()) assertEquals(0, outcome.metrics().orElseThrow().lifetimeRothConversions().signum());
        assertEquals(original, CompleteProjectionSnapshot.freeze(plan));
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.AVAILABLE, null));
        for (var outcome : engine.analyze(plan, settings).outcomes())
            assertEquals(0, new BigDecimal("50000").compareTo(outcome.metrics().orElseThrow().lifetimeRothConversions()));
    }

    @Test void pairedCandidatesFreezeAvailabilityAndShareUnchangedWorlds() {
        var plan = RothEligibilityFinancialTest.plan(false, AccountType.TRADITIONAL_403B, AccountType.ROTH_IRA,
                RothConversionStrategy.FIXED_AMOUNT);
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.AVAILABLE, null));
        var a = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyCandidate("A", plan);
        var b = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyCandidate("B", plan);
        var expectedEstate = new ProjectionEngine().project(plan).getYearAt(0).getAfterTaxEstateValue();
        source.setRothConversionAvailability(new EmployerRothConversionAvailability(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, null));
        var settings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(2, 7, BigDecimal.ZERO, BigDecimal.ZERO);
        var lifetime = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloLifetime.Individual(
                new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloLifetime.Life(Optional.empty()));
        var request = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonRequest(a, b,
                new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonRequest.Fixed(
                        settings, java.time.LocalDate.of(2026, 1, 1), 2026, lifetime));
        var result = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request);
        for (var pair : result.outcomes()) {
            assertEquals(pair.outcomeA(), pair.outcomeB());
            assertEquals(0, expectedEstate.compareTo(pair.outcomeA().terminal().orElseThrow().afterTaxEstate()));
        }
    }
}
