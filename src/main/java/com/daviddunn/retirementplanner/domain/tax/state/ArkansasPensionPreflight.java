package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Internal navigation data for unresolved ordinary pension receipts in an explicitly selected Arkansas year. */
public final class ArkansasPensionPreflight {
    private ArkansasPensionPreflight() { }

    /** The pension reference locates the original editor row, even when a spouse receives the payment. */
    public record Review(Pension pension, String name, AccountOwnership originalOwner, AccountOwnership recipient) { }

    public static List<Review> pensionsToReview(Household household, LocalDate receiptDate,
            EffectiveHouseholdDeathView death, List<IncomeSourceAttribution> annualPensions, ArkansasTaxEvidence evidence) {
        var unresolved = annualPensions.stream()
                .filter(source -> source.pension().orElseThrow().sourceOrigin() == RetirementIncomeSourceOrigin.UNKNOWN)
                .filter(source -> evidence.factsFor(source).qualification() == ArkansasTaxEvidence.SourceQualification.UNSPECIFIED)
                .toList();
        if (unresolved.isEmpty()) return List.of();

        List<Review> review = new ArrayList<>();
        for (var person : household.members()) {
            for (var source : person.getIncomeSources()) {
                if (!(source instanceof Pension pension) || pension.getSourceOrigin() != RetirementIncomeSourceOrigin.UNKNOWN) continue;
                var receipt = HouseholdPensionIncomeCalculator.annualReceipt(pension, person, receiptDate, death);
                if (receipt.amount().signum() > 0 && unresolved.contains(IncomeSourceAttribution.pension(
                        pension, receipt.recipient(), receipt.amount(), receiptDate.getYear()))) {
                    review.add(new Review(pension, pension.getName(), pension.getOwnership(), receipt.recipient()));
                }
            }
        }
        return List.copyOf(review);
    }
}
