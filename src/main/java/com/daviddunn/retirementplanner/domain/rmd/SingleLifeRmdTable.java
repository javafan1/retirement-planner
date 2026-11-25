package com.daviddunn.retirementplanner.domain.rmd;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** IRS Table I under 26 CFR 1.401(a)(9)-9(b). Frozen for beneficiary calculation V1. */
final class SingleLifeRmdTable {
    private static final List<BigDecimal> FACTORS = load();

    private SingleLifeRmdTable() { }

    static BigDecimal divisor(int age) {
        if (age < 0 || FACTORS.isEmpty()) {
            throw new IllegalArgumentException("A verified Single Life Table divisor is unavailable.");
        }
        return FACTORS.get(Math.min(age, 120));
    }

    private static List<BigDecimal> load() {
        try (var file = SingleLifeRmdTable.class.getResourceAsStream("/rules/rmd-single-life-v1.txt")) {
            if (file == null) return List.of();
            var factors = new String(file.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.startsWith("#") && !line.isBlank())
                    .flatMap(line -> Arrays.stream(line.trim().split("\\s+"))).map(BigDecimal::new).toList();
            return factors.size() == 121 && factors.stream().allMatch(factor -> factor.signum() > 0)
                    ? factors : List.of();
        } catch (IOException | NumberFormatException unreadable) {
            return List.of();
        }
    }
}
