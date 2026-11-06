package com.daviddunn.retirementplanner.app.socialsecurity;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SinglePersonStage3CoupleGoldenTest {
    @Test void coupleGridAndSurvivorCashFlowsRemainExact() throws Exception {
        var result = new IntegratedRetirementClaimingGridCalculator().calculate(Stage4TestPlans.plan());
        assertEquals(81, result.cells().size());
        var text = new StringBuilder();
        text.append(result.currentPlanBaseline().evaluatedStrategy()).append('\n');
        result.currentPlanBaseline().projection().getYears().forEach(y ->
                text.append(y.getCalendarYear()).append('|').append(y.getSocialSecurityResult()).append('\n'));
        result.cells().forEach(c -> text.append(c.strategy()).append('|')
                .append(c.integratedResult().orElseThrow().metrics()).append('\n'));
        var path = Path.of("src/test/resources/single-person-stage3-couple-golden.txt");
        var legacy = Files.readString(path).replace("\r\n", "\n");
        assertEquals(legacy.lines().limit(6).toList(), text.toString().lines().limit(6).toList(),
                "Original claiming identity and every SS payment remain exact");
        var oracle = new IntegratedRetirementClaimingGridCalculator().calculate(
                PensionScheduledPaymentOracle.plan(com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MI));
        assertEquals(oracle.currentPlanBaseline().metrics(), result.currentPlanBaseline().metrics());
        for (int index = 0; index < 81; index++) {
            assertEquals(oracle.cells().get(index).strategy(), result.cells().get(index).strategy());
            assertEquals(oracle.cells().get(index).integratedResult().orElseThrow().metrics(),
                    result.cells().get(index).integratedResult().orElseThrow().metrics(), "Independent scheduled-payment oracle cell " + index);
        }
        // Approved B0 financial correction only. The original resource stays immutable as historical evidence.
        assertEquals("f000a3d13ae714dd84f6f7a72dc2323642a7d96f906a090326908c3458488e44",
                com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec.sha256(
                        text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var evidence = Path.of("target/phase22b0"); Files.createDirectories(evidence);
        Files.writeString(evidence.resolve("grid-before.txt"), legacy);
        Files.writeString(evidence.resolve("grid-after-verified.txt"), text.toString());
    }
}

