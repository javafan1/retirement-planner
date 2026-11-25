package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Run-only evidence supplied to the isolated Arkansas calculation. */
public record ArkansasTaxEvidence(
        Map<IncomeSourceAttribution, ReceiptFacts> receipts,
        Map<AccountOwnership, LocalDate> modeledDeaths,
        Optional<LifePrincipal> lifePrincipal) {

    public static final ArkansasTaxEvidence NONE = new ArkansasTaxEvidence(Map.of(), Map.of());

    public ArkansasTaxEvidence(Map<IncomeSourceAttribution, ReceiptFacts> receipts,
            Map<AccountOwnership, LocalDate> modeledDeaths) {
        this(receipts, modeledDeaths, Optional.empty());
    }

    public ArkansasTaxEvidence {
        receipts = Map.copyOf(receipts);
        modeledDeaths = Map.copyOf(modeledDeaths);
        Objects.requireNonNull(lifePrincipal);
    }

    /** A run-only expectation constructed from admitted definitions and the effective annual death view. */
    public static final class LifePrincipal {
        private final BenefitEvaluationContext context;
        private final List<IncomeSourceAttribution> expected;

        private LifePrincipal(RetirementPlan plan, EffectiveHouseholdDeathView deaths, int year) {
            var definitions = plan.getConditionalBenefits();
            ConditionalBenefitAdmission.requireArkansasLifePrincipal(
                    plan.getHousehold(), definitions, plan.getFinancialCalculationMode());
            context = BenefitEvaluationContext.capture(plan.getHousehold(), deaths,
                    plan.getPlanningAssumptions().getProjectionStartDate(), year);
            expected = new ConditionalBenefitEvaluator().evaluate(definitions, context).receipts().stream()
                    .map(IncomeSourceAttribution::conditional).toList();
        }

        public static Optional<LifePrincipal> capture(RetirementPlan plan, EffectiveHouseholdDeathView deaths, int year) {
            return ConditionalBenefitAdmission.hasArkansasConditionalBenefits(plan)
                    ? Optional.of(new LifePrincipal(plan, deaths, year))
                    : Optional.empty();
        }

        boolean matches(AnnualIncomeTaxFacts annual, Map<AccountOwnership, LocalDate> recordedDeaths,
                List<IncomeSourceAttribution> actual) {
            var household = annual.household();
            var opening = context.opening().getYear() == context.year()
                    ? context.opening() : LocalDate.of(context.year(), 1, 1);
            return annual.taxYear() == context.year()
                    && household.distributionDate().equals(opening)
                    && household.birthDates().equals(Map.of(AccountOwnership.PRIMARY, context.primaryBirthDate(),
                            AccountOwnership.SPOUSE, context.spouseBirthDate().orElseThrow()))
                    && household.householdOwners().equals(Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE))
                    && household.householdOwners().containsAll(recordedDeaths.keySet())
                    && household.householdOwners().stream().allMatch(owner ->
                            context.deaths().deathDate(owner).filter(date -> date.getYear() <= context.year())
                                    .equals(Optional.ofNullable(recordedDeaths.get(owner)))
                            && context.deaths().isAlive(owner, context.year()) == household.activeOwners().contains(owner))
                    && expected.equals(actual);
        }
    }

    public record ReceiptWindow(LocalDate first, LocalDate last) {
        public ReceiptWindow {
            Objects.requireNonNull(first);
            Objects.requireNonNull(last);
            if (last.isBefore(first)) throw new IllegalArgumentException("Reversed Arkansas receipt window.");
        }
    }

    public enum SourceQualification {
        UNSPECIFIED, ORDINARY_EMPLOYMENT_PENSION, OUTSIDE_ORDINARY_SCOPE
    }

    public enum Form5329Status {
        UNKNOWN, NO_APPLICABLE_ADDITIONAL_TAX, APPLICABLE_ADDITIONAL_TAX
    }

    public record ReceiptFacts(
            Optional<ReceiptWindow> window,
            SourceQualification qualification,
            Form5329Status additionalTax) {
        public ReceiptFacts {
            Objects.requireNonNull(window);
            Objects.requireNonNull(qualification);
            Objects.requireNonNull(additionalTax);
        }
    }

    private static final ReceiptFacts UNKNOWN = new ReceiptFacts(
            Optional.empty(), SourceQualification.UNSPECIFIED, Form5329Status.UNKNOWN);

    ReceiptFacts factsFor(IncomeSourceAttribution source) {
        return receipts.getOrDefault(source, UNKNOWN);
    }

    static SourceQualification pensionQualification(RetirementIncomeSourceOrigin origin, ReceiptFacts facts) {
        if (facts.qualification() == SourceQualification.ORDINARY_EMPLOYMENT_PENSION
                && origin != RetirementIncomeSourceOrigin.UNKNOWN
                && origin != RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN) {
            throw new ArkansasIncomeReconciliation.Rejected(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                    ArkansasProductionTaxCalculator.FailureCategory.ATTRIBUTION,
                    "Arkansas pension source origin contradicts the supplied employment-plan evidence.");
        }
        return switch (origin) {
            case EMPLOYER_RETIREMENT_PLAN -> SourceQualification.ORDINARY_EMPLOYMENT_PENSION;
            case UNKNOWN -> facts.qualification();
            case INDIVIDUALLY_PURCHASED_CONTRACT, OTHER_OR_SPECIAL_ARRANGEMENT -> SourceQualification.OUTSIDE_ORDINARY_SCOPE;
        };
    }
}
