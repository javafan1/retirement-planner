package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Objects;

public enum ArkansasLowIncomePolicy {
    STRICT("Strict — Verify Low-Income Eligibility"),
    REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED("Regular Tax Only — Low-Income Relief Not Modeled");

    public static final String REGULAR_TAX_DISCLOSURE = "Arkansas Regular-Tax Approximation: Low-income relief under "
            + "§§26-51-301 and 26-51-501 is not modeled. Actual Arkansas income tax may be lower. "
            + "This difference may affect withdrawals, Roth conversions and projected ending assets.";

    private final String label;

    ArkansasLowIncomePolicy(String label) {
        this.label = label;
    }

    public String label() { return label; }

    @JsonCreator
    public static ArkansasLowIncomePolicy fromCode(String code) {
        return valueOf(Objects.requireNonNull(code));
    }
}
