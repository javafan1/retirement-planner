package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityMortalityDistributionProviderIntegrationTest {

    @Test
    void providerDistributionsFeedExistingMortalityWeightedComparisonDirectly()
            throws Exception {
        SocialSecurityMortalityTable table =
                new CsvSocialSecurityMortalityTableLoader().loadResource(
                        "/mortality/synthetic-qx-fixture-v1.csv",
                        CsvSocialSecurityMortalityTableLoaderTest.metadata());
        SocialSecurityMortalityDistributionProvider provider =
                new SocialSecurityMortalityDistributionProvider(table);
        LocalDate birthDate = LocalDate.of(1955, 1, 2);
        LocalDate baseDate = LocalDate.of(2025, 1, 2);

        SocialSecurityMortalityDistribution primary = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        birthDate,
                        baseDate,
                        SocialSecurityMortalityCategory.MALE))
                .distribution();
        SocialSecurityMortalityDistribution spouse = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(
                        birthDate,
                        baseDate,
                        SocialSecurityMortalityCategory.FEMALE))
                .distribution();

        SocialSecurityMortalityWeightedComparisonResult result =
                new SocialSecurityMortalityWeightedComparisonCalculator().calculate(
                        new SocialSecurityMortalityWeightedComparisonRequest(
                                strategy(birthDate, LocalDate.of(2025, 1, 2)),
                                strategy(birthDate, LocalDate.of(2022, 1, 2)),
                                primary,
                                spouse,
                                baseDate,
                                baseDate,
                                new BigDecimal("0.01")));

        assertEquals(16, result.scenarios().size());
        assertEquals(0, BigDecimal.ONE.compareTo(result.totalJointProbability()));
        assertTrue(result.expectedPresentValueStrategyA().signum() > 0);
        assertTrue(result.expectedPresentValueStrategyB().signum() > 0);
        assertEquals(table.metadata().tableId(), "synthetic-qx-fixture-v1");
    }

    private SocialSecurityStrategyRequest strategy(
            LocalDate birthDate,
            LocalDate primaryClaimDate) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 2),
                null,
                election(AccountOwnership.PRIMARY,
                        birthDate, 3000, primaryClaimDate),
                election(AccountOwnership.SPOUSE,
                        birthDate, 1200, LocalDate.of(2017, 1, 2)),
                LocalDate.of(2015, 1, 2),
                LocalDate.of(2015, 1, 2),
                LocalDate.of(2028, 1, 2),
                LocalDate.of(2029, 1, 2),
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
