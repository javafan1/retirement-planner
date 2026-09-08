package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rmd.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.rules.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurvivorFinancialMechanicsTest {
    private RetirementPlan plan() {
        return LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2031, 67));
    }
    private GovernmentRules rules() throws Exception {
        return new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
    }
    private ProjectionEvaluationContext context(Integer primary, Integer spouse) {
        return ProjectionEvaluationContext.withLifetimeScenario(new HouseholdLifetimeScenario(
                Optional.ofNullable(primary).map(Year::of), Optional.ofNullable(spouse).map(Year::of)));
    }
    private void horizon(RetirementPlan plan, int start, int length) {
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), length, LocalDate.of(start, 7, 1)));
    }
    private List<Account> accounts(RetirementPlan plan, AccountOwnership owner) {
        List<Account> result = List.of(new TraditionalIRA("IRA " + owner, owner, new BigDecimal("100000")),
                new Traditional401K("401 " + owner, owner, new BigDecimal("100000")),
                new Traditional403B("403 " + owner, owner, new BigDecimal("100000")));
        result.forEach(plan.getAccountPortfolio()::addAccount);
        return result;
    }
    @ParameterizedTest
    @EnumSource(value=AccountOwnership.class, names={"PRIMARY","SPOUSE"})
    void rmdEligibilityExcludesEveryAccountCategoryBeforeLookup(AccountOwnership dead) throws Exception {
        var plan = plan();
        accounts(plan, AccountOwnership.PRIMARY);
        accounts(plan, AccountOwnership.SPOUSE);
        var snapshot = RmdBalanceSnapshot.from(LocalDate.of(2040,12,31), ProjectedPortfolio.from(plan.getAccountPortfolio()));
        var survivor = dead == AccountOwnership.PRIMARY ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
        var result = new HouseholdRmdCalculator().calculate(plan, snapshot, 2041, rules(), Set.of(survivor));
        var deceasedRmd = dead == AccountOwnership.PRIMARY ? result.getPrimaryRmd() : result.getSpouseRmd();
        var survivorRmd = dead == AccountOwnership.PRIMARY ? result.getSpouseRmd() : result.getPrimaryRmd();
        money("0", deceasedRmd.getTotalRmd());
        assertTrue(survivorRmd.getIraRmd().signum() > 0);
        assertTrue(survivorRmd.getTraditional401kRmds().getFirst().getAmount().signum() > 0);
        assertTrue(survivorRmd.getTraditional403bRmds().getFirst().getAmount().signum() > 0);
        money("0", new HouseholdRmdCalculator().calculate(plan, snapshot, 2200, rules(), Set.of()).getTotalRmd());
    }
    @Test
    void excludedOpeningOwnerNeedsNoDataAndStaleDistributionsAreIgnored() throws Exception {
        var plan = plan();
        var accounts = accounts(plan, AccountOwnership.PRIMARY);
        var opening = new OpeningRmdCalculator();
        money("0", opening.calculate(plan, 2200, rules(), Set.of()).getDistributedBeforeProjection());
        for (Account account : accounts) {
            account.setOpeningRmdAccountData(new OpeningRmdAccountData(2199, new BigDecimal("100000"), new BigDecimal("1000")));
        }
        money("0", opening.calculate(plan, 2200, rules(), Set.of()).getAnnualRequirement().getTotalRmd());
        accounts.getFirst().setOpeningRmdAccountData(new OpeningRmdAccountData(2200, new BigDecimal("100000"), BigDecimal.ONE));
        var error = assertThrows(IllegalStateException.class, () -> opening.calculate(plan, 2200, rules(), Set.of()));
        assertTrue(error.getMessage().contains("already-distributed"));
    }
    @Test
    void survivorOpeningDistributionRemainsTaxableOnceAndNotNewCash() throws Exception {
        var plan = plan(); horizon(plan, 2040, 1);
        var ira = accounts(plan, AccountOwnership.SPOUSE).getFirst();
        for (Account account : plan.getAccountPortfolio().getAccounts(AccountOwnership.SPOUSE)) {
            account.setOpeningRmdAccountData(new OpeningRmdAccountData(2040, new BigDecimal("100000"),
                    account == ira ? new BigDecimal("1000") : BigDecimal.ZERO));
        }
        var y = new ProjectionEngine().project(plan, context(2039,null)).getYearAt(0);
        money("1000", y.getRmdDistributedBeforeProjection());
        money(y.getRequiredMinimumDistribution().subtract(new BigDecimal("1000")).toPlainString(), y.getRmdDistributedInProjection());
        // All discretionary withdrawals use brokerage in this fixture; annual RMD enters taxable income once.
        money(y.getRequiredMinimumDistribution().add(y.getTaxableSocialSecurity()).toPlainString(), y.getAdjustedGrossIncome());
    }
    @Test
    void lifetimeEngineStopsRmdAndConversionAtDeathButKeepsAssetsAndTaxFunding() {
        var plan = plan(); horizon(plan, 2034, 4);
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
        plan.getAccountPortfolio().addAccount(ira);
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        fixed(plan, RothConversionStopRule.NEVER);
        var p = new ProjectionEngine().project(plan, context(2036,2037));
        assertTrue(p.getYearAt(1).getRequiredMinimumDistribution().signum() > 0);
        money("0", p.getYearAt(2).getRequiredMinimumDistribution());
        money("10000", p.getYearAt(2).getRequestedRothConversion());
        money("0", p.getYearAt(2).getRothConversion());
        money("10000", p.getYearAt(2).getRothConversionShortfall());
        assertTrue(p.getYearAt(3).getEndingBalance(ira).compareTo(p.getYearAt(2).getEndingBalance(ira)) > 0);
        assertTrue(p.getYearAt(3).getEstimatedHeirTax().signum() > 0);
        money("1000000", ira.getCurrentBalance());
    }
    @Test
    void firstHouseholdRmdStopDoesNotResumeAfterDeathInLifetimeRun() {
        var plan = plan(); horizon(plan, 2034, 3);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Spouse IRA", AccountOwnership.SPOUSE, new BigDecimal("1000000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(new RothIRA("Spouse Roth", AccountOwnership.SPOUSE, BigDecimal.ZERO));
        fixed(plan, RothConversionStopRule.FIRST_HOUSEHOLD_RMD);
        var p = new ProjectionEngine().project(plan, context(2036,null));
        money("10000", p.getYearAt(0).getRothConversion());
        assertTrue(p.getYearAt(1).getRequiredMinimumDistribution().signum() > 0);
        money("0", p.getYearAt(1).getRothConversion());
        money("0", p.getYearAt(2).getRequiredMinimumDistribution());
        money("0", p.getYearAt(2).getRequestedRothConversion());
        fixed(plan, RothConversionStopRule.NEVER);
        money("10000", new ProjectionEngine().project(plan, context(2036,null)).getYearAt(2).getSpouseRothConversion());
    }
    @ParameterizedTest
    @EnumSource(value=AccountOwnership.class, names={"PRIMARY","SPOUSE"})
    void conversionCapacityAndExecutionAgreeForSurvivorOnly(AccountOwnership survivor) {
        var plan = plan();
        for (var owner : List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE)) {
            plan.getAccountPortfolio().addAccount(new TraditionalIRA("IRA " + owner, owner, new BigDecimal("7000")));
            plan.getAccountPortfolio().addAccount(new RothIRA("Roth " + owner, owner, BigDecimal.ZERO));
        }
        var converter = new ProjectedPortfolioRothConverter();
        var portfolio = ProjectedPortfolio.from(plan.getAccountPortfolio());
        money("7000", converter.getMaximumConvertibleAmount(portfolio, Set.of(survivor)));
        var result = converter.convertHousehold(portfolio, new BigDecimal("10000"), Set.of(survivor));
        money("7000", result.getTotalConversion());
        money("7000", result.getConversion(survivor));
        money("0", converter.getMaximumConvertibleAmount(portfolio, Set.of()));
        money("0", converter.convertHousehold(portfolio, new BigDecimal("10000"), Set.of()).getTotalConversion());
    }
    @Test
    void deceasedDestinationCannotReceiveSurvivingOwnersConversion() {
        var plan = plan();
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("IRA", AccountOwnership.SPOUSE, new BigDecimal("7000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        var portfolio = ProjectedPortfolio.from(plan.getAccountPortfolio());
        var converter = new ProjectedPortfolioRothConverter();
        money("0", converter.getMaximumConvertibleAmount(portfolio, Set.of(AccountOwnership.SPOUSE)));
        money("0", converter.convertHousehold(portfolio, BigDecimal.ONE, Set.of(AccountOwnership.SPOUSE)).getTotalConversion());
    }
    @Test
    void authoritativePensionReconcilesCashAndTaxThroughSurvivorAndSecondDeath() {
        var plan = plan(); var p = new ProjectionEngine().project(plan, context(2031,2033));
        String[] pensions = {"12000","6600","7260","0","0"};
        for(int i=0;i<5;i++) {
            var y=p.getYearAt(i);
            money(pensions[i], y.getGuaranteedIncome().subtract(y.getSocialSecurityResult().householdBenefit()));
            money(pensions[i], y.getAdjustedGrossIncome().subtract(y.getTaxableSocialSecurity()));
        }
        var tax = new TaxIncomeCalculator();
        var a = plan.getPlanningAssumptions();
        var zero = tax.calculate(plan.getHousehold(), LocalDate.of(2030,7,1), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, a.getDeathScenarioAssumptions(),
                HouseholdSocialSecurityResult.zero(), Optional.of(BigDecimal.ZERO));
        money("0",zero.getPensionIncome());
        var legacy = tax.calculate(plan.getHousehold(), LocalDate.of(2030,7,1), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, a.getDeathScenarioAssumptions(), HouseholdSocialSecurityResult.zero());
        money("12000",legacy.getPensionIncome());
    }
    @Test
    void authoritativePensionRetainsColaStartAndEndDates() {
        var plan=plan(); var calc=new HouseholdPensionIncomeCalculator();
        var view=EffectiveHouseholdDeathView.resolve(plan.getPlanningAssumptions().getDeathScenarioAssumptions(), context(2031,null).householdLifetimeScenario());
        money("0",calc.calculate(plan.getHousehold(),LocalDate.of(2029,12,31),view));
        money("6600",calc.calculate(plan.getHousehold(),LocalDate.of(2031,12,31),view));
        money("3993",calc.calculate(plan.getHousehold(),LocalDate.of(2033,12,31),view));
        money("0",calc.calculate(plan.getHousehold(),LocalDate.of(2034,12,31),view));
    }
    @Test
    void pensionOverrideReachesTaxFundingAndBracketFillIterations() throws Exception {
        var original = new Household(new Person("A","Test",LocalDate.of(1960,1,1)),
                new Person("B","Test",LocalDate.of(1962,1,1)));
        original.getPrimaryPerson().addIncomeSource(new Pension("Original",AccountOwnership.PRIMARY,
                LocalDate.of(2020,1,1),null,new BigDecimal("10000"),BigDecimal.ZERO));
        var received = new Household(new Person("A","Test",LocalDate.of(1960,1,1)),
                new Person("B","Test",LocalDate.of(1962,1,1)));
        received.getPrimaryPerson().addIncomeSource(new Pension("Received",AccountOwnership.PRIMARY,
                LocalDate.of(2020,1,1),null,new BigDecimal("1000"),BigDecimal.ZERO));
        var account = new TraditionalIRA("IRA",AccountOwnership.PRIMARY,new BigDecimal("1000000"));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account,account.getCurrentBalance())));
        var withdrawals = new com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        var strategy = new com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy();
        var death = new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null);
        var ss = HouseholdSocialSecurityResult.zero();
        var date=LocalDate.of(2030,12,31); var rules=rules();
        var calc=new RothConversionBracketFillCalculator();
        var expected=calc.calculateConversion(received,date,portfolio,withdrawals,strategy,FilingStatus.SINGLE,rules,
                new BigDecimal("100000"),BigDecimal.ZERO,BigDecimal.ZERO,death,BigDecimal.ZERO,ss,BigDecimal.ZERO);
        var actual=calc.calculateConversion(original,date,portfolio,withdrawals,strategy,FilingStatus.SINGLE,rules,
                new BigDecimal("100000"),BigDecimal.ZERO,BigDecimal.ZERO,death,BigDecimal.ZERO,ss,BigDecimal.ZERO,
                Optional.of(new BigDecimal("12000")));
        money(expected.toPlainString(),actual);
        var taxes=new TaxFundingCalculator();
        var result=taxes.calculate(original,date,portfolio,withdrawals,strategy,FilingStatus.SINGLE,rules,
                actual,BigDecimal.ZERO,BigDecimal.ZERO,death,BigDecimal.ZERO,ss,BigDecimal.ZERO,Optional.of(new BigDecimal("12000")));
        var reference=taxes.calculate(received,date,portfolio,withdrawals,strategy,FilingStatus.SINGLE,rules,
                expected,BigDecimal.ZERO,BigDecimal.ZERO,death,BigDecimal.ZERO,ss,BigDecimal.ZERO);
        assertTrue(result.getAdditionalWithdrawal().signum()>0);
        money(reference.getAdditionalWithdrawal().toPlainString(),result.getAdditionalWithdrawal());
        money(reference.getTotalIncomeTax().toPlainString(),result.getTotalIncomeTax());
        assertTrue(result.getFederalTaxCalculation().getTaxableIncome().subtract(new BigDecimal("100000")).abs()
                .compareTo(new BigDecimal("0.05"))<0);
    }
    @Test
    void deceasedAccountStillFundsSpendingAndTaxesWithExistingTaxClassification() {
        var source=plan(); horizon(source,2030,1);
        source.getHousehold().addExpense(new Expense("Additional spending",new BigDecimal("80000")));
        var portfolio=new AccountPortfolio();
        var ira=new TraditionalIRA("IRA",AccountOwnership.PRIMARY,new BigDecimal("100000"));
        portfolio.addAccount(ira);
        var plan=new RetirementPlan(source.getHousehold(),portfolio,source.getPlanningAssumptions());
        var y=new ProjectionEngine().project(plan,context(2030,2033)).getYearAt(0);
        money("0",y.getRequiredMinimumDistribution());
        assertTrue(y.getPortfolioWithdrawal().signum()>0);
        assertTrue(y.getTaxFundingWithdrawal().signum()>0);
        assertTrue(y.getAdjustedGrossIncome().compareTo(y.getTaxableSocialSecurity())>0);
        assertTrue(y.getEndingBalance(ira).compareTo(ira.getCurrentBalance())<0);
        money("100000",ira.getCurrentBalance());
    }
    private void fixed(RetirementPlan plan, RothConversionStopRule stop) {
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("10000"), stop,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ANNUAL));
    }
    private void money(String expected, BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual), "Expected " + expected + " got " + actual); }
}
