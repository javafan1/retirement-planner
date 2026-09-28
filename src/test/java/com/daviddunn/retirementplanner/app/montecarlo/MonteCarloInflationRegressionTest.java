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
        // Captured before modifying ProjectionEngine or either Monte Carlo execution path.
        assertEquals("01ed3b0ad6eb20a31d0c5b0dc4736eff5e8946ffd9913fe4d5703be22fb74bab", hash(fixed.outcomes().toString()));
        assertEquals("f7bc8ef768c25b2de93e9be750ca448bdf31561bd76a6b1ff94823c805811296", hash(longevity.outcomes().toString()));
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
