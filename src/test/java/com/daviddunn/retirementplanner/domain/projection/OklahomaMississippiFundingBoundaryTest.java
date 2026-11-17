package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent piecewise oracles at pool exhaustion, senior cutoff and source qualification changes. */
class OklahomaMississippiFundingBoundaryTest {
    private static final LocalDate DATE = LocalDate.of(2026,1,1);
    private static final WithdrawalStrategy STRATEGY = new TaxDeferredFirstWithdrawalStrategy();

    @Test
    void oklahomaFundingExhaustsTheRemainingHundredDollarRetirementPool() throws Exception {
        var ira=new TraditionalIRA("IRA",AccountOwnership.PRIMARY,b("100000"));
        var allocation=new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio(ira),b("9900"),STRATEGY);
        var result=fund(StateTaxJurisdiction.OK,60,allocation,"20000");
        // After W>100: F=1408+.12W; S=350+.045W; W=1758/.835.
        cents("2105.39",result.getAdditionalWithdrawal());
        cents("1660.65",result.getFederalTaxCalculation().getFederalIncomeTax());
        assertEquals(b("444.74"),result.getStateTaxCalculation().incomeTax());
        assertEquals(b("100000"),ira.getCurrentBalance());
    }

    @Test
    void oklahomaTraditionalFundingCrossesSeniorAgiCliffWhileFundingIncomeUsesPool() throws Exception {
        var ira=new TraditionalIRA("IRA",AccountOwnership.PRIMARY,b("100000"));
        var allocation=new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio(ira),BigDecimal.ZERO,STRATEGY);
        var result=fund(StateTaxJurisdiction.OK,65,allocation,"14920");
        // Initially S=87.20 with senior exemption, but W>80 removes it. Funding IRA
        // itself remains within the 10k pool: T=14920-6350-1000=7570; S=125.90.
        assertEquals(b("125.90"),result.getStateTaxCalculation().incomeTax());
        cents("125.90",result.getAdditionalWithdrawal());
        assertEquals(0,result.getFederalTaxCalculation().getFederalIncomeTax().signum());
    }

    @Test
    void mississippiFundingMovesFromExemptIraToExplicitlyUnqualifiedEmployerReceipts() throws Exception {
        var ira=new TraditionalIRA("Qualified IRA",AccountOwnership.PRIMARY,b("1000"));
        var employer=new Traditional401K("Unmet retirement conditions",AccountOwnership.PRIMARY,b("100000"));
        employer.setRetirementParticipantFacts(new RetirementParticipantFacts(null,null,
                RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET,null));
        var allocation=new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio(ira,employer),BigDecimal.ZERO,STRATEGY);
        var result=fund(StateTaxJurisdiction.MS,60,allocation,"50000");
        // First 1000 funding is state-exempt. Then F=3820+.12W;
        // S=1268+.04(W-1000); W=5048/.84=6009.523809...
        cents("6009.52",result.getAdditionalWithdrawal());
        cents("4541.14",result.getFederalTaxCalculation().getFederalIncomeTax());
        assertEquals(b("1468.38"),result.getStateTaxCalculation().incomeTax());
        assertEquals(b("1000"),ira.getCurrentBalance());
        assertEquals(b("100000"),employer.getCurrentBalance());
    }

    private static ProjectedPortfolio portfolio(Account... accounts) {
        return new ProjectedPortfolio(Arrays.stream(accounts).map(a->new ProjectedAccountBalance(a,a.getCurrentBalance())).toList());
    }
    private static TaxFundingResult fund(StateTaxJurisdiction state,int age,ProjectedWithdrawalAllocation allocation,String investment) throws Exception {
        var h=new Household(new Person("Primary","Example",LocalDate.of(2026-age,6,1)));
        return new TaxFundingCalculator().calculate(h,DATE,allocation.getPortfolio(),allocation.getWithdrawalBreakdown(),STRATEGY,
                FilingStatus.SINGLE,new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),BigDecimal.ZERO,
                b(investment),null,null,BigDecimal.ZERO,null,BigDecimal.ZERO,Optional.empty(),List.of(),
                TaxHouseholdContext.capture(h,DATE,null),StateTaxAssumptions.simplified(state));
    }
    private static BigDecimal b(String value) { return new BigDecimal(value); }
    private static void cents(String expected,BigDecimal actual) {
        assertTrue(b(expected).subtract(actual).abs().compareTo(b("0.01"))<=0,()->expected+" versus "+actual);
    }
}
