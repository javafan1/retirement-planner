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
        assertEquals(Files.readString(path).replace("\r\n", "\n"), text.toString());
    }
}

