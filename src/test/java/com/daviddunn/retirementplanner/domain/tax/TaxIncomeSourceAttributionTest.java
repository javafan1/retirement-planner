package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TaxIncomeSourceAttributionTest {
    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private static final LocalDate DATE = LocalDate.of(2026, 1, 1);

    private static Household household(boolean couple) {
        Person primary = new Person("Primary", "Person", LocalDate.of(1960, 1, 1));
        return couple ? new Household(primary,
                new Person("Spouse", "Person", LocalDate.of(1965, 1, 1))) : new Household(primary);
    }

    @Test
    void selectedSurvivorSocialSecurityBelongsToActualRecipientOnly() {
        var selected = new HouseholdSocialSecurityResult(
                BigDecimal.ZERO, money("12000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, money("30000"), SocialSecurityBenefitSelection.NONE,
                SocialSecurityBenefitSelection.SURVIVOR, money("30000"));
        var income = new TaxIncomeCalculator().calculate(household(true), DATE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, selected);
        var sources = income.getIncomeSources();
        assertEquals(1, sources.size());
        assertEquals(AccountOwnership.SPOUSE, sources.getFirst().owner().orElseThrow());
        assertEquals(IncomeSourceAttribution.Kind.SOCIAL_SECURITY, sources.getFirst().kind());
        assertEquals(selected.householdBenefit(), sources.getFirst().grossAmount());
        assertTrue(sources.getFirst().modeledTaxableAmount().isEmpty());
    }

    @Test
    void legacySocialSecurityUsesRecordOwnerAndNeverManufacturesSpouse() {
        Household household = household(false);
        household.getPrimaryPerson().addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.PRIMARY,
                DATE, null, money("2000"), 66, BigDecimal.ZERO, 2026));
        var income = new TaxIncomeCalculator().calculate(household, DATE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(1, income.getIncomeSources().size());
        assertEquals(AccountOwnership.PRIMARY, income.getIncomeSources().getFirst().owner().orElseThrow());
        assertEquals(income.getSocialSecurityIncome(), income.getIncomeSources().getFirst().grossAmount());
    }

    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void finalTaxFundingSourcesRetainOwnerWithoutIterationDuplicates(AccountOwnership owner) throws Exception {
        Household household = household(owner == AccountOwnership.SPOUSE);
        Account account = new TraditionalIRA("Funding IRA", owner, money("1000000"));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account, account.getCurrentBalance())));
        var spending = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio, money("100000"),
                new TaxDeferredFirstWithdrawalStrategy());
        var result = new TaxFundingCalculator().calculate(household, DATE, spending.getPortfolio(),
                spending.getWithdrawalBreakdown(), new TaxDeferredFirstWithdrawalStrategy(),
                household.hasSpouse() ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),
                BigDecimal.ZERO, money("10000"));
        var funding = result.getIncomeSources().stream()
                .filter(source -> source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
        assertEquals(1, funding.size());
        assertEquals(owner, funding.getFirst().owner().orElseThrow());
        assertEquals(result.getAdditionalWithdrawal(), funding.getFirst().grossAmount());
        assertEquals(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, funding.getFirst().kind());
        assertEquals(2026, funding.getFirst().taxYear().orElseThrow());
        var spendingFacts = result.getIncomeSources().stream()
                .filter(source -> source.purpose() == IncomeSourceAttribution.Purpose.CASH_FLOW).toList();
        assertEquals(1, spendingFacts.size());
        assertEquals(money("100000"), spendingFacts.getFirst().grossAmount());
        assertEquals(3, result.getIncomeSources().size());
        var interest = result.getIncomeSources().stream()
                .filter(source -> source.kind() == IncomeSourceAttribution.Kind.INTEREST).findFirst().orElseThrow();
        assertFalse(interest.isRetirementDistribution());
        assertEquals(money("10000"), interest.grossAmount());
        assertTrue(interest.account().isEmpty());
        assertEquals(money("1000000"), account.getCurrentBalance());
        assertThrows(UnsupportedOperationException.class, () -> result.getIncomeSources().clear());
    }

    @Test
    void brokerageTaxFundingNeverBecomesRetirementIncome() throws Exception {
        Account account = new BrokerageAccount("Brokerage", AccountOwnership.PRIMARY, money("1000000"));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account, account.getCurrentBalance())));
        var result = new TaxFundingCalculator().calculate(household(false), DATE, portfolio,
                WithdrawalBreakdown.zero(), new TaxableFirstWithdrawalStrategy(), FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),
                BigDecimal.ZERO, money("100000"));
        var funding = result.getIncomeSources().stream()
                .filter(source -> source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING)
                .findFirst().orElseThrow();
        assertEquals(IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, funding.kind());
        assertEquals(AccountType.BROKERAGE, funding.account().orElseThrow().type());
        assertEquals(result.getAdditionalWithdrawal(), funding.grossAmount());
        assertFalse(funding.isRetirementDistribution());
        assertTrue(funding.modeledTaxableAmount().isEmpty());
    }

    @Test
    void legacyTotalsRemainExplicitlyUnattributedAndUnchanged() {
        var income = new TaxIncomeCalculator().calculate(household(false), DATE,
                money("10000"), money("4000"), money("2000"), money("1000"),
                null, null, null, Optional.of(money("5000")));
        assertEquals(money("14000"), income.getTaxDeferredWithdrawals());
        assertEquals(money("22000"), income.getOrdinaryIncomeBeforeSocialSecurity());
        assertEquals(5, income.getIncomeSources().size());
        assertTrue(income.getIncomeSources().stream().allMatch(source -> source.owner().isEmpty()
                && source.account().isEmpty()));
        assertTrue(income.getIncomeSources().stream().anyMatch(source ->
                source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD && source.grossAmount().equals(money("4000"))));
    }

    @Test
    void partiallyAttributedLegacyWithdrawalRetainsUnknownRemainder() {
        var income = new TaxIncomeCalculator().calculate(household(false), DATE,
                money("10000"), BigDecimal.ZERO, BigDecimal.ZERO);
        Account account = new TraditionalIRA("Known", AccountOwnership.PRIMARY, money("50000"));
        var source = IncomeSourceAttribution.withdrawal(account, money("6000"),
                IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW);
        var attributed = TaxIncomeAttribution.attach(income, List.of(source), 2026);
        assertEquals(income.getTaxDeferredWithdrawals(), attributed.getTaxDeferredWithdrawals());
        assertEquals(2, attributed.getIncomeSources().size());
        assertTrue(attributed.getIncomeSources().stream().anyMatch(fact -> fact.owner().isEmpty()
                && fact.grossAmount().equals(money("4000"))));
    }

    @Test
    void metadataOverAllocationIsRejectedInsteadOfFabricatingTotals() {
        var income = new TaxIncomeCalculator().calculate(household(false), DATE,
                money("10000"), BigDecimal.ZERO, BigDecimal.ZERO);
        Account account = new TraditionalIRA("Known", AccountOwnership.PRIMARY, money("50000"));
        var source = IncomeSourceAttribution.withdrawal(account, money("10001"),
                IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW);
        assertThrows(IllegalArgumentException.class, () -> TaxIncomeAttribution.attach(income, List.of(source), 2026));
    }

    @Test
    void pensionRecordsRemainOwnerAttributedAtTaxBoundary() {
        Household household = household(true);
        household.getPrimaryPerson().addIncomeSource(new Pension("Primary pension", AccountOwnership.PRIMARY,
                DATE, null, money("1000"), BigDecimal.ZERO));
        household.requireSpouse("Test").addIncomeSource(new Pension("Spouse pension", AccountOwnership.SPOUSE,
                DATE, null, money("2000"), BigDecimal.ZERO));
        var income = new TaxIncomeCalculator().calculate(household, DATE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(money("36000"), income.getPensionIncome());
        assertEquals(2, income.getIncomeSources().size());
        assertEquals(Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE),
                new HashSet<>(income.getIncomeSources().stream().map(source -> source.owner().orElseThrow()).toList()));
        assertTrue(income.getIncomeSources().stream().allMatch(source -> source.pension().isPresent()));
    }

    @Test
    void incomeSourceMetadataIsNotSerialized() throws Exception {
        var income = new TaxIncomeCalculator().calculate(household(false), DATE,
                money("10000"), BigDecimal.ZERO, money("1000"));
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(income);
        assertFalse(json.contains("incomeSources"));
        assertFalse(json.contains("Unattributed"));
    }
}
