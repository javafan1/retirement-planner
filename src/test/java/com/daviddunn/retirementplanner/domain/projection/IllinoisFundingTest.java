package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IllinoisFundingTest {
    @ParameterizedTest
    @CsvSource({"0,0.00,false", "10000,350.21,false", "100000,4805.21,false",
            "10000,350.21,true", "100000,4805.21,true",
            "150000,7425.00,false", "150000,7280.21,true"})
    void repeatedFundingRetainsSourcesAndCannotSubtractInvestment(String interest, String expected, boolean brokerage) throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account, account.getCurrentBalance())));
        var strategy = new TaxDeferredFirstWithdrawalStrategy();
        var spending = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio,
                new BigDecimal("80000"), strategy);
        if (brokerage) {
            var cash = new BrokerageAccount("Brokerage", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
            var accounts = new ArrayList<>(spending.getPortfolio().getAccountBalances());
            accounts.add(new ProjectedAccountBalance(cash, cash.getCurrentBalance()));
            portfolio = new ProjectedPortfolio(accounts);
        } else {
            portfolio = spending.getPortfolio();
        }
        var household = new Household(new Person("Primary", "", LocalDate.of(1966, 1, 1)));
        var date = LocalDate.of(2026, 1, 1);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var outcome = new TaxFundingCalculator().calculate(household, date, portfolio,
                spending.getWithdrawalBreakdown(), brokerage ? new TaxableFirstWithdrawalStrategy() : strategy,
                FilingStatus.SINGLE, rules, BigDecimal.ZERO, new BigDecimal(interest), null, null,
                BigDecimal.ZERO, null, BigDecimal.ZERO, Optional.empty(), List.of(),
                TaxHouseholdContext.capture(household, date, null), StateTaxAssumptions.simplified(StateTaxJurisdiction.IL));
        assertEquals(new BigDecimal(expected), outcome.getStateTaxCalculation().incomeTax());
        assertTrue(outcome.getAdditionalWithdrawal().subtract(outcome.getTotalIncomeTax()).abs()
                .compareTo(new BigDecimal("0.01")) <= 0);
        var funding = outcome.getIncomeSources().stream().filter(s ->
                s.purpose() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
        assertEquals(1, funding.size());
        assertEquals(AccountOwnership.PRIMARY, funding.getFirst().owner().orElseThrow());
        assertEquals(brokerage ? AccountType.BROKERAGE : AccountType.TRADITIONAL_IRA,
                funding.getFirst().account().orElseThrow().type());
        assertEquals(0, funding.getFirst().grossAmount().compareTo(outcome.getAdditionalWithdrawal()));
    }
}
