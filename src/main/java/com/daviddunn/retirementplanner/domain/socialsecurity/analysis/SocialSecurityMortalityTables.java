package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.io.IOException;

/** Explicit, offline access to versioned production mortality tables. */
public final class SocialSecurityMortalityTables {

    public static final String SSA_PERIOD_2022_TABLE_ID =
            "SSA_PERIOD_LIFE_TABLE_2022";
    public static final String SSA_PERIOD_2022_RESOURCE =
            "/mortality/ssa-period-life-table-2022.csv";

    private static final SocialSecurityMortalityTableMetadata SSA_PERIOD_2022_METADATA =
            new SocialSecurityMortalityTableMetadata(
                    SSA_PERIOD_2022_TABLE_ID,
                    "SSA Period Life Table 2022",
                    "U.S. Social Security Administration",
                    "Mortality experience 2022; Annual Statistical Supplement 2025 Table 4.C6",
                    SocialSecurityMortalityTableType.PERIOD,
                    0,
                    120,
                    "Period life table (mortality and survival indicators, by sex and age), 2022.");

    private SocialSecurityMortalityTables() {
    }

    public static SocialSecurityMortalityTable ssaPeriod2022() {
        try {
            return new CsvSocialSecurityMortalityTableLoader().loadResource(
                    SSA_PERIOD_2022_RESOURCE,
                    SSA_PERIOD_2022_METADATA);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load bundled SSA Period Life Table 2022.",
                    exception);
        }
    }

    public static SocialSecurityMortalityTableMetadata ssaPeriod2022Metadata() {
        return SSA_PERIOD_2022_METADATA;
    }
}
