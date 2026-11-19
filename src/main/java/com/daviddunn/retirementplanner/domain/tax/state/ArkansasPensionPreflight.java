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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Internal navigation data for unresolved ordinary pension receipts in an explicitly selected Arkansas year. */
public final class ArkansasPensionPreflight {
    private ArkansasPensionPreflight() { }

    /** Ordinal among pensions in household input order, valid only for the matching immutable input document. */
    public record Review(int pensionIndex, String name, AccountOwnership originalOwner, AccountOwnership recipient) { }

    public static List<Review> pensionsToReview(Household household, LocalDate receiptDate,
            EffectiveHouseholdDeathView death, List<IncomeSourceAttribution> annualPensions, ArkansasTaxEvidence evidence,
            Set<AccountOwnership> reviewRecipients) {
        var unresolved = annualPensions.stream()
                .filter(source -> source.pension().orElseThrow().sourceOrigin() == RetirementIncomeSourceOrigin.UNKNOWN)
                .filter(source -> evidence.factsFor(source).qualification() == ArkansasTaxEvidence.SourceQualification.UNSPECIFIED)
                .filter(source -> source.owner().filter(reviewRecipients::contains).isPresent())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (unresolved.isEmpty()) return List.of();

        List<Review> review = new ArrayList<>();
        int nextIndex = 0;
        for (var person : household.members()) {
            for (var source : person.getIncomeSources()) {
                if (!(source instanceof Pension pension)) continue;
                int index = nextIndex++;
                if (pension.getSourceOrigin() != RetirementIncomeSourceOrigin.UNKNOWN) continue;
                var receipt = HouseholdPensionIncomeCalculator.annualReceipt(pension, person, receiptDate, death);
                if (receipt.amount().signum() > 0 && unresolved.remove(IncomeSourceAttribution.pension(
                        pension, receipt.recipient(), receipt.amount(), receiptDate.getYear()))) {
                    review.add(new Review(index, pension.getName(), pension.getOwnership(), receipt.recipient()));
                }
            }
        }
        return List.copyOf(review);
    }

    /** The owner must first verify that these are still the request's original inputs. */
    public static Optional<Pension> resolve(Household household, Review review) {
        if (review.pensionIndex() < 0) return Optional.empty();
        return household.members().stream().flatMap(person -> person.getIncomeSources().stream())
                .filter(Pension.class::isInstance).map(Pension.class::cast).skip(review.pensionIndex()).findFirst()
                .filter(pension -> pension.getOwnership() == review.originalOwner()
                        && pension.getName().equals(review.name())
                        && pension.getSourceOrigin() == RetirementIncomeSourceOrigin.UNKNOWN);
    }
}
