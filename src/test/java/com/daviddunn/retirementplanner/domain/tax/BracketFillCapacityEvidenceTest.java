package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reproduces the preexisting aggregate-tolerant path without changing its results. */
class BracketFillCapacityEvidenceTest {
    @Test
    void existingNorthCarolinaTrialExceedsCapacityAndRetainsUnownedResidual() throws Exception {
        var date = LocalDate.of(2026, 1, 1);
        var household = new Household(new Person("Primary", "Example", LocalDate.of(1960, 1, 1)));
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000"));
        var roth = new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO);
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(ira, ira.getCurrentBalance()),
                new ProjectedAccountBalance(roth, BigDecimal.ZERO)));
        var strategy = new TaxDeferredFirstWithdrawalStrategy();
        var withdrawals = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio, BigDecimal.ZERO, strategy);
        var proposal = new RothConversionBracketFillCalculator().calculateConversion(household, date, portfolio,
                withdrawals.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json"), new BigDecimal("20000"),
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, new BigDecimal("100000"), Optional.empty(),
                List.of(), TaxHouseholdContext.capture(household, date, null), Set.of(AccountOwnership.PRIMARY),
                StateTaxAssumptions.simplified(StateTaxJurisdiction.NC));
        assertTrue(proposal.compareTo(new BigDecimal("1000")) > 0);
        var execution = new ProjectedPortfolioRothConverter().convertHousehold(portfolio, proposal,
                Set.of(AccountOwnership.PRIMARY), 2026);
        assertEquals(0, execution.getTotalConversion().compareTo(new BigDecimal("1000")));
        var income = new TaxIncomeCalculator().calculate(household, date, BigDecimal.ZERO,
                proposal, BigDecimal.ZERO);
        var attributed = TaxIncomeAttribution.attach(income, execution.getIncomeSources(2026), 2026);
        assertTrue(attributed.getIncomeSources().stream().anyMatch(source ->
                source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION
                        && source.owner().isEmpty() && source.grossAmount().signum() > 0));
        assertEquals(new BigDecimal("1000"), ira.getCurrentBalance());
    }
}
