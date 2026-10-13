package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class PennsylvaniaFundingTest {
    @ParameterizedTest
    @CsvSource({"0,0.00,false", "10000,307.00,false", "100000,3070.00,false", "10000,307.00,true"})
    void additionalFundingReclassifiesIraWithoutExcludingInvestmentsOrDuplicatingSources(String interest,
            String expected, boolean cash) throws Exception {
        var result = funding(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")), interest, cash);
        assertEquals(new BigDecimal(expected), result.getStateTaxCalculation().incomeTax());
        assertTrue(result.getAdditionalWithdrawal().subtract(result.getTotalIncomeTax()).abs().compareTo(new BigDecimal("0.01")) <= 0);
        var sources = result.getIncomeSources().stream().filter(source ->
                source.purpose() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
        assertEquals(1, sources.size());
        assertEquals(0, result.getAdditionalWithdrawal().compareTo(sources.getFirst().grossAmount()));
        assertEquals(AccountOwnership.PRIMARY, sources.getFirst().owner().orElseThrow());
        assertEquals(cash ? AccountType.CHECKING : AccountType.TRADITIONAL_IRA, sources.getFirst().account().orElseThrow().type());
        if (interest.equals("0") && !cash) {
            // W = 8770 + .22 W; admitted IRA funding remains PA-exempt. Existing federal formula is unchanged.
            assertEquals(new BigDecimal("11243.59"), result.getAdditionalWithdrawal().setScale(2, RoundingMode.HALF_UP));
            var balance = new BigDecimal("920000").subtract(result.getAdditionalWithdrawal());
            var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
            assertEquals(new BigDecimal("39683.69"), new com.daviddunn.retirementplanner.domain.rmd.RmdCalculator().calculateRmd(balance, 77, rules));
            assertEquals(balance.divide(new BigDecimal("22.9"), 2, RoundingMode.HALF_UP),
                    new com.daviddunn.retirementplanner.domain.rmd.RmdCalculator().calculateRmd(balance, 77, rules));
        }
    }

    @Test
    void missingEmployerFactsDuringTaxFundingAbortBeforeSuccessfulArithmetic() throws Exception {
        var failure = assertThrows(StateTaxUnavailableException.class, () -> funding(
                new Traditional401K("Employer", AccountOwnership.PRIMARY, new BigDecimal("1000000")), "10000", false));
        assertTrue(failure.result().estimatedIncomeTax().isEmpty());
        assertTrue(failure.result().unavailableReasons().contains(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION));
    }

    @Test
    void mixedTaxFundingThatExhaustsSupportedIraThenUsesEmployerPlanIsUnavailable() throws Exception {
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("90000"));
        var employer = new Traditional401K("Employer", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(ira, ira.getCurrentBalance()),
                new ProjectedAccountBalance(employer, employer.getCurrentBalance())));
        var strategy = new TaxDeferredFirstWithdrawalStrategy();
        var spending = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio, new BigDecimal("80000"), strategy);
        var household = new Household(new Person("Primary", "", LocalDate.of(1950, 1, 1)));
        var date = LocalDate.of(2026, 1, 1);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var failure = assertThrows(StateTaxUnavailableException.class, () -> new TaxFundingCalculator().calculate(
                household, date, spending.getPortfolio(), spending.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE,
                rules, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, BigDecimal.ZERO,
                Optional.empty(), List.of(), TaxHouseholdContext.capture(household, date, null), StateTaxAssumptions.simplified(StateTaxJurisdiction.PA)));
        assertTrue(failure.result().unavailableReasons().contains(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION));
        assertEquals(new BigDecimal("90000"), ira.getCurrentBalance());
        assertEquals(new BigDecimal("1000000"), employer.getCurrentBalance());
    }

    static TaxFundingResult funding(Account account, String interest, boolean cash) throws Exception {
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account, account.getCurrentBalance())));
        var deferred = new TaxDeferredFirstWithdrawalStrategy();
        var spending = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio, new BigDecimal("80000"), deferred);
        portfolio = spending.getPortfolio();
        if (cash) {
            var bank = AccountFactory.create(AccountType.CHECKING, "Cash", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
            var balances = new ArrayList<>(portfolio.getAccountBalances());
            balances.add(new ProjectedAccountBalance(bank, bank.getCurrentBalance()));
            portfolio = new ProjectedPortfolio(balances);
        }
        var household = new Household(new Person("Primary", "", LocalDate.of(1950, 1, 1)));
        var date = LocalDate.of(2026, 1, 1);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        return new TaxFundingCalculator().calculate(household, date, portfolio, spending.getWithdrawalBreakdown(),
                cash ? new TaxableFirstWithdrawalStrategy() : deferred, FilingStatus.SINGLE, rules,
                BigDecimal.ZERO, new BigDecimal(interest), null, null, BigDecimal.ZERO, null, BigDecimal.ZERO,
                Optional.empty(), List.of(), TaxHouseholdContext.capture(household, date, null), StateTaxAssumptions.simplified(StateTaxJurisdiction.PA));
    }
}
