package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Visualization eligibility only; never adjusts money or financial results. */
final class CompositionDisplayReconciliation {
    // Two independently cent-rounded amounts (base withdrawal and final total) can each
    // contribute half a cent. Fixed absolute allowance, not scaled by assets or years.
    private static final BigDecimal ROUNDING_ALLOWANCE = new BigDecimal("0.01");

    static boolean withinCurrencyPrecision(BigDecimal reported, BigDecimal components) {
        return reported != null && components != null
                && reported.signum() >= 0 && components.signum() >= 0
                && reported.subtract(components).abs().compareTo(ROUNDING_ALLOWANCE) <= 0;
    }

    static boolean hasCompleteAccounts(ProjectionYear year, List<Account> expectedAccounts) {
        if (expectedAccounts == null || year.getEndingRetainedNonQualifiedAssets() == null
                || year.getEndingRetainedNonQualifiedAssets().signum() < 0) return false;
        // ProjectedPortfolio retains the source Account references, including exhausted accounts.
        // Names are not identifiers: different owners may legitimately use the same account name.
        Set<Account> expected = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var account : expectedAccounts) {
            if (account == null || !expected.add(account)) return false;
        }
        Set<Account> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var snapshot : year.getEndingAccountSnapshots()) {
            if (snapshot == null) return false;
            var account = snapshot.getAccount();
            if (!expected.contains(account) || !seen.add(account)
                    || account.getType() == null || account.getProjectionAssetType() == null
                    || !account.getType().allowsOwnership(account.getOwnership())
                    || snapshot.getEndingBalance() == null || snapshot.getEndingBalance().signum() < 0) return false;
            // BigDecimal cannot represent NaN or infinity.
        }
        return seen.size() == expected.size();
    }
}
