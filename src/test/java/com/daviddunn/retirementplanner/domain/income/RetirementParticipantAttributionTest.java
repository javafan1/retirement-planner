package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class RetirementParticipantAttributionTest {
    static final RetirementParticipantFacts FACTS = new RetirementParticipantFacts(
            RetirementParticipantFacts.SeparationStatus.SEPARATED, LocalDate.of(2030, 1, 1),
            RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2031, 1, 1));

    @ParameterizedTest @EnumSource(value = IncomeSourceAttribution.Kind.class, names = {
            "ORDINARY_RETIREMENT_WITHDRAWAL", "RMD", "OPENING_RMD"})
    void everyEmployerWithdrawalSnapshotRetainsImmutableFacts(IncomeSourceAttribution.Kind kind) {
        var account = new Traditional401K("Employer", AccountOwnership.SPOUSE, new BigDecimal("100000"));
        account.setRetirementParticipantFacts(FACTS);
        var source = IncomeSourceAttribution.withdrawal(account, new BigDecimal("10000"), kind,
                IncomeSourceAttribution.Purpose.TAX_FUNDING).inYear(2029);
        account.setRetirementParticipantFacts(RetirementParticipantFacts.UNKNOWN);
        assertEquals(FACTS, source.account().orElseThrow().participantFacts());
        assertEquals(AccountOwnership.SPOUSE, source.owner().orElseThrow());
        assertEquals(RetirementParticipantFacts.SeparationStatus.NOT_SEPARATED,
                source.account().orElseThrow().participantFacts().separationStatusOn(LocalDate.of(2029, 12, 31)));
        assertEquals(new BigDecimal("10000"), source.modeledTaxableAmount().orElseThrow());
    }

    @Test void actualTaxFundingAllocationCapturesSourceMetadata() {
        var account = new Traditional403B("Employer", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        account.setRetirementParticipantFacts(FACTS);
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account, account.getCurrentBalance())));
        var allocation = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(
                portfolio, new BigDecimal("1000"), new TaxDeferredFirstWithdrawalStrategy());
        assertEquals(FACTS, allocation.getWithdrawalBreakdown().getIncomeSources().getFirst()
                .account().orElseThrow().participantFacts());
    }

    @Test void actualEmployerRmdAllocationPreservesFactsWithoutChangingAmount() {
        var account = new Traditional403B("Employer", AccountOwnership.SPOUSE, new BigDecimal("100000"));
        account.setRetirementParticipantFacts(FACTS);
        var requirement = new com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdResult(Map.of(
                AccountOwnership.PRIMARY, com.daviddunn.retirementplanner.domain.rmd.OwnerRmdResult.zero(),
                AccountOwnership.SPOUSE, new com.daviddunn.retirementplanner.domain.rmd.OwnerRmdResult(
                        BigDecimal.ZERO, List.of(), List.of(new com.daviddunn.retirementplanner.domain.rmd.AccountRmd(
                                account, new BigDecimal("3000"))))));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(account, account.getCurrentBalance())));
        var allocation = new ProjectedWithdrawalAllocator().allocateHouseholdRmds(portfolio, requirement);
        var source = allocation.getWithdrawalBreakdown().getIncomeSources().getFirst();
        assertEquals(FACTS, source.account().orElseThrow().participantFacts());
        assertEquals(IncomeSourceAttribution.Kind.RMD, source.kind());
        assertEquals(new BigDecimal("3000"), source.grossAmount());
        assertEquals(new BigDecimal("97000"), allocation.getPortfolio().getBalance(account));
        assertEquals(new BigDecimal("100000"), account.getCurrentBalance());
    }

    @Test void survivorPensionKeepsOriginalParticipantsFactsAndRecipient() {
        var pension = new Pension("Pension", AccountOwnership.PRIMARY, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("2000"), BigDecimal.ZERO, new BigDecimal("1000"));
        pension.setRetirementParticipantFacts(FACTS);
        var source = IncomeSourceAttribution.pension(pension, AccountOwnership.SPOUSE, new BigDecimal("12000"), 2035);
        pension.setRetirementParticipantFacts(RetirementParticipantFacts.UNKNOWN);
        assertEquals(FACTS, source.pension().orElseThrow().participantFacts());
        assertEquals(AccountOwnership.PRIMARY, source.pension().orElseThrow().originalOwner());
        assertEquals(AccountOwnership.SPOUSE, source.owner().orElseThrow());
    }

    @ParameterizedTest @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void partialExecutedConversionsStillTransferEveryExecutedDollarToSameOwner(AccountOwnership owner) {
        var source = new TraditionalIRA("IRA", owner, new BigDecimal("25000"));
        var destination = new RothIRA("Roth", owner, BigDecimal.ZERO);
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(source, source.getCurrentBalance()),
                new ProjectedAccountBalance(destination, BigDecimal.ZERO)));
        var result = new ProjectedPortfolioRothConverter().convertHousehold(portfolio, new BigDecimal("100000"), Set.of(owner));
        assertEquals(new BigDecimal("25000"), result.getTotalConversion());
        var attribution = result.getIncomeSources(2026).getFirst();
        assertEquals(attribution.grossAmount(), attribution.modeledTaxableAmount().orElseThrow());
        assertEquals(owner, attribution.destination().orElseThrow().owner());
        assertEquals(BigDecimal.ZERO, result.getPortfolio().getAccountBalances().getFirst().getBalance());
        assertEquals(new BigDecimal("25000"), result.getPortfolio().getAccountBalances().get(1).getBalance());
        assertEquals(new BigDecimal("25000"), source.getCurrentBalance());
    }
}
