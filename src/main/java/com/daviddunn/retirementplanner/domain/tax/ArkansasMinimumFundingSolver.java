package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.roth.ProjectedRothConversionResult;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;

/** Ascending interval proof on the cent grid, followed by the exact fractional terminal capacity. */
public final class ArkansasMinimumFundingSolver {
    public static final String POLICY_VERSION = "AR-FUNDING-P2-1";
    private static final int MAX_SOURCES = 64;
    private static final int DEFAULT_EVALUATIONS = 512;
    private static final BigDecimal MAX_CAPACITY = new BigDecimal("1000000000000");

    public enum Status { CALCULATED, INSUFFICIENT_FUNDS, UNSUPPORTED }
    public enum Reason { SUFFICIENT, EXHAUSTED_CAPACITY, SOURCE_OR_TAX, UNSUPPORTED_DOMAIN, BUDGET_EXHAUSTED }
    public record Metrics(int sourceSegmentsVisited, int candidates, int intervalTaxBounds, int maximumDepth, int candidateBudget) { }
    public record Result(Status status, Reason reason, Optional<ArkansasAnnualFunding.Candidate> candidate,
                         BigDecimal capacity, Metrics metrics, String detail, Optional<ArkansasTaxIssue> issue) {
        public Result {
            if ((status == Status.CALCULATED) != candidate.isPresent()) {
                throw new IllegalArgumentException("Only a calculated Arkansas funding result has a candidate.");
            }
        }
    }

    private final int evaluationBudget;

    public ArkansasMinimumFundingSolver() { this(DEFAULT_EVALUATIONS); }

    ArkansasMinimumFundingSolver(int evaluationBudget) {
        if (evaluationBudget < 1 || evaluationBudget > DEFAULT_EVALUATIONS) {
            throw new IllegalArgumentException("Invalid Arkansas evaluation budget.");
        }
        this.evaluationBudget = evaluationBudget;
    }

    public Result solve(ArkansasAnnualFunding.Facts facts, ProjectedRothConversionResult conversion, WithdrawalStrategy order) {
        return solve(facts, conversion, order, AnalysisCancellationToken.none());
    }

    Result solve(ArkansasAnnualFunding.Facts facts, ProjectedRothConversionResult conversion,
            WithdrawalStrategy order, AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(facts);
        Objects.requireNonNull(conversion);
        Objects.requireNonNull(order);
        var portfolio = conversion.getPortfolio();
        cancellation.throwIfCancellationRequested();
        var pass = new Pass(new ArkansasAnnualFunding.Evaluator(facts, conversion, order), portfolio.getTotalBalance(), cancellation);
        if (!(order instanceof TaxableFirstWithdrawalStrategy || order instanceof TaxDeferredFirstWithdrawalStrategy)
                || portfolio.getAccountBalances().size() > MAX_SOURCES || pass.capacity.compareTo(MAX_CAPACITY) > 0
                || pass.capacity.scale() > 4096 || portfolio.getAccountBalances().stream()
                    .map(balance -> AccountReference.capture(balance.getAccount())).distinct().count()
                        != portfolio.getAccountBalances().size()) {
            return pass.result(Status.UNSUPPORTED, Reason.UNSUPPORTED_DOMAIN, null,
                    "Arkansas funding requires a reviewed account order and bounded account domain.");
        }
        try {
            facts.verifyCashSources();
            var zero = pass.evaluate(ZERO);
            if (zero.candidate().deficit().signum() == 0) return pass.success(zero.candidate());
            for (var segment : segments(portfolio, order)) {
                pass.segments++;
                var candidate = pass.search(segment.first(), segment.last(), segment.taxDeferred(), 1);
                if (candidate != null) return pass.success(candidate);
            }
            if (cents(floorCents(pass.capacity)).compareTo(pass.capacity) < 0) {
                pass.segments++;
                var terminal = pass.evaluate(pass.capacity).candidate();
                if (terminal.deficit().signum() == 0) return pass.success(terminal);
            }
            return pass.result(Status.INSUFFICIENT_FUNDS, Reason.EXHAUSTED_CAPACITY, null,
                    "Every executable funding amount is insufficient.");
        } catch (BudgetExhausted ex) {
            return pass.result(Status.UNSUPPORTED, Reason.BUDGET_EXHAUSTED, null, ex.getMessage(),
                    ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.FUNDING_BUDGET));
        } catch (ArkansasAnnualFunding.Unavailable ex) {
            return pass.result(Status.UNSUPPORTED, Reason.SOURCE_OR_TAX, null, ex.getMessage(), ex.issue());
        } catch (NiitUnsupportedInputException ex) {
            return pass.result(Status.UNSUPPORTED, Reason.SOURCE_OR_TAX, null, ex.getMessage(),
                    ArkansasTaxIssue.of(ArkansasTaxIssue.Kind.NIIT));
        } catch (IllegalArgumentException ex) {
            return pass.result(Status.UNSUPPORTED, Reason.SOURCE_OR_TAX, null, ex.getMessage());
        }
    }

    private record Segment(long first, long last, boolean taxDeferred) { }

    private static List<Segment> segments(ProjectedPortfolio portfolio, WithdrawalStrategy order) {
        var result = new ArrayList<Segment>();
        var boundary = portfolio.getRetainedNonQualifiedAssets();
        if (floorCents(boundary) > 0) result.add(new Segment(1, floorCents(boundary), false));
        for (var account : order.orderAccounts(portfolio)) {
            var first = floorCents(boundary) + 1;
            boundary = boundary.add(account.getBalance());
            var last = floorCents(boundary);
            if (first <= last) {
                result.add(new Segment(first, last, account.getAccount().getType().getTaxTreatment() == TaxTreatment.TAX_DEFERRED));
            }
        }
        return List.copyOf(result);
    }

    static long floorCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    static BigDecimal cents(long amount) { return BigDecimal.valueOf(amount, 2); }

    private final class Pass {
        private final ArkansasAnnualFunding.Evaluator evaluator;
        private final BigDecimal capacity;
        private final AnalysisCancellationToken cancellation;
        private int segments;
        private int candidates;
        private int bounds;
        private int maximumDepth;

        Pass(ArkansasAnnualFunding.Evaluator evaluator, BigDecimal capacity, AnalysisCancellationToken cancellation) {
            this.evaluator = evaluator;
            this.capacity = capacity;
            this.cancellation = cancellation;
        }

        ArkansasAnnualFunding.Evaluator.Priced evaluate(BigDecimal amount) {
            cancellation.throwIfCancellationRequested();
            if (candidates == evaluationBudget) throw new BudgetExhausted();
            candidates++;
            return evaluator.evaluate(amount);
        }

        ArkansasAnnualFunding.Candidate search(long first, long last, boolean deferred, int depth) {
            maximumDepth = Math.max(maximumDepth, depth);
            return search(first, last, deferred, depth, evaluate(cents(first)));
        }

        private ArkansasAnnualFunding.Candidate search(long first, long last, boolean deferred, int depth,
                                                       ArkansasAnnualFunding.Evaluator.Priced left) {
            cancellation.throwIfCancellationRequested();
            maximumDepth = Math.max(maximumDepth, depth);
            if (left.candidate().deficit().signum() == 0) return left.candidate();
            if (first == last) return null;
            bounds++;
            var floor = evaluator.taxFloor(left, deferred ? cents(last - first) : ZERO);
            if (floor.isPresent() && floor.get().compareTo(left.candidate().otherTaxCash().add(cents(last))) > 0) {
                return null;
            }
            long middle = first + (last - first) / 2;
            var earlier = search(first, middle, deferred, depth + 1, left);
            return earlier != null ? earlier : search(middle + 1, last, deferred, depth + 1);
        }

        Result success(ArkansasAnnualFunding.Candidate candidate) {
            return result(Status.CALCULATED, Reason.SUFFICIENT, candidate, "First proven sufficient executable withdrawal.");
        }

        Result result(Status status, Reason reason, ArkansasAnnualFunding.Candidate candidate, String detail) {
            return result(status, reason, candidate, detail, null);
        }

        Result result(Status status, Reason reason, ArkansasAnnualFunding.Candidate candidate, String detail, ArkansasTaxIssue issue) {
            return new Result(status, reason, Optional.ofNullable(candidate), capacity,
                    new Metrics(segments, candidates, bounds, maximumDepth, evaluationBudget), detail, Optional.ofNullable(issue));
        }
    }

    private static final class BudgetExhausted extends RuntimeException {
        BudgetExhausted() { super("Arkansas funding proof exceeded the deterministic candidate budget."); }
    }
}
