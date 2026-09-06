package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityProductionMortalityTableTest {

    @Test
    void loadsExplicitVersionedSsaPeriodTableMetadata() {
        SocialSecurityMortalityTable table =
                SocialSecurityMortalityTables.ssaPeriod2022();
        SocialSecurityMortalityTableMetadata metadata = table.metadata();

        assertEquals("SSA_PERIOD_LIFE_TABLE_2022", metadata.tableId());
        assertEquals("SSA Period Life Table 2022", metadata.displayName());
        assertEquals("U.S. Social Security Administration", metadata.sourceName());
        assertEquals(
                "Mortality experience 2022; Annual Statistical Supplement 2025 Table 4.C6",
                metadata.sourceVersion());
        assertEquals(SocialSecurityMortalityTableType.PERIOD, metadata.tableType());
        assertEquals(0, metadata.minimumAge());
        assertEquals(120, metadata.maximumAge());
        assertEquals(120, table.entries().size());
    }

    @Test
    void representativePublishedValuesMatchSsaTableExactly() {
        SocialSecurityMortalityTable table =
                SocialSecurityMortalityTables.ssaPeriod2022();

        assertQx(table, 0, "0.006064", "0.005119");
        assertQx(table, 65, "0.017897", "0.011018");
        assertQx(table, 85, "0.100525", "0.076841");
        assertQx(table, 118, "0.957970", "0.957970");
        assertQx(table, 119, "1.000000", "1.000000");
        assertNotEquals(
                table.probabilityOfDeathWithinYear(
                        SocialSecurityMortalityCategory.MALE, 65),
                table.probabilityOfDeathWithinYear(
                        SocialSecurityMortalityCategory.FEMALE, 65));
    }

    @Test
    void productionAgesAreContiguousUniqueAndAllQxAreValid() {
        SocialSecurityMortalityTable table =
                SocialSecurityMortalityTables.ssaPeriod2022();
        Set<Integer> ages = new HashSet<>();

        for (int index = 0; index < table.entries().size(); index++) {
            SocialSecurityMortalityTableEntry entry = table.entries().get(index);
            assertEquals(index, entry.attainedAge());
            assertTrue(ages.add(entry.attainedAge()));
            assertProbability(entry.maleQx());
            assertProbability(entry.femaleQx());
        }
        assertEquals(120, ages.size());
    }

    @Test
    void productionProviderGeneratesDistinctNormalizedCategoryDistributions() {
        SocialSecurityMortalityDistributionProvider provider =
                new SocialSecurityMortalityDistributionProvider(
                        SocialSecurityMortalityTables.ssaPeriod2022());
        LocalDate birthDate = LocalDate.of(1960, 6, 4);
        LocalDate baseDate = LocalDate.of(2030, 6, 4);

        SocialSecurityMortalityDistributionResult male = provider.createDistribution(
                request(birthDate, baseDate, SocialSecurityMortalityCategory.MALE));
        SocialSecurityMortalityDistributionResult female = provider.createDistribution(
                request(birthDate, baseDate, SocialSecurityMortalityCategory.FEMALE));

        assertEquals(70, male.firstModeledAttainedAge());
        assertEquals(120, male.terminalDeathAge());
        assertEquals(50, male.distribution().probabilities().size());
        assertEquals(71, male.distribution().deathAges().getFirst());
        assertEquals(120, male.distribution().deathAges().getLast());
        assertNormalized(male.distribution());
        assertNormalized(female.distribution());
        assertNotEquals(
                male.distribution().probabilities(),
                female.distribution().probabilities());
        male.distribution().probabilities().forEach(probability ->
                assertTrue(!SocialSecurityDeathDateCalculator.calculateDeathDate(
                                birthDate,
                                probability.deathAge())
                        .isBefore(baseDate)));
    }

    @Test
    void productionProviderPreservesBirthdayLeapDayAndJanuaryFirstConventions() {
        SocialSecurityMortalityDistributionProvider provider =
                new SocialSecurityMortalityDistributionProvider(
                        SocialSecurityMortalityTables.ssaPeriod2022());
        SocialSecurityMortalityDistributionResult birthday = provider.createDistribution(
                request(LocalDate.of(1960, 6, 4), LocalDate.of(2030, 6, 4),
                        SocialSecurityMortalityCategory.MALE));
        SocialSecurityMortalityDistributionResult dayAfter = provider.createDistribution(
                request(LocalDate.of(1960, 6, 4), LocalDate.of(2030, 6, 5),
                        SocialSecurityMortalityCategory.MALE));
        SocialSecurityMortalityDistributionResult leapDay = provider.createDistribution(
                request(LocalDate.of(1960, 2, 29), LocalDate.of(2030, 2, 28),
                        SocialSecurityMortalityCategory.FEMALE));
        SocialSecurityMortalityDistributionResult januaryFirst =
                provider.createDistribution(request(
                        LocalDate.of(1960, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        SocialSecurityMortalityCategory.FEMALE));

        assertEquals(70, birthday.firstModeledAttainedAge());
        assertEquals(71, dayAfter.firstModeledAttainedAge());
        assertEquals(70, leapDay.firstModeledAttainedAge());
        assertEquals(70, januaryFirst.firstModeledAttainedAge());
        assertNormalized(leapDay.distribution());
        assertNormalized(januaryFirst.distribution());
    }

    private SocialSecurityMortalityDistributionRequest request(
            LocalDate birthDate,
            LocalDate baseDate,
            SocialSecurityMortalityCategory category) {
        return new SocialSecurityMortalityDistributionRequest(
                birthDate,
                baseDate,
                category);
    }

    private void assertQx(
            SocialSecurityMortalityTable table,
            int age,
            String male,
            String female) {
        assertDecimal(new BigDecimal(male),
                table.probabilityOfDeathWithinYear(
                        SocialSecurityMortalityCategory.MALE, age));
        assertDecimal(new BigDecimal(female),
                table.probabilityOfDeathWithinYear(
                        SocialSecurityMortalityCategory.FEMALE, age));
    }

    private void assertProbability(BigDecimal probability) {
        assertTrue(probability.signum() >= 0);
        assertTrue(probability.compareTo(BigDecimal.ONE) <= 0);
    }

    private void assertNormalized(SocialSecurityMortalityDistribution distribution) {
        BigDecimal total = distribution.probabilities().stream()
                .map(SocialSecurityMortalityProbability::probability)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertDecimal(BigDecimal.ONE, total);
    }

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
