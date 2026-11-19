package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasJointWorksheet;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasJointWorksheet.RecipientBounds;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRateWorksheet.Policy;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasTaxRules;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.daviddunn.retirementplanner.domain.tax.state.ArkansasIncomeReconciliation.require;
import static com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE;
import static java.math.BigDecimal.ZERO;

/** Isolated Arkansas annual tax calculation. Arkansas has no production jurisdiction identifier in P1. */
public final class ArkansasProductionTaxCalculator {
    public static final Policy POLICY = Policy.P_STATUTORY_CENTS;
    public static final String UNRESOLVED_PENSION_ORIGIN =
            "Arkansas pension origin may change this year's tax. Verify the original employment-related plan from its documents.";

    public enum FailureCategory { LEGAL_OR_SOURCE, ATTRIBUTION, OUTSIDE_REVIEWED_POLICY }

    /** Arkansas-specific output with the shared state-tax status and reasons. No surrogate jurisdiction. */
    public record Result(Optional<BigDecimal> estimatedIncomeTax, Optional<FailureCategory> failureCategory, List<String> limitations,
                         List<StateTaxUnavailableReason> unavailableReasons, Optional<Election> election,
                         Optional<ArkansasTaxIssue> issue) {
        public Result(Optional<BigDecimal> estimatedIncomeTax, Optional<FailureCategory> failureCategory,
                List<String> limitations, List<StateTaxUnavailableReason> unavailableReasons, Optional<Election> election) {
            this(estimatedIncomeTax, failureCategory, limitations, unavailableReasons, election,
                    unavailableReasons.stream().findFirst().map(ArkansasTaxIssue::fromSourceReason));
        }

        public Result {
            Objects.requireNonNull(estimatedIncomeTax);
            Objects.requireNonNull(failureCategory);
            Objects.requireNonNull(election);
            limitations = List.copyOf(limitations);
            unavailableReasons = List.copyOf(unavailableReasons);
            if (estimatedIncomeTax.isPresent() != election.isPresent()
                    || estimatedIncomeTax.isPresent() == issue.isPresent()
                    || estimatedIncomeTax.isPresent() != unavailableReasons.isEmpty()
                    || estimatedIncomeTax.isPresent() == failureCategory.isPresent()
                    || estimatedIncomeTax.filter(tax -> tax.signum() < 0).isPresent() || limitations.isEmpty()) {
                throw new IllegalArgumentException("Invalid isolated Arkansas tax result.");
            }
        }

        public StateTaxCalculationStatus calculationStatus() {
            return estimatedIncomeTax.isPresent() ? StateTaxCalculationStatus.CALCULATED : StateTaxCalculationStatus.UNSUPPORTED;
        }

        public BigDecimal incomeTax() {
            return estimatedIncomeTax.orElseThrow(() -> new IllegalStateException("Arkansas income tax is unavailable."));
        }
    }

    public record Election(BigDecimal retirementExclusion, BigDecimal standardDeduction,
            BigDecimal credits, BigDecimal taxableNet, List<ArkansasJointWorksheet.Recipient> recipients) {
        public Election {
            for (var amount : List.of(retirementExclusion, standardDeduction, credits, taxableNet)) {
                if (amount.signum() < 0) throw new IllegalArgumentException("Negative Arkansas worksheet amount.");
            }
            recipients = List.copyOf(recipients);
            if (recipients.isEmpty() || recipients.size() > 2
                    || recipients.stream().map(ArkansasJointWorksheet.Recipient::owner).distinct().count() != recipients.size()) {
                throw new IllegalArgumentException("Invalid Arkansas worksheet recipients.");
            }
        }
    }

    private enum AgeCredit { ELIGIBLE, INELIGIBLE, UNCERTAIN }

    /** A continuous lower envelope over a homogeneous funding source, including the statutory jumps. */
    public Optional<BigDecimal> fundingTaxFloor(AnnualIncomeTaxFacts atStart, ArkansasTaxEvidence evidence,
            BigDecimal maximumOrdinaryIncrease) {
        if (maximumOrdinaryIncrease.signum() < 0) throw new IllegalArgumentException("Negative funding interval.");
        try {
            boolean joint = atStart.filingStatus() == FilingStatus.MARRIED_FILING_JOINTLY;
            var household = atStart.household();
            var filers = (joint ? household.householdOwners() : household.activeOwners()).stream().sorted().toList();
            var income = new ArkansasIncomeReconciliation(atStart, evidence).reconcile();
            var recipients = new ArrayList<RecipientBounds>();
            for (var filer : filers) {
                var age = ageCredit(atStart, evidence, filer);
                var certain = income.certain().getOrDefault(filer, ZERO);
                var possible = income.possible().getOrDefault(filer, ZERO);
                if (age == AgeCredit.UNCERTAIN || certain.compareTo(possible) != 0) return Optional.empty();
                recipients.add(new RecipientBounds(filer, certain, certain, age == AgeCredit.ELIGIBLE));
            }
            var bounds = worksheet.boundStatutoryChoices(income.ordinary(), recipients, maximumOrdinaryIncrease);
            if (bounds.maximum().signum() == 0 || outsideLowIncome(atStart.taxYear(), joint, filers.size(), income)) {
                return Optional.of(bounds.minimum());
            }
            return Optional.empty();
        } catch (IllegalArgumentException | DateTimeException ex) {
            return Optional.empty();
        }
    }

    private final ArkansasJointWorksheet worksheet = new ArkansasJointWorksheet();

    @FunctionalInterface
    public interface TargetTaxFloor {
        BigDecimal lowerBound(BigDecimal ordinaryIncreaseLow, BigDecimal ordinaryIncreaseHigh,
                Map<AccountOwnership, BigDecimal> qualifyingIncreaseLow, Map<AccountOwnership, BigDecimal> qualifyingIncreaseHigh);
    }

    /** Exclusion-only envelope. Unknown policy or base evidence supplies no positive lower bound. */
    public Optional<TargetTaxFloor> prepareTargetTaxFloor(AnnualIncomeTaxFacts base, ArkansasTaxEvidence evidence) {
        try {
            var household = base.household();
            boolean joint = base.filingStatus() == FilingStatus.MARRIED_FILING_JOINTLY;
            if (!joint && base.filingStatus() != FilingStatus.SINGLE
                    || !ArkansasTaxRules.supportsLowIncomeProof(base.taxYear())) return Optional.empty();
            var filers = (joint ? household.householdOwners() : household.activeOwners()).stream().sorted().toList();
            if (filers.size() != (joint ? 2 : 1)) return Optional.empty();
            validateDeaths(base, evidence, joint);
            var income = new ArkansasIncomeReconciliation(base, evidence).reconcile();
            var recipients = new ArrayList<RecipientBounds>();
            for (var owner : filers) {
                var age = ageCredit(base, evidence, owner);
                if (age == AgeCredit.UNCERTAIN) return Optional.empty();
                recipients.add(new RecipientBounds(owner, income.certain().getOrDefault(owner, ZERO),
                        income.possible().getOrDefault(owner, ZERO), age == AgeCredit.ELIGIBLE));
            }
            return Optional.of((low, high, eligibleLow, eligibleHigh) -> worksheet.targetTaxFloor(base.taxYear(),
                    income.ordinary(), low, high, recipients.stream().map(r -> new ArkansasJointWorksheet.TargetReceipts(
                            r, eligibleLow.getOrDefault(r.owner(), ZERO), eligibleHigh.getOrDefault(r.owner(), ZERO))).toList()));
        } catch (IllegalArgumentException | DateTimeException | NiitUnsupportedInputException unavailable) {
            return Optional.empty();
        }
    }

    /** The request contributes annual facts. Its registered state is irrelevant to this explicit isolated call. */
    public Result calculateUnregistered(StateTaxCalculationRequest request, ArkansasTaxEvidence evidence) {
        return calculateUnregistered(request.annualFacts(), evidence);
    }

    public Result calculateUnregistered(AnnualIncomeTaxFacts request, ArkansasTaxEvidence evidence) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(evidence);
        if (request.taxYear() < ArkansasTaxRules.REVIEWED_YEAR) {
            return unsupported(UNSUPPORTED_MODEL_RULE, FailureCategory.OUTSIDE_REVIEWED_POLICY,
                    "Arkansas years before 2026 are outside the reviewed planning policy.",
                    ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.POLICY));
        }
        try {
            int reviewedYear = ArkansasTaxRules.reviewedYearFor(request.taxYear());
            boolean joint = request.filingStatus() == FilingStatus.MARRIED_FILING_JOINTLY;
            if (!joint && request.filingStatus() != FilingStatus.SINGLE) {
                return unsupported(UNSUPPORTED_MODEL_RULE, FailureCategory.OUTSIDE_REVIEWED_POLICY,
                        "Arkansas planning coverage requires Single or actual married filing jointly.",
                        ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.POLICY));
            }
            require(request.projectedRules().getTaxYear() == request.taxYear(),
                    "Arkansas request requires projected federal rules for the same year.");
            var household = request.household();
            var filers = (joint ? household.householdOwners() : household.activeOwners()).stream().sorted().toList();
            require(filers.size() == (joint ? 2 : 1) && !household.activeOwners().isEmpty(),
                    "Arkansas filing status requires the corresponding actual annual filers.");
            validateDeaths(request, evidence, joint);
            var income = new ArkansasIncomeReconciliation(request, evidence).reconcile();

            var ageCredits = new ArrayList<AgeCredit>();
            for (var filer : filers) ageCredits.add(ageCredit(request, evidence, filer));
            var firstChoice = taxForAges(income, filers, ageCredits, false);
            if (ageCredits.contains(AgeCredit.UNCERTAIN)) {
                var alternate = taxForAges(income, filers, ageCredits, true);
                requireLegal(firstChoice.minimum().compareTo(alternate.minimum()) == 0
                                && firstChoice.maximum().compareTo(alternate.maximum()) == 0,
                        "Arkansas age-65 credit eligibility is material and cannot be verified at the modeled death or from the available birth dates.");
            }
            if (firstChoice.minimum().compareTo(firstChoice.maximum()) != 0) {
                var recipients = income.unresolvedPensionRecipients().stream().filter(recipient ->
                        income.certain().getOrDefault(recipient, ZERO).compareTo(ArkansasTaxRules.RETIREMENT_LIMIT) < 0)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                if (!recipients.isEmpty()) {
                    return unsupported(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                            FailureCategory.LEGAL_OR_SOURCE, UNRESOLVED_PENSION_ORIGIN,
                            new ArkansasTaxIssue(ArkansasTaxIssue.Kind.PENSION_ORIGIN, recipients));
                }
                return unsupported(UNSUPPORTED_MODEL_RULE, FailureCategory.LEGAL_OR_SOURCE,
                        "Arkansas age-59½ receipt timing is material across the possible taxable range.",
                        ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.RECEIPT_TIMING));
            }

            var selected = firstChoice.atCertainReceipts();
            if (selected.tax().signum() > 0 && !ArkansasTaxRules.supportsLowIncomeProof(request.taxYear())) {
                return unsupported(UNSUPPORTED_MODEL_RULE, FailureCategory.OUTSIDE_REVIEWED_POLICY,
                        "Arkansas low-income admission proof is unavailable for this year.",
                        ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.LOW_INCOME));
            }
            if (selected.tax().signum() > 0 && !outsideLowIncome(request.taxYear(), joint, filers.size(), income)) {
                return unsupported(UNSUPPORTED_MODEL_RULE, FailureCategory.LEGAL_OR_SOURCE,
                        "Arkansas low-income qualification is unresolved. The §26-51-301 schedule or §26-51-501 additional credit could change this positive estimate.",
                        ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.LOW_INCOME));
            }

            var notes = new ArrayList<>(planningLimitations());
            notes.add("Arkansas modeled ordinary income " + income.ordinary().toPlainString()
                    + ", representative retirement exclusion " + selected.retirementSubtraction().toPlainString()
                    + ", Arkansas standard deduction " + selected.standardDeduction().toPlainString()
                    + ", taxable net " + selected.netIncome().toPlainString()
                    + ", ordinary credits " + selected.credits().toPlainString()
                    + ". Verified annual outcome across represented eligibility bounds.");
            if (request.taxYear() != reviewedYear)
                notes.add("Arkansas " + reviewedYear + " reviewed tax parameters held nominally for " + request.taxYear()
                        + ". Statutory low-income admission ceilings are bounded separately for that year.");
            var election = new Election(selected.retirementSubtraction(), selected.standardDeduction(),
                    selected.credits(), selected.netIncome(), firstChoice.selectedRecipients());
            return new Result(Optional.of(selected.tax()), Optional.empty(), notes, List.of(), Optional.of(election), Optional.empty());
        } catch (ArkansasIncomeReconciliation.Rejected rejected) {
            return unsupported(rejected.reason, rejected.category, rejected.getMessage(), rejected.issue);
        } catch (NiitUnsupportedInputException rejected) {
            return unsupported(UNSUPPORTED_MODEL_RULE, FailureCategory.LEGAL_OR_SOURCE, rejected.getMessage(),
                    ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.NIIT));
        } catch (DateTimeException | IllegalArgumentException rejected) {
            return unsupported(UNSUPPORTED_MODEL_RULE, FailureCategory.LEGAL_OR_SOURCE, rejected.getMessage(),
                    ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.SOURCE_OR_TAX));
        }
    }

    private ArkansasJointWorksheet.BoundedResult taxForAges(ArkansasIncomeReconciliation.Income income,
            List<AccountOwnership> filers, List<AgeCredit> ages, boolean includeUncertain) {
        var recipients = new ArrayList<RecipientBounds>();
        for (int index = 0; index < filers.size(); index++) {
            var owner = filers.get(index);
            var age = ages.get(index);
            recipients.add(new RecipientBounds(owner, income.certain().getOrDefault(owner, ZERO),
                    income.possible().getOrDefault(owner, ZERO),
                    age == AgeCredit.ELIGIBLE || age == AgeCredit.UNCERTAIN && includeUncertain));
        }
        return worksheet.boundStatutoryChoices(income.ordinary(), recipients);
    }

    private static BigDecimal lowestPossibleNet(ArkansasIncomeReconciliation.Income income, int filers) {
        var relief = income.possible().values().stream().map(amount -> amount.min(ArkansasTaxRules.RETIREMENT_LIMIT))
                .reduce(ZERO, BigDecimal::add);
        return income.ordinary().subtract(relief)
                .subtract(ArkansasTaxRules.STANDARD_DEDUCTION.multiply(BigDecimal.valueOf(filers))).max(ZERO);
    }

    private static boolean outsideLowIncome(int year, boolean joint, int filers, ArkansasIncomeReconciliation.Income income) {
        return income.grossLowerBound().compareTo(ArkansasTaxRules.lowIncomeGrossCeiling(year, joint)) > 0
                && lowestPossibleNet(income, filers).compareTo(ArkansasTaxRules.additionalCreditNetCeiling(year)) > 0;
    }

    private static void validateDeaths(AnnualIncomeTaxFacts request, ArkansasTaxEvidence evidence, boolean joint) {
        evidence.modeledDeaths().forEach((owner, death) -> {
            var household = request.household();
            var birth = household.birthDates().get(owner);
            require(household.householdOwners().contains(owner) && !household.activeOwners().contains(owner)
                            && death.getDayOfYear() == 1 && (birth == null || !death.isBefore(birth))
                            && (joint ? death.getYear() == request.taxYear() : death.getYear() < request.taxYear()),
                    "Arkansas death evidence must match the modeled January 1 death and annual filing status.");
        });
    }

    private static AgeCredit ageCredit(AnnualIncomeTaxFacts request, ArkansasTaxEvidence evidence, AccountOwnership owner) {
        var birth = request.household().birthDates().get(owner);
        if (birth == null) return AgeCredit.UNCERTAIN;
        var cutoff = LocalDate.of(request.taxYear(), 12, 31).plusDays(1);
        require(!birth.isAfter(request.household().distributionDate()), "Arkansas filer birth date follows the annual tax date.");
        var birthday = birth.plusYears(65);
        if (birthday.isAfter(cutoff)) return AgeCredit.INELIGIBLE;
        if (request.household().activeOwners().contains(owner)) return AgeCredit.ELIGIBLE;
        var death = evidence.modeledDeaths().getOrDefault(owner, LocalDate.of(request.taxYear(), 1, 1));
        return birthday.isBefore(death) ? AgeCredit.ELIGIBLE : AgeCredit.UNCERTAIN;
    }

    private static void requireLegal(boolean condition, String explanation) {
        if (!condition) throw new ArkansasIncomeReconciliation.Rejected(UNSUPPORTED_MODEL_RULE, explanation);
    }

    private static Result unsupported(StateTaxUnavailableReason reason, FailureCategory category, String explanation, ArkansasTaxIssue issue) {
        return new Result(Optional.empty(), Optional.of(category), List.of(explanation), List.of(reason),
                Optional.empty(), Optional.of(issue));
    }

    public static List<String> planningLimitations() {
        return List.of(
                ArkansasTaxRules.VERSION + " " + POLICY + " is a bounded plan estimate. Whole dollars choose the high schedule and adjustment. Fractional amounts feed the marginal slices and final liability rounds to cents. Final 2026 annual-return instructions are unverified. The dated estimated-payment worksheet is a separate comparison.",
                "Reviewed 2026 values are held nominally in future years. Statutory indexing can change thresholds, deductions, credits and low-income eligibility. Actual joint filers use one combined schedule and may pay less under a separate filing method outside this model.",
                "Ordinary Arkansas income excludes federally included Social Security once. One $6,000 retirement exclusion per eligible recipient. Own IRA age uses full possible receipt dates, and the completed own-IRA Roth transfer interpretation shares the same pool. A specific DFA conversion-exclusion ruling has not been verified.",
                "Ordinary personal and senior credits are nonrefundable. The 65 Special alternative is compared with claiming the same recipient's retirement exclusion. Missing 2026 low-income schedules and additional-credit tables permit only tax-stable zero or an estimate proved outside both possible ranges.",
                "Ordinary documented employment and direct spouse-survivor pensions use the current recipient. Specialized plan, inherited, basis and employer-conversion treatments require separate evidence. Potentially early cash requires evidence of no applicable Form 5329-related additional tax. The calculator models no federal or Arkansas penalty ledger.",
                "Admitted commercial nonqualified annuities and other supported conditional income enter once at their evidenced taxable amount. Recovered basis receives no new tax or retirement exclusion. Existing federal NIIT and Advanced evidence remain required.",
                "Brokerage cash does not itself create gains. Modeled taxable investments enter once. Unmodeled realized gains may understate tax, and missing qualifying long-term capital-gain or special state-basis relief may overstate it."
        );
    }
}
