package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;

/** Non-indexed statutory IRC 1411 rules; isolated from ordinary bracket projections. */
public final class NetInvestmentIncomeTaxRules {
    private record Rules(String version, int firstTaxYear, BigDecimal rate, Map<FilingStatus, BigDecimal> thresholds) { }
    private static final Rules RULES = load();
    private NetInvestmentIncomeTaxRules() { }
    private static Rules load() {
        try (var input = NetInvestmentIncomeTaxRules.class.getResourceAsStream("/rules/niit-2013-v1.json")) {
            var rules = new ObjectMapper().readValue(input, Rules.class);
            if (!"irc-1411-2013-v1".equals(rules.version()) || rules.firstTaxYear() != 2013 || rules.thresholds().size() != FilingStatus.values().length
                    || rules.rate().signum() <= 0 || rules.rate().compareTo(BigDecimal.ONE) >= 0
                    || rules.thresholds().values().stream().anyMatch(v -> v.signum() <= 0))
                throw new IllegalArgumentException("Invalid NIIT rules.");
            return rules;
        } catch (Exception failure) { throw new IllegalStateException("Cannot load NIIT rules.", failure); }
    }
    public static BigDecimal rate() { return RULES.rate(); }
    public static int firstTaxYear() { return RULES.firstTaxYear(); }
    public static BigDecimal threshold(FilingStatus status) { return java.util.Objects.requireNonNull(RULES.thresholds().get(status)); }
}
