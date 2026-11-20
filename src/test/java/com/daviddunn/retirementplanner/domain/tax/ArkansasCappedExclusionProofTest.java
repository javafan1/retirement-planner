package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.AnnualIncomeTaxFacts;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.b;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.couple;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.single;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasCappedExclusionProofTest {
    private static final LocalDate OPENING = LocalDate.of(2026, 1, 1);
    private static final LocalDate PENSION_START = LocalDate.of(2028, 1, 1);
    private static final LocalDate BIRTH = LocalDate.of(1965, 7, 1);
    private static final BigDecimal CENT = b(".01");

    enum Conversion {
        NONE("0", "9650.00", "2398.41", "12048.41"),
        FIXED("5000", "10750.00", "2583.41", "13333.41"),
        TARGET("37800", "17966.00", "4084.31", "22050.31");

        final BigDecimal amount;
        final String federal;
        final String state;
        final String funding;

        Conversion(String amount, String federal, String state, String funding) {
            this.amount = b(amount);
            this.federal = federal;
            this.state = state;
            this.funding = funding;
        }
    }

    @ParameterizedTest
    @EnumSource(Conversion.class)
    void threeYearProductionFixtureFundsThe2028TaxWithoutInferringPensionOrigin(Conversion conversion) {
        // Independent tax arithmetic for the fixture's 2026 nominal federal and Arkansas parameters.
        var federalTaxable = b("84000").add(conversion.amount).subtract(b("16100"));
        var federal = b("12400").multiply(b(".10")).add(b("38000").multiply(b(".12")))
                .add(federalTaxable.subtract(b("50400")).multiply(b(".22")));
        var arkansasNet = b("84000").add(conversion.amount).subtract(b("6000")).subtract(b("2470"));
        var stateBeforeCredit = conversion == Conversion.TARGET
                ? arkansasNet.multiply(b(".037")).subtract(b("79.90"))
                : b("5600").multiply(b(".02")).add(b("4800").multiply(b(".03")))
                        .add(b("10400").multiply(b(".034")))
                        .add(arkansasNet.subtract(b("26400")).multiply(b(".037")));
        var state = stateBeforeCredit.subtract(b("29")).setScale(2, RoundingMode.HALF_UP);
        money(conversion.federal, federal);
        money(conversion.state, state);
        var liability = federal.add(state);
        money(conversion.funding, liability);
        // All admissible earlier points draw only from checking, with no change in taxable income.
        money("0.01", liability.subtract(b(conversion.funding).subtract(CENT)));
        if (conversion == Conversion.TARGET) money("105700", federalTaxable);

        for (boolean documented : List.of(false, true)) {
            var plan = singleFixture(documented, conversion);
            var years = run(plan);
            assertEquals(List.of(2026, 2027, 2028), years.stream().map(ProjectionYear::getCalendarYear).toList());
            var year = years.getLast();
            var tax = year.arkansasValidationTax().orElseThrow();
            money("84000", year.getGuaranteedIncome());
            money(conversion.federal, year.getFederalIncomeTax());
            money(conversion.state, year.getStateIncomeTax());
            money(conversion.funding, year.getTaxFundingWithdrawal());
            money(conversion.amount.toPlainString(), year.getRothConversion());
            money("6000", tax.provenance().election().retirementExclusion());
            money("0", tax.taxableFundingIncome());
            money("0", year.getHouseholdCashSettlement().retainedHouseholdSurplus());
            money("0", year.getRequiredMinimumDistribution());
            var checking = plan.getAccountPortfolio().getAccounts().get(1);
            var funding = year.getIncomeSources().stream()
                    .filter(source -> source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
            assertEquals(1, funding.size());
            var payment = funding.getFirst();
            assertEquals(IncomeSourceAttribution.AccountReference.capture(checking), payment.account().orElseThrow());
            assertEquals(PRIMARY, payment.owner().orElseThrow());
            money(conversion.funding, payment.grossAmount());
            money(b("30000").subtract(liability).toPlainString(), year.getEndingBalance(checking));
            money(b("200000").subtract(conversion.amount).toPlainString(),
                    year.getEndingBalance(plan.getAccountPortfolio().getAccounts().getFirst()));
            money(conversion.amount.toPlainString(), year.getEndingBalance(plan.getAccountPortfolio().getAccounts().getLast()));
            var pensions = year.getIncomeSources().stream()
                    .filter(source -> source.kind() == IncomeSourceAttribution.Kind.PENSION).toList();
            assertEquals(2, pensions.size());
            assertEquals(documented ? RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN : RetirementIncomeSourceOrigin.UNKNOWN,
                    pensions.getLast().pension().orElseThrow().sourceOrigin());
            assertEquals(512, tax.fundingMetrics().candidateBudget());
            assertTrue(tax.fundingMetrics().candidates() < 40, tax.fundingMetrics()::toString);
            if (conversion == Conversion.TARGET) {
                money("105700", year.getFederalTaxableIncome());
                assertEquals(1, tax.targetMetrics().orElseThrow().exactTrials());
            }
            System.out.printf("2028 %s documented=%s funding=%s target=%s%n",
                    conversion, documented, tax.fundingMetrics(), tax.targetMetrics());
        }
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void jointProofKeepsSeparatePoolsForTheActualRecipients(boolean primaryCapped, boolean spouseCapped) throws Exception {
        var plan = selectArkansas(couple(PENSION_START, 1, DeathScenario.BOTH_SURVIVE));
        for (var owner : List.of(PRIMARY, SPOUSE)) {
            boolean capped = owner == PRIMARY ? primaryCapped : spouseCapped;
            addPension(plan, owner, PENSION_START, capped ? "500" : "400", true, null);
            addPension(plan, owner, PENSION_START, "5000", false, null);
        }
        var facts = annual(plan, FilingStatus.MARRIED_FILING_JOINTLY);
        var calculator = new ArkansasProductionTaxCalculator();
        var result = calculator.calculateUnregistered(facts, ArkansasTaxEvidence.NONE);
        var floor = calculator.fundingTaxFloor(facts, ArkansasTaxEvidence.NONE, b("30000"));
        if (primaryCapped && spouseCapped) {
            money("12000", result.election().orElseThrow().retirementExclusion());
            assertTrue(floor.orElseThrow().compareTo(result.incomeTax()) <= 0);
        } else {
            assertTrue(floor.isEmpty());
            var expected = java.util.EnumSet.noneOf(AccountOwnership.class);
            if (!primaryCapped) expected.add(PRIMARY);
            if (!spouseCapped) expected.add(SPOUSE);
            assertEquals(ArkansasTaxIssue.Kind.PENSION_ORIGIN, result.issue().orElseThrow().kind());
            assertEquals(Set.copyOf(expected), result.issue().orElseThrow().reviewRecipients());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"5999.99", "6000", "6000.01"})
    void checksTheExactCapWithoutRoundingOrCountingTheUnknownSource(String certain) throws Exception {
        var plan = selectArkansas(single(BIRTH, PENSION_START, 1, TAXABLE_FIRST, "200000"));
        addPension(plan, PRIMARY, PENSION_START.withMonth(12), certain, true, null);
        addPension(plan, PRIMARY, PENSION_START, "6500", false, null);
        var facts = annual(plan, FilingStatus.SINGLE);
        var calculator = new ArkansasProductionTaxCalculator();
        assertEquals(b(certain).compareTo(b("6000")) >= 0,
                calculator.fundingTaxFloor(facts, ArkansasTaxEvidence.NONE, b("1")).isPresent());
    }

    @Test
    void inheritedCappedBoundCoversDeferredFundingAcrossArkansasRateAndAdjustmentEdges() throws Exception {
        var plan = singleFixture(false, Conversion.NONE);
        var seed = annual(plan, FilingStatus.SINGLE);
        var facts = new ArkansasAnnualFunding.Facts(seed, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new ArkansasAnnualFunding.Cash(b("84000"), ZERO, ZERO, b("84000"), ZERO));
        var evaluator = new ArkansasAnnualFunding.Evaluator(facts, withNoConversion(plan), new TaxDeferredFirstWithdrawalStrategy());
        var floor = evaluator.taxFloor(evaluator.evaluate(ZERO), b("40000")).orElseThrow();
        // Base Arkansas net is 75,530. Enter 94,701, cross several $100 adjustments, and leave at 97,601.
        for (String request : List.of("0", ".01", "19170.99", "19171", "19171.01", "19270.99", "19271",
                "20470.99", "20471", "22070.99", "22071", "40000")) {
            var priced = evaluator.evaluate(b(request)).candidate();
            money(request, priced.taxableFundingIncome());
            assertTrue(floor.compareTo(priced.totalTax()) <= 0, request);
        }
    }

    @Test
    void theLastAllowedExactCandidateCanSucceedButTheNextAttemptExhaustsTheBudget() throws Exception {
        var plan = singleFixture(false, Conversion.NONE);
        var seed = annual(plan, FilingStatus.SINGLE);
        var conversion = withNoConversion(plan);
        var strategy = new TaxableFirstWithdrawalStrategy();
        var solver = new ArkansasMinimumFundingSolver(1);
        var enough = new ArkansasAnnualFunding.Facts(seed, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new ArkansasAnnualFunding.Cash(b("84000"), ZERO, ZERO, b("71951.59"), ZERO));
        var missingCent = new ArkansasAnnualFunding.Facts(seed, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new ArkansasAnnualFunding.Cash(b("84000"), ZERO, ZERO, b("71951.60"), ZERO));
        var succeeded = solver.solve(enough, conversion, strategy);
        money("0", succeeded.candidate().orElseThrow().requested());
        var exhausted = solver.solve(missingCent, conversion, strategy);
        assertEquals(ArkansasTaxIssue.Kind.FUNDING_BUDGET, exhausted.issue().orElseThrow().kind());
        assertTrue(exhausted.candidate().isEmpty());
        assertEquals(1, succeeded.metrics().candidates());
        assertEquals(1, exhausted.metrics().candidates());
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void pensionOriginRemainsWithTheOriginalOwnerAcrossBothSurvivorTransitions(DeathScenario death) {
        var plan = selectArkansas(couple(OPENING, 3, death));
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(death, 2027, 67, ONE), 3, OPENING,
                a.getStateTaxAssumptions()));
        var original = death == DeathScenario.PRIMARY_DIES ? PRIMARY : SPOUSE;
        var survivor = original == PRIMARY ? SPOUSE : PRIMARY;
        addPension(plan, original, OPENING, "2000", true, "2000");
        addPension(plan, original, OPENING, "5000", false, "5000");
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Survivor checking", survivor, b("100000")));
        plan.getHousehold().addExpense(new Expense("Spending", b("84000"), GrowthCategory.NONE,
                OPENING, null, ExpenseType.RECURRING));
        var years = run(plan);
        assertEquals(List.of(FilingStatus.MARRIED_FILING_JOINTLY, FilingStatus.MARRIED_FILING_JOINTLY, FilingStatus.SINGLE),
                years.stream().map(year -> year.arkansasValidationTax().orElseThrow().filingStatus()).toList());
        for (int i = 0; i < years.size(); i++) {
            var year = years.get(i);
            var tax = year.arkansasValidationTax().orElseThrow();
            money("6000", tax.provenance().election().retirementExclusion());
            assertTrue(tax.fundingMetrics().candidates() < 40);
            var sources = year.getIncomeSources().stream()
                    .filter(source -> source.kind() == IncomeSourceAttribution.Kind.PENSION).toList();
            assertEquals(2, sources.size());
            for (var source : sources) {
                assertEquals(original, source.pension().orElseThrow().originalOwner());
                assertEquals(i == 0 ? original : survivor, source.owner().orElseThrow());
            }
            assertTrue(sources.stream().anyMatch(source ->
                    source.pension().orElseThrow().sourceOrigin() == RetirementIncomeSourceOrigin.UNKNOWN));
        }
    }

    private static RetirementPlan singleFixture(boolean documented, Conversion conversion) {
        var plan = selectArkansas(single(BIRTH, OPENING, 3, TAXABLE_FIRST, "200000"));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Tax checking", PRIMARY, b("30000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth destination", PRIMARY, ZERO));
        addPension(plan, PRIMARY, PENSION_START, "2000", true, null);
        addPension(plan, PRIMARY, PENSION_START, "5000", documented, null);
        plan.getHousehold().addExpense(new Expense("Spending", b("84000"), GrowthCategory.NONE,
                PENSION_START, null, ExpenseType.RECURRING));
        if (conversion != Conversion.NONE) {
            var strategy = conversion == Conversion.FIXED ? RothConversionStrategy.FIXED_AMOUNT
                    : RothConversionStrategy.FILL_22_PERCENT_BRACKET;
            plan.setRothConversionRequest(new RothConversionRequest(true, 2028,
                    conversion == Conversion.FIXED ? b("5000") : ZERO,
                    RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME));
        }
        return plan;
    }

    private static void addPension(RetirementPlan plan, AccountOwnership owner, LocalDate start,
            String monthly, boolean documented, String survivor) {
        var person = plan.getHousehold().peopleByOwner().get(owner);
        var pension = new Pension(documented ? "Employer pension" : "Other pension", owner, start, null, b(monthly),
                ZERO, survivor == null ? null : b(survivor));
        if (documented) pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        person.addIncomeSource(pension);
    }

    private static AnnualIncomeTaxFacts annual(RetirementPlan plan, FilingStatus filing) throws Exception {
        var household = plan.getHousehold();
        var assumptions = plan.getPlanningAssumptions();
        var death = EffectiveHouseholdDeathView.resolve(household, assumptions.getDeathScenarioAssumptions(), Optional.empty());
        var pensions = new HouseholdPensionIncomeCalculator().calculateWithSources(household,
                PENSION_START.withMonth(12).withDayOfMonth(31), death);
        var context = TaxHouseholdContext.capture(household, PENSION_START, assumptions.getDeathScenarioAssumptions(),
                Optional.of(pensions));
        var base = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var rules = new GovernmentRuleProjectionService().project(base, assumptions, 2028);
        return ArkansasAnnualFunding.captureIncome(context, filing, rules, household, HouseholdSocialSecurityResult.zero(),
                WithdrawalBreakdown.zero(), ZERO, ZERO, List.of());
    }

    private static List<ProjectionYear> run(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class,
                ProjectionEngine.forArkansasProduction().projectArkansasWithOutcome(plan, () -> false, ignored -> {}))
                .completedYears();
    }

    private static com.daviddunn.retirementplanner.domain.roth.ProjectedRothConversionResult withNoConversion(RetirementPlan plan) {
        var portfolio = new ProjectedPortfolio(plan.getAccountPortfolio().getAccounts().stream()
                .map(account -> new ProjectedAccountBalance(account, account.getCurrentBalance())).toList());
        return new ProjectedPortfolioRothConverter().convertHousehold(portfolio, ZERO);
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, b(expected).compareTo(actual), () -> "Expected " + expected + ", got " + actual);
    }
}
