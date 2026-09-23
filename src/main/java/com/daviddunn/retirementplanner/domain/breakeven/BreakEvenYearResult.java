package com.daviddunn.retirementplanner.domain.breakeven;

import java.math.BigDecimal;

/** SS values are cumulative over shared years; all other values are year-end balances. */
public record BreakEvenYearResult(int year, Integer primaryAge, Integer spouseAge,
        BigDecimal baselineValue, BigDecimal currentValue, BigDecimal difference) { }
