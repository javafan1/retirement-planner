package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** Binds the selected assumption and the accepted execution scope to one retained result. */
public final class SpousalInheritancePlanningEvidence {

    private static final String KEY = "spousal-inheritance-planning=";
    private static final String APPLIED_KEY = "spousal-inheritance-applied=";
    public static final String APPLIED_POLICY = "FL-BASIC-SPOUSE-OWNER-IRA-RMD-V1";
    public static final String ROLLOVER_POLICY = "FL-BASIC-SPOUSE-OWNER-401K-DIRECT-ROLLOVER-V1";
    public static final String BENEFICIARY_POLICY = "FL-BASIC-SPOUSE-BENEFICIARY-IRA-RMD-V1";
    public static final String EMPLOYER_BENEFICIARY_POLICY = "FL-BASIC-SPOUSE-BENEFICIARY-401K-RMD-V1";
    public static final String POST_RBD_401K_OWNER_POLICY = "FL-BASIC-SPOUSAL-401K-POST-RBD-OWNER-ROLLOVER-V1";
    public static final String POST_RBD_401K_POLICY = "FL-BASIC-SPOUSAL-401K-POST-RBD-BENEFICIARY-V1";
    public static final String DEATH_YEAR_IRA_POLICY = "FL-BASIC-SPOUSAL-DEATH-YEAR-RMD-V1";
    public static final String SUCCESSOR_ORDINARY_POLICY = "FL-BASIC-SPOUSAL-SUCCESSOR-ORDINARY-WITHDRAWALS-V1";
    public static final String NOTICE =
            "Saved for future use. Current projections use legacy retirement account inheritance calculations.";
    public static final String EDITOR_NOTICE =
            "Florida Basic supports qualifying traditional IRA and 401(k) RMDs, ordinary successor withdrawals, and certain 401(k) rollovers. "
                    + "Employer-plan options depend on plan terms. Other routes remain pending under legacy calculations.";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AnalysisSnapshotCodec CODEC = new AnalysisSnapshotCodec();

    private SpousalInheritancePlanningEvidence() { }

    public static String completed(SpousalInheritanceAssumption assumption, String inputs,
                                   RetainedProjectionSnapshot annual, String legacyProvenance) {
        return assumption == null ? legacyProvenance : prefix(assumption, inputs, annual) + legacyProvenance;
    }

    static void validate(String inputs, RetainedProjectionSnapshot annual, String provenance) {
        var assumption = read(inputs);
        if (applied(annual)) {
            var expected = (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_IRA_SCHEMA
                    || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_401K_SCHEMA
                    || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA)
                    ? SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY
                    : SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER;
            if (assumption == null || (annual.schemaVersion() != RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA
                        && annual.schemaVersion() != RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA
                        && assumption.treatment() != expected)
                    || !provenance.startsWith(prefix(assumption, inputs, annual)) || provenance.contains(KEY)
                    || provenance.indexOf(APPLIED_KEY) != provenance.lastIndexOf(APPLIED_KEY)) {
                throw new IllegalArgumentException("Missing or mismatched applied spousal IRA RMD policy.");
            }
            return;
        }
        if (provenance.contains(APPLIED_KEY) || (assumption == null && provenance.contains(KEY))
                || (assumption != null && !provenance.startsWith(prefix(assumption, inputs, annual)))
                || provenance.indexOf(KEY) != provenance.lastIndexOf(KEY)) {
            throw new IllegalArgumentException("Missing or mismatched spousal inheritance planning evidence.");
        }
    }

    static String calculationProvenance(String provenance) {
        if (!provenance.startsWith(KEY) && !provenance.startsWith(APPLIED_KEY)) return provenance;
        int binding = provenance.indexOf("; ");
        return provenance.substring(provenance.indexOf("; ", binding + 2) + 2);
    }

    static String disclosure(String inputs, RetainedProjectionSnapshot annual) {
        var assumption = read(inputs);
        if (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_OWNER_SCHEMA) {
            return "Spousal retirement account inheritance: SPOUSE AS OWNER applied in Florida Basic. "
                    + "After the modeled January 1 post-RBD death, each original 401(k) pays its death-year minimum to the spouse. "
                    + "The following January 1 each plan pays its beneficiary minimum before the eligible remainder rolls directly "
                    + "to the spouse's own traditional IRA. This assumes confirmed plan terms. The transfer contributes to "
                    + "the receiving IRA's year-end balance for the next year's minimum.";
        }
        if (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA) {
            return "Spousal retirement account inheritance: SPOUSE AS BENEFICIARY applied to traditional 401(k)s "
                    + "in Florida Basic after the plan's confirmed required beginning date. Death is modeled January 1. "
                    + "Each original plan pays its death-year minimum to the survivor. Following-year minimums use the "
                    + "greater applicable Single Life factor. This requires compatible plan terms permitting beneficiary "
                    + "life-expectancy payments and additional withdrawals. Actual plans may require faster payouts.";
        }
        if (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA) {
            return "Spousal retirement account inheritance: " + assumption.treatment().label()
                    + " applied to traditional IRAs in Florida Basic. The modeled death date is January 1. "
                    + "After the required beginning date, the surviving spouse receives the original owner's death-year minimum.";
        }
        if (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA) {
            var detail = annual.spousal401kRollovers() != null
                    ? " This includes qualifying traditional 401(k) direct rollover to own-IRA treatment. Actual employer-plan terms may restrict rollovers."
                    : annual.beneficiary401kObligations() != null
                        ? " Beneficiary 401(k) RMDs use recalculated Single Life expectancy. The model assumes additional payouts are available. Actual employer-plan terms may restrict payout options."
                        : "";
            return "Spousal retirement account inheritance: " + assumption.treatment().label()
                    + " applied to qualifying required distributions and ordinary withdrawals for expenses and taxes in Florida Basic."
                    + detail;
        }
        if (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_401K_SCHEMA) {
            String sources = annual.beneficiaryIraObligations().isEmpty() ? "traditional 401(k)" : "traditional IRA and 401(k)";
            return "Spousal retirement account inheritance: SPOUSE AS BENEFICIARY applied in Florida Basic. "
                    + "Qualifying " + sources + " distributions use recalculated Single Life expectancy. "
                    + "Actual employer-plan terms may restrict payout options.";
        }
        if (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_IRA_SCHEMA) {
            return "Spousal retirement account inheritance: SPOUSE AS BENEFICIARY applied in Florida Basic. "
                    + "Qualifying traditional IRA distributions use recalculated Single Life expectancy without a spouse-as-employee election.";
        }
        if (annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_401K_ROLLOVER_SCHEMA) {
            return annual.spousal401kRollovers().isEmpty()
                    ? "Spousal retirement account inheritance: SPOUSE AS OWNER applied in Florida Basic. "
                        + "No employer-plan balance remained for a modeled rollover."
                    : "Spousal retirement account inheritance: SPOUSE AS OWNER applied in Florida Basic, including qualifying "
                        + "traditional 401(k) direct rollover to own-IRA treatment. Actual employer-plan terms may restrict rollovers.";
        }
        if (applied(annual)) return "Spousal retirement account inheritance: SPOUSE AS OWNER applied to qualifying "
                + "traditional IRA required distributions in Florida Basic. This is a modeled election.";
        return assumption == null ? "" : "Spousal retirement account inheritance: "
                + assumption.treatment().label() + ". " + NOTICE;
    }

    static boolean applied(RetainedProjectionSnapshot annual) {
        return annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_IRA_RMD_SCHEMA
                || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_401K_ROLLOVER_SCHEMA
                || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_IRA_SCHEMA
                || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_401K_SCHEMA
                || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA
                || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA
                || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA
                || annual.schemaVersion() == RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_OWNER_SCHEMA;
    }

    private static SpousalInheritanceAssumption read(String inputs) {
        try {
            var node = JSON.readTree(inputs).path("planningAssumptions").path("economicAssumptions").path("spousalInheritance");
            return node.isMissingNode() || node.isNull() ? null : SpousalInheritanceAssumption.fromWire(node);
        } catch (java.io.IOException invalid) {
            // Legacy reader validation owns unrelated unavailable historical inputs.
            return null;
        }
    }

    private static String prefix(SpousalInheritanceAssumption assumption, String inputs, RetainedProjectionSnapshot annual) {
        if (applied(annual)) {
            var policy = switch (annual.schemaVersion()) {
                case RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_SCHEMA -> POST_RBD_401K_POLICY;
                case RetainedProjectionSnapshot.SPOUSAL_POST_RBD_401K_OWNER_SCHEMA -> POST_RBD_401K_OWNER_POLICY;
                case RetainedProjectionSnapshot.SPOUSAL_DEATH_YEAR_IRA_SCHEMA -> DEATH_YEAR_IRA_POLICY;
                case RetainedProjectionSnapshot.SPOUSAL_SUCCESSOR_ORDINARY_SCHEMA -> SUCCESSOR_ORDINARY_POLICY;
                case RetainedProjectionSnapshot.SPOUSAL_401K_ROLLOVER_SCHEMA -> ROLLOVER_POLICY;
                case RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_IRA_SCHEMA -> BENEFICIARY_POLICY;
                case RetainedProjectionSnapshot.SPOUSAL_BENEFICIARY_401K_SCHEMA -> EMPLOYER_BENEFICIARY_POLICY;
                default -> APPLIED_POLICY;
            };
            var binding = CODEC.executionDigest(List.of(policy, CompleteProjectionSnapshot.digest(inputs), annual));
            return APPLIED_KEY + policy + "; binding=" + binding + "; ";
        }
        var binding = CODEC.executionDigest(List.of(CompleteProjectionSnapshot.digest(inputs), annual));
        return KEY + "v" + assumption.version() + "/" + assumption.treatment().name() + "; binding=" + binding + "; ";
    }
}
