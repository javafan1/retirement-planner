package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy;
import com.daviddunn.retirementplanner.domain.projection.HouseholdCashSettlement;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectedWithdrawalAllocation;
import com.daviddunn.retirementplanner.domain.projection.ProjectedWithdrawalAllocator;
import com.daviddunn.retirementplanner.domain.roth.ProjectedRothConversionResult;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasRecipientDiagnostic;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.Form5329Status;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.ReceiptFacts;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.ReceiptWindow;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.SourceQualification;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationRequest;
import com.daviddunn.retirementplanner.domain.tax.state.AnnualIncomeTaxFacts;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;

/** Isolated Arkansas annual path. There is no caller in the production projection or jurisdiction router. */
public final class ArkansasAnnualFunding {
    public record Cash(BigDecimal guaranteed, BigDecimal rmd, BigDecimal spendingWithdrawal,
                       BigDecimal ordinaryExpenses, BigDecimal medicare) {
        public Cash {
            for (var value : List.of(guaranteed, rmd, spendingWithdrawal, ordinaryExpenses, medicare)) {
                if (value.signum() < 0) throw new IllegalArgumentException("Negative annual cash input.");
            }
            var spending = ordinaryExpenses.add(medicare).subtract(guaranteed).subtract(rmd).max(ZERO);
            if (spending.compareTo(spendingWithdrawal) != 0) {
                throw new IllegalArgumentException("Price Arkansas funding after ordinary spending and Medicare are settled.");
            }
        }

        public BigDecimal otherTaxCash() {
            return guaranteed.add(rmd).subtract(ordinaryExpenses).subtract(medicare).max(ZERO);
        }
    }

    /** The seed contains the annual attributed income after RMD/spending and before conversion. */
    public record Facts(AnnualIncomeTaxFacts seed, ArkansasTaxEvidence evidence,
                        Optional<ReceiptWindow> projectedReceipts, Map<AccountReference, Form5329Status> additionalTax,
                        Cash cash, boolean captureLowIncomeEvidence, ArkansasLowIncomePolicy lowIncomePolicy) {
        public Facts(AnnualIncomeTaxFacts seed, ArkansasTaxEvidence evidence,
                     Optional<ReceiptWindow> projectedReceipts, Map<AccountReference, Form5329Status> additionalTax,
                     Cash cash, boolean captureLowIncomeEvidence) {
            this(seed, evidence, projectedReceipts, additionalTax, cash, captureLowIncomeEvidence,
                    ArkansasLowIncomePolicy.STRICT);
        }

        public Facts(AnnualIncomeTaxFacts seed, ArkansasTaxEvidence evidence,
                     Optional<ReceiptWindow> projectedReceipts, Map<AccountReference, Form5329Status> additionalTax,
                     Cash cash) {
            this(seed, evidence, projectedReceipts, additionalTax, cash, false);
        }

        public Facts(StateTaxCalculationRequest seed, ArkansasTaxEvidence evidence,
                     Optional<ReceiptWindow> projectedReceipts, Map<AccountReference, Form5329Status> additionalTax,
                     Cash cash) {
            this(seed.annualFacts(), evidence, projectedReceipts, additionalTax, cash);
        }
        public Facts {
            Objects.requireNonNull(seed);
            Objects.requireNonNull(evidence);
            Objects.requireNonNull(projectedReceipts);
            additionalTax = Map.copyOf(additionalTax);
            Objects.requireNonNull(cash);
            Objects.requireNonNull(lowIncomePolicy);
            if (seed.income().getRothConversion().signum() != 0
                    || seed.income().getIncomeSources().stream().anyMatch(source -> source.niit().isPresent()
                        || source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION
                        || source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING)) {
                throw new IllegalArgumentException("The Arkansas seed must precede conversion and funding and contain no completed NIIT evidence.");
            }
        }

        void verifyCashSources() {
            var income = seed.income();
            BigDecimal guaranteed = income.getPensionIncome().add(income.getSocialSecurityIncome());
            BigDecimal spending = ZERO;
            BigDecimal rmd = ZERO;
            for (var source : income.getIncomeSources()) {
                if (source.conditionalBenefit().isPresent()) guaranteed = guaranteed.add(source.grossAmount());
                if (source.purpose() != IncomeSourceAttribution.Purpose.CASH_FLOW) continue;
                switch (source.kind()) {
                    case ORDINARY_WITHDRAWAL, ORDINARY_RETIREMENT_WITHDRAWAL -> spending = spending.add(source.grossAmount());
                    case RMD -> rmd = rmd.add(source.grossAmount());
                    default -> throw new Unavailable("Unclassified Arkansas annual cash source.");
                }
            }
            if (guaranteed.compareTo(cash.guaranteed()) != 0 || spending.compareTo(cash.spendingWithdrawal()) != 0
                    || rmd.compareTo(cash.rmd()) != 0) {
                throw new Unavailable("Arkansas annual guaranteed income, spending withdrawals or projected RMD cash contradict the executed sources.");
            }
        }
    }

    /**
     * Adapts the normal annual incomes, selected Social Security/pensions and executed opening,
     * current RMD and spending traces. Supplemental sources contain the selected opening RMD,
     * conditional and investment/mode facts, with pension sources supplied by the tax context.
     */
    public static StateTaxCalculationRequest captureIncome(StateTaxCalculationRequest context, Household household,
            HouseholdSocialSecurityResult socialSecurity, WithdrawalBreakdown executedRmdAndSpending,
            BigDecimal openingRmd, BigDecimal investmentIncome, List<IncomeSourceAttribution> supplemental) {
        var annual = captureIncome(context.household(), context.filingStatus(), context.projectedRules(), household,
                socialSecurity, executedRmdAndSpending, openingRmd, investmentIncome, supplemental);
        return new StateTaxCalculationRequest(annual.taxYear(), annual.filingStatus(), annual.household(),
                annual.income(), annual.federal(), annual.projectedRules(), context.assumptions());
    }

    public static AnnualIncomeTaxFacts captureIncome(TaxHouseholdContext taxContext, FilingStatus filing,
            GovernmentRules rules, Household household, HouseholdSocialSecurityResult socialSecurity,
            WithdrawalBreakdown executedRmdAndSpending, BigDecimal openingRmd, BigDecimal investmentIncome,
            List<IncomeSourceAttribution> supplemental) {
        Objects.requireNonNull(socialSecurity);
        var date = taxContext.distributionDate();
        int year = date.getYear();
        var pensions = taxContext.pensionIncome();
        if (!taxContext.activeOwners().equals(taxContext.householdOwners()) && pensions.isEmpty()) {
            throw new Unavailable("Arkansas survivor income requires the selected annual pension facts.");
        }
        var sources = new ArrayList<>(executedRmdAndSpending.getIncomeSources());
        sources.addAll(supplemental);
        pensions.ifPresent(selected -> sources.addAll(selected.sources()));
        if (sources.stream().anyMatch(source -> source.taxYear().isPresent()
                && source.taxYear().getAsInt() != year)) {
            throw new Unavailable("Arkansas annual evidence belongs to another tax year.");
        }
        var annualIncome = new TaxIncomeCalculator().calculate(household, date,
                executedRmdAndSpending.getTaxDeferredWithdrawal(), openingRmd, ZERO, investmentIncome, ZERO,
                null, socialSecurity, pensions.map(result -> result.total()));
        var attributed = TaxIncomeAttribution.attach(annualIncome, sources, year);
        var federal = new FederalTaxCalculator().calculate(attributed, filing, rules);
        return new AnnualIncomeTaxFacts(year, filing, taxContext, attributed, federal, rules);
    }

    public record Candidate(BigDecimal requested, WithdrawalBreakdown allocation,
                            ProjectedPortfolio portfolio, AnnualIncomeTaxFacts annualTaxFacts,
                            ArkansasProductionTaxCalculator.Result stateTax, Cash cash,
                            ArkansasTaxEvidence evidence) {
        public Candidate {
            if (requested.signum() < 0 || cash == null
                    || requested.compareTo(allocation.getTotalWithdrawal()) != 0
                    || stateTax.estimatedIncomeTax().isEmpty()) {
                throw new IllegalArgumentException("An Arkansas candidate requires executable cash and calculated tax.");
            }
        }
        public BigDecimal spendable() { return allocation.getTotalWithdrawal(); }
        public BigDecimal taxableFundingIncome() { return allocation.getTaxDeferredWithdrawal(); }
        public BigDecimal otherTaxCash() { return cash.otherTaxCash(); }
        public BigDecimal federalTax() { return annualTaxFacts.federal().getFederalIncomeTax(); }
        public BigDecimal totalTax() { return federalTax().add(stateTax.incomeTax()); }
        public BigDecimal deficit() { return totalTax().subtract(otherTaxCash().add(spendable())).max(ZERO); }
        public BigDecimal surplus() { return spendable().subtract(totalTax().subtract(otherTaxCash()).max(ZERO)).max(ZERO); }
        public List<IncomeSourceAttribution> incomeSources() {
            var sources = new ArrayList<>(annualTaxFacts.income().getIncomeSources());
            annualTaxFacts.federal().niit().ifPresent(niit -> sources.add(IncomeSourceAttribution.niit(niit, annualTaxFacts.taxYear())));
            return List.copyOf(sources);
        }
    }

    public record Settlement(HouseholdCashSettlement cash, ProjectedPortfolio portfolio) { }

    public static Settlement settle(Candidate candidate) {
        var cash = candidate.cash();
        if (candidate.deficit().signum() != 0) {
            throw new IllegalArgumentException("Settle only the sufficient Arkansas candidate from this annual pass.");
        }
        var ledger = HouseholdCashSettlement.settleMinimumSufficient(cash.guaranteed(), cash.rmd(),
                cash.spendingWithdrawal(), candidate.spendable(), cash.ordinaryExpenses(), cash.medicare(),
                candidate.totalTax());
        return new Settlement(ledger, candidate.portfolio()
                .withAdditionalRetainedNonQualifiedAssets(ledger.retainedHouseholdSurplus()));
    }

    /** One evaluator prices and retains the very portfolio returned by the allocator. */
    static final class Evaluator {
        private final Facts facts;
        private final ProjectedPortfolio afterConversion;
        private final WithdrawalStrategy order;
        private final BigDecimal conversionAmount;
        private final List<IncomeSourceAttribution> converted;
        private final ArkansasProductionTaxCalculator state = new ArkansasProductionTaxCalculator();
        private final FederalTaxCalculator federal = new FederalTaxCalculator();
        private final ProjectedWithdrawalAllocator allocator = new ProjectedWithdrawalAllocator();

        Evaluator(Facts facts, ProjectedRothConversionResult conversion, WithdrawalStrategy order) {
            this.facts = facts;
            this.afterConversion = conversion.getPortfolio();
            this.order = order;
            this.conversionAmount = conversion.getTotalConversion();
            this.converted = conversion.getIncomeSources(facts.seed().taxYear());
        }

        record Priced(Candidate candidate, ArkansasTaxEvidence evidence) { }

        Priced evaluate(BigDecimal withdrawal) {
            ProjectedWithdrawalAllocation executed;
            try {
                executed = allocator.allocateTaxFundingWithdrawal(afterConversion, withdrawal, order);
            } catch (IllegalStateException shortfall) {
                throw new Unavailable("Arkansas funding capacity could not be reproduced by the withdrawal allocator.");
            }
            var source = executed.getWithdrawalBreakdown();
            var trace = source.getIncomeSources().stream().map(s -> s.inYear(facts.seed().taxYear())).toList();
            var allocation = new WithdrawalBreakdown(source.getCashWithdrawal(), source.getTaxableWithdrawal(),
                    source.getTaxDeferredWithdrawal(), source.getRothWithdrawal(), trace);
            var debits = trace.stream().map(IncomeSourceAttribution::grossAmount).reduce(ZERO, BigDecimal::add);
            if (debits.compareTo(withdrawal) != 0
                    || afterConversion.getTotalBalance().subtract(executed.getPortfolio().getTotalBalance()).compareTo(debits) != 0) {
                throw new IllegalArgumentException("Arkansas funding debits do not reconcile to the executed allocation.");
            }

            var seed = facts.seed();
            var original = seed.income();
            var sources = new ArrayList<>(original.getIncomeSources());
            sources.addAll(converted);
            sources.addAll(trace);
            var income = new TaxIncome(original.getPensionIncome(), original.getSocialSecurityIncome(),
                    original.getTaxDeferredWithdrawals().add(allocation.getTaxDeferredWithdrawal()), conversionAmount,
                    original.getTaxableInterestIncome(), sources).withConditionalOrdinaryIncome(original.getConditionalOrdinaryIncome());
            var input = new AnnualIncomeTaxFacts(seed.taxYear(), seed.filingStatus(), seed.household(), income,
                    federal.calculate(income, seed.filingStatus(), seed.projectedRules()), seed.projectedRules());
            var evidence = evidence(trace);
            ArkansasProductionTaxCalculator.Result result;
            try (var ignored = ArkansasRecipientDiagnostic.exactFunding(withdrawal.signum() > 0)) {
                result = state.calculateUnregistered(input, evidence, facts.captureLowIncomeEvidence(), facts.lowIncomePolicy());
            }
            if (result.estimatedIncomeTax().isEmpty()) {
                var issue = result.issue().orElseThrow();
                if (issue.lowIncomeEvidence().isPresent()) {
                    boolean belowFederal = facts.cash().otherTaxCash().add(allocation.getTotalWithdrawal())
                            .compareTo(input.federal().getFederalIncomeTax()) < 0;
                    issue = issue.withExactFundingProbe(new ArkansasTaxIssue.ExactFundingProbe(
                            withdrawal.signum() == 0 ? ArkansasTaxIssue.Withdrawal.ZERO : ArkansasTaxIssue.Withdrawal.POSITIVE,
                            belowFederal ? ArkansasTaxIssue.FederalCash.BELOW_FEDERAL_TAX
                                    : ArkansasTaxIssue.FederalCash.COVERS_FEDERAL_TAX));
                }
                throw new Unavailable(result.limitations().getFirst(), issue);
            }
            return new Priced(new Candidate(withdrawal, allocation, executed.getPortfolio(), input, result,
                    facts.cash(), evidence), evidence);
        }

        Optional<BigDecimal> taxFloor(Priced left, BigDecimal maximumTaxableIncrease) {
            var candidate = left.candidate();
            return state.fundingTaxFloor(candidate.annualTaxFacts(), left.evidence(), maximumTaxableIncrease, facts.lowIncomePolicy())
                    .map(candidate.federalTax()::add);
        }

        ArkansasTaxEvidence evidence(List<IncomeSourceAttribution> trace) {
            var receipts = new HashMap<>(facts.evidence().receipts());
            var year = facts.seed().taxYear();
            var opening = facts.seed().household().distributionDate();
            var window = facts.projectedReceipts().orElseGet(() -> new ReceiptWindow(opening, LocalDate.of(year, 12, 31)));
            if (window.first().isBefore(opening) || window.last().getYear() != year) {
                throw new Unavailable("Arkansas projected receipts must lie in the modeled annual slice.");
            }
            for (var source : facts.seed().income().getIncomeSources()) {
                if (!source.isRetirementDistribution()) continue;
                if (source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD) {
                    if (opening.getDayOfYear() == 1 && source.grossAmount().signum() > 0) {
                        throw new Unavailable("Same-year opening RMD requires an earlier receipt interval.");
                    }
                    if (opening.getDayOfYear() > 1) {
                        var preProjection = new ReceiptWindow(LocalDate.of(year, 1, 1), opening.minusDays(1));
                        receipts.putIfAbsent(source, distributionFacts(source, preProjection));
                    }
                } else {
                    receipts.putIfAbsent(source, distributionFacts(source, window));
                }
            }
            var distributions = new ArrayList<>(converted);
            distributions.addAll(trace);
            for (var source : distributions) {
                if (!source.isRetirementDistribution()) continue;
                receipts.put(source, distributionFacts(source, window));
            }
            return new ArkansasTaxEvidence(receipts, facts.evidence().modeledDeaths(), facts.evidence().lifePrincipal());
        }

        private ReceiptFacts distributionFacts(IncomeSourceAttribution source, ReceiptWindow window) {
            var status = source.account().map(account -> facts.additionalTax()
                    .getOrDefault(account, Form5329Status.UNKNOWN)).orElse(Form5329Status.UNKNOWN);
            return new ReceiptFacts(Optional.of(window), SourceQualification.UNSPECIFIED,
                    source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION ? Form5329Status.UNKNOWN : status);
        }
    }

    static final class Unavailable extends IllegalArgumentException {
        private final com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue issue;
        Unavailable(String message) { this(message, null); }
        Unavailable(String message, com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue issue) {
            super(message);
            this.issue = issue;
        }
        com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue issue() { return issue; }
    }

    /** Reconciles a proved capacity failure with the exact deficit at terminal executable capacity. */
    public static Candidate priceTerminal(Facts facts, ProjectedRothConversionResult conversion,
                                          WithdrawalStrategy strategy) {
        facts.verifyCashSources();
        return new Evaluator(facts, conversion, strategy).evaluate(conversion.getPortfolio().getTotalBalance()).candidate();
    }
}
