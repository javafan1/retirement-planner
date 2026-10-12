package com.daviddunn.retirementplanner.app.montecarlo;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloInflationRegressionTest {
    @Test
    void seed417FinancialFingerprints() throws Exception {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var settings = MonteCarloSettings.forPlan(plan, 25, 417, new BigDecimal("0.12"));
        var analyzer = new MonteCarloAnalyzer();
        var fixed = analyzer.analyze(plan, settings);
        var longevity = analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, settings));
        // Phase 6C.3: traced against pre-change classes; annual Michigan, federal and RMD
        // worksheets are protected separately by MichiganTaxFeedbackRegressionTest.
        assertEquals("909edbcabaca5186ee0c9d91938ea6887d3b0ad3e8f741367b935adb9c2b28af", hash(fixed.outcomes().toString()));
        assertEquals("9b2e62ae73fe5be35d92152e1576afe90d2546233ec6f801bb9e92ccb9859c2c", hash(longevity.outcomes().toString()));
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
