package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.rmd.*;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IncomeSourceAttributionTest {
    private static BigDecimal money(String amount) { return new BigDecimal(amount); }

    private static ProjectedPortfolio portfolio(Account... accounts) {
        return new ProjectedPortfolio(Arrays.stream(accounts)
                .map(account -> new ProjectedAccountBalance(account, account.getCurrentBalance())).toList());
    }

    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void ordinaryWithdrawalRetainsOwnerAccountAndType(AccountOwnership owner) {
        Account account = new TraditionalIRA("IRA", owner, money("80000"));
        var allocation = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(
                portfolio(account), money("25000"), new TaxDeferredFirstWithdrawalStrategy());
        var sources = allocation.getWithdrawalBreakdown().getIncomeSources();
        assertEquals(1, sources.size());
        var source = sources.getFirst();
        assertEquals(owner, source.owner().orElseThrow());
        assertEquals(AccountType.TRADITIONAL_IRA, source.account().orElseThrow().type());
        assertEquals(IncomeSourceAttribution.AccountReference.capture(account), source.account().orElseThrow());
        assertEquals(money("25000"), source.grossAmount());
        assertEquals(money("25000"), source.modeledTaxableAmount().orElseThrow());
        assertTrue(source.isRetirementDistribution());
        assertEquals(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, source.kind());
        assertEquals(money("80000"), account.getCurrentBalance());
        assertThrows(UnsupportedOperationException.class, () -> sources.clear());
    }

    @Test
    void mixedAccountsKeepSeparateIdentitiesEvenWithDuplicateNames() {
        Account primary = new TraditionalIRA("Same name", AccountOwnership.PRIMARY, money("40000"));
        Account spouse = new Traditional401K("Same name", AccountOwnership.SPOUSE, money("50000"));
        var allocation = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(
                portfolio(primary, spouse), money("60000"), new TaxDeferredFirstWithdrawalStrategy());
        var sources = allocation.getWithdrawalBreakdown().getIncomeSources();
        assertEquals(2, sources.size());
        assertEquals(money("40000"), sources.get(0).grossAmount());
        assertEquals(money("20000"), sources.get(1).grossAmount());
        assertEquals(AccountOwnership.PRIMARY, sources.get(0).owner().orElseThrow());
        assertEquals(AccountOwnership.SPOUSE, sources.get(1).owner().orElseThrow());
        assertEquals(AccountType.TRADITIONAL_401K, sources.get(1).account().orElseThrow().type());
        assertNotEquals(sources.get(0).account(), sources.get(1).account());
        assertEquals(money("60000"), allocation.getWithdrawalBreakdown().getTotalWithdrawal());
    }

    @Test
    void sameOwnerDuplicateNamesRemainDistinct() {
        Account first = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, money("100"));
        Account second = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, money("100"));
        var a = IncomeSourceAttribution.AccountReference.capture(first);
        var b = IncomeSourceAttribution.AccountReference.capture(second);
        assertNotEquals(a, b);
        assertEquals(a, IncomeSourceAttribution.AccountReference.capture(first));
        assertEquals(a.hashCode(), IncomeSourceAttribution.AccountReference.capture(first).hashCode());
    }

    @Test
    void inheritedSourceKeepsExistingInformationWithoutEligibilityConclusion() {
        var information = new InheritedAccountInformation(LocalDate.of(1940, 1, 1),
                LocalDate.of(2020, 1, 1), BeneficiaryRelationship.OTHER);
        Account account = new InheritedTraditionalIRA("Inherited IRA", AccountOwnership.PRIMARY,
                money("50000"), information);
        var source = IncomeSourceAttribution.withdrawal(account, money("1000"),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW);
        assertEquals(AccountType.INHERITED_TRADITIONAL_IRA, source.account().orElseThrow().type());
        assertEquals(information, source.account().orElseThrow().inheritedInformation().orElseThrow());
        assertTrue(source.isRetirementDistribution());
    }

    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void rmdRetainsSourceAndSubtype(AccountOwnership owner) {
        Account ira = new TraditionalIRA("IRA", owner, money("50000"));
        var owners = new EnumMap<AccountOwnership, OwnerRmdResult>(AccountOwnership.class);
        owners.put(AccountOwnership.PRIMARY, OwnerRmdResult.zero());
        owners.put(owner, new OwnerRmdResult(money("2000"), List.of(), List.of()));
        var requirement = new HouseholdRmdResult(owners);
        var allocation = new ProjectedWithdrawalAllocator().allocateHouseholdRmds(portfolio(ira), requirement);
        var source = allocation.getWithdrawalBreakdown().getIncomeSources().getFirst();
        assertEquals(owner, source.owner().orElseThrow());
        assertEquals(IncomeSourceAttribution.Kind.RMD, source.kind());
        assertTrue(source.isRetirementDistribution());
        assertEquals(money("2000"), source.grossAmount());
        assertEquals(money("48000"), allocation.getPortfolio().getBalance(ira));
        assertEquals(money("50000"), ira.getCurrentBalance());
    }

    @Test
    void bothOwnersRmdsRetainDifferentAccountTypes() {
        Account ira = new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY, money("50000"));
        Account employer = new Traditional403B("Spouse 403b", AccountOwnership.SPOUSE, money("90000"));
        var requirement = new HouseholdRmdResult(Map.of(
                AccountOwnership.PRIMARY, new OwnerRmdResult(money("2000"), List.of(), List.of()),
                AccountOwnership.SPOUSE, new OwnerRmdResult(BigDecimal.ZERO, List.of(),
                        List.of(new AccountRmd(employer, money("3000"))))));
        var sources = new ProjectedWithdrawalAllocator().allocateHouseholdRmds(portfolio(ira, employer), requirement)
                .getWithdrawalBreakdown().getIncomeSources();
        assertEquals(2, sources.size());
        assertTrue(sources.stream().allMatch(source -> source.kind() == IncomeSourceAttribution.Kind.RMD));
        assertEquals(Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE),
                new HashSet<>(sources.stream().map(source -> source.owner().orElseThrow()).toList()));
        assertEquals(0, money("5000").compareTo(sources.stream().map(IncomeSourceAttribution::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void conversionRetainsSameOwnerDestination(AccountOwnership owner) {
        Account source = new TraditionalIRA("Source", owner, money("50000"));
        Account destination = new RothIRA("Roth", owner, BigDecimal.ZERO);
        var result = new ProjectedPortfolioRothConverter().convertHousehold(
                portfolio(source, destination), money("20000"), Set.of(owner));
        var fact = result.getIncomeSources(2026).getFirst();
        assertEquals(IncomeSourceAttribution.Kind.ROTH_CONVERSION, fact.kind());
        assertEquals(owner, fact.owner().orElseThrow());
        assertEquals(owner, fact.destination().orElseThrow().owner());
        assertEquals(AccountType.TRADITIONAL_IRA, fact.account().orElseThrow().type());
        assertEquals(AccountType.ROTH_IRA, fact.destination().orElseThrow().type());
        assertEquals(money("20000"), fact.grossAmount());
        assertEquals(fact.grossAmount(), fact.modeledTaxableAmount().orElseThrow());
        assertEquals(2026, fact.taxYear().orElseThrow());
        assertEquals(money("50000"), source.getCurrentBalance());
    }

    @Test
    void multiOwnerConversionRecordsOnlyExecutedCapacity() {
        Account primary = new TraditionalIRA("Primary", AccountOwnership.PRIMARY, money("10000"));
        Account spouse = new TraditionalIRA("Spouse", AccountOwnership.SPOUSE, money("15000"));
        var result = new ProjectedPortfolioRothConverter().convertHousehold(portfolio(primary, spouse,
                new RothIRA("Primary Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO),
                new RothIRA("Spouse Roth", AccountOwnership.SPOUSE, BigDecimal.ZERO)),
                money("60000"), Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE));
        var facts = result.getIncomeSources(2026);
        assertEquals(2, facts.size());
        assertEquals(money("10000"), facts.get(0).grossAmount());
        assertEquals(money("15000"), facts.get(1).grossAmount());
        assertEquals(money("25000"), result.getTotalConversion());
        assertTrue(facts.stream().allMatch(source -> source.account().orElseThrow().owner()
                == source.destination().orElseThrow().owner()));
    }

    @Test
    void retainedHouseholdAssetsDoNotManufactureAnAccountOrPerson() {
        var portfolio = new ProjectedPortfolio(List.of(), money("10000"));
        var source = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio,
                money("1000"), new TaxableFirstWithdrawalStrategy()).getWithdrawalBreakdown()
                .getIncomeSources().getFirst();
        assertTrue(source.owner().isEmpty());
        assertTrue(source.account().isEmpty());
        assertFalse(source.isRetirementDistribution());
        assertEquals(BigDecimal.ZERO, source.modeledTaxableAmount().orElseThrow());
    }

    @Test
    void brokerageWithdrawalDoesNotInventTaxableGainOrRetirementIncome() {
        Account brokerage = new BrokerageAccount("Brokerage", AccountOwnership.PRIMARY, money("50000"));
        var source = new ProjectedWithdrawalAllocator().calculateWithdrawalBreakdown(portfolio(brokerage),
                money("1000"), new TaxableFirstWithdrawalStrategy()).getIncomeSources().getFirst();
        assertFalse(source.isRetirementDistribution());
        assertEquals(IncomeSourceAttribution.Purpose.TAX_FUNDING, source.purpose());
        assertTrue(source.modeledTaxableAmount().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class,
            names = {"INTEREST", "DIVIDEND", "CAPITAL_GAIN", "SOCIAL_SECURITY", "OTHER"})
    void nonRetirementIncomeNeverBecomesADistribution(IncomeSourceAttribution.Kind kind) {
        var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                "Known classification", money("1000"), Optional.empty());
        assertFalse(source.isRetirementDistribution());
        assertTrue(source.owner().isEmpty());
        assertTrue(source.modeledTaxableAmount().isEmpty());
        assertEquals(2026, source.inYear(2026).taxYear().orElseThrow());
        assertTrue(source.taxYear().isEmpty());
    }

    @Test
    void pensionSurvivorRetainsRecipientAndOriginalOwner() {
        Person primary = new Person("Primary", "Person", LocalDate.of(1960, 1, 1));
        Person spouse = new Person("Spouse", "Person", LocalDate.of(1962, 1, 1));
        Pension pension = new Pension("Pension", AccountOwnership.PRIMARY, LocalDate.of(2026, 1, 1),
                null, money("2000"), BigDecimal.ZERO, money("1000"));
        primary.addIncomeSource(pension);
        var household = new Household(primary, spouse);
        var death = EffectiveHouseholdDeathView.resolve(household,
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2027), Optional.empty());
        var calculator = new HouseholdPensionIncomeCalculator();
        var before = calculator.calculateWithSources(household, LocalDate.of(2026, 1, 1), death);
        var after = calculator.calculateWithSources(household, LocalDate.of(2027, 1, 1), death);
        assertEquals(money("24000"), before.total());
        assertEquals(money("12000"), after.total());
        var fact = after.sources().getFirst();
        assertEquals(AccountOwnership.SPOUSE, fact.owner().orElseThrow());
        assertEquals(AccountOwnership.PRIMARY, fact.pension().orElseThrow().originalOwner());
        assertEquals(money("1000"), fact.pension().orElseThrow().survivorMonthlyBenefit().orElseThrow());
        assertEquals(IncomeSourceAttribution.Kind.PENSION, fact.kind());
        assertEquals(after.total(), calculator.calculate(household, LocalDate.of(2027, 1, 1), death));
    }

    @Test
    void sourceValidationRejectsNegativeAndInconsistentAmounts() {
        assertThrows(IllegalArgumentException.class, () -> IncomeSourceAttribution.aggregate(
                IncomeSourceAttribution.Kind.INTEREST, IncomeSourceAttribution.Purpose.INCOME,
                "Interest", money("-1"), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> IncomeSourceAttribution.aggregate(
                IncomeSourceAttribution.Kind.INTEREST, IncomeSourceAttribution.Purpose.INCOME,
                "Interest", money("100"), Optional.of(money("101"))));
    }
}
