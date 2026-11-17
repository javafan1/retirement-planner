package com.daviddunn.retirementplanner.domain.tax.state.mississippi;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.time.LocalDate;

/** State-local cash receipt qualification. Conversion clause (ff) does not use this age test. */
public final class MississippiRetirementEligibility {
    public boolean exemptCash(IncomeSourceAttribution source, TaxHouseholdContext household) {
        var owner = source.owner().orElseThrow();
        var account = source.account().orElseThrow();
        var year = household.distributionDate().getYear();
        var start = source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD
                ? LocalDate.of(year, 1, 1) : household.distributionDate();
        var end = source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD
                ? household.distributionDate().minusDays(1) : LocalDate.of(year, 12, 31);
        if (end.isBefore(start)) end = start; // Opening RMD at Jan1 still requires already-qualified age.
        var normalAge = household.birthDates().get(owner).plusYears(59).plusMonths(6);
        var facts = account.participantFacts();
        if (facts.retirementConditionsMetDate() != null && facts.retirementConditionsMetDate().isAfter(start)
                && !facts.retirementConditionsMetDate().isAfter(end))
            throw new IllegalArgumentException("Mississippi receipt interval crosses retirement eligibility; payment timing is unavailable.");
        if (facts.retirementConditionsStatusOn(start) == RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET) {
            if (account.type() == AccountType.TRADITIONAL_IRA || account.type() == AccountType.ROLLOVER_IRA) {
                if (!normalAge.isAfter(start))
                    throw new IllegalArgumentException("Represented unmet retirement conditions contradict ordinary qualified IRA treatment.");
            } else return false;
        }
        // Dated employer-plan qualification already met throughout the receipt interval
        // is not undone by crossing the separate ordinary IRA age boundary later.
        if (account.type() != AccountType.TRADITIONAL_IRA && account.type() != AccountType.ROLLOVER_IRA
                && facts.retirementConditionsStatusOn(start) == RetirementParticipantFacts.RetirementConditionsStatus.MET)
            return true;
        if (!normalAge.isAfter(start)) return true; // Approved ordinary-qualified-plan assumption; no adverse facts.
        if (!normalAge.isAfter(end))
            throw new IllegalArgumentException("Mississippi retirement cash interval crosses age 59 1/2 without dated qualification; payment timing is unavailable.");
        if (account.type() == AccountType.TRADITIONAL_IRA || account.type() == AccountType.ROLLOVER_IRA) {
            if (facts.retirementConditionsStatus() != RetirementParticipantFacts.RetirementConditionsStatus.UNKNOWN)
                throw new IllegalArgumentException("Early IRA exception requires independent Mississippi qualification evidence.");
            return false; // Ordinary clearly early cash; federal penalty coverage is a separate model limitation.
        }
        throw new IllegalArgumentException("Mississippi early employer receipt needs dated plan retirement qualification.");
    }
}
