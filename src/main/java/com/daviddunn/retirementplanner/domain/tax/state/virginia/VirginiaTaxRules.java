package com.daviddunn.retirementplanner.domain.tax.state.virginia;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.util.Objects;

/** VA §§58.1-320/-321/-322.03, reviewed 2026-11-17. Nominal enacted schedule, including sunset. */
public final class VirginiaTaxRules {
    public static final String VERSION="VA-2026-1";
    public static final String REFERENCE_KEY="virginia-income-tax-rules";
    public BigDecimal standardDeduction(int year,FilingStatus status) {
        if (year<2026) throw new IllegalArgumentException("Virginia rules begin in 2026.");
        BigDecimal single=new BigDecimal(year==2026 ? "8750" : year==2027 ? "9200" : year<=2029 ? "9300" : "3000");
        return switch (Objects.requireNonNull(status)) {
            case SINGLE -> single;
            case MARRIED_FILING_JOINTLY -> single.multiply(new BigDecimal("2"));
            default -> throw new IllegalArgumentException("Virginia supports Single/MFJ only.");
        };
    }
    public BigDecimal noTaxThreshold(FilingStatus status) {
        return switch (Objects.requireNonNull(status)) {
            case SINGLE -> new BigDecimal("11950");
            case MARRIED_FILING_JOINTLY -> new BigDecimal("23900");
            default -> throw new IllegalArgumentException("Virginia supports Single/MFJ only.");
        };
    }
    /** Exact rate arithmetic; caller rounds final planning tax only. */
    public BigDecimal tax(BigDecimal base) {
        Objects.requireNonNull(base);
        if (base.signum()<0) throw new IllegalArgumentException("Negative Virginia taxable income.");
        if (base.compareTo(new BigDecimal("3000"))<=0) return base.multiply(new BigDecimal("0.02"));
        if (base.compareTo(new BigDecimal("5000"))<=0) return new BigDecimal("60").add(base.subtract(new BigDecimal("3000")).multiply(new BigDecimal("0.03")));
        if (base.compareTo(new BigDecimal("17000"))<=0) return new BigDecimal("120").add(base.subtract(new BigDecimal("5000")).multiply(new BigDecimal("0.05")));
        return new BigDecimal("720").add(base.subtract(new BigDecimal("17000")).multiply(new BigDecimal("0.0575")));
    }
}
