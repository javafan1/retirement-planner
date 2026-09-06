package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, contiguous attained-age qx table. */
public final class SocialSecurityMortalityTable {

    private final SocialSecurityMortalityTableMetadata metadata;
    private final List<SocialSecurityMortalityTableEntry> entries;
    private final Map<Integer, SocialSecurityMortalityTableEntry> entriesByAge;

    public SocialSecurityMortalityTable(
            SocialSecurityMortalityTableMetadata metadata,
            List<SocialSecurityMortalityTableEntry> entries) {
        this.metadata = Objects.requireNonNull(
                metadata,
                "Mortality table metadata is required.");
        Objects.requireNonNull(entries, "Mortality table entries are required.");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Mortality table entries cannot be empty.");
        }

        Map<Integer, SocialSecurityMortalityTableEntry> indexed = new HashMap<>();
        for (SocialSecurityMortalityTableEntry entry : entries) {
            Objects.requireNonNull(entry, "Mortality table cannot contain a null entry.");
            if (entry.attainedAge() < metadata.minimumAge()
                    || entry.attainedAge() >= metadata.maximumAge()) {
                throw new IllegalArgumentException(
                        "Mortality table age "
                                + entry.attainedAge()
                                + " must be between "
                                + metadata.minimumAge()
                                + " and "
                                + (metadata.maximumAge() - 1)
                                + ".");
            }
            if (indexed.put(entry.attainedAge(), entry) != null) {
                throw new IllegalArgumentException(
                        "Mortality table contains duplicate attained age "
                                + entry.attainedAge()
                                + ".");
            }
        }
        for (int age = metadata.minimumAge(); age < metadata.maximumAge(); age++) {
            if (!indexed.containsKey(age)) {
                throw new IllegalArgumentException(
                        "Mortality table does not contain required attained age "
                                + age
                                + ".");
            }
        }
        this.entries = List.copyOf(entries);
        this.entriesByAge = Map.copyOf(indexed);
    }

    public SocialSecurityMortalityTableMetadata metadata() {
        return metadata;
    }

    public List<SocialSecurityMortalityTableEntry> entries() {
        return entries;
    }

    /** qx: probability alive at exact age x dies before exact age x+1. */
    public BigDecimal probabilityOfDeathWithinYear(
            SocialSecurityMortalityCategory category,
            int attainedAge) {
        Objects.requireNonNull(category, "Mortality category is required.");
        SocialSecurityMortalityTableEntry entry = entriesByAge.get(attainedAge);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Mortality table does not contain age "
                            + attainedAge
                            + " for category "
                            + category
                            + ".");
        }
        return entry.probabilityOfDeathWithinYear(category);
    }
}
