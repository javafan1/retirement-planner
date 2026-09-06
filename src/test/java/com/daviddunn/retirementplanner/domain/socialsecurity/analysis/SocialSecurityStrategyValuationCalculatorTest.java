package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityStrategyValuationCalculatorTest {

    @Test
    void monthlyValuationsReconcileToLifetimeSummary() {
        SocialSecurityStrategyRequest request = new SocialSecurityStrategyRequest(
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2031, 12, 31),
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 6, 4), 3000,
                        LocalDate.of(2030, 6, 4)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 2, 28), 0,
                        LocalDate.of(2032, 2, 28)),
                null,
                null,
                null,
                null,
                new BigDecimal("0.02"));
        SocialSecurityLifetimeResult lifetime =
                new SocialSecurityStrategyCalculator().calculate(request);
        SocialSecurityStrategyValuation valuation =
                new SocialSecurityStrategyValuationCalculator().calculate(
                        lifetime,
                        request.analysisDate(),
                        new BigDecimal("0.01"));

        assertEquals(lifetime.monthlyResults().size(),
                valuation.monthlyValuations().size());
        assertMoney(lifetime.householdLifetimeNominalBenefits(),
                valuation.nominalLifetimeBenefits());
        assertMoney(valuation.nominalLifetimeBenefits(),
                valuation.monthlyValuations().getLast()
                        .cumulativeNominalBenefit());
        assertMoney(valuation.realLifetimeBenefits(),
                valuation.monthlyValuations().getLast()
                        .cumulativeRealBenefit());
        assertMoney(valuation.presentValue(),
                valuation.monthlyValuations().getLast()
                        .cumulativePresentValue());
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            int benefit,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(
                owner,
                birthDate,
                BigDecimal.valueOf(benefit),
                2030,
                claimDate);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
