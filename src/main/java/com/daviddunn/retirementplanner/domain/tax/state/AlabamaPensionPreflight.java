package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.PensionPlanClassification;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Finds editable pension records with positive receipts in potentially eligible Alabama tax years. */
public final class AlabamaPensionPreflight {
    private AlabamaPensionPreflight() { }

    public static List<Pension> pensionsToReview(Household household, PlanningAssumptions assumptions) {
        if (assumptions.getStateTaxAssumptions().jurisdiction() != StateTaxJurisdiction.AL) return List.of();

        var start = assumptions.getProjectionStartDate();
        var endYear = (long) start.getYear() + assumptions.getProjectionLengthYears() - 1;
        if (endYear > Year.MAX_VALUE) return List.of();
        var deaths = assumptions.getDeathScenarioAssumptions();
        if (!household.hasSpouse() && deaths.getDeathScenario() == DeathScenario.SPOUSE_DIES) return List.of();
        var deathView = EffectiveHouseholdDeathView.resolve(household, deaths, Optional.empty());
        var configuredStatus = assumptions.getTaxAssumptions().getFilingStatus();
        var review = new ArrayList<Pension>();
        for (var person : household.members()) {
            for (var source : person.getIncomeSources()) {
                if (!(source instanceof Pension pension)
                        || pension.getPlanClassification() != PensionPlanClassification.UNKNOWN
                        || pension.getOwnership() == AccountOwnership.JOINT
                        || !household.peopleByOwner().containsKey(pension.getOwnership())) continue;

                int first = Math.max(Math.max(start.getYear(), pension.getStartDate().getYear()),
                        AlabamaProductionTaxCalculator.FIRST_SUPPORTED_YEAR);
                int last = pension.getEndDate() == null ? (int) endYear
                        : Math.min((int) endYear, pension.getEndDate().getYear());
                for (int year = first; year <= last; year++) {
                    if (deathView.isHouseholdDeceased(year)) break;
                    var date = year == start.getYear() ? start : LocalDate.of(year, 12, 31);
                    var status = household.hasSpouse()
                            && deathView.firstDeathYear().filter(death -> death.getValue() < date.getYear()).isPresent()
                            ? FilingStatus.SINGLE : configuredStatus;
                    var taxHousehold = TaxHouseholdContext.capture(household, date, deaths, Optional.empty());
                    if (!AlabamaProductionTaxCalculator.hasSupportedFilers(status, taxHousehold)) continue;
                    var receipt = HouseholdPensionIncomeCalculator.annualReceipt(pension, person, date, deathView);
                    if (receipt.amount().signum() > 0 && taxHousehold.activeOwners().contains(receipt.recipient())) {
                        review.add(pension);
                        break;
                    }
                }
            }
        }
        return List.copyOf(review);
    }

    /** Distinguishes confirmed pension ambiguity from other state-tax failures in the UI. */
    public static boolean explainsFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof StateTaxUnavailableException unavailable
                    && unavailable.result().jurisdiction() == StateTaxJurisdiction.AL
                    && unavailable.result().limitations().contains(AlabamaProductionTaxCalculator.UNKNOWN_PENSION_CLASSIFICATION)) {
                return true;
            }
        }
        return false;
    }
}
