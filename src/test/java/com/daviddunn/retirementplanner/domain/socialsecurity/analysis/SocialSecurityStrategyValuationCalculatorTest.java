package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    @ParameterizedTest
    @CsvSource({"2030-06-15,2030-06-30,3000", "2030-06-15,2030-12-31,3000",
            "2030-06-15,2090-12-31,3000", "2030-06-15,2090-12-31,0"})
    void auditAndStreamingTotalsMatchIndependentDecimalSum(String start, String end, int benefit) {
        var request = new SocialSecurityStrategyRequest(LocalDate.parse(start), LocalDate.parse(end),
                election(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4), benefit, LocalDate.of(2030, 6, 4)),
                election(AccountOwnership.SPOUSE, LocalDate.of(1965, 2, 28), 0, LocalDate.of(2032, 2, 28)),
                null, null, null, null, BigDecimal.ZERO);
        var monthlyFactor = new BigDecimal("1.01");
        var rate = monthlyFactor.pow(12).subtract(BigDecimal.ONE);
        var date = LocalDate.of(2029, 1, 1);
        var lifetime = new SocialSecurityStrategyCalculator().calculate(request);
        var calculator = new SocialSecurityStrategyValuationCalculator();
        var audit = calculator.calculate(lifetime, date, rate);
        var compact = calculator.calculateSummary(lifetime, date, rate);
        var streaming = calculator.calculateSummary(request, date, rate);
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal monthlyCents = BigDecimal.ZERO;
        for (var month : lifetime.monthlyResults()) {
            int months = Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(date), month.month()));
            var pv = month.householdBenefit().divide(monthlyFactor.pow(months), MathContext.DECIMAL128);
            expected = expected.add(pv);
            monthlyCents = monthlyCents.add(pv.setScale(2, RoundingMode.HALF_UP));
        }
        assertMoney(expected, audit.presentValue());
        assertEquals(compact, streaming);
        assertMoney(expected, streaming.presentValue());
        assertMoney(audit.presentValue(), audit.monthlyValuations().getLast().cumulativePresentValue());
        if (benefit > 0) {
            assertNotEquals(0, expected.compareTo(monthlyCents));
        }
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
