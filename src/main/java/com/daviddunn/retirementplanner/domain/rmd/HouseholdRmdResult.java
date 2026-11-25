package com.daviddunn.retirementplanner.domain.rmd;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Map;
import java.util.List;
import java.util.EnumMap;
import java.util.Collections;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
/*
HouseholdRmdResult
│
├── Primary
│   ├── IRA
│   ├── 401(k)s
│   ├── 403(b)s
│   └── Total
│
├── Spouse
│   ├── IRA
│   ├── 401(k)s
│   ├── 403(b)s
│   └── Total
│
└── Household Total
 */
public final class HouseholdRmdResult {

    private final OwnerRmdResult primaryRmd;
    private final OwnerRmdResult spouseRmd;
    private final BeneficiaryIraRmd beneficiaryIraRmd;
    private final List<Beneficiary401kRmd> employerBeneficiaryRmds;

    public HouseholdRmdResult(Map<AccountOwnership, OwnerRmdResult> owners) {
        this(owners, null);
    }

    public HouseholdRmdResult(Map<AccountOwnership, OwnerRmdResult> owners, BeneficiaryIraRmd beneficiaryIraRmd) {
        this(owners, beneficiaryIraRmd, List.of());
    }

    public HouseholdRmdResult(Map<AccountOwnership, OwnerRmdResult> owners, BeneficiaryIraRmd beneficiaryIraRmd,
            List<Beneficiary401kRmd> employerBeneficiaryRmds) {
        owners = Map.copyOf(owners);
        this.employerBeneficiaryRmds = List.copyOf(employerBeneficiaryRmds);
        this.primaryRmd = Objects.requireNonNull(owners.get(AccountOwnership.PRIMARY));
        this.spouseRmd = owners.get(AccountOwnership.SPOUSE);
        this.beneficiaryIraRmd = beneficiaryIraRmd;
        if (owners.containsKey(AccountOwnership.JOINT)) {
            throw new IllegalArgumentException("Joint ownership has no owner RMD.");
        }
    }

    public Map<AccountOwnership, OwnerRmdResult> ownerResults() {
        var results = new EnumMap<AccountOwnership, OwnerRmdResult>(AccountOwnership.class);
        results.put(AccountOwnership.PRIMARY, primaryRmd);
        if (spouseRmd != null) results.put(AccountOwnership.SPOUSE, spouseRmd);
        return Collections.unmodifiableMap(results);
    }

    public HouseholdRmdResult(
            OwnerRmdResult primaryRmd,
            OwnerRmdResult spouseRmd) {

        this(Map.of(AccountOwnership.PRIMARY, primaryRmd, AccountOwnership.SPOUSE, spouseRmd));
    }

    public OwnerRmdResult getPrimaryRmd() {
        return primaryRmd;
    }

    public OwnerRmdResult getSpouseRmd() {
        if (spouseRmd == null) throw new IllegalStateException("No spouse RMD result exists.");
        return spouseRmd;
    }

    public BigDecimal getTotalRmd() {

        var ownerTotal = primaryRmd
                .getTotalRmd()
                .add(
                        spouseRmd == null ? BigDecimal.ZERO : spouseRmd.getTotalRmd());
        var total = beneficiaryIraRmd == null ? ownerTotal : ownerTotal.add(beneficiaryIraRmd.requiredAmount());
        for (var employer : employerBeneficiaryRmds) total = total.add(employer.requiredAmount());
        return total;
    }

    public BeneficiaryIraRmd beneficiaryIraRmd() { return beneficiaryIraRmd; }

    public List<Beneficiary401kRmd> employerBeneficiaryRmds() { return employerBeneficiaryRmds; }

    public static HouseholdRmdResult zero() {

        return new HouseholdRmdResult(
                OwnerRmdResult.zero(),
                OwnerRmdResult.zero());
    }

    @Override
    public String toString() {

        return "HouseholdRmdResult{" +
                "primaryRmd=" + primaryRmd +
                ", spouseRmd=" + spouseRmd +
                ", totalRmd=" + getTotalRmd() +
                '}';
    }
}
