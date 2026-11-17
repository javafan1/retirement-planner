package com.daviddunn.retirementplanner.domain.tax.state.mississippi;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.math.BigDecimal;
import java.util.*;

/** State-local interpretation of the existing modeled bucket, never another income channel. */
public final class MississippiInvestmentReconciliation {
    public Map<AccountOwnership,BigDecimal> reconcile(TaxIncome income, Set<AccountOwnership> active, boolean single) {
        Objects.requireNonNull(income);
        Objects.requireNonNull(active);
        if (single && active.size() != 1) throw new IllegalArgumentException("Single investment allocation requires one active filer.");
        var owners = new EnumMap<AccountOwnership,BigDecimal>(AccountOwnership.class);
        BigDecimal explicit = BigDecimal.ZERO, explicitInterest = BigDecimal.ZERO, placeholder = null;
        var seen = new HashSet<IncomeSourceAttribution>();
        for (var s : income.getIncomeSources()) {
            if (!Set.of(IncomeSourceAttribution.Kind.INTEREST,IncomeSourceAttribution.Kind.DIVIDEND,
                    IncomeSourceAttribution.Kind.CAPITAL_GAIN,IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL).contains(s.kind())
                    || s.grossAmount().signum()==0) continue;
            if (!seen.add(s)) throw new IllegalArgumentException("Duplicate Mississippi investment attribution.");
            var amount = s.modeledTaxableAmount().orElse(BigDecimal.ZERO);
            if (amount.signum() > 0 && s.account().isPresent() && seen.stream().anyMatch(previous ->
                    previous.account().equals(s.account())
                    && previous.modeledTaxableAmount().orElse(BigDecimal.ZERO).signum() > 0
                    && (previous.kind() == Kind.CAPITAL_GAIN && s.kind() == Kind.ORDINARY_WITHDRAWAL
                        || previous.kind() == Kind.ORDINARY_WITHDRAWAL && s.kind() == Kind.CAPITAL_GAIN))) {
                throw new IllegalArgumentException("Overlapping gain/withdrawal evidence cannot establish disjoint taxable investment receipts.");
            }
            if (s.account().isPresent() && (s.account().get().type().getTaxTreatment()==TaxTreatment.TAX_DEFERRED
                    || s.account().get().type().getTaxTreatment()==TaxTreatment.ROTH && amount.signum()>0))
                throw new IllegalArgumentException("Retirement account conflicts with investment classification.");
            if (s.kind()==IncomeSourceAttribution.Kind.INTEREST || s.kind()==IncomeSourceAttribution.Kind.DIVIDEND) {
                if (s.modeledTaxableAmount().isEmpty() || amount.compareTo(s.grossAmount())!=0)
                    throw new IllegalArgumentException("Special/exempt investment adjustments require separate facts.");
            }
            // This exact runtime aggregate signature is emitted by TaxIncomeCalculator/TaxIncomeAttribution.
            // Same-kind INTEREST replaces it; other attached investment kinds do not.
            boolean aggregate = s.kind()==IncomeSourceAttribution.Kind.INTEREST && s.owner().isEmpty()
                    && s.account().isEmpty() && s.destination().isEmpty()
                    && s.purpose()==IncomeSourceAttribution.Purpose.INCOME && s.description().equals("Unattributed INTEREST");
            if (aggregate) {
                if (placeholder!=null) throw new IllegalArgumentException("Ambiguous investment aggregate evidence.");
                placeholder=amount;
                continue;
            }
            explicit=explicit.add(amount);
            if (s.kind()==IncomeSourceAttribution.Kind.INTEREST) explicitInterest=explicitInterest.add(amount);
            if (amount.signum()==0) continue; // Unknown brokerage gains are not assigned or invented.
            var owner=s.owner().orElse(null);
            if (owner==AccountOwnership.JOINT) {
                if (single || !active.containsAll(Set.of(AccountOwnership.PRIMARY,AccountOwnership.SPOUSE)))
                    throw new IllegalArgumentException("Joint investment evidence conflicts with annual resident ownership.");
                // JOINT does not establish individual shares; retain unallocated income.
            } else if (owner!=null) {
                if (!active.contains(owner)) throw new IllegalArgumentException("Investment recipient is not active in the annual household.");
                owners.merge(owner,amount,BigDecimal::add);
            }
        }
        var total=income.getTaxableInterestIncome();
        if (explicit.compareTo(total)>0 || placeholder!=null && placeholder.add(explicitInterest).compareTo(total)!=0)
            throw new IllegalArgumentException("Investment attribution exceeds or contradicts the modeled bucket; AGI is unchanged.");
        if (single) {
            var allocated=owners.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
            owners.merge(active.iterator().next(),total.subtract(allocated),BigDecimal::add);
        }
        return Map.copyOf(owners); // MFJ unallocated income stays taxable; never invent individual shares.
    }
}
