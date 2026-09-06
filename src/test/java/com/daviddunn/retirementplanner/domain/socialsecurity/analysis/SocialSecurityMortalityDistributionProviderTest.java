package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityMortalityDistributionProviderTest {

    private SocialSecurityMortalityTable table;
    private SocialSecurityMortalityDistributionProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        table = new CsvSocialSecurityMortalityTableLoader().loadResource(
                "/mortality/synthetic-qx-fixture-v1.csv",
                CsvSocialSecurityMortalityTableLoaderTest.metadata());
        provider = new SocialSecurityMortalityDistributionProvider(table);
    }

    @Test
    void convertsConditionalQxToUnconditionalDeathProbabilities() {
        SocialSecurityMortalityDistributionResult result = provider.createDistribution(
                request(
                        LocalDate.of(1960, 6, 4),
                        LocalDate.of(2030, 6, 4),
                        SocialSecurityMortalityCategory.MALE));

        assertEquals(70, result.firstModeledAttainedAge());
        assertEquals(74, result.terminalDeathAge());
        assertEquals(List.of(71, 72, 73, 74),
                result.distribution().deathAges());
        assertProbability("0.10", result, 71);
        assertProbability("0.180", result, 72);
        assertProbability("0.3600", result, 73);
        assertProbability("0.3600", result, 74);
        assertExactNormalization(result.distribution());

        // At age 71: survival .90 = death .18 + survival .72.
        assertDecimal(new BigDecimal("0.90"),
                new BigDecimal("0.180").add(new BigDecimal("0.720")));
    }

    @Test
    void terminalBucketReceivesAllResidualSurvivalExactly() {
        SocialSecurityMortalityDistributionResult result = provider.createDistribution(
                request(
                        LocalDate.of(1960, 6, 4),
                        LocalDate.of(2030, 6, 4),
                        SocialSecurityMortalityCategory.FEMALE));

        assertProbability("0.05", result, 71);
        assertProbability("0.0950", result, 72);
        assertProbability("0.213750", result, 73);
        assertProbability("0.641250", result, 74);
        assertExactNormalization(result.distribution());
    }

    @Test
    void exactBirthdayIncludesCurrentInterval() {
        SocialSecurityMortalityDistributionResult result = provider.createDistribution(
                request(
                        LocalDate.of(1960, 6, 4),
                        LocalDate.of(2030, 6, 4),
                        SocialSecurityMortalityCategory.MALE));

        assertEquals(70, result.firstModeledAttainedAge());
        assertEquals(71, result.distribution().deathAges().getFirst());
    }

    @Test
    void afterBirthdaySkipsPartiallyElapsedInterval() {
        SocialSecurityMortalityDistributionResult result = provider.createDistribution(
                request(
                        LocalDate.of(1960, 6, 4),
                        LocalDate.of(2030, 6, 5),
                        SocialSecurityMortalityCategory.MALE));

        assertEquals(71, result.firstModeledAttainedAge());
        assertEquals(List.of(72, 73, 74), result.distribution().deathAges());
        assertProbability("0.20", result, 72);
        assertProbability("0.400", result, 73);
        assertProbability("0.400", result, 74);
    }

    @Test
    void dayBeforeBirthdayStartsAtNextCompleteAgeInterval() {
        SocialSecurityMortalityDistributionResult result = provider.createDistribution(
                request(
                        LocalDate.of(1960, 6, 4),
                        LocalDate.of(2030, 6, 3),
                        SocialSecurityMortalityCategory.MALE));

        assertEquals(70, result.firstModeledAttainedAge());
    }

    @Test
    void januaryFirstAndLeapDayUseChronologicalLocalDateBirthdays() {
        SocialSecurityMortalityDistributionResult january = provider.createDistribution(
                request(
                        LocalDate.of(1960, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        SocialSecurityMortalityCategory.MALE));
        SocialSecurityMortalityDistributionResult leapDay = provider.createDistribution(
                request(
                        LocalDate.of(1960, 2, 29),
                        LocalDate.of(2030, 2, 28),
                        SocialSecurityMortalityCategory.MALE));
        SocialSecurityMortalityDistributionResult afterLeapBirthday =
                provider.createDistribution(request(
                        LocalDate.of(1960, 2, 29),
                        LocalDate.of(2030, 3, 1),
                        SocialSecurityMortalityCategory.MALE));

        assertEquals(70, january.firstModeledAttainedAge());
        assertEquals(70, leapDay.firstModeledAttainedAge());
        assertEquals(71, afterLeapBirthday.firstModeledAttainedAge());
    }

    @Test
    void categorySelectionChangesDistribution() {
        SocialSecurityMortalityDistributionResult male = provider.createDistribution(
                request(LocalDate.of(1960, 6, 4), LocalDate.of(2030, 6, 4),
                        SocialSecurityMortalityCategory.MALE));
        SocialSecurityMortalityDistributionResult female = provider.createDistribution(
                request(LocalDate.of(1960, 6, 4), LocalDate.of(2030, 6, 4),
                        SocialSecurityMortalityCategory.FEMALE));

        assertNotEquals(
                male.distribution().probabilities(),
                female.distribution().probabilities());
        assertProbability("0.10", male, 71);
        assertProbability("0.05", female, 71);
    }

    @Test
    void adjustmentsNormalizeAndShiftEarlyCumulativeMortality() {
        LocalDate birth = LocalDate.of(1960, 6, 4);
        LocalDate base = LocalDate.of(2030, 6, 4);
        SocialSecurityMortalityDistribution lower = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(birth, base,
                        SocialSecurityMortalityCategory.MALE,
                        SocialSecurityMortalityAdjustment.of(new BigDecimal("0.75"))))
                .distribution();
        SocialSecurityMortalityDistribution standard = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(birth, base,
                        SocialSecurityMortalityCategory.MALE))
                .distribution();
        SocialSecurityMortalityDistribution explicitStandard = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(birth, base,
                        SocialSecurityMortalityCategory.MALE,
                        SocialSecurityMortalityAdjustment.of(new BigDecimal("1.00"))))
                .distribution();
        SocialSecurityMortalityDistribution higher = provider.createDistribution(
                new SocialSecurityMortalityDistributionRequest(birth, base,
                        SocialSecurityMortalityCategory.MALE,
                        SocialSecurityMortalityAdjustment.of(new BigDecimal("2.00"))))
                .distribution();

        assertExactNormalization(lower);
        assertExactNormalization(standard);
        assertExactNormalization(higher);
        assertEquals(standard.probabilities(), explicitStandard.probabilities());
        BigDecimal lowerBy72 = cumulativeThrough(lower, 72);
        BigDecimal standardBy72 = cumulativeThrough(standard, 72);
        BigDecimal higherBy72 = cumulativeThrough(higher, 72);
        assertTrue(lowerBy72.compareTo(standardBy72) < 0);
        assertTrue(higherBy72.compareTo(standardBy72) > 0);
    }

    private BigDecimal cumulativeThrough(
            SocialSecurityMortalityDistribution distribution,
            int deathAge) {
        return distribution.probabilities().stream()
                .filter(item -> item.deathAge() <= deathAge)
                .map(SocialSecurityMortalityProbability::probability)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void exposesVersionMetadataConventionAndReproducesExactly() {
        SocialSecurityMortalityDistributionRequest request = request(
                LocalDate.of(1960, 6, 4),
                LocalDate.of(2030, 6, 4),
                SocialSecurityMortalityCategory.MALE);
        SocialSecurityMortalityDistributionResult first =
                provider.createDistribution(request);
        SocialSecurityMortalityDistributionResult second =
                provider.createDistribution(request);

        assertEquals(table.metadata(), provider.metadata());
        assertEquals(table.metadata(), first.tableMetadata());
        assertEquals(SocialSecurityMortalityTableType.TEST_FIXTURE,
                first.tableMetadata().tableType());
        assertEquals(
                SocialSecurityMortalityPartialYearConvention
                        .NEXT_COMPLETE_BIRTHDAY_INTERVAL,
                first.partialYearConvention());
        assertEquals(first.distribution().probabilities(),
                second.distribution().probabilities());
        assertEquals(first.tableMetadata(), second.tableMetadata());
    }

    @Test
    void rejectsBaseBeforeBirthAndUnsupportedStartingAges() {
        assertThrows(IllegalArgumentException.class,
                () -> request(
                        LocalDate.of(2030, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        SocialSecurityMortalityCategory.MALE));

        IllegalArgumentException tooYoung = assertThrows(
                IllegalArgumentException.class,
                () -> provider.createDistribution(request(
                        LocalDate.of(1965, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        SocialSecurityMortalityCategory.MALE)));
        assertTrue(tooYoung.getMessage().contains("starting age 65"));

        IllegalArgumentException terminal = assertThrows(
                IllegalArgumentException.class,
                () -> provider.createDistribution(request(
                        LocalDate.of(1956, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        SocialSecurityMortalityCategory.MALE)));
        assertTrue(terminal.getMessage().contains("terminal age 74"));
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

    private void assertProbability(
            String expected,
            SocialSecurityMortalityDistributionResult result,
            int deathAge) {
        assertDecimal(
                new BigDecimal(expected),
                result.distribution().probabilityFor(deathAge)
                        .orElseThrow()
                        .probability());
    }

    private void assertExactNormalization(
            SocialSecurityMortalityDistribution distribution) {
        BigDecimal total = distribution.probabilities().stream()
                .map(SocialSecurityMortalityProbability::probability)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertDecimal(BigDecimal.ONE, total);
    }

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
