package com.daviddunn.retirementplanner.ui.socialsecurity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecurityStrategyAnalyzerDialogInputTest {

    @Test
    void acceptsPracticalRangeAndReturnsActionableValidation() {
        assertEquals(new BigDecimal("0.50"),
                SocialSecurityStrategyAnalyzerDialog.adjustment("0.50", "Primary")
                        .factor());
        assertEquals(new BigDecimal("3.00"),
                SocialSecurityStrategyAnalyzerDialog.adjustment("3.00", "Spouse")
                        .factor());

        IllegalArgumentException text = assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityStrategyAnalyzerDialog.adjustment("not-a-number", "Primary"));
        assertEquals(
                "Primary mortality adjustment must be a number from 0.50 to 3.00.",
                text.getMessage());
        IllegalArgumentException range = assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityStrategyAnalyzerDialog.adjustment("3.01", "Spouse"));
        assertEquals("Spouse mortality adjustment must be from 0.50 to 3.00.",
                range.getMessage());
    }
}
