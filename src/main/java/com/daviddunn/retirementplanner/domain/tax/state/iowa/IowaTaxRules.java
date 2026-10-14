package com.daviddunn.retirementplanner.domain.tax.state.iowa;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxBaseBreakdown.Coverage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Nominal Iowa parameters, deliberately separate from the research-state catalog. */
public record IowaTaxRules(
        int validatedYear,
        String version,
        BigDecimal rate,
        BigDecimal alternativeRate,
        Map<FilingStatus, BigDecimal> ordinaryThresholds,
        Map<FilingStatus, BigDecimal> seniorThresholds,
        Map<FilingStatus, BigDecimal> personalCredits,
        BigDecimal ageCredit,
        int retirementAge,
        int reliefAge,
        int creditAge,
        Coverage basePolicy,
        List<String> sources) {

    public IowaTaxRules {
        if (validatedYear != 2026 || retirementAge != 55 || reliefAge != 65 || creditAge != 65) {
            throw new IllegalArgumentException("Iowa age/year changes require admission review.");
        }
        if (Objects.requireNonNull(version).isBlank()) {
            throw new IllegalArgumentException("Iowa version is required.");
        }
        ordinaryThresholds = validateMap(ordinaryThresholds);
        seniorThresholds = validateMap(seniorThresholds);
        personalCredits = validateMap(personalCredits);
        positive(rate);
        positive(alternativeRate);
        positive(ageCredit);
        if (rate.compareTo(BigDecimal.ONE) >= 0 || alternativeRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Invalid Iowa rate.");
        }
        Objects.requireNonNull(basePolicy);
        sources = List.copyOf(sources);
        if (sources.isEmpty() || sources.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Iowa official sources are required.");
        }
    }

    public boolean supportsYear(int year) {
        return year >= validatedYear;
    }

    public BigDecimal threshold(FilingStatus status, boolean senior) {
        return Objects.requireNonNull((senior ? seniorThresholds : ordinaryThresholds).get(status));
    }

    private static Map<FilingStatus, BigDecimal> validateMap(Map<FilingStatus, BigDecimal> values) {
        var result = Map.copyOf(values);
        if (!result.keySet().equals(Set.of(FilingStatus.SINGLE, FilingStatus.MARRIED_FILING_JOINTLY))) {
            throw new IllegalArgumentException("Iowa supports Single and MFJ only.");
        }
        result.values().forEach(IowaTaxRules::positive);
        return result;
    }

    private static void positive(BigDecimal amount) {
        if (Objects.requireNonNull(amount).signum() <= 0) {
            throw new IllegalArgumentException("Iowa parameter must be positive.");
        }
    }
}
