package com.daviddunn.retirementplanner.domain.benefit;

import java.math.BigDecimal;
import java.util.*;

public record AnnualBenefitEvaluation(List<ConditionalBenefitReceipt> receipts) {
    public static final AnnualBenefitEvaluation EMPTY = new AnnualBenefitEvaluation(List.of());
    public AnnualBenefitEvaluation { receipts = List.copyOf(receipts); }
    public BigDecimal gross() { return receipts.stream().map(ConditionalBenefitReceipt::gross).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal taxable() { return receipts.stream().map(ConditionalBenefitReceipt::taxable).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal excluded() { return receipts.stream().map(ConditionalBenefitReceipt::excluded).reduce(BigDecimal.ZERO, BigDecimal::add); }
}
