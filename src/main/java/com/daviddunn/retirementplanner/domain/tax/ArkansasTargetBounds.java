package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.roth.ProjectedRothConversionResult;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator.TargetTaxFloor;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRetirementEligibility;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;

/** Exclusion proof only. This relaxation never selects or executes a candidate. */
final class ArkansasTargetBounds {
    private static final BigDecimal BOX_RESOLUTION = new BigDecimal("0.0000000001");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final int MAX_BOXES_PER_INTERVAL = 160;
    private static final int MAX_TOTAL_BOXES = 8192;

    record Interval(BigDecimal low, BigDecimal high) { }

    private record ConversionSlice(BigDecimal start, BigDecimal amount) {
        BigDecimal debit(BigDecimal conversion) {
            return conversion.subtract(start).max(ZERO).min(amount);
        }
    }

    private enum Eligibility { CERTAIN, POSSIBLE, NONE }

    private record Source(BigDecimal balance, ConversionSlice conversion, AccountOwnership owner,
                          Eligibility eligibility, boolean fundingPrefix) { }

    private final ArkansasAnnualFunding.Facts facts;
    private final List<Source> deferredSources;
    private final BigDecimal nonDeferredPrefix;
    private final BigDecimal deferredTotal;
    private final BigDecimal portfolioTotal;
    private final boolean ordinaryStateSources;
    private final BigDecimal beforeInheritedFunding;
    private Optional<TargetTaxFloor> stateFloor = Optional.empty();
    private final Optional<NetInvestmentIncomeTaxCalculation> investment;
    private final SocialSecurityTaxCalculator socialSecurity = new SocialSecurityTaxCalculator();
    private final FederalTaxableIncomeCalculator taxableIncome = new FederalTaxableIncomeCalculator();
    private final FederalIncomeTaxCalculator regularTax = new FederalIncomeTaxCalculator();
    private final NetInvestmentIncomeTaxCalculator niit = new NetInvestmentIncomeTaxCalculator();
    private int federalProbes;
    private int stateProbes;
    private int boxes;

    ArkansasTargetBounds(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio base,
                         ProjectedRothConversionResult fullConversion, WithdrawalStrategy order) {
        this.facts = facts;
        portfolioTotal = base.getTotalBalance();
        Map<Account, ConversionSlice> slices = new IdentityHashMap<>();
        var consumed = ZERO;
        boolean ordinaryState = true;
        for (var transfer : fullConversion.getAllocations()) {
            var source = transfer.sourceAccount();
            if (source.getType().getTaxTreatment() != TaxTreatment.TAX_DEFERRED
                    || transfer.destinationAccount().getType() != AccountType.ROTH_IRA
                    || slices.put(source, new ConversionSlice(consumed, transfer.amount())) != null) {
                throw new ArkansasAnnualFunding.Unavailable("Arkansas target requires a stable pretax conversion prefix.");
            }
            ordinaryState &= ownIra(source.getType());
            consumed = consumed.add(transfer.amount());
        }
        var prefix = base.getRetainedNonQualifiedAssets();
        var deferred = ZERO;
        BigDecimal inheritedBoundary = null;
        var sources = new ArrayList<Source>();
        for (var projected : order.orderAccounts(base)) {
            if (projected.getBalance().signum() == 0) continue;
            var account = projected.getAccount();
            var type = account.getType();
            if (type.getTaxTreatment() == TaxTreatment.TAX_DEFERRED) {
                var slice = slices.getOrDefault(account, new ConversionSlice(ZERO, ZERO));
                if (slice.amount().compareTo(projected.getBalance()) > 0) {
                    throw new ArkansasAnnualFunding.Unavailable("Conversion prefix exceeds its frozen source balance.");
                }
                boolean inherited = AccountReference.capture(account).inheritedInformation().isPresent();
                if (type == AccountType.INHERITED_TRADITIONAL_IRA && inherited && slice.amount().signum() == 0) {
                    if (inheritedBoundary == null) inheritedBoundary = deferred;
                } else {
                    boolean ordinaryFunding = !inherited && (ownIra(type)
                            || type == AccountType.TRADITIONAL_401K || type == AccountType.TRADITIONAL_403B);
                    ordinaryState &= ordinaryFunding;
                    if (ordinaryFunding && (inheritedBoundary == null || slice.amount().signum() > 0)) {
                        sources.add(new Source(projected.getBalance(), slice, account.getOwnership(),
                                qualification(account), inheritedBoundary == null));
                    }
                }
                deferred = deferred.add(projected.getBalance());
            } else if (deferred.signum() == 0 && type.getTaxTreatment() != TaxTreatment.ROTH) {
                prefix = prefix.add(projected.getBalance());
            }
        }
        deferredSources = List.copyOf(sources);
        deferredTotal = deferred;
        nonDeferredPrefix = prefix;
        ordinaryStateSources = ordinaryState;
        beforeInheritedFunding = inheritedBoundary;
        var seed = facts.seed();
        federalProbes++;
        investment = new FederalTaxCalculator().calculate(seed.income(), seed.filingStatus(), seed.projectedRules()).niit();
        if (ordinaryStateSources && beforeInheritedFunding == null) {
            prepareStateFloor(base, order);
        }
    }

    void prepareReachableStateFloor(ProjectedPortfolio base, WithdrawalStrategy order, BigDecimal upper,
            AnalysisCancellationToken cancellation) {
        // Prefix conversion dollars and the remaining pretax prefix debited for funding sum
        // to the original balance. Equality does not certify the inherited source unreachable.
        if (ordinaryStateSources && beforeInheritedFunding != null && beforeInheritedFunding.compareTo(upper) > 0) {
            cancellation.throwIfCancellationRequested();
            prepareStateFloor(base, order);
        }
    }

    private void prepareStateFloor(ProjectedPortfolio base, WithdrawalStrategy order) {
        var unconverted = new ProjectedRothConversionResult(base, List.of());
        federalProbes++;
        stateFloor = new ArkansasProductionTaxCalculator().prepareTargetTaxFloor(facts.seed(),
                new ArkansasAnnualFunding.Evaluator(facts, unconverted, order).evidence(List.of()));
    }

    /** Upper envelope, rounded outward. Taxable income is monotone in aggregate pretax inclusion. */
    BigDecimal federalUpper(BigDecimal target) {
        var seed = facts.seed();
        var ordinary = seed.income().getOrdinaryIncomeBeforeSocialSecurity();
        var deduction = seed.projectedRules().getFederalTaxRules(seed.filingStatus()).getStandardDeduction();
        var high = target.add(deduction).subtract(ordinary).max(ZERO).min(deferredTotal);
        if (federalTaxable(high).compareTo(target) <= 0) return high;
        var low = ZERO;
        while (high.subtract(low).compareTo(BOX_RESOLUTION) > 0) {
            var middle = low.add(high).divide(TWO, 10, RoundingMode.CEILING);
            if (middle.compareTo(high) >= 0) break;
            if (federalTaxable(middle).compareTo(target) <= 0) low = middle;
            else high = middle;
        }
        return high;
    }

    BigDecimal federalTaxable(BigDecimal increase) {
        federalProbes++;
        return taxableAtAgi(agi(increase));
    }

    boolean baselineFederalInsolvent() {
        return federalLiability(ZERO).compareTo(facts.cash().otherTaxCash().add(portfolioTotal)) > 0;
    }

    /**
     * An interval is excluded only if every permissible combined conversion + deferred debit
     * fails the cash necessary condition. Feasibility of the relaxed problem never certifies a trial.
     */
    boolean excludes(Interval conversion, BigDecimal upper, BigDecimal target) {
        if (conversion.low().compareTo(upper) > 0) return true;

        // All deferred balances exhausted. Only then can a debit reach the Roth/cash suffix.
        if (deferredTotal.compareTo(upper) <= 0 && federalTaxable(deferredTotal).compareTo(target) <= 0
                && taxFloor(conversion, deferredTotal, deferredTotal)
                    .compareTo(facts.cash().otherTaxCash().add(portfolioTotal)) <= 0) {
            return false;
        }

        // A potential prefix solution makes further proof of this outer interval unnecessary.
        if (!prefixImpossible(conversion, upper, upper)) return false;
        return excludesPrefix(conversion, conversion.low(), upper, boxes + MAX_BOXES_PER_INTERVAL);
    }

    private boolean excludesPrefix(Interval conversion, BigDecimal low, BigDecimal high, int localLimit) {
        if (boxes >= MAX_TOTAL_BOXES) throw new Budget();
        if (boxes >= localLimit) return false;
        boxes++;
        if (prefixImpossible(conversion, low, high)) return true;
        if (high.subtract(low).compareTo(BOX_RESOLUTION) <= 0) return false;
        var middle = low.add(high).divide(TWO, 10, RoundingMode.CEILING);
        return middle.compareTo(high) < 0 && excludesPrefix(conversion, middle, high, localLimit)
                && excludesPrefix(conversion, low, middle, localLimit);
    }

    private boolean prefixImpossible(Interval conversion, BigDecimal low, BigDecimal high) {
        var greatestCash = facts.cash().otherTaxCash().add(nonDeferredPrefix).add(high.subtract(conversion.low()));
        return taxFloor(conversion, low, high).compareTo(greatestCash) > 0;
    }

    private BigDecimal taxFloor(Interval conversion, BigDecimal low, BigDecimal high) {
        var federal = federalLiability(low);
        if (stateFloor.isEmpty()) return federal;
        var eligibleLow = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        var eligibleHigh = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        var priorRemainingLow = ZERO;
        var priorRemainingHigh = ZERO;
        var fundingLow = low.subtract(conversion.high()).max(ZERO);
        var fundingHigh = high.subtract(conversion.low()).max(ZERO);
        for (var source : deferredSources) {
            var convertedLow = source.conversion().debit(conversion.low());
            var convertedHigh = source.conversion().debit(conversion.high());
            var fundLow = ZERO;
            var fundHigh = ZERO;
            // A later own IRA may convert in a different order, but funding cannot cross the certified boundary.
            if (source.fundingPrefix()) {
                var remainingLow = source.balance().subtract(convertedHigh);
                var remainingHigh = source.balance().subtract(convertedLow);
                fundLow = fundingLow.subtract(priorRemainingHigh).max(ZERO).min(remainingLow);
                fundHigh = fundingHigh.subtract(priorRemainingLow).max(ZERO).min(remainingHigh);
                priorRemainingLow = priorRemainingLow.add(remainingLow);
                priorRemainingHigh = priorRemainingHigh.add(remainingHigh);
            }
            if (source.eligibility() == Eligibility.CERTAIN) {
                eligibleLow.merge(source.owner(), convertedLow.add(fundLow), BigDecimal::add);
            }
            if (source.eligibility() != Eligibility.NONE) {
                eligibleHigh.merge(source.owner(), convertedHigh.add(fundHigh).min(source.balance()), BigDecimal::add);
            }
        }
        stateProbes++;
        return federal.add(stateFloor.orElseThrow().lowerBound(low, high, eligibleLow, eligibleHigh));
    }

    private Eligibility qualification(Account account) {
        if (!ownIra(account.getType())) return Eligibility.CERTAIN;
        var seed = facts.seed();
        var birth = seed.household().birthDates().get(account.getOwnership());
        if (birth == null) return Eligibility.POSSIBLE;
        var threshold = ArkansasRetirementEligibility.age59AndHalf(birth);
        var first = facts.projectedReceipts().map(w -> w.first()).orElse(seed.household().distributionDate());
        var last = facts.projectedReceipts().map(w -> w.last()).orElse(LocalDate.of(seed.taxYear(), 12, 31));
        return !first.isBefore(threshold) ? Eligibility.CERTAIN
                : last.isBefore(threshold) ? Eligibility.NONE : Eligibility.POSSIBLE;
    }

    private static boolean ownIra(AccountType type) {
        return type == AccountType.TRADITIONAL_IRA || type == AccountType.ROLLOVER_IRA;
    }

    private BigDecimal agi(BigDecimal increase) {
        var seed = facts.seed();
        var base = seed.income();
        var income = new TaxIncome(base.getPensionIncome(), base.getSocialSecurityIncome(),
                base.getTaxDeferredWithdrawals(), increase, base.getTaxableInterestIncome())
                .withConditionalOrdinaryIncome(base.getConditionalOrdinaryIncome());
        return income.getOrdinaryIncomeBeforeSocialSecurity().add(
                socialSecurity.calculateTaxableBenefits(income, seed.filingStatus(), seed.projectedRules()));
    }

    private BigDecimal taxableAtAgi(BigDecimal agi) {
        var seed = facts.seed();
        return taxableIncome.calculateTaxableIncome(agi, seed.filingStatus(), seed.projectedRules());
    }

    private BigDecimal federalLiability(BigDecimal increase) {
        federalProbes++;
        var seed = facts.seed();
        var agi = agi(increase);
        var federal = regularTax.calculateTax(taxableAtAgi(agi), seed.filingStatus(), seed.projectedRules());
        return investment.map(e -> federal.add(niit.calculate(seed.filingStatus(), agi, e.taxableAnnuities(),
                e.taxableInterest()).tax())).orElse(federal);
    }

    int federalProbes() { return federalProbes; }
    int stateProbes() { return stateProbes; }
    int boxes() { return boxes; }

    static final class Budget extends RuntimeException {
        Budget() { super("Arkansas targeted conversion exhausted the interval-proof budget."); }
    }
}
