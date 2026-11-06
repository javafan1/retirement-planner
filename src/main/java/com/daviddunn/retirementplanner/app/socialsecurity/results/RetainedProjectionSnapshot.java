package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyResult;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculation;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rules.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Lossless retained-result graph. Not an analysis payload and not connected to any analyzer. */
public record RetainedProjectionSnapshot(int schemaVersion, List<AccountData> accounts, List<Annual> years,
        ProjectionMetrics metrics, WeightedSnapshots.Strategy strategy, Boolean advancedSocialSecurityPathUsed,
        List<String> warnings) {
    public static final int SCHEMA = 1;
    public static final int AVAILABILITY_SCHEMA = 2;
    public RetainedProjectionSnapshot {
        if (schemaVersion != SCHEMA && schemaVersion != AVAILABILITY_SCHEMA)
            throw new IllegalArgumentException("Unsupported retained-projection schema.");
        accounts = List.copyOf(accounts); years = List.copyOf(years); warnings = List.copyOf(warnings);
        boolean explicitAvailability = accounts.stream().anyMatch(a -> a.rothConversionAvailability() != null);
        if ((schemaVersion == AVAILABILITY_SCHEMA) != explicitAvailability)
            throw new IllegalArgumentException("Retained account availability/schema mismatch.");
        if ((strategy == null) != (advancedSocialSecurityPathUsed == null))
            throw new IllegalArgumentException("Incomplete retained strategy context.");
        if (strategy != null && metrics == null) throw new IllegalArgumentException("Retained strategy metrics are required.");
        if (strategy != null) strategy.restore();
    }
    public static RetainedProjectionSnapshot capture(Projection projection) {
        return capture(projection, null, null, null, List.of());
    }
    public static RetainedProjectionSnapshot capture(IntegratedSocialSecurityStrategyResult result) {
        return capture(result.projection(), result.metrics(), WeightedSnapshots.Strategy.from(result.evaluatedStrategy()),
                result.advancedSocialSecurityPathUsed(), result.warnings());
    }
    private static RetainedProjectionSnapshot capture(Projection projection, ProjectionMetrics metrics,
            WeightedSnapshots.Strategy strategy, Boolean advanced, List<String> warnings) {
        var registry = new CaptureRegistry();
        for (var year : projection.getYears()) for (var balance : year.getEndingAccountSnapshots()) registry.add(balance.getAccount());
        int version = registry.accounts.stream().anyMatch(a -> a.rothConversionAvailability() != null)
                ? AVAILABILITY_SCHEMA : SCHEMA;
        return new RetainedProjectionSnapshot(version, registry.accounts,
                projection.getYears().stream().map(y -> Annual.capture(y, registry)).toList(), metrics, strategy, advanced, warnings);
    }
    public Projection restoreProjection() {
        var identities = accounts.stream().map(AccountData::restore).toList();
        var registry = new CaptureRegistry(); identities.forEach(registry::add);
        var projection = new Projection();
        for (var year : years) {
            var restored = year.restore(identities);
            // Reject constructor/version drift rather than silently substituting or rounding stored results.
            if (!year.equals(Annual.capture(restored, registry)))
                throw new IllegalArgumentException("Retained projection cannot be reconstructed exactly.");
            projection.addYear(restored);
        }
        return projection;
    }
    public IntegratedSocialSecurityStrategyResult restoreStrategyResult() {
        if (strategy == null) throw new IllegalStateException("No strategy context was captured.");
        return new IntegratedSocialSecurityStrategyResult(strategy.restore(), restoreProjection(), metrics, advancedSocialSecurityPathUsed, warnings);
    }

    public record Inherited(LocalDate birth, LocalDate death, BeneficiaryRelationship relationship) {
        static Inherited capture(InheritedAccountInformation i) {
            return i == null ? null : new Inherited(i.getOriginalOwnerDateOfBirth(), i.getOriginalOwnerDateOfDeath(), i.getBeneficiaryRelationship());
        }
        InheritedAccountInformation restore() { return new InheritedAccountInformation(birth, death, relationship); }
    }
    public record OpeningRmd(int year, BigDecimal priorBalance, BigDecimal alreadyDistributed) {
        static OpeningRmd capture(OpeningRmdAccountData d) {
            return d == null ? null : new OpeningRmd(d.getDistributionYear(), d.getPriorDecember31Balance(), d.getRmdAlreadyDistributedBeforeProjection());
        }
        OpeningRmdAccountData restore() { return new OpeningRmdAccountData(year, priorBalance, alreadyDistributed); }
    }
    private static final Map<AccountType, Class<? extends Account>> ACCOUNT_CLASSES = Map.ofEntries(
            Map.entry(AccountType.TRADITIONAL_IRA, TraditionalIRA.class), Map.entry(AccountType.ROLLOVER_IRA, RolloverIRA.class),
            Map.entry(AccountType.ROTH_IRA, RothIRA.class), Map.entry(AccountType.TRADITIONAL_401K, Traditional401K.class),
            Map.entry(AccountType.TRADITIONAL_403B, Traditional403B.class), Map.entry(AccountType.ROTH_401K, Roth401K.class),
            Map.entry(AccountType.BROKERAGE, BrokerageAccount.class), Map.entry(AccountType.CHECKING, CheckingAccount.class),
            Map.entry(AccountType.SAVINGS, SavingsAccount.class), Map.entry(AccountType.INHERITED_TRADITIONAL_IRA, InheritedTraditionalIRA.class),
            Map.entry(AccountType.INHERITED_ROTH_IRA, InheritedRothIRA.class));
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = AccountDataDecoder.class)
    public record AccountData(AccountType type, String name, AccountOwnership owner, BigDecimal currentBalance,
            OpeningRmd openingRmd, RetirementParticipantFacts participant, Inherited inherited,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            com.daviddunn.retirementplanner.domain.roth.EmployerRothConversionAvailability rothConversionAvailability) {
        public AccountData(AccountType type, String name, AccountOwnership owner, BigDecimal currentBalance,
                OpeningRmd openingRmd, RetirementParticipantFacts participant, Inherited inherited) {
            this(type, name, owner, currentBalance, openingRmd, participant, inherited, null);
        }
        public AccountData {
            Objects.requireNonNull(type); Objects.requireNonNull(name); Objects.requireNonNull(owner);
            Objects.requireNonNull(currentBalance); Objects.requireNonNull(participant);
            if (rothConversionAvailability != null
                    && !com.daviddunn.retirementplanner.domain.roth.RothConversionEligibilityPolicy.isEmployerPretaxSource(type))
                throw new IllegalArgumentException("Availability belongs only to traditional employer accounts.");
            if ((inherited != null) != (type == AccountType.INHERITED_TRADITIONAL_IRA || type == AccountType.INHERITED_ROTH_IRA))
                throw new IllegalArgumentException("Inherited account metadata mismatch.");
        }
        static AccountData capture(Account a) {
            if (a.getClass() != ACCOUNT_CLASSES.get(a.getType())) throw new IllegalArgumentException("Unsupported retained account class.");
            var inherited = switch (a) {
                case InheritedTraditionalIRA i -> i.getInheritedAccountInformation();
                case InheritedRothIRA i -> i.getInheritedAccountInformation();
                default -> null;
            };
            return new AccountData(a.getType(), a.getName(), a.getOwnership(), a.getCurrentBalance(), OpeningRmd.capture(a.getOpeningRmdAccountData()),
                    a.getRetirementParticipantFacts(), Inherited.capture(inherited), a.getRothConversionAvailability());
        }
        Account restore() {
            Account a = inherited == null ? AccountFactory.create(type, name, owner, currentBalance)
                    : AccountFactory.createInherited(type, name, owner, currentBalance, inherited.restore());
            a.setRetirementParticipantFacts(participant);
            a.setOpeningRmdAccountData(openingRmd == null ? null : openingRmd.restore());
            a.setRothConversionAvailability(rothConversionAvailability);
            return a;
        }
    }

    /** Read original tokens directly: tree round trips must not change BigDecimal precision or scale. */
    public static final class AccountDataDecoder extends com.fasterxml.jackson.databind.JsonDeserializer<AccountData> {
        @Override public AccountData deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            if (!parser.isExpectedStartObjectToken())
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Invalid retained account.");
            AccountType type = null; String name = null; AccountOwnership owner = null; BigDecimal balance = null;
            OpeningRmd opening = null; RetirementParticipantFacts participant = null; Inherited inherited = null;
            com.daviddunn.retirementplanner.domain.roth.EmployerRothConversionAvailability availability = null;
            var seen = new HashSet<String>();
            while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                String field = parser.currentName();
                if (field == null || !seen.add(field))
                    throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Duplicate/invalid retained account field.");
                parser.nextToken();
                switch (field) {
                    case "type" -> type = context.readValue(parser, AccountType.class);
                    case "name" -> name = context.readValue(parser, String.class);
                    case "owner" -> owner = context.readValue(parser, AccountOwnership.class);
                    case "currentBalance" -> balance = context.readValue(parser, BigDecimal.class);
                    case "openingRmd" -> opening = context.readValue(parser, OpeningRmd.class);
                    case "participant" -> participant = context.readValue(parser, RetirementParticipantFacts.class);
                    case "inherited" -> inherited = context.readValue(parser, Inherited.class);
                    case "rothConversionAvailability" -> {
                        if (parser.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_NULL)
                            throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Explicit retained availability cannot be null.");
                        availability = context.readValue(parser,
                                com.daviddunn.retirementplanner.domain.roth.EmployerRothConversionAvailability.class);
                    }
                    default -> throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Unknown retained account field: " + field);
                }
            }
            if (!seen.containsAll(Set.of("type", "name", "owner", "currentBalance", "openingRmd", "participant", "inherited")))
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Missing required retained account field.");
            return new AccountData(type, name, owner, balance, opening, participant, inherited, availability);
        }
    }
    public record Balance(int account, BigDecimal endingBalance) { }
    public record Reference(int account, RetirementParticipantFacts participant) {
        public Reference { Objects.requireNonNull(participant); }
        IncomeSourceAttribution.AccountReference restore(List<Account> identities) {
            return IncomeSourceAttribution.AccountReference.restore(at(identities, account), participant);
        }
    }
    private static final class CaptureRegistry {
        final List<AccountData> accounts = new ArrayList<>();
        final Map<IncomeSourceAttribution.AccountReference, Integer> references = new HashMap<>();
        void add(Account account) {
            var identity = IncomeSourceAttribution.AccountReference.capture(account);
            if (!references.containsKey(identity)) {
                references.put(identity, accounts.size()); accounts.add(AccountData.capture(account));
            }
        }
        int index(IncomeSourceAttribution.AccountReference reference) {
            var i = references.get(reference);
            if (i == null) throw new IllegalArgumentException("Attribution identity has no retained account snapshot; full account facts are unavailable.");
            return i;
        }
        Reference reference(IncomeSourceAttribution.AccountReference reference) {
            return reference == null ? null : new Reference(index(reference), reference.participantFacts());
        }
    }
    public record PensionFacts(AccountOwnership owner, BigDecimal monthly, BigDecimal survivor, BigDecimal cola,
            LocalDate start, LocalDate end, RetirementParticipantFacts participant) {
        static PensionFacts capture(IncomeSourceAttribution.PensionFacts p) {
            return p == null ? null : new PensionFacts(p.originalOwner(), p.monthlyBenefit(), p.survivorMonthlyBenefit().orElse(null),
                    p.annualColaRate(), p.startDate().orElse(null), p.endDate().orElse(null), p.participantFacts());
        }
        IncomeSourceAttribution.PensionFacts restore() {
            return new IncomeSourceAttribution.PensionFacts(owner, monthly, Optional.ofNullable(survivor), cola,
                    Optional.ofNullable(start), Optional.ofNullable(end), participant);
        }
    }
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = IncomeReader.class)
    public record Income(IncomeSourceAttribution.Kind kind, IncomeSourceAttribution.Purpose purpose, AccountOwnership owner,
            Reference account, Reference destination, String description, BigDecimal gross, BigDecimal taxable,
            Integer taxYear, PensionFacts pension,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt conditionalBenefit) {
        public Income(IncomeSourceAttribution.Kind kind, IncomeSourceAttribution.Purpose purpose, AccountOwnership owner,
                Reference account, Reference destination, String description, BigDecimal gross, BigDecimal taxable, Integer taxYear, PensionFacts pension) {
            this(kind,purpose,owner,account,destination,description,gross,taxable,taxYear,pension,null);
        }
        public Income {
            boolean conditionalKind = kind == IncomeSourceAttribution.Kind.CONDITIONAL_RECURRING || kind == IncomeSourceAttribution.Kind.CONDITIONAL_LUMP_SUM;
            if (conditionalKind != (conditionalBenefit != null)) throw new IllegalArgumentException("Missing or misplaced retained conditional receipt.");
            if (conditionalBenefit != null) {
                var expected = IncomeSourceAttribution.conditional(conditionalBenefit);
                if (kind != expected.kind() || purpose != expected.purpose() || owner != expected.owner().orElseThrow()
                        || account != null || destination != null || pension != null || !description.equals(expected.description())
                        || !gross.equals(expected.grossAmount()) || !taxable.equals(expected.modeledTaxableAmount().orElseThrow())
                        || taxYear == null || taxYear != conditionalBenefit.year())
                    throw new IllegalArgumentException("Inconsistent retained conditional receipt.");
            }
        }
        static Income capture(IncomeSourceAttribution i, CaptureRegistry registry) {
            return new Income(i.kind(), i.purpose(), i.owner().orElse(null), registry.reference(i.account().orElse(null)),
                    registry.reference(i.destination().orElse(null)), i.description(), i.grossAmount(), i.modeledTaxableAmount().orElse(null),
                    i.taxYear().isPresent() ? i.taxYear().getAsInt() : null, PensionFacts.capture(i.pension().orElse(null)), i.conditionalBenefit().orElse(null));
        }
        IncomeSourceAttribution restore(List<Account> identities) {
            return new IncomeSourceAttribution(kind, purpose, Optional.ofNullable(owner),
                    Optional.ofNullable(account).map(r -> r.restore(identities)), Optional.ofNullable(destination).map(r -> r.restore(identities)),
                    description, gross, Optional.ofNullable(taxable), taxYear == null ? OptionalInt.empty() : OptionalInt.of(taxYear),
                    Optional.ofNullable(pension).map(PensionFacts::restore), Optional.ofNullable(conditionalBenefit));
        }
    }

    /** Only the new, known optional receipt is defaulted. All original creator properties stay required. */
    public static final class IncomeReader extends com.fasterxml.jackson.databind.JsonDeserializer<Income> {
        private static final com.fasterxml.jackson.databind.ObjectReader TREE = new com.fasterxml.jackson.databind.ObjectMapper()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true)).reader();
        @Override public Income deserialize(com.fasterxml.jackson.core.JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context)
                throws java.io.IOException {
            com.fasterxml.jackson.databind.JsonNode node = TREE.readTree(parser);
            var required = Set.of("kind","purpose","owner","account","destination","description","gross","taxable","taxYear","pension");
            if (!node.isObject()) throw com.fasterxml.jackson.databind.JsonMappingException.from(parser,"Retained income object required.");
            var seen = new HashSet<String>();
            node.fieldNames().forEachRemaining(seen::add);
            if (!seen.containsAll(required) || seen.stream().anyMatch(key -> !required.contains(key) && !key.equals("conditionalBenefit")))
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser,"Missing or unknown retained income field.");
            return new Income(value(context,node,"kind",IncomeSourceAttribution.Kind.class), value(context,node,"purpose",IncomeSourceAttribution.Purpose.class),
                    value(context,node,"owner",AccountOwnership.class),value(context,node,"account",Reference.class),value(context,node,"destination",Reference.class),
                    value(context,node,"description",String.class),value(context,node,"gross",BigDecimal.class),value(context,node,"taxable",BigDecimal.class),
                    value(context,node,"taxYear",Integer.class),value(context,node,"pension",PensionFacts.class),
                    value(context,node,"conditionalBenefit",com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt.class));
        }
        private static <T> T value(com.fasterxml.jackson.databind.DeserializationContext context, com.fasterxml.jackson.databind.JsonNode node,
                String name, Class<T> type) throws java.io.IOException {
            return !node.hasNonNull(name) ? null : context.readTreeAsValue(node.get(name),type);
        }
    }
    public record Decision(Income source, MichiganRetirementEligibility.Qualification qualification, BigDecimal eligibleAmount, String reason) {
        static Decision capture(MichiganRetirementEligibility.Decision d, CaptureRegistry registry) {
            return new Decision(Income.capture(d.source(), registry), d.qualification(), d.eligibleAmount(), d.reason());
        }
        MichiganRetirementEligibility.Decision restore(List<Account> identities) {
            return new MichiganRetirementEligibility.Decision(source.restore(identities), qualification, eligibleAmount, reason);
        }
    }
    public record Michigan(BigDecimal retirementIncome, BigDecimal deduction, BigDecimal taxableIncome, BigDecimal incomeTax,
            BigDecimal ordinaryIncome, BigDecimal exemption, BigDecimal eligibleRetirementIncome, List<Decision> decisions, List<String> limitations) {
        public Michigan { decisions = List.copyOf(decisions); limitations = List.copyOf(limitations); }
        static Michigan capture(MichiganTaxCalculation m, CaptureRegistry registry) {
            return m == null ? null : new Michigan(m.retirementIncome(), m.retirementDeduction(), m.taxableIncome(), m.incomeTax(),
                    m.ordinaryIncome(), m.personalExemption(), m.eligibleRetirementIncome(), m.eligibilityDecisions().stream()
                    .map(d -> Decision.capture(d, registry)).toList(), m.limitations());
        }
        MichiganTaxCalculation restore(List<Account> identities) {
            return new MichiganTaxCalculation(retirementIncome, deduction, taxableIncome, incomeTax, ordinaryIncome, exemption,
                    eligibleRetirementIncome, decisions.stream().map(d -> d.restore(identities)).toList(), limitations);
        }
    }
    public record State(StateTaxJurisdiction jurisdiction, StateTaxAssumptions.CalculationMethod method,
            StateTaxCalculationStatus status, BigDecimal incomeTax, List<String> limitations, Michigan michigan,
            List<StateTaxUnavailableReason> unavailableReasons) {
        public State { limitations = List.copyOf(limitations); unavailableReasons = List.copyOf(unavailableReasons); }
        static State capture(ProductionStateTaxResult s, CaptureRegistry registry) {
            return s == null ? null : new State(s.jurisdiction(), s.calculationMethod(), s.calculationStatus(), s.estimatedIncomeTax().orElse(null),
                    s.limitations(), Michigan.capture(s.michiganDetails().orElse(null), registry), s.unavailableReasons());
        }
        ProductionStateTaxResult restore(List<Account> identities) {
            return new ProductionStateTaxResult(jurisdiction, method, status, Optional.ofNullable(incomeTax), limitations,
                    Optional.ofNullable(michigan).map(m -> m.restore(identities)), unavailableReasons);
        }
    }
    public record Irmaa(FilingStatus filingStatus, BigDecimal minimum, BigDecimal maximum, boolean minimumInclusive,
            boolean maximumInclusive, BigDecimal monthlyB, BigDecimal monthlyD) {
        static Irmaa capture(IrmaaBracket b) {
            return new Irmaa(b.getFilingStatus(), b.getMinimumModifiedAdjustedGrossIncome(), b.getMaximumModifiedAdjustedGrossIncome(),
                    b.isMinimumIncomeInclusive(), b.isMaximumIncomeInclusive(), b.getMonthlyPartBPremium(), b.getMonthlyPartDPremium());
        }
        IrmaaBracket restore() { return new IrmaaBracket(filingStatus, minimum, maximum, minimumInclusive, maximumInclusive, monthlyB, monthlyD); }
    }
    public record Medicare(BigDecimal magi, Irmaa bracket, int participants, BigDecimal monthlyB, BigDecimal annualB,
            BigDecimal monthlyD, BigDecimal annualD, BigDecimal total) {
        static Medicare capture(MedicarePremiumCalculation m) {
            return m == null ? null : new Medicare(m.modifiedAdjustedGrossIncome(), Irmaa.capture(m.irmaaBracket()), m.coveredMedicareParticipants(),
                    m.monthlyPartBPremium(), m.annualPartBPremium(), m.monthlyPartDPremium(), m.annualPartDPremium(), m.totalAnnualMedicarePremium());
        }
        MedicarePremiumCalculation restore() { return new MedicarePremiumCalculation(magi, bracket.restore(), participants, monthlyB, annualB, monthlyD, annualD, total); }
    }
    public record Annual(int index, int year, int primaryAge,
            BigDecimal beginning, BigDecimal growth, BigDecimal income, HouseholdSocialSecurityResult socialSecurity,
            BigDecimal expenses, BigDecimal cashNeed, BigDecimal withdrawal, BigDecimal rmd, BigDecimal rmdBefore,
            BigDecimal rmdDuring, BigDecimal excessRmd, BigDecimal taxFunding, BigDecimal ending, List<Balance> balances,
            BigDecimal agi, BigDecimal taxableSocialSecurity, BigDecimal federalTaxable, BigDecimal federalDeduction,
            BigDecimal federalTax, BigDecimal stateTax, BigDecimal michiganRetirementIncome, BigDecimal michiganDeduction,
            BigDecimal michiganTaxable, BigDecimal effectiveRate, State state, Medicare medicare,
            BigDecimal requestedRoth, BigDecimal roth, BigDecimal primaryRoth, BigDecimal spouseRoth,
            BigDecimal retainedBeginning, BigDecimal retainedGrowth, BigDecimal retainedEnding, HouseholdCashSettlement settlement,
            BigDecimal heirTax, BigDecimal estate, List<Income> sources) {
        public Annual { balances = List.copyOf(balances); sources = List.copyOf(sources); }
        static Annual capture(ProjectionYear y, CaptureRegistry r) {
            return new Annual(y.getProjectionYear(), y.getCalendarYear(), y.getPrimaryPersonAge(), y.getBeginningInvestableAssets(),
                    y.getInvestmentGrowth(), y.getGuaranteedIncome(), y.getSocialSecurityResult(), y.getAnnualExpenses(), y.getCashFlowNeed(),
                    y.getPortfolioWithdrawal(), y.getRequiredMinimumDistribution(), y.getRmdDistributedBeforeProjection(), y.getRmdDistributedInProjection(),
                    y.getExcessRmd(), y.getTaxFundingWithdrawal(), y.getEndingInvestableAssets(), y.getEndingAccountSnapshots().stream()
                    .map(b -> new Balance(r.index(IncomeSourceAttribution.AccountReference.capture(b.getAccount())), b.getEndingBalance())).toList(),
                    y.getAdjustedGrossIncome(), y.getTaxableSocialSecurity(), y.getFederalTaxableIncome(), y.getFederalStandardDeduction(), y.getFederalIncomeTax(),
                    y.getStateIncomeTax(), y.getMichiganRetirementIncome(), y.getMichiganRetirementDeduction(), y.getMichiganTaxableIncome(), y.getCombinedEffectiveTaxRate(),
                    State.capture(y.stateTaxCalculation().orElse(null), r), Medicare.capture(y.getMedicarePremiumCalculation()), y.getRequestedRothConversion(),
                    y.getRothConversion(), y.getPrimaryRothConversion(), y.spouseRothConversion().orElse(null), y.getBeginningRetainedNonQualifiedAssets(),
                    y.getRetainedNonQualifiedAssetGrowth(), y.getEndingRetainedNonQualifiedAssets(), y.getHouseholdCashSettlement(), y.getEstimatedHeirTax(),
                    y.getAfterTaxEstateValue(), y.getIncomeSources().stream().map(i -> Income.capture(i, r)).toList());
        }
        ProjectionYear restore(List<Account> identities) {
            var snapshots = balances.stream().map(b -> new ProjectedAccountSnapshot(at(identities, b.account()), b.endingBalance())).toList();
            if (state == null && medicare == null) {
                return new ProjectionYear(index, year, beginning, growth, income, expenses, cashNeed, withdrawal, rmd, excessRmd, ending, snapshots, primaryAge);
            }
            if (state == null || medicare == null) throw new IllegalArgumentException("Incomplete retained annual calculation.");
            var federal = new FederalTaxCalculation(agi, taxableSocialSecurity, federalDeduction, federalTaxable, federalTax);
            return new ProjectionYear(index, year, beginning, growth, income, socialSecurity, expenses, cashNeed, withdrawal, rmd,
                    rmdBefore, rmdDuring, excessRmd, retainedBeginning, retainedGrowth, retainedEnding, settlement, ending, snapshots,
                    federal, state.restore(identities), medicare.restore(), taxFunding, requestedRoth, roth, primaryRoth, spouseRoth,
                    heirTax, estate, primaryAge, sources.stream().map(i -> i.restore(identities)).toList());
        }
    }
    private static <T> T at(List<T> values, int index) {
        if (index < 0 || index >= values.size()) throw new IllegalArgumentException("Invalid retained account reference.");
        return values.get(index);
    }
}
