package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Actual joint method only. No arbitrary investment split or filing optimization. */
public final class ArkansasJointWorksheet {
    private final ArkansasRateWorksheet rates = new ArkansasRateWorksheet();
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
        var gross = policy == ArkansasRateWorksheet.Policy.P_STATUTORY_CENTS
                ? rates.statutoryTaxBeforeCredits(ArkansasTaxRules.REVIEWED_YEAR, net)
                : rates.grossTax(ArkansasTaxRules.REVIEWED_YEAR, net, policy);
        var tax = netTax(gross, credits);
        return new Result(relief, deduction, credits, net, tax);
    }

    public record RecipientBounds(AccountOwnership owner, BigDecimal certain, BigDecimal possible, boolean senior) {
        public RecipientBounds {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(certain);
            Objects.requireNonNull(possible);
            if (certain.signum() < 0 || certain.compareTo(possible) > 0) {
                throw new IllegalArgumentException("Invalid Arkansas receipt bounds.");
            }
        }
    }

    public record BoundedResult(Result atCertainReceipts, BigDecimal minimum, BigDecimal maximum,
                                List<Recipient> selectedRecipients) {
        public BoundedResult {
            selectedRecipients = List.copyOf(selectedRecipients);
        }
    }

    /**
     * Bounds the best legal election when any part of a receipt may have qualified.
     * A tax-stable equality of the bounds is sufficient to admit timing uncertainty.
     */
    public BoundedResult boundStatutoryChoices(BigDecimal income, List<RecipientBounds> recipients) {
        return boundStatutoryChoices(income, recipients, BigDecimal.ZERO);
    }

    /** For increasing ordinary income whose added eligible receipts cannot exceed that increase. */
    public BoundedResult boundStatutoryChoices(BigDecimal income, List<RecipientBounds> recipients,
            BigDecimal maximumIncomeIncrease) {
        recipients = List.copyOf(recipients);
        if (recipients.isEmpty() || recipients.size() > 2
                || recipients.stream().map(RecipientBounds::possible).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(income) > 0)
            throw new IllegalArgumentException("Arkansas requires bounded receipts for one or two filers.");
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        Result bestAtCertain = null;
        List<Recipient> selectedRecipients = List.of();
        int seniors = (int) recipients.stream().filter(RecipientBounds::senior).count();
        for (int mask = 0; mask < (1 << seniors); mask++) {
            int seniorIndex = 0;
            var certain = new ArrayList<Recipient>();
            BigDecimal possibleRelief = BigDecimal.ZERO;
            for (var recipient : recipients) {
                boolean claim = !recipient.senior() || (mask & (1 << seniorIndex++)) != 0;
                certain.add(new Recipient(recipient.owner(), recipient.certain(), recipient.senior(), claim));
                if (claim) possibleRelief = possibleRelief.add(recipient.possible().min(ArkansasTaxRules.RETIREMENT_LIMIT));
            }
            var result = calculate(income, certain, ArkansasRateWorksheet.Policy.P_STATUTORY_CENTS);
            var first = income.subtract(possibleRelief).subtract(result.standardDeduction()).max(BigDecimal.ZERO);
            var range = rates.statutoryGrossBounds(first, result.netIncome().add(maximumIncomeIncrease));
            var low = netTax(range.minimum(), result.credits());
            var high = netTax(range.maximum(), result.credits());
            minimum = minimum == null ? low : minimum.min(low);
            maximum = maximum == null ? high : maximum.min(high);
            if (bestAtCertain == null || result.tax().compareTo(bestAtCertain.tax()) < 0) {
                bestAtCertain = result;
                selectedRecipients = certain;
            }
        }
        return new BoundedResult(bestAtCertain, minimum, maximum, selectedRecipients);
    }

    private static BigDecimal netTax(BigDecimal gross, BigDecimal credits) {
        return gross.subtract(credits).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Per-recipient bounds for the separate outer conversion/funding proof. */
    public record TargetReceipts(RecipientBounds existing, BigDecimal addedCertain, BigDecimal addedPossible) { }

    public BigDecimal targetTaxFloor(int year, BigDecimal ordinary, BigDecimal increaseLow,
                                    BigDecimal increaseHigh, List<TargetReceipts> receipts) {
        var deduction = ArkansasTaxRules.STANDARD_DEDUCTION.multiply(BigDecimal.valueOf(receipts.size()));
        int seniors = (int) receipts.stream().filter(r -> r.existing().senior()).count();
        BigDecimal floor = null;
        for (int mask = 0; mask < (1 << seniors); mask++) {
            int seniorIndex = 0;
            int credits = receipts.size();
            BigDecimal minimumRelief = BigDecimal.ZERO;
            BigDecimal maximumBaseRelief = BigDecimal.ZERO;
            BigDecimal maximumAddedRelief = BigDecimal.ZERO;
            for (var receipt : receipts) {
                var base = receipt.existing();
                boolean claim = !base.senior() || (mask & (1 << seniorIndex++)) != 0;
                if (base.senior()) credits += claim ? 1 : 2;
                if (claim) {
                    var cap = ArkansasTaxRules.RETIREMENT_LIMIT;
                    var baseRelief = base.possible().min(cap);
                    minimumRelief = minimumRelief.add(base.certain().add(receipt.addedCertain()).min(cap));
                    maximumBaseRelief = maximumBaseRelief.add(baseRelief);
                    maximumAddedRelief = maximumAddedRelief.add(base.possible().add(receipt.addedPossible())
                            .min(cap).subtract(baseRelief));
                }
            }
            var first = ordinary.add(increaseLow.subtract(maximumAddedRelief.min(increaseLow)))
                    .subtract(maximumBaseRelief).subtract(deduction).max(BigDecimal.ZERO);
            if (!ArkansasTaxRules.supportsLowIncomeProof(year)
                    || ordinary.add(increaseLow).compareTo(ArkansasTaxRules.lowIncomeGrossCeiling(year, receipts.size() == 2)) <= 0
                    || first.compareTo(ArkansasTaxRules.additionalCreditNetCeiling(year)) <= 0) {
                return BigDecimal.ZERO;
            }
            var last = ordinary.add(increaseHigh).subtract(minimumRelief).subtract(deduction).max(first);
            var tax = netTax(rates.statutoryGrossBounds(first, last).minimum(),
                    ArkansasTaxRules.PERSONAL_CREDIT.multiply(BigDecimal.valueOf(credits)));
            floor = floor == null ? tax : floor.min(tax);
        }
        return Objects.requireNonNull(floor);
    }
}
