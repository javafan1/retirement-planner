package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KentuckyProjectionAcceptanceTest {

    public static RetirementPlan plan(boolean couple) {
        var plan = PennsylvaniaProductionProjectionTest.plan(couple, StateTaxJurisdiction.KY);
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        if (couple) plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.FEMALE);
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(
                assumptions.getEconomicAssumptions(),
                assumptions.getTaxAssumptions(),
                assumptions.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, 67, BigDecimal.ONE),
                3,
                assumptions.getProjectionStartDate(),
                assumptions.getStateTaxAssumptions()));
        plan.setRothConversionRequest(new RothConversionRequest(
                true, 2027, new BigDecimal("100000"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        return plan;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void singleAndCoupleRmdConversionAndFundingSurvivePersistence(boolean couple, @TempDir Path directory)
            throws Exception {
        var plan = plan(couple);
        var balances = plan.getAccountPortfolio().getAccounts().stream().map(Account::getCurrentBalance).toList();
        var projection = new ProjectionEngine().project(plan);
        assertEquals(List.of(2027, 2028, 2029), projection.getYears().stream()
                .map(ProjectionYear::getCalendarYear).toList());
        for (var year : projection.getYears()) {
            assertKentuckyYear(year);
            assertTrue(year.getRmdDistributedInProjection().signum() > 0);
            assertEquals(0, year.getRothConversion().compareTo(new BigDecimal("100000")));
            var kinds = year.getIncomeSources().stream().map(IncomeSourceAttribution::kind).toList();
            assertTrue(kinds.indexOf(IncomeSourceAttribution.Kind.RMD)
                    < kinds.indexOf(IncomeSourceAttribution.Kind.ROTH_CONVERSION));
            assertTrue(year.getTaxFundingWithdrawal().signum() > 0);
            assertEquals(0, year.getBeginningInvestableAssets().add(year.getInvestmentGrowth())
                    .subtract(year.getPortfolioWithdrawal()).add(year.getRetainedHouseholdSurplus())
                    .setScale(2, RoundingMode.HALF_UP).compareTo(year.getEndingInvestableAssets()));
            if (!couple) {
                assertTrue(year.spouseRothConversion().isEmpty());
                assertTrue(year.getIncomeSources().stream().noneMatch(source ->
                        source.owner().orElse(null) == AccountOwnership.SPOUSE));
            }
        }
        assertEquals(0, projection.getYearAt(0).getRmdDistributedBeforeProjection()
                .compareTo(new BigDecimal(couple ? "20000" : "15000")));
        assertEquals(balances, plan.getAccountPortfolio().getAccounts().stream().map(Account::getCurrentBalance).toList());
        var repository = new JsonRetirementPlanRepository();
        var file = directory.resolve("kentucky.json");
        repository.save(plan, file);
        var loaded = repository.load(file);
        assertEquals(StateTaxJurisdiction.KY, loaded.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        assertEquals(couple, loaded.getHousehold().hasSpouse());
        for (var candidate : List.of(plan, loaded, new RetirementPlanScenarioCopyService().copy(loaded))) {
            assertEquals(FloridaProductionProjectionTest.financialRows(projection),
                    FloridaProductionProjectionTest.financialRows(new ProjectionEngine().project(candidate)));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capacityLimitedConversionTaxesExecutedAmountsAndKeepsOwners(boolean couple) {
        var plan = plan(couple);
        for (var account : plan.getAccountPortfolio().getAccounts()) {
            if (account.getType() == AccountType.CHECKING) {
                account.setCurrentBalance(new BigDecimal("20000000"));
            }
        }
        plan.setRothConversionRequest(new RothConversionRequest(
                true, 2027, new BigDecimal("5000000"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var year = new ProjectionEngine().project(plan).getYearAt(0);
        assertKentuckyYear(year);
        assertTrue(year.getRothConversionShortfall().signum() > 0);
        assertTrue(year.getPrimaryRothConversion().signum() > 0);
        if (couple) assertTrue(year.getSpouseRothConversion().signum() > 0);
        var sources = year.getIncomeSources().stream()
                .filter(source -> source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION).toList();
        assertEquals(0, sources.stream().map(IncomeSourceAttribution::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(year.getRothConversion()));
        assertTrue(sources.stream().allMatch(source -> source.owner().orElseThrow()
                == source.destination().orElseThrow().owner()));
        assertTrue(year.getAdjustedGrossIncome().compareTo(year.getRequestedRothConversion()) < 0);
        assertTrue(year.stateTaxCalculation().orElseThrow().limitations().getLast().contains("capped retirement subtraction"));
    }

    @Test
    void missingPrimaryRothDestinationCannotConvertAcrossOwners() {
        var plan = plan(true);
        for (var account : List.copyOf(plan.getAccountPortfolio().getAccounts())) {
            if (account.getOwnership() == AccountOwnership.PRIMARY && account.getType().getTaxTreatment() == TaxTreatment.ROTH) {
                plan.getAccountPortfolio().removeAccount(account);
            } else if (account.getType() == AccountType.CHECKING) {
                account.setCurrentBalance(new BigDecimal("20000000"));
            }
        }
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("5000000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var year = new ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(0, year.getPrimaryRothConversion().signum());
        assertTrue(year.getSpouseRothConversion().signum() > 0);
        assertTrue(year.getRothConversionShortfall().signum() > 0);
        assertTrue(year.getIncomeSources().stream().filter(source -> source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION)
                .allMatch(source -> source.owner().orElseThrow() == AccountOwnership.SPOUSE
                        && source.destination().orElseThrow().owner() == AccountOwnership.SPOUSE));
        assertTrue(year.getStateIncomeTax().signum() > 0);
    }

    @Test
    void kentuckyAdmissionPreservesAnnuityNiitBrokerageSafeguard() {
        var plan = NonqualifiedAnnuityFinancialTest.fixture(true);
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.KY)));
        plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Unclassified gain in Advanced annuity model", AccountOwnership.PRIMARY, new BigDecimal("1000")));
        assertThrows(com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException.class,
                () -> new ProjectionEngine().project(plan));
    }

    @Test
    void savedBaselineRetainsKentuckyAfterCurrentPlanMoves() {
        var plan = plan(true);
        var expected = new ProjectionEngine().project(plan);
        var baseline = com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory.create(plan, "Kentucky");
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var actual = new com.daviddunn.retirementplanner.domain.baseline.BaselineProjectionService(
                new ProjectionEngine()).projectBaseline(baseline);
        assertEquals(FloridaProductionProjectionTest.financialRows(expected), FloridaProductionProjectionTest.financialRows(actual));
        actual.getYears().forEach(KentuckyProjectionAcceptanceTest::assertKentuckyYear);
    }

    @Test
    void admittedAnnuityUsesTaxableReceiptNotGrossAndLeavesFederalNiitUnchanged() {
        var plan = NonqualifiedAnnuityFinancialTest.fixture(true);
        var definition = plan.getConditionalBenefits().getFirst();
        plan.setConditionalBenefits(List.of(com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.change(
                definition, "grossAmount", new BigDecimal("5000"))));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("294240"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var florida = new ProjectionEngine().project(plan).getYearAt(0);
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.KY)));
        var kentucky = new ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(0, new BigDecimal("345600").compareTo(kentucky.getAdjustedGrossIncome()));
        assertEquals(florida.getFederalIncomeTax(), kentucky.getFederalIncomeTax());
        // Primary annuity+conversion share one 31110 cap; one 3470 joint deduction; two 40 senior credits.
        assertEquals(new BigDecimal("10805.70"), kentucky.getStateIncomeTax());
        var receipt = kentucky.getIncomeSources().stream().flatMap(source -> source.conditionalBenefit().stream())
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("60000").compareTo(receipt.gross()));
        assertEquals(0, new BigDecimal("51360").compareTo(receipt.taxable()));
    }

    @Test
    void bracketFillUsesSelectedKentuckyFundingInsteadOfMichiganCompatibilityDefault() {
        var plan = plan(true);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, BigDecimal.ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                RothConversionFrequency.ONE_TIME));
        var projection = new ProjectionEngine().project(plan);
        assertTrue(projection.getYearAt(0).getRothConversion().signum() > 0);
        assertTrue(projection.getYearAt(0).getStateIncomeTax().signum() > 0);
        // Subsequent years have no one-time conversion and can legally have zero
        // Kentucky tax after the standard deduction and senior relief.
        for (var year : projection.getYears()) {
            assertEquals(StateTaxJurisdiction.KY, year.getStateTaxJurisdiction());
            assertEquals(StateTaxCalculationStatus.CALCULATED,
                    year.stateTaxCalculation().orElseThrow().calculationStatus());
            assertTrue(year.getStateIncomeTax().signum() >= 0);
        }
        var totalState = projection.getYears().stream().map(ProjectionYear::getStateIncomeTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalCombined = projection.getYears().stream().map(ProjectionYear::getTotalIncomeTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalFederal = projection.getYears().stream().map(ProjectionYear::getFederalIncomeTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(totalCombined, totalFederal.add(totalState));
    }

    public static void assertKentuckyYear(ProjectionYear year) {
        assertEquals(StateTaxJurisdiction.KY, year.getStateTaxJurisdiction());
        var state = year.stateTaxCalculation().orElseThrow();
        assertEquals(StateTaxCalculationStatus.CALCULATED, state.calculationStatus());
        assertTrue(state.michiganDetails().isEmpty());
        assertFalse(state.limitations().isEmpty());
        assertTrue(year.getStateIncomeTax().signum() > 0);
        assertEquals(year.getFederalIncomeTax().add(year.getStateIncomeTax()), year.getTotalIncomeTax());
    }
}
