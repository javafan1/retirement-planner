package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityMortalityWeightedComparisonRequestTest {

    @Test
    void rejectsMortalitySupportBeforeKnownAliveBaseDate() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SocialSecurityMortalityWeightedComparisonRequest(
                        strategy(),
                        strategy(),
                        distribution(60),
                        distribution(80),
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        BigDecimal.ZERO));

        assertTrue(exception.getMessage().contains("Primary mortality death age 60"));
        assertTrue(exception.getMessage().contains("before mortality base date"));
    }

    private SocialSecurityMortalityDistribution distribution(int age) {
        return new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(age, BigDecimal.ONE)));
    }

    private SocialSecurityStrategyRequest strategy() {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1),
                null,
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 1, 2), 3000,
                        LocalDate.of(2030, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 1, 2), 1200,
                        LocalDate.of(2032, 1, 2)),
                LocalDate.of(2023, 1, 2),
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2053, 1, 2),
                LocalDate.of(2060, 1, 2),
                new BigDecimal("0.02"));
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
                2025,
                claimDate);
    }
}
