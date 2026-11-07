package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.financial.ContractualPremiums;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Optional;

/** Digest-bound additive deterministic capability; restoration never evaluates an expense. */
public final class ContractualPremiumExecutionSemantics {
    public static final String KEY = "; contractual-premiums=";
    public static final String MODEL = "insured-alive-months-v1";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private ContractualPremiumExecutionSemantics() { }

    public static String completed(RetirementPlan plan, String inputs, RetainedProjectionSnapshot annual, String provenance) {
        if (!ContractualPremiums.configured(plan)) return provenance;
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), plan.getPlanningAssumptions().getDeathScenarioAssumptions(), Optional.empty());
        for (var year : annual.years()) {
            var expected = plan.getHousehold().getExpenses().stream().filter(e -> e.isContractualInsurancePremium())
                    .map(e -> ContractualPremiums.annual(e, plan.getPlanningAssumptions().getProjectionStartDate(), year.year(), deaths))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            // Legacy negative expense adjustments may offset an otherwise valid premium total.
            if (plan.getHousehold().getExpenses().stream().noneMatch(e -> e.getAnnualAmount().signum() < 0)
                    && expected.compareTo(year.settlement().ordinaryExpenses()) > 0)
                throw new IllegalArgumentException("Completed expenses omit contractual premiums.");
        }
        return provenance + stamp(inputs, annual);
    }

    public static void validate(String inputs, RetainedProjectionSnapshot annual, String provenance) {
        boolean configured = false;
        // Legacy history may intentionally preserve incompatible financial assumptions for inspection.
        // Inspect only the expense discriminator here; leave unrelated historical decoding to its
        // established compatibility boundary. Escaped JSON property names remain correctly decoded.
        try {
            for (var expense : JSON.readTree(inputs).path("household").path("expenses"))
                if ("CONTRACTUAL_INSURANCE_PREMIUM".equals(expense.path("expenseType").asText())
                        || expense.has("contractualPremium")) configured = true;
        } catch (java.io.IOException invalid) {
            if (provenance.contains(KEY)) throw new IllegalArgumentException("Invalid premium input binding.", invalid);
            return; // Preserve the existing read-only legacy unavailable-input contract.
        }
        if (configured) CompleteProjectionSnapshot.restoreInputs(inputs).validateHouseholdReferences();
        if (configured != provenance.contains(KEY) || provenance.indexOf(KEY) != provenance.lastIndexOf(KEY)
                || configured && !provenance.endsWith(stamp(inputs, annual)))
            throw new IllegalArgumentException("Missing or mismatched contractual premium execution provenance.");
    }

    private static String stamp(String inputs, RetainedProjectionSnapshot annual) {
        try { return KEY + MODEL + "; binding=" + com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec.sha256(
                JSON.writeValueAsBytes(List.of(CompleteProjectionSnapshot.digest(inputs), annual))); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("Cannot bind premium evidence.", failure); }
    }
}
