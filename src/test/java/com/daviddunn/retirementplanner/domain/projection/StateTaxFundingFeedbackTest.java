package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StateTaxFundingFeedbackTest {
    @Test
    void bracketFillUsesSameFederalTargetAndFloridaAllowsMoreConversion() throws Exception {
        var household = new Household(new Person("Primary", "Example", LocalDate.of(1963, 6, 4)),
                new Person("Spouse", "Example", LocalDate.of(1965, 2, 28)));
        var portfolio = new com.daviddunn.retirementplanner.domain.financial.AccountPortfolio();
        portfolio.addAccount(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        portfolio.addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA("Roth", AccountOwnership.PRIMARY,
                new BigDecimal("100000")));
        var plan = new RetirementPlan(household, portfolio,
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 1, LocalDate.of(2026, 1, 1)));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2026,
                BigDecimal.ZERO, com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ONE_TIME));
        var mi = new ProjectionEngine().project(plan).getYearAt(0);
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var fl = new ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(new BigDecimal("203563.35"), cents(mi.getRothConversion()));
        assertEquals(new BigDecimal("207668.00"), cents(fl.getRothConversion()));
        for (var year : List.of(mi, fl)) {
            assertTrue(year.getFederalTaxableIncome().subtract(new BigDecimal("211400")).abs().compareTo(new BigDecimal("0.01")) <= 0);
            assertEquals(new BigDecimal("35932.00"), cents(year.getFederalIncomeTax()));
        }
        assertEquals(new BigDecimal("4104.65"), cents(mi.getStateIncomeTax()));
        assertEquals(0, fl.getStateIncomeTax().signum());
        var lines = new ArrayList<String>();
        lines.add("Metric|Michigan|Florida|Difference");
        lines.add(row("State tax", mi.getStateIncomeTax(), fl.getStateIncomeTax()));
        lines.add(row("Total tax", mi.getTotalIncomeTax(), fl.getTotalIncomeTax()));
        lines.add(row("Tax funding", mi.getTaxFundingWithdrawal(), fl.getTaxFundingWithdrawal()));
        lines.add(row("Roth conversion", mi.getRothConversion(), fl.getRothConversion()));
        lines.add(row("Ending assets", mi.getEndingInvestableAssets(), fl.getEndingInvestableAssets()));
        lines.add(row("After-tax estate", mi.getAfterTaxEstateValue(), fl.getAfterTaxEstateValue()));
        java.nio.file.Files.write(java.nio.file.Path.of("target/phase6d1-conversion-ab.txt"), lines);
    }

    private static String row(String metric, BigDecimal mi, BigDecimal fl) {
        return metric + "|" + cents(mi) + "|" + cents(fl) + "|" + cents(fl.subtract(mi));
    }

    @Test
    void floridaFundingAndLaterRmdDifferOnlyThroughBalances() throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
        var household = new Household(new Person("Primary", "Example", LocalDate.of(1950, 1, 1)));
        var strategy = new TaxDeferredFirstWithdrawalStrategy();
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var spending = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(new ProjectedPortfolio(List.of(
                new ProjectedAccountBalance(account, account.getCurrentBalance()))), new BigDecimal("80000"), strategy);
        var context = TaxHouseholdContext.capture(household, LocalDate.of(2026, 1, 1), null);
        var outcomes = new EnumMap<StateTaxJurisdiction, TaxFundingResult>(StateTaxJurisdiction.class);
        for (var state : StateTaxJurisdiction.values()) {
            var outcome = new TaxFundingCalculator().calculate(household, context.distributionDate(), spending.getPortfolio(),
                    spending.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE, rules, BigDecimal.ZERO,
                    BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, BigDecimal.ZERO, Optional.empty(), List.of(),
                    context, StateTaxAssumptions.simplified(state));
            outcomes.put(state, outcome);
            assertTrue(outcome.getAdditionalWithdrawal().subtract(outcome.getTotalIncomeTax()).abs()
                    .compareTo(new BigDecimal("0.01")) <= 0);
            var fundingSources = outcome.getIncomeSources().stream()
                    .filter(s -> s.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
            assertEquals(1, fundingSources.size());
            assertEquals(AccountOwnership.PRIMARY, fundingSources.getFirst().owner().orElseThrow());
            assertEquals(0, fundingSources.getFirst().grossAmount().compareTo(outcome.getAdditionalWithdrawal()));
        }
        var mi = outcomes.get(StateTaxJurisdiction.MI);
        var fl = outcomes.get(StateTaxJurisdiction.FL);
        assertEquals(new BigDecimal("12265.52"), cents(mi.getAdditionalWithdrawal()));
        // Federal worksheet: 1240 + 38000*.12 + (13500+W)*.22 = 8770+.22W.
        // Florida: W=8770/.78, using the unchanged funding tolerance.
        assertEquals(new BigDecimal("11243.59"), cents(fl.getAdditionalWithdrawal()));
        assertEquals(0, fl.getStateTaxCalculation().incomeTax().signum());
        assertEquals(new BigDecimal("797.11"), cents(mi.getStateTaxCalculation().incomeTax()));
        assertTrue(mi.getFederalTaxCalculation().getFederalIncomeTax().compareTo(fl.getFederalTaxCalculation().getFederalIncomeTax()) > 0);
        var rmd = new com.daviddunn.retirementplanner.domain.rmd.RmdCalculator();
        var miBalance = new BigDecimal("920000").subtract(mi.getAdditionalWithdrawal());
        var flBalance = new BigDecimal("920000").subtract(fl.getAdditionalWithdrawal());
        assertEquals(new BigDecimal("39639.06"), rmd.calculateRmd(miBalance, 77, rules));
        assertEquals(new BigDecimal("39683.69"), rmd.calculateRmd(flBalance, 77, rules));
        assertEquals(flBalance.divide(new BigDecimal("22.9"), 2, RoundingMode.HALF_UP),
                rmd.calculateRmd(flBalance, 77, rules));
        var lines = new ArrayList<String>();
        lines.add("Metric|Michigan|Florida");
        lines.add("State tax|" + cents(mi.getStateTaxCalculation().incomeTax()) + "|0.00");
        lines.add("Total tax|" + cents(mi.getTotalIncomeTax()) + "|" + cents(fl.getTotalIncomeTax()));
        lines.add("Tax funding|" + cents(mi.getAdditionalWithdrawal()) + "|" + cents(fl.getAdditionalWithdrawal()));
        lines.add("Ending IRA|" + cents(miBalance) + "|" + cents(flBalance));
        lines.add("Following RMD|" + rmd.calculateRmd(miBalance, 77, rules) + "|" + rmd.calculateRmd(flBalance, 77, rules));
        java.nio.file.Files.write(java.nio.file.Path.of("target/phase6d1-funding-ab.txt"), lines);
    }

    private static BigDecimal cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
