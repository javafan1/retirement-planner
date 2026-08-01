package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import java.math.BigDecimal;

public final class MichiganTaxParametersUnUsed {

    public static final int BASE_YEAR = 2026;

    public static final BigDecimal INCOME_TAX_RATE =
            new BigDecimal("0.0425");

    public static final BigDecimal RETIREMENT_DEDUCTION_MARRIED =
            new BigDecimal("131794");

    public static final BigDecimal RETIREMENT_DEDUCTION_SINGLE =
            new BigDecimal("65897");

    private MichiganTaxParametersUnUsed() {
    }
}