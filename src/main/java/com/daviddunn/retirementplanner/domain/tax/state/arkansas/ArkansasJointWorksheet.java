package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.math.BigDecimal;
import java.util.*;

/** Actual joint method only. No arbitrary investment split or filing optimization. */
public final class ArkansasJointWorksheet {
    public record Recipient(AccountOwnership owner, BigDecimal qualifyingReceipts, boolean senior,
            boolean claimRetirementPool) {
        public Recipient {
            Objects.requireNonNull(owner);
            if (owner != AccountOwnership.PRIMARY && owner != AccountOwnership.SPOUSE)
                throw new IllegalArgumentException("An individual recipient is required.");
            if (Objects.requireNonNull(qualifyingReceipts).signum() < 0) throw new IllegalArgumentException("Negative receipts.");
        }
    }
    public record Result(BigDecimal retirementSubtraction, BigDecimal standardDeduction,
            BigDecimal credits, BigDecimal netIncome, BigDecimal tax) { }

    /** At most four ordinary joint alternatives: each senior's pool versus 65 Special.
     * This does not choose filing status or use unpublished low-income tables. */
    public Result lowestReviewedChoice(BigDecimal income, List<Recipient> recipients, ArkansasRateWorksheet.Policy policy) {
        recipients = List.copyOf(recipients);
        if (recipients.isEmpty() || recipients.size() > 2) throw new IllegalArgumentException("One or two filers required.");
        Result best = null;
        for (int mask = 0; mask < (1 << recipients.size()); mask++) {
            var choice = new ArrayList<Recipient>();
            for (int i = 0; i < recipients.size(); i++) {
                var r = recipients.get(i);
                choice.add(new Recipient(r.owner(), r.qualifyingReceipts(), r.senior(),
                        !r.senior() || (mask & (1 << i)) != 0));
            }
            var candidate = calculate(income, choice, policy);
            if (best == null || candidate.tax().compareTo(best.tax()) < 0) best = candidate;
        }
        return Objects.requireNonNull(best);
    }

    public Result calculate(BigDecimal ordinaryStateIncome, List<Recipient> recipients, ArkansasRateWorksheet.Policy policy) {
        Objects.requireNonNull(ordinaryStateIncome);
        recipients = List.copyOf(recipients);
        if (recipients.isEmpty() || recipients.size() > 2 || recipients.stream().map(Recipient::owner).distinct().count() != recipients.size())
            throw new IllegalArgumentException("One or two distinct actual filers required.");
        var receipts = recipients.stream().map(Recipient::qualifyingReceipts).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ordinaryStateIncome.compareTo(receipts) < 0) throw new IllegalArgumentException("Retirement evidence exceeds income.");
        var relief = recipients.stream().filter(Recipient::claimRetirementPool).map(r -> r.qualifyingReceipts().min(ArkansasTaxRules.RETIREMENT_LIMIT))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int creditCount = recipients.size();
        for (var recipient : recipients) if (recipient.senior()) creditCount += recipient.claimRetirementPool() ? 1 : 2;
        var credits = ArkansasTaxRules.PERSONAL_CREDIT.multiply(BigDecimal.valueOf(creditCount));
        var deduction = ArkansasTaxRules.STANDARD_DEDUCTION.multiply(BigDecimal.valueOf(recipients.size()));
        var net = ordinaryStateIncome.subtract(relief).subtract(deduction).max(BigDecimal.ZERO);
        var tax = new ArkansasRateWorksheet().grossTax(2026, net, policy).subtract(credits).max(BigDecimal.ZERO).setScale(2);
        return new Result(relief, deduction, credits, net, tax);
    }
}
