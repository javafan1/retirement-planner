package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RothConversionDestinationServiceTest {
    @TempDir Path directory;
    private final RothConversionDestinationService service = new RothConversionDestinationService();

    @Test void createsPrimaryDestinationOnceAndPreservesExactRequest() {
        var plan = plan(false);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Primary Traditional IRA", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        var request = request(2026);
        var owners = service.ownersRequiringDestination(plan, request);
        assertEquals(List.of(AccountOwnership.PRIMARY), owners);

        var applied = service.apply(plan, request);
        assertTrue(applied.changed());
        assertEquals(List.of(AccountOwnership.PRIMARY), applied.createdOwners());
        var modeled = plan.getAccountPortfolio().getAccounts().stream().filter(RothIRA.class::isInstance)
                .map(RothIRA.class::cast).findFirst().orElseThrow();
        assertEquals(AccountOwnership.PRIMARY, modeled.getOwnership());
        assertEquals(BigDecimal.ZERO, modeled.getCurrentBalance());
        assertTrue(modeled.getName().contains("Modeled"));
        assertSame(request, plan.getRothConversionRequest());

        assertTrue(service.ownersRequiringDestination(plan, request).isEmpty());
        assertFalse(service.apply(plan, request).changed());
        assertEquals(1, plan.getAccountPortfolio().getAccounts().stream().filter(RothIRA.class::isInstance).count());
    }

    @Test void onlyCreatesForOwnersWithUsableSourcesAndReusesOrdinaryRothIras() {
        var plan = plan(true);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Spouse IRA", AccountOwnership.SPOUSE, BigDecimal.ZERO));
        var spouseRoth = new RothIRA("Spouse existing", AccountOwnership.SPOUSE, BigDecimal.ZERO);
        plan.getAccountPortfolio().addAccount(spouseRoth);
        plan.getAccountPortfolio().addAccount(new Roth401K("Primary designated Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        assertEquals(List.of(AccountOwnership.PRIMARY), service.ownersRequiringDestination(plan, request(2026)));
        service.apply(plan, request(2026));
        assertSame(spouseRoth, plan.getAccountPortfolio().getAccounts().stream()
                .filter(account -> account.getOwnership() == AccountOwnership.SPOUSE && account instanceof RothIRA)
                .findFirst().orElseThrow());
        assertEquals(1, plan.getAccountPortfolio().getAccounts().stream()
                .filter(account -> account.getOwnership() == AccountOwnership.PRIMARY && account instanceof RothIRA).count());
    }

    @Test void inheritedWrongOwnerAndUnsupportedDestinationsDoNotSatisfyDestinationRequirement() {
        var plan = plan(false);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(new InheritedRothIRA("Inherited", AccountOwnership.PRIMARY,
                BigDecimal.ZERO, new InheritedAccountInformation(LocalDate.of(1940, 1, 1),
                LocalDate.of(2020, 1, 1), BeneficiaryRelationship.OTHER)));
        plan.getAccountPortfolio().addAccount(new RothIRA("Spouse Roth", AccountOwnership.SPOUSE, BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("No owner", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        assertEquals(List.of(AccountOwnership.PRIMARY), service.ownersRequiringDestination(plan, request(2026)));
    }

    @Test void employerEligibilityUsesScheduleAndHorizonAndLegacyUnknownRemainsUsable() {
        var plan = plan(false);
        var employer = new Traditional401K("Primary 401(k)", AccountOwnership.PRIMARY, BigDecimal.ZERO);
        employer.setRothConversionAvailability(new EmployerRothConversionAvailability(
                EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2030));
        plan.getAccountPortfolio().addAccount(employer);
        plan.setPlanningAssumptions(new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 10,
                LocalDate.of(2026, 1, 1)));
        assertEquals(List.of(AccountOwnership.PRIMARY), service.ownersRequiringDestination(plan, request(2028)));
        assertTrue(service.ownersRequiringDestination(plan, request(2040)).isEmpty(),
                "A schedule beginning after the modeled horizon does not create a destination.");

        employer.setRothConversionAvailability(new EmployerRothConversionAvailability(
                EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, null));
        assertThrows(IllegalArgumentException.class, () -> service.ownersRequiringDestination(plan, request(2026)));
        employer.setRothConversionAvailability(null);
        assertEquals(List.of(AccountOwnership.PRIMARY), service.ownersRequiringDestination(plan, request(2026)));
    }

    @Test void disabledRequestAndInvalidNewEnablementNeverCreateAccounts() {
        var plan = plan(false);
        int count = plan.getAccountPortfolio().getAccounts().size();
        var disabled = new RothConversionRequest(false, 2026, BigDecimal.ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME);
        assertTrue(service.ownersRequiringDestination(plan, disabled).isEmpty());
        service.apply(plan, disabled);
        assertEquals(count, plan.getAccountPortfolio().getAccounts().size());

        assertThrows(IllegalArgumentException.class, () -> service.apply(plan, request(2026)));
        assertEquals(count, plan.getAccountPortfolio().getAccounts().size());
        assertNotNull(plan.getRothConversionRequest());
        assertFalse(plan.getRothConversionRequest().isEnabled());
    }

    @Test void twoOwnerCreationRollsBackCompletelyWhenSecondAdditionFails() {
        var primary = new Person("Primary", "Person", LocalDate.of(1965, 1, 1));
        var spouse = new Person("Spouse", "Person", LocalDate.of(1966, 1, 1));
        var portfolio = new FailingPortfolio(List.of(
                new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY, BigDecimal.ZERO),
                new TraditionalIRA("Spouse IRA", AccountOwnership.SPOUSE, BigDecimal.ZERO)));
        var plan = new RetirementPlan(new Household(primary, spouse), portfolio,
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 1, LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalStateException.class, () -> service.apply(plan, request(2026)));
        assertEquals(2, portfolio.getAccounts().size());
        assertNull(plan.getRothConversionRequest());
    }

    @Test void automaticallyModeledDestinationPersistsAndAppearsInSnapshotsWithoutChangingTheirSchema() throws Exception {
        var plan = plan(false);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY, new BigDecimal("100000")));
        var request = request(2026);
        service.apply(plan, request);
        String frozen = CompleteProjectionSnapshot.freeze(plan);
        assertTrue(frozen.contains("Primary Roth IRA (Modeled)"));
        var repository = new JsonRetirementPlanRepository();
        var file = directory.resolve("plan.json");
        repository.save(plan, file);
        var loaded = repository.load(file);
        assertEquals(frozen, CompleteProjectionSnapshot.freeze(loaded));
        repository.saveWithIdentity(loaded, directory.resolve("copy.json"), java.util.UUID.randomUUID());
        assertEquals(frozen, CompleteProjectionSnapshot.freeze(repository.load(directory.resolve("copy.json"))));

        var retained = RetainedProjectionSnapshot.capture(new ProjectionEngine().project(loaded));
        assertEquals(1, retained.schemaVersion(), "Ordinary Roth IRA creation must not bump retained schema.");
        var decoded = new RetainedProjectionCodec().decode(new RetainedProjectionCodec().encode(retained).bytes());
        assertEquals(retained, RetainedProjectionSnapshot.capture(decoded.restoreProjection()));
    }

    @Test void automaticDestinationHasTheSameFinancialBehaviorAsAnEquivalentManualRothIra() {
        var automatic = plan(false);
        automatic.getAccountPortfolio().addAccount(new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("100000.00")));
        automatic.getAccountPortfolio().addAccount(new BrokerageAccount("Tax funding", AccountOwnership.PRIMARY,
                new BigDecimal("100000.00")));
        var request = new RothConversionRequest(true, 2026, new BigDecimal("50000.00"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME);
        service.apply(automatic, request);

        var manual = plan(false);
        manual.getAccountPortfolio().addAccount(new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("100000.00")));
        manual.getAccountPortfolio().addAccount(new BrokerageAccount("Tax funding", AccountOwnership.PRIMARY,
                new BigDecimal("100000.00")));
        manual.getAccountPortfolio().addAccount(new RothIRA("Primary Roth IRA (Modeled)", AccountOwnership.PRIMARY,
                BigDecimal.ZERO));
        manual.setRothConversionRequest(request);

        var automaticYear = new ProjectionEngine().project(automatic).getYearAt(0);
        var manualYear = new ProjectionEngine().project(manual).getYearAt(0);
        assertEquals(manualYear.getRothConversion(), automaticYear.getRothConversion());
        assertEquals(manualYear.getFederalIncomeTax(), automaticYear.getFederalIncomeTax());
        assertEquals(manualYear.getTaxFundingWithdrawal(), automaticYear.getTaxFundingWithdrawal());
        assertEquals(manualYear.getEndingInvestableAssets(), automaticYear.getEndingInvestableAssets());
        assertEquals(manualYear.getAfterTaxEstateValue(), automaticYear.getAfterTaxEstateValue());
        assertEquals(new BigDecimal("50000.00"), automaticYear.getRothConversion());

        var monteCarloSettings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(
                2, 177, BigDecimal.ZERO, BigDecimal.ZERO);
        var analyzer = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer();
        assertEquals(analyzer.analyze(manual, monteCarloSettings).outcomes(),
                analyzer.analyze(automatic, monteCarloSettings).outcomes());
    }

    @Test void namesAreCollisionSafeAndAccountOrderingIsPrimaryThenSpouse() {
        var plan = plan(true);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Spouse IRA", AccountOwnership.SPOUSE, BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(new BrokerageAccount("Primary Roth IRA (Modeled)", AccountOwnership.PRIMARY,
                BigDecimal.ZERO));
        var result = service.apply(plan, request(2026));
        assertEquals(List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE), result.createdOwners());
        var created = plan.getAccountPortfolio().getAccounts().stream().filter(RothIRA.class::isInstance).toList();
        assertEquals("Primary Roth IRA (Modeled) 2", created.get(0).getName());
        assertEquals("Spouse Roth IRA (Modeled)", created.get(1).getName());
    }

    private static RetirementPlan plan(boolean couple) {
        Person primary = new Person("Primary", "Person", LocalDate.of(1965, 1, 1));
        Household household = couple
                ? new Household(primary, new Person("Spouse", "Person", LocalDate.of(1966, 1, 1)))
                : new Household(primary);
        return new RetirementPlan(household, new AccountPortfolio(),
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 1, LocalDate.of(2026, 1, 1)));
    }

    private static RothConversionRequest request(int startYear) {
        return new RothConversionRequest(true, startYear, new BigDecimal("50000.00"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL);
    }

    private static final class FailingPortfolio extends AccountPortfolio {
        private final List<Account> internal = new ArrayList<>();
        FailingPortfolio(List<Account> accounts) { super(accounts); internal.addAll(accounts); }
        @Override public void addAccount(Account account) {
            if (account.getOwnership() == AccountOwnership.SPOUSE) throw new IllegalStateException("test write failure");
            internal.add(account);
        }
        @Override public void removeAccount(Account account) { internal.remove(account); }
        @Override public List<Account> getAccounts() { return List.copyOf(internal); }
    }
}
