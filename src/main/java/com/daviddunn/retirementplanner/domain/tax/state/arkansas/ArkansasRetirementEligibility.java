package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import java.time.LocalDate;
import java.util.Objects;

/** Source-local legal worksheet, not a production admission service. */
public final class ArkansasRetirementEligibility {
    public enum Source { OWN_IRA_CASH, OWN_IRA_CONVERSION_INTERPRETATION, ORDINARY_EMPLOYER_PLAN,
        DIRECT_SURVIVOR_PENSION, COMMERCIAL_ANNUITY, QUALIFIED_ROTH, UNSUPPORTED_SPECIAL }
    public enum Decision { QUALIFYING_POOL, TAXABLE_WITHOUT_POOL, OUTSIDE_FEDERAL_BASE, UNSUPPORTED }

    public Decision assess(Source source, LocalDate birthDate, LocalDate firstReceipt, LocalDate lastReceipt,
            boolean evidencedOrdinaryPlan) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(firstReceipt);
        Objects.requireNonNull(lastReceipt);
        if (lastReceipt.isBefore(firstReceipt)) throw new IllegalArgumentException("Reversed receipt interval.");
        return switch (source) {
            case OWN_IRA_CASH, OWN_IRA_CONVERSION_INTERPRETATION -> {
                if (birthDate == null) yield Decision.UNSUPPORTED;
                var qualifies = age59AndHalf(birthDate);
                if (firstReceipt.isBefore(birthDate)) yield Decision.UNSUPPORTED;
                if (!firstReceipt.isBefore(qualifies)) yield Decision.QUALIFYING_POOL;
                if (lastReceipt.isBefore(qualifies)) yield Decision.TAXABLE_WITHOUT_POOL;
                yield Decision.UNSUPPORTED;
            }
            case ORDINARY_EMPLOYER_PLAN, DIRECT_SURVIVOR_PENSION -> evidencedOrdinaryPlan
                    ? Decision.QUALIFYING_POOL : Decision.UNSUPPORTED;
            case COMMERCIAL_ANNUITY -> Decision.TAXABLE_WITHOUT_POOL;
            case QUALIFIED_ROTH -> Decision.OUTSIDE_FEDERAL_BASE;
            case UNSUPPORTED_SPECIAL -> Decision.UNSUPPORTED;
        };
    }

    /** The preparatory calendar convention clamps month ends at each step. */
    public static LocalDate age59AndHalf(LocalDate birthDate) {
        return Objects.requireNonNull(birthDate).plusYears(59).plusMonths(6);
    }
}
