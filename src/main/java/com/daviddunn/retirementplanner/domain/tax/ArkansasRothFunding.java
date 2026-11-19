package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.roth.ProjectedRothConversionResult;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.daviddunn.retirementplanner.domain.tax.ArkansasMinimumFundingSolver.cents;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasMinimumFundingSolver.floorCents;
import static java.math.BigDecimal.ZERO;

/** Arkansas-only targeted proof. The production bracket-fill calculator remains separate. */
public final class ArkansasRothFunding {
    public static final String TARGET_POLICY_VERSION = "AR-TARGET-P3A-1";
    private static final BigDecimal MAX_CAPACITY = new BigDecimal("1000000000000");
    private static final int MAX_OUTER_INTERVALS = 512;
    private static final int MAX_INNER_EVALUATIONS = 4096;
    private final ProjectedPortfolioRothConverter converter = new ProjectedPortfolioRothConverter();
    private final ArkansasMinimumFundingSolver funding = new ArkansasMinimumFundingSolver();
    private final int intervalBudget;

    public ArkansasRothFunding() { this(MAX_OUTER_INTERVALS); }

    ArkansasRothFunding(int intervalBudget) {
        if (intervalBudget < 1 || intervalBudget > MAX_OUTER_INTERVALS) {
            throw new IllegalArgumentException("Invalid Arkansas outer proof budget.");
        }
        this.intervalBudget = intervalBudget;
    }

    public record Trial(BigDecimal requested, ProjectedRothConversionResult conversion,
                        ArkansasMinimumFundingSolver.Result funding) {
        public BigDecimal shortfall() { return requested.subtract(conversion.getTotalConversion()); }
    }

    public enum TargetStatus { CERTIFIED, NO_FEASIBLE_TARGET, INSUFFICIENT_FUNDS, UNSUPPORTED }

    public record TargetMetrics(int outerIntervals, int excludedIntervals, int exactTrials, int innerEvaluations,
                                int innerTaxBounds, int federalProofCalls, int arkansasProofCalls,
                                int incomeBoxes, int maximumOuterDepth, int peakOuterTrialStates) { }

    public record TargetResult(TargetStatus status, Optional<Trial> trial, Optional<BigDecimal> unusedTarget,
                               TargetMetrics metrics, String detail, Optional<ArkansasTaxIssue> issue) {
        public TargetResult {
            if ((status == TargetStatus.CERTIFIED) != (trial.isPresent() && unusedTarget.isPresent())) {
                throw new IllegalArgumentException("Only a certified Arkansas target has a funded conversion.");
            }
        }
        public int trials() { return metrics.exactTrials(); }
    }

    /** The caller supplies the already RMD- and spending-debited portfolio. */
    public Trial fixed(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio afterSpending,
                       WithdrawalStrategy order, BigDecimal requested) {
        return fixed(facts, afterSpending, order, requested, AnalysisCancellationToken.none());
    }

    public Trial fixed(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio afterSpending,
                       WithdrawalStrategy order, BigDecimal requested, AnalysisCancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        var conversion = execute(facts, afterSpending, requested);
        return new Trial(requested, conversion, funding.solve(facts, conversion, order, cancellation));
    }

    public TargetResult targeted(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio afterSpending,
                                 WithdrawalStrategy order, BigDecimal targetTaxableIncome) {
        return targeted(facts, afterSpending, order, targetTaxableIncome, AnalysisCancellationToken.none());
    }

    public TargetResult targeted(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio afterSpending,
                                 WithdrawalStrategy order, BigDecimal targetTaxableIncome, AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(facts);
        Objects.requireNonNull(afterSpending);
        Objects.requireNonNull(order);
        if (Objects.requireNonNull(targetTaxableIncome).signum() < 0) {
            throw new IllegalArgumentException("Negative Arkansas federal taxable-income target.");
        }
        return new Pass(facts, afterSpending, order, targetTaxableIncome, cancellation).solve();
    }

    private ProjectedRothConversionResult execute(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio base,
                                                   BigDecimal requested) {
        return converter.convertHousehold(base, requested, facts.seed().household().activeOwners(), facts.seed().taxYear());
    }

    private record CentInterval(long first, long last) { }

    private final class Pass {
        private final ArkansasAnnualFunding.Facts facts;
        private final ProjectedPortfolio base;
        private final WithdrawalStrategy order;
        private final BigDecimal target;
        private final AnalysisCancellationToken cancellation;
        private ArkansasTargetBounds bounds;
        private BigDecimal upper;
        private int intervals;
        private int excluded;
        private int trials;
        private int inner;
        private int innerBounds;
        private int maximumDepth;

        Pass(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio base, WithdrawalStrategy order, BigDecimal target,
                AnalysisCancellationToken cancellation) {
            this.facts = facts;
            this.base = base;
            this.order = order;
            this.target = target;
            this.cancellation = Objects.requireNonNull(cancellation);
        }

        TargetResult solve() {
            cancellation.throwIfCancellationRequested();
            try {
                facts.verifyCashSources();
                if (!(order instanceof TaxableFirstWithdrawalStrategy || order instanceof TaxDeferredFirstWithdrawalStrategy)
                        || base.getAccountBalances().size() > 64 || base.getTotalBalance().compareTo(MAX_CAPACITY) > 0
                        || base.getTotalBalance().scale() > 4096 || base.getAccountBalances().stream()
                            .map(balance -> AccountReference.capture(balance.getAccount())).distinct().count()
                                != base.getAccountBalances().size()
                        || facts.seed().taxYear() != facts.seed().projectedRules().getTaxYear()) {
                    throw new ArkansasAnnualFunding.Unavailable("Arkansas target requires reviewed ordering, year and capacity.");
                }
                var capacity = converter.getMaximumConvertibleAmount(base,
                        facts.seed().household().activeOwners(), facts.seed().taxYear());
                var full = execute(facts, base, capacity);
                if (full.getTotalConversion().compareTo(capacity) != 0) {
                    throw new ArkansasAnnualFunding.Unavailable("Arkansas conversion capacity does not reconcile to execution.");
                }
                bounds = new ArkansasTargetBounds(facts, base, full, order);
                if (bounds.baselineFederalInsolvent()) {
                    return result(TargetStatus.INSUFFICIENT_FUNDS, null, "Even baseline federal tax exceeds all available cash.");
                }
                if (bounds.federalTaxable(ZERO).compareTo(target) > 0) {
                    return result(TargetStatus.NO_FEASIBLE_TARGET, null,
                            "Federal taxable income already exceeds the target without conversion or tax funding.");
                }
                upper = bounds.federalUpper(target);
                var gridTop = floorCents(capacity.min(upper));

                // One authoritative probe at the highest feasible no-funding point often closes cash-funded runs.
                boolean terminal = capacity.compareTo(cents(floorCents(capacity))) > 0
                        && capacity.compareTo(upper) <= 0 && bounds.federalTaxable(capacity).compareTo(target) <= 0;
                if (terminal) {
                    var trial = exact(capacity);
                    if (trial != null) return certified(trial);
                } else if (bounds.federalTaxable(cents(gridTop)).compareTo(target) <= 0) {
                    var trial = exact(cents(gridTop));
                    if (trial != null) return certified(trial);
                    gridTop--;
                }

                for (var slice : conversionIntervals(full, gridTop).reversed()) {
                    var trial = search(slice.first(), slice.last(), 1);
                    if (trial != null) return certified(trial);
                }
                return result(TargetStatus.NO_FEASIBLE_TARGET, null,
                        "Every admitted conversion amount was excluded or checked without a funded target result.");
            } catch (ArkansasTargetBounds.Budget ex) {
                return result(TargetStatus.UNSUPPORTED, null, ex.getMessage(),
                        ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.TARGET_BUDGET));
            } catch (ArkansasAnnualFunding.Unavailable ex) {
                return result(TargetStatus.UNSUPPORTED, null, ex.getMessage(), ex.issue());
            } catch (NiitUnsupportedInputException ex) {
                return result(TargetStatus.UNSUPPORTED, null, ex.getMessage(),
                        ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.NIIT));
            } catch (IllegalArgumentException | DateTimeException ex) {
                return result(TargetStatus.UNSUPPORTED, null, ex.getMessage());
            }
        }

        private List<CentInterval> conversionIntervals(ProjectedRothConversionResult full, long gridTop) {
            var slices = new ArrayList<CentInterval>();
            long first = 0;
            var boundary = ZERO;
            for (var allocation : full.getAllocations()) {
                boundary = boundary.add(allocation.amount());
                long last = Math.min(gridTop, floorCents(boundary));
                if (last >= first) slices.add(new CentInterval(first, last));
                first = last + 1;
                if (last == gridTop) break;
            }
            if (first <= gridTop) slices.add(new CentInterval(first, gridTop));
            return slices;
        }

        private Trial search(long first, long last, int depth) {
            cancellation.throwIfCancellationRequested();
            if (intervals == intervalBudget) throw new ArkansasTargetBounds.Budget();
            intervals++;
            maximumDepth = Math.max(maximumDepth, depth);
            var range = new ArkansasTargetBounds.Interval(cents(first), cents(last));
            if (bounds.excludes(range, upper, target)) {
                excluded++;
                return null;
            }
            if (first == last) return exact(range.low());
            long middle = first + (last - first) / 2;
            var later = search(middle + 1, last, depth + 1);
            return later == null ? search(first, middle, depth + 1) : later;
        }

        private Trial exact(BigDecimal requested) {
            cancellation.throwIfCancellationRequested();
            if (bounds.federalTaxable(requested).compareTo(target) > 0) return null;
            if (inner == MAX_INNER_EVALUATIONS) throw new ArkansasTargetBounds.Budget();
            trials++;
            var conversion = execute(facts, base, requested);
            if (conversion.getTotalConversion().compareTo(requested) != 0) {
                throw new ArkansasAnnualFunding.Unavailable("Arkansas conversion amount does not reconcile to execution.");
            }
            var priced = new ArkansasMinimumFundingSolver(Math.min(512, MAX_INNER_EVALUATIONS - inner))
                    .solve(facts, conversion, order, cancellation);
            inner += priced.metrics().candidates();
            innerBounds += priced.metrics().intervalTaxBounds();
            if (priced.status() == ArkansasMinimumFundingSolver.Status.UNSUPPORTED) {
                if (bounds.excludes(new ArkansasTargetBounds.Interval(requested, requested), upper, target)) {
                    excluded++;
                    return null;
                }
                throw new ArkansasAnnualFunding.Unavailable(priced.detail(), priced.issue().orElse(null));
            }
            return priced.candidate().filter(c -> c.annualTaxFacts().federal().getTaxableIncome().compareTo(target) <= 0)
                    .map(c -> new Trial(requested, conversion, priced)).orElse(null);
        }

        private TargetResult certified(Trial trial) {
            return result(TargetStatus.CERTIFIED, trial, "Maximum executable conversion certified within the funded federal ceiling.");
        }

        private TargetResult result(TargetStatus status, Trial trial, String detail) {
            return result(status, trial, detail, null);
        }

        private TargetResult result(TargetStatus status, Trial trial, String detail, ArkansasTaxIssue issue) {
            var unused = Optional.ofNullable(trial).map(t -> target.subtract(
                    t.funding().candidate().orElseThrow().annualTaxFacts().federal().getTaxableIncome()));
            return new TargetResult(status, Optional.ofNullable(trial), unused,
                    new TargetMetrics(intervals, excluded, trials, inner, innerBounds,
                            bounds == null ? 0 : bounds.federalProbes(), bounds == null ? 0 : bounds.stateProbes(),
                            bounds == null ? 0 : bounds.boxes(), maximumDepth, trials == 0 ? 0 : 1), detail,
                    Optional.ofNullable(issue));
        }
    }
}
