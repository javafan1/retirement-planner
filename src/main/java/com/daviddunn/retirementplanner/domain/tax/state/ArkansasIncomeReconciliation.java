package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.TaxCharacter;
import com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityTerms.Qualification;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator.FailureCategory;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.ReceiptFacts;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.ReceiptWindow;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRetirementEligibility;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRetirementEligibility.Decision;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRetirementEligibility.Source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.Form5329Status.*;
import static com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.SourceQualification.*;
import static com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason.*;
import static java.math.BigDecimal.ZERO;

/** Arkansas-local bridge from immutable federal/source facts to ordinary receipts. */
final class ArkansasIncomeReconciliation {
    record Income(BigDecimal ordinary, BigDecimal grossLowerBound,
                  Map<AccountOwnership, BigDecimal> certain, Map<AccountOwnership, BigDecimal> possible,
                  Set<AccountOwnership> unresolvedPensionRecipients) { }

    static final class Rejected extends IllegalArgumentException {
        final StateTaxUnavailableReason reason;
        final FailureCategory category;
        final ArkansasTaxIssue issue;

        Rejected(StateTaxUnavailableReason reason, String message) {
            this(reason, FailureCategory.LEGAL_OR_SOURCE, message);
        }

        Rejected(StateTaxUnavailableReason reason, FailureCategory category, String message) {
            this(reason, category, message, ArkansasTaxIssue.fromSourceReason(reason));
        }

        Rejected(StateTaxUnavailableReason reason, FailureCategory category, String message, ArkansasTaxIssue issue) {
            super(message);
            this.reason = reason;
            this.category = category;
            this.issue = issue;
        }
    }

    private record AccountPayment(IncomeSourceAttribution.AccountReference account, Kind kind, Purpose purpose,
                                  BigDecimal gross, BigDecimal taxable) { }

    private final AnnualIncomeTaxFacts request;
    private final ArkansasTaxEvidence evidence;
    private final LocalDate first;
    private final LocalDate last;
    private final EnumMap<Kind, BigDecimal> totals = new EnumMap<>(Kind.class);
    private final EnumMap<AccountOwnership, BigDecimal> certain = new EnumMap<>(AccountOwnership.class);
    private final EnumMap<AccountOwnership, BigDecimal> possible = new EnumMap<>(AccountOwnership.class);
    private final Set<AccountOwnership> unresolvedPensionRecipients = new HashSet<>();
    private final Set<AccountPayment> accountPayments = new HashSet<>();
    private final Set<UUID> conditionalIds = new HashSet<>();
    private final Map<IncomeSourceAttribution.AccountReference, Set<Kind>> taxableInvestmentKinds = new HashMap<>();
    private BigDecimal investment = ZERO;
    private BigDecimal explicitInterest = ZERO;
    private BigDecimal interestPlaceholder;

    private final ArkansasRecipientDiagnostic.Stage diagnosticStage;

    ArkansasIncomeReconciliation(AnnualIncomeTaxFacts request, ArkansasTaxEvidence evidence,
            ArkansasRecipientDiagnostic.Stage diagnosticStage) {
        this.request = request;
        this.evidence = evidence;
        this.diagnosticStage = diagnosticStage;
        first = LocalDate.of(request.taxYear(), 1, 1);
        last = LocalDate.of(request.taxYear(), 12, 31);
    }

    Income reconcile() {
        var income = request.income();
        var sources = income.getIncomeSources();
        validateLifePrincipal();
        require(sources.containsAll(evidence.receipts().keySet()), "Arkansas receipt evidence references an absent payment.");
        var seen = new HashSet<IncomeSourceAttribution>();
        for (var source : sources) {
            require(source.taxYear().isPresent() && source.taxYear().getAsInt() == request.taxYear(),
                    "Arkansas source attribution must identify the same tax year.");
            require(seen.add(source), "Duplicate Arkansas source attribution.");
            if (source.grossAmount().signum() == 0) continue;
            var facts = evidence.factsFor(source);
            if (facts.qualification() == OUTSIDE_ORDINARY_SCOPE)
                throw new Rejected(UNSUPPORTED_TRANSACTION_TYPE, "Represented specialized Arkansas source or state basis requires separate treatment.");
            if (facts.additionalTax() == APPLICABLE_ADDITIONAL_TAX)
                throw new Rejected(UNSUPPORTED_MODEL_RULE, FailureCategory.LEGAL_OR_SOURCE,
                        "The Arkansas Form 5329-related additional tax is outside the modeled ordinary liability.",
                        ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.ADDITIONAL_TAX));
            source.account().ifPresent(account -> require(accountPayments.add(new AccountPayment(
                    account, source.kind(), source.purpose(), source.grossAmount().stripTrailingZeros(),
                    source.modeledTaxableAmount().map(BigDecimal::stripTrailingZeros).orElse(null))),
                    "Duplicate Arkansas account payment."));

            switch (source.kind()) {
                case ORDINARY_WITHDRAWAL, INTEREST, CAPITAL_GAIN, DIVIDEND -> investment(source);
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION -> retirement(source, facts);
                case PENSION -> pension(source, facts);
                case CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM, NONQUALIFIED_ANNUITY -> conditional(source);
                case SOCIAL_SECURITY -> {
                    source.owner().ifPresent(owner -> require(request.household().activeOwners().contains(owner),
                            "Social Security must belong to an active recipient."));
                    add(Kind.SOCIAL_SECURITY, source.grossAmount());
                }
                case NIIT_EVIDENCE, FINANCIAL_CALCULATION_POLICY ->
                        throw new Rejected(UNSUPPORTED_MODEL_RULE, "Nonzero annual tax metadata cannot establish a cash receipt.");
                case OTHER -> throw new Rejected(UNSUPPORTED_TRANSACTION_TYPE, "Unclassified ordinary income cannot establish Arkansas treatment.");
            }
        }

        require(total(Kind.ORDINARY_RETIREMENT_WITHDRAWAL).compareTo(income.getTaxDeferredWithdrawals()) == 0
                && total(Kind.ROTH_CONVERSION).compareTo(income.getRothConversion()) == 0
                && total(Kind.PENSION).compareTo(income.getPensionIncome()) == 0
                && total(Kind.CONDITIONAL_RECURRING).compareTo(income.getConditionalOrdinaryIncome()) == 0
                && total(Kind.SOCIAL_SECURITY).compareTo(income.getSocialSecurityIncome()) <= 0,
                "Arkansas withdrawal, conversion, pension, conditional or Social Security attribution contradicts annual income.");
        var investmentTotal = income.getTaxableInterestIncome();
        require(investment.compareTo(investmentTotal) <= 0
                        && (interestPlaceholder == null || interestPlaceholder.add(explicitInterest).compareTo(investmentTotal) == 0),
                "Investment attribution overlaps or contradicts the modeled investment bucket.");
        request.household().pensionIncome().ifPresent(pensions -> require(
                pensions.total().compareTo(income.getPensionIncome()) == 0
                        && Set.copyOf(pensions.sources()).equals(Set.copyOf(sources.stream().filter(s -> s.kind() == Kind.PENSION).toList())),
                "Frozen household pension selection contradicts attributed payments."));

        var federal = new FederalTaxCalculator().calculate(income, request.filingStatus(), request.projectedRules());
        require(federal.getAdjustedGrossIncome().compareTo(request.federal().getAdjustedGrossIncome()) == 0
                        && federal.getTaxableSocialSecurity().compareTo(request.federal().getTaxableSocialSecurity()) == 0
                        && federal.niit().equals(request.federal().niit()),
                "Arkansas requires reconciled federal AGI, taxable Social Security and applicable NIIT evidence.");
        for (var source : sources) {
            source.niit().ifPresent(niit -> require(federal.niit().filter(niit::equals).isPresent(),
                    "Completed NIIT evidence contradicts the federal calculation."));
        }
        var ordinary = request.federal().getAdjustedGrossIncome().subtract(request.federal().getTaxableSocialSecurity());
        // Ignore potentially excluded receipts and other unrepresented income when proving
        // a gross-income lower bound. A lower bound can only increase conservative refusals.
        return new Income(ordinary, ordinary, Map.copyOf(certain), Map.copyOf(possible),
                Set.copyOf(unresolvedPensionRecipients));
    }

    private void retirement(IncomeSourceAttribution source, ReceiptFacts facts) {
        var owner = activeRecipient(source);
        var account = source.account().orElseThrow(() -> new Rejected(
                MISSING_RETIREMENT_QUALIFICATION, "Arkansas retirement income requires a frozen source account."));
        var type = account.type();
        if (account.inheritedInformation().isPresent())
            throw new Rejected(INHERITED_DISTRIBUTION_HISTORY_REQUIRED, "Inherited retirement history is outside ordinary Arkansas coverage.");
        var amount = taxable(source);
        boolean conversion = source.kind() == Kind.ROTH_CONVERSION;
        if (conversion) {
            require(source.purpose() == Purpose.CONVERSION, "Arkansas conversion evidence must describe a completed transfer.");
            var destination = source.destination().orElseThrow(() -> new Rejected(
                    MISSING_RETIREMENT_QUALIFICATION, "Arkansas conversion evidence requires a same-owner Roth IRA destination."));
            if (!ownIra(type) || destination.type() != AccountType.ROTH_IRA || destination.owner() != owner)
                throw new Rejected(UNSUPPORTED_TRANSACTION_TYPE, "Arkansas only covers completed own traditional/rollover IRA to own Roth IRA conversions.");
        } else {
            boolean validPurpose = switch (source.kind()) {
                case OPENING_RMD -> source.purpose() == Purpose.INCOME && type.isSubjectToOwnerRmd();
                case RMD -> source.purpose() == Purpose.CASH_FLOW && type.isSubjectToOwnerRmd();
                default -> source.purpose() == Purpose.CASH_FLOW || source.purpose() == Purpose.TAX_FUNDING;
            };
            require(source.destination().isEmpty() && validPurpose,
                    "Arkansas retirement withdrawal or RMD purpose conflicts with its source.");
            if (type.getTaxTreatment() == TaxTreatment.ROTH) {
                if (amount.signum() == 0) return;
                throw new Rejected(NONQUALIFIED_ROTH_BASIS_REQUIRED, "Arkansas requires additional basis facts for taxable Roth cash.");
            }
        }
        if (!ownIra(type) && type != AccountType.TRADITIONAL_401K && type != AccountType.TRADITIONAL_403B)
            throw new Rejected(UNSUPPORTED_TRANSACTION_TYPE, "Unsupported Arkansas retirement source.");
        if (amount.compareTo(source.grossAmount()) != 0)
            throw new Rejected(MISSING_DISTRIBUTION_BASIS, "Arkansas ordinary retirement amounts must be fully taxable before the state exclusion.");
        var birth = birthDate(owner);
        var window = window(facts);
        require(!window.first().isBefore(birth), "Arkansas retirement receipt precedes the recipient's birth.");
        if (source.kind() == Kind.OPENING_RMD && facts.window().isPresent())
            require(window.last().isBefore(request.household().distributionDate()), "Opening RMD evidence must precede the projection opening date.");
        if (!conversion) requireNoEarlyAdditionalTax(window, birth, facts);
        add(conversion ? Kind.ROTH_CONVERSION : Kind.ORDINARY_RETIREMENT_WITHDRAWAL, amount);
        var decision = new ArkansasRetirementEligibility().assess(
                ownIra(type) ? (conversion ? Source.OWN_IRA_CONVERSION_INTERPRETATION : Source.OWN_IRA_CASH)
                        : Source.ORDINARY_EMPLOYER_PLAN,
                birth, window.first(), window.last(), true);
        if (decision == Decision.QUALIFYING_POOL) certain.merge(owner, amount, BigDecimal::add);
        if (decision != Decision.TAXABLE_WITHOUT_POOL) possible.merge(owner, amount, BigDecimal::add);
    }

    private void pension(IncomeSourceAttribution source, ReceiptFacts facts) {
        var owner = activeRecipient(source);
        var pension = source.pension().orElseThrow(() -> new Rejected(
                MISSING_RETIREMENT_QUALIFICATION, "Arkansas pension requires frozen original plan and recipient facts."));
        var qualification = ArkansasTaxEvidence.pensionQualification(pension.sourceOrigin(), facts);
        if (qualification == OUTSIDE_ORDINARY_SCOPE) {
            String detail = pension.sourceOrigin() == RetirementIncomeSourceOrigin.INDIVIDUALLY_PURCHASED_CONTRACT
                    ? " A privately purchased annuity requires the annuity model's separate taxable-amount and basis evidence."
                    : " The special arrangement requires separate qualification and tax evidence.";
            throw new Rejected(UNSUPPORTED_TRANSACTION_TYPE, "Arkansas pension source is outside ordinary employment-plan coverage." + detail);
        }
        require(source.purpose() == Purpose.INCOME && source.account().isEmpty()
                        && request.household().householdOwners().contains(pension.originalOwner()),
                "Arkansas pension origin or payment purpose is inconsistent.");
        var window = window(facts);
        LocalDate earliest = pension.startDate().filter(date -> date.isAfter(window.first())).orElse(window.first());
        LocalDate latest = pension.endDate().filter(date -> date.isBefore(window.last())).orElse(window.last());
        require(!latest.isBefore(earliest), "Arkansas pension payment lies outside its active dates.");
        if (owner != pension.originalOwner()) {
            require(!request.household().activeOwners().contains(pension.originalOwner())
                            && pension.survivorMonthlyBenefit().filter(value -> value.signum() > 0).isPresent(),
                    "Arkansas direct survivor pension requires a payment to the surviving spouse of the original recipient.");
        } else {
            requireNoEarlyAdditionalTax(new ReceiptWindow(earliest, latest), birthDate(owner), facts);
        }
        var amount = taxable(source);
        if (amount.compareTo(source.grossAmount()) != 0)
            throw new Rejected(MISSING_DISTRIBUTION_BASIS, "Arkansas pension needs separately verified state basis for a partial taxable payment.");
        add(Kind.PENSION, amount);
        if (amount.signum() > 0) {
            if (qualification == ORDINARY_EMPLOYMENT_PENSION) {
                certain.merge(owner, amount, BigDecimal::add);
            } else {
                unresolvedPensionRecipients.add(owner);
            }
            possible.merge(owner, amount, BigDecimal::add);
        }
    }

    private void conditional(IncomeSourceAttribution source) {
        activeRecipient(source);
        var receipt = source.conditionalBenefit().orElseThrow();
        require(conditionalIds.add(receipt.benefitId()), "Duplicate Arkansas conditional benefit receipt.");
        if (receipt.taxCharacter() == TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED) {
            // The exact receipt set was authenticated before source reconciliation.
            return;
        }
        if (receipt.taxCharacter() != TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION && receipt.annuityBasis() == null)
            throw new Rejected(UNSUPPORTED_TRANSACTION_TYPE, "This conditional benefit exclusion is outside ordinary Arkansas coverage.");
        if (receipt.annuityBasis() != null && FinancialCalculationPolicy.modelsNiit(request.income().getIncomeSources())
                && receipt.annuityBasis().terms().qualification() != Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1)
            throw new Rejected(UNSUPPORTED_MODEL_RULE, FailureCategory.LEGAL_OR_SOURCE,
                    "Advanced NIIT requires the existing verified domestic annuity qualification.",
                    ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.NIIT));
        add(Kind.CONDITIONAL_RECURRING, taxable(source));
    }

    private void validateLifePrincipal() {
        var sources = request.income().getIncomeSources();
        var conditional = sources.stream().filter(source -> source.conditionalBenefit().isPresent()).toList();
        if (evidence.lifePrincipal().isPresent()) {
            if (!FinancialCalculationPolicy.modelsNiit(sources)
                    && conditional.stream().noneMatch(evidence.receipts()::containsKey)
                    && evidence.lifePrincipal().orElseThrow().matches(request, evidence.modeledDeaths(), conditional)) {
                return;
            }
        } else if (conditional.stream().noneMatch(source -> source.conditionalBenefit().orElseThrow().taxCharacter()
                == TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED)) {
            return;
        }
        throw new Rejected(UNSUPPORTED_MODEL_RULE, FailureCategory.ATTRIBUTION,
                "Arkansas life insurance receipts do not match the qualified definitions and modeled death evidence.",
                ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.LIFE_PRINCIPAL_EVIDENCE));
    }

    private void investment(IncomeSourceAttribution source) {
        var amount = source.modeledTaxableAmount().orElse(ZERO);
        var account = source.account().orElse(null);
        if (source.kind() == Kind.ORDINARY_WITHDRAWAL) {
            require(source.purpose() == Purpose.CASH_FLOW || source.purpose() == Purpose.TAX_FUNDING,
                    "Arkansas cash withdrawals require spending or funding purpose.");
            if (account == null) {
                require(source.owner().isEmpty() && source.description().equals("Retained household assets")
                                && source.modeledTaxableAmount().isPresent() && amount.signum() == 0,
                        "Unattributed Arkansas cash withdrawal cannot establish its source.");
            } else {
                var treatment = account.type().getTaxTreatment();
                require(treatment == TaxTreatment.TAXABLE || treatment == TaxTreatment.CASH
                                && source.modeledTaxableAmount().isPresent() && amount.signum() == 0,
                        "Arkansas cash or brokerage attribution conflicts with account type or taxable cash.");
            }
        } else {
            require(source.purpose() == Purpose.INCOME && source.modeledTaxableAmount().isPresent()
                            && amount.compareTo(source.grossAmount()) == 0,
                    "Arkansas investment income needs its full modeled taxable amount.");
            require(account == null || account.type().getTaxTreatment() == TaxTreatment.TAXABLE
                            || account.type().getTaxTreatment() == TaxTreatment.CASH,
                    "Retirement account evidence conflicts with Arkansas investment classification.");
        }
        if (amount.signum() == 0) return;
        source.owner().ifPresent(owner -> require(owner == AccountOwnership.JOINT
                ? request.filingStatus() == FilingStatus.MARRIED_FILING_JOINTLY
                    && request.household().activeOwners().size() == 2
                : request.household().activeOwners().contains(owner),
                "Taxable Arkansas investment income conflicts with annual ownership."));
        if (account != null) {
            var kinds = taxableInvestmentKinds.computeIfAbsent(account, key -> new HashSet<>());
            kinds.add(source.kind());
            require(!kinds.containsAll(Set.of(Kind.CAPITAL_GAIN, Kind.ORDINARY_WITHDRAWAL)),
                    "Overlapping gain and brokerage withdrawal attribution cannot establish distinct taxable amounts.");
        }
        boolean placeholder = source.kind() == Kind.INTEREST && account == null && source.owner().isEmpty()
                && source.description().equals("Unattributed INTEREST");
        if (placeholder) {
            require(interestPlaceholder == null, "Duplicate Arkansas investment placeholder.");
            interestPlaceholder = amount;
        } else {
            investment = investment.add(amount);
            if (source.kind() == Kind.INTEREST) explicitInterest = explicitInterest.add(amount);
        }
    }

    private ReceiptWindow window(ReceiptFacts facts) {
        var window = facts.window().orElse(new ReceiptWindow(first, last));
        require(!window.first().isBefore(first) && !window.last().isAfter(last), "Arkansas receipt window lies outside the tax year.");
        return window;
    }

    private static void requireNoEarlyAdditionalTax(ReceiptWindow window, LocalDate birth, ReceiptFacts facts) {
        if (window.first().isBefore(ArkansasRetirementEligibility.age59AndHalf(birth))
                && facts.additionalTax() != NO_APPLICABLE_ADDITIONAL_TAX)
            throw new Rejected(UNSUPPORTED_MODEL_RULE, FailureCategory.LEGAL_OR_SOURCE,
                    "Potential early cash requires evidence that the Arkansas Form 5329-related additional tax is inapplicable.",
                    ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.ADDITIONAL_TAX));
    }

    private AccountOwnership activeRecipient(IncomeSourceAttribution source) {
        var owner = source.owner().orElseThrow(() -> new Rejected(
                MISSING_RETIREMENT_QUALIFICATION, "Arkansas taxable receipt requires its actual recipient."));
        if (!request.household().activeOwners().contains(owner)) {
            ArkansasRecipientDiagnostic.rejected(request, source, diagnosticStage);
            throw new Rejected(UNSUPPORTED_MODEL_RULE, FailureCategory.ATTRIBUTION,
                    "Arkansas taxable receipt requires an active individual recipient.");
        }
        return owner;
    }

    private LocalDate birthDate(AccountOwnership owner) {
        var birth = request.household().birthDates().get(owner);
        if (birth == null) throw new Rejected(MISSING_RETIREMENT_QUALIFICATION, "Arkansas retirement age requires the actual recipient's birth date.");
        return birth;
    }

    private static BigDecimal taxable(IncomeSourceAttribution source) {
        return source.modeledTaxableAmount().orElseThrow(() -> new Rejected(
                MISSING_DISTRIBUTION_BASIS, "Arkansas source requires its modeled taxable amount."));
    }

    private void add(Kind kind, BigDecimal amount) { totals.merge(kind, amount, BigDecimal::add); }

    private BigDecimal total(Kind kind) { return totals.getOrDefault(kind, ZERO); }

    private static boolean ownIra(AccountType type) {
        return type == AccountType.TRADITIONAL_IRA || type == AccountType.ROLLOVER_IRA;
    }

    static void require(boolean condition, String explanation) {
        if (!condition) throw new Rejected(UNSUPPORTED_MODEL_RULE, FailureCategory.ATTRIBUTION, explanation);
    }
}
