package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvSocialSecurityMortalityTableLoaderTest {

    private final CsvSocialSecurityMortalityTableLoader loader =
            new CsvSocialSecurityMortalityTableLoader();

    @Test
    void loadsVersionedSyntheticFixtureAndPreservesMetadata() throws Exception {
        SocialSecurityMortalityTableMetadata metadata = metadata();
        SocialSecurityMortalityTable table = loader.loadResource(
                "/mortality/synthetic-qx-fixture-v1.csv",
                metadata);

        assertEquals(metadata, table.metadata());
        assertEquals(4, table.entries().size());
        assertDecimal(new BigDecimal("0.20"),
                table.probabilityOfDeathWithinYear(
                        SocialSecurityMortalityCategory.MALE,
                        71));
        assertDecimal(new BigDecimal("0.10"),
                table.probabilityOfDeathWithinYear(
                        SocialSecurityMortalityCategory.FEMALE,
                        71));
        assertThrows(UnsupportedOperationException.class,
                () -> table.entries().clear());
    }

    @Test
    void rejectsInvalidHeaderAndNumericValue() {
        assertThrows(IOException.class,
                () -> load("age,male,female\n70,0.1,0.1\n"));
        IOException exception = assertThrows(IOException.class,
                () -> load("age,male_qx,female_qx\n70,nope,0.1\n"));
        assertTrue(exception.getMessage().contains("line 2"));
    }

    @Test
    void rejectsInvalidQxDuplicateAndMissingAge() {
        IOException qx = assertThrows(IOException.class,
                () -> load("age,male_qx,female_qx\n"
                        + "70,-0.01,0.1\n71,0.1,0.1\n"
                        + "72,0.1,0.1\n73,0.1,0.1\n"));
        assertTrue(qx.getMessage().contains("between 0 and 1"));

        IOException duplicate = assertThrows(IOException.class,
                () -> load("age,male_qx,female_qx\n"
                        + "70,0.1,0.1\n70,0.1,0.1\n"
                        + "72,0.1,0.1\n73,0.1,0.1\n"));
        assertTrue(duplicate.getMessage().contains("duplicate attained age 70"));

        IOException missing = assertThrows(IOException.class,
                () -> load("age,male_qx,female_qx\n"
                        + "70,0.1,0.1\n72,0.1,0.1\n73,0.1,0.1\n"));
        assertTrue(missing.getMessage().contains("required attained age 71"));
    }

    @Test
    void tableRejectsOutOfRangeAndMissingLookup() {
        SocialSecurityMortalityTable table = new SocialSecurityMortalityTable(
                new SocialSecurityMortalityTableMetadata(
                        "one-age", "One age", "Synthetic", "1",
                        SocialSecurityMortalityTableType.TEST_FIXTURE,
                        70, 71, "Test only."),
                List.of(new SocialSecurityMortalityTableEntry(
                        70, BigDecimal.ONE, BigDecimal.ONE)));

        assertThrows(IllegalArgumentException.class,
                () -> table.probabilityOfDeathWithinYear(
                        SocialSecurityMortalityCategory.MALE,
                        69));
    }

    private SocialSecurityMortalityTable load(String csv) throws IOException {
        return loader.load(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                metadata());
    }

    static SocialSecurityMortalityTableMetadata metadata() {
        return new SocialSecurityMortalityTableMetadata(
                "synthetic-qx-fixture-v1",
                "Synthetic qx fixture v1",
                "Retirement Planner test suite",
                "1.0",
                SocialSecurityMortalityTableType.TEST_FIXTURE,
                70,
                74,
                "Hand-authored probabilities for tests; not official actuarial data.");
    }

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
