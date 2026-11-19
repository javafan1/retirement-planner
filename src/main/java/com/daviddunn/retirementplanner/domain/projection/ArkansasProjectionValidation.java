package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding;
import com.daviddunn.retirementplanner.domain.tax.ArkansasMinimumFundingSolver;
import com.daviddunn.retirementplanner.domain.tax.ArkansasRothFunding;
import com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;

/** Package-internal capability. Only the isolated validation harness can supply it to the engine. */
final class ArkansasProjectionValidation {
    @FunctionalInterface
    interface EvidenceProvider {
        AnnualEvidence forYear(int year, List<IncomeSourceAttribution> pensions);
    }

    record AnnualEvidence(BigDecimal taxableInvestments, List<IncomeSourceAttribution> investments,
            Map<IncomeSourceAttribution, ArkansasTaxEvidence.ReceiptFacts> receipts,
            Map<IncomeSourceAttribution.AccountReference, ArkansasTaxEvidence.Form5329Status> additionalTax) {
        static final AnnualEvidence EMPTY = new AnnualEvidence(ZERO, List.of(), Map.of(), Map.of());

        AnnualEvidence {
            if (taxableInvestments.signum() < 0) throw new IllegalArgumentException("Negative Arkansas investment evidence.");
            investments = List.copyOf(investments);
            receipts = Map.copyOf(receipts);
            additionalTax = Map.copyOf(additionalTax);
            if (investments.stream().anyMatch(source -> switch (source.kind()) {
                case INTEREST, DIVIDEND, CAPITAL_GAIN -> false;
                default -> true;
            })) {
                throw new IllegalArgumentException("Expected taxable investment attribution.");
            }
        }
    }

    record Execution(ArkansasRothFunding.Trial conversion, ArkansasAnnualFunding.Candidate funding,
                     ArkansasValidationTax disclosure) { }

    private final EvidenceProvider evidence;
    private final ArkansasRothFunding roth = new ArkansasRothFunding();

    ArkansasProjectionValidation(EvidenceProvider evidence) {
        this.evidence = java.util.Objects.requireNonNull(evidence);
    }

    Execution execute(Household household, TaxHouseholdContext context, LocalDate firstReceipt,
            EffectiveHouseholdDeathView death, FilingStatus filing, GovernmentRules rules,
            HouseholdSocialSecurityResult socialSecurity, WithdrawalBreakdown withdrawals,
            BigDecimal openingRmd, List<IncomeSourceAttribution> supplemental, ArkansasAnnualFunding.Cash cash,
            ProjectedPortfolio portfolio, WithdrawalStrategy strategy, BigDecimal fixed, BigDecimal target) {
        int year = rules.getTaxYear();
        try {
            var currentOwners = context.householdOwners().stream().filter(owner -> death.isAlive(owner, year))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            var taxContext = new TaxHouseholdContext(firstReceipt, context.birthDates(), context.householdOwners(),
                    currentOwners, context.pensionIncome());
            var deaths = new EnumMap<AccountOwnership, LocalDate>(AccountOwnership.class);
            for (var owner : context.householdOwners()) {
                death.deathDate(owner).filter(date -> date.getYear() <= year).ifPresent(date -> deaths.put(owner, date));
            }
            var pensions = context.pensionIncome().map(p -> p.sources()).orElse(List.of());
            var dated = evidence.forYear(year, pensions);
            var sources = new ArrayList<>(supplemental);
            sources.addAll(dated.investments());
            var annual = ArkansasAnnualFunding.captureIncome(taxContext, filing, rules, household, socialSecurity,
                    withdrawals, openingRmd, dated.taxableInvestments(), sources);
            var taxEvidence = new ArkansasTaxEvidence(dated.receipts(), deaths);
            var facts = new ArkansasAnnualFunding.Facts(annual, taxEvidence,
                    Optional.empty(), dated.additionalTax(), cash);

            var targeted = target == null ? null : roth.targeted(facts, portfolio, strategy, target);
            if (targeted != null && targeted.status() == ArkansasRothFunding.TargetStatus.UNSUPPORTED) {
                throw unavailableForTrial(year, ArkansasProjectionUnavailableException.Reason.TARGET_PROOF, targeted.detail(),
                        household, firstReceipt, death, pensions, taxEvidence);
            }
            // An exhausted target room preserves the established engine's no-conversion policy.
            var trial = targeted == null ? roth.fixed(facts, portfolio, strategy, fixed)
                    : targeted.trial().orElseGet(() -> roth.fixed(facts, portfolio, strategy, ZERO));
            var funded = trial.funding();
            if (funded.status() == ArkansasMinimumFundingSolver.Status.INSUFFICIENT_FUNDS) {
                var terminal = ArkansasAnnualFunding.priceTerminal(facts, trial.conversion(), strategy);
                if (terminal.deficit().signum() <= 0) {
                    throw unavailable(year, ArkansasProjectionUnavailableException.Reason.FUNDING_PROOF,
                            "Terminal allocation disagrees with the proved shortage.");
                }
                throw FundingConstraint.insufficient("Insufficient Arkansas tax-funding assets.",
                        FundingFailure.Stage.WITHDRAWAL_ALLOCATION, terminal.spendable().add(terminal.deficit()),
                        terminal.deficit(), null);
            }
            if (funded.status() == ArkansasMinimumFundingSolver.Status.UNSUPPORTED) {
                var reason = funded.reason() == ArkansasMinimumFundingSolver.Reason.SOURCE_OR_TAX
                        ? ArkansasProjectionUnavailableException.Reason.SOURCE_OR_TAX
                        : ArkansasProjectionUnavailableException.Reason.FUNDING_PROOF;
                throw unavailableForTrial(year, reason, funded.detail(), household, firstReceipt, death, pensions, taxEvidence);
            }
            var candidate = funded.candidate().orElseThrow();
            var disclosure = new ArkansasValidationTax(candidate.stateTax(), filing, candidate.taxableFundingIncome(),
                    funded.metrics(), Optional.ofNullable(targeted).map(ArkansasRothFunding.TargetResult::metrics),
                    ArkansasValidationTax.reviewedVersions(),
                    ArkansasAnnualProvenance.capture(candidate, taxEvidence, dated.additionalTax(), target));
            return new Execution(trial, candidate, disclosure);
        } catch (IllegalArgumentException | NiitUnsupportedInputException ex) {
            throw unavailable(year, ArkansasProjectionUnavailableException.Reason.SOURCE_OR_TAX, ex.getMessage());
        }
    }

    private static ArkansasProjectionUnavailableException unavailable(int year,
            ArkansasProjectionUnavailableException.Reason reason, String detail) {
        return new ArkansasProjectionUnavailableException(year, reason, detail);
    }

    private static ArkansasProjectionUnavailableException unavailableForTrial(int year,
            ArkansasProjectionUnavailableException.Reason reason, String detail,
            Household household, LocalDate date, EffectiveHouseholdDeathView death,
            List<IncomeSourceAttribution> pensions, ArkansasTaxEvidence evidence) {
        var review = detail.contains(ArkansasProductionTaxCalculator.UNRESOLVED_PENSION_ORIGIN)
                ? ArkansasPensionPreflight.pensionsToReview(household, date, death, pensions, evidence) : List.<ArkansasPensionPreflight.Review>of();
        return new ArkansasProjectionUnavailableException(year, reason, detail, review);
    }
}
