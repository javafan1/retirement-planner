package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/** Classpath-only default catalog, parsed/validated once; parse is also a fixture/import gate. */
public final class StateTaxDatasetRepository {

    private static final Set<FilingStatus> STATUSES = Set.of(
            FilingStatus.SINGLE, FilingStatus.MARRIED_FILING_JOINTLY);
    private static final Set<String> CODES = Set.of("CA", "MI", "FL", "IL", "PA", "NY", "CO", "NJ");
    private static final Set<String> QUALIFICATIONS = Set.of(
            "QUALIFYING_RETIREMENT_BENEFIT_ONLY",
            "QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME",
            "ELIGIBLE_PLAN_AND_RETIRED", "QUALIFYING_PRIVATE_RETIREMENT",
            "COLORADO_QUALIFYING_PENSION_ANNUITY", "COMPLETE_IRA_TO_ROTH_CONVERSION",
            "STATE_TAXABLE_CONVERSION_AMOUNT_ESTABLISHED");

    public Map<String, StateTaxRules> load() throws IOException {
        return Cache.RULES;
    }

    private static final class Cache {
        private static final Map<String, StateTaxRules> RULES = loadResource();

        private static Map<String, StateTaxRules> loadResource() {
            try (InputStream input = StateTaxDatasetRepository.class.getResourceAsStream(
                    "/tax/state/state-tax-2026.json")) {
                if (input == null) {
                    throw new IOException("Missing state tax resource");
                }
                return new StateTaxDatasetRepository().parse(input);
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }
    }

    public Map<String, StateTaxRules> parse(InputStream input) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        try {
            JsonNode root = mapper.readTree(Objects.requireNonNull(input));
            object(root, "schemaVersion datasetVersion taxYear currency model jurisdictions");
            choice(root, "schemaVersion", "1.0-draft", "1.1-validated-boundaries");
            equal(root, "currency", "USD");
            int year = integer(root, "taxYear");
            require(year == 2026, "Only the supplied 2026 snapshot is supported");
            String version = text(root, "datasetVersion");
            JsonNode model = required(root, "model");
            object(model, "name purpose calculationBasis supportedFilingStatuses jurisdictionCount "
                    + "effectiveRateOverride calculationStatusPolicy excluded");
            text(model, "name");
            text(model, "purpose");
            text(model, "calculationStatusPolicy");
            equal(model, "calculationBasis", "FULL_YEAR_RESIDENT");
            require(new HashSet<>(strings(required(model, "supportedFilingStatuses")))
                    .equals(Set.of("SINGLE", "MARRIED_FILING_JOINTLY")), "Unsupported filing status");
            require(integer(model, "jurisdictionCount") == 8, "Expected eight jurisdictions");
            strings(required(model, "excluded"));
            JsonNode override = required(model, "effectiveRateOverride");
            object(override, "storedIn method base note");
            equal(override, "storedIn", "RETIREMENT_PLAN_NOT_DATASET");
            equal(override, "method", "EFFECTIVE_RATE_OVERRIDE");
            equal(override, "base", "SIMPLIFIED_STATE_TAXABLE_INCOME");
            text(override, "note");

            Map<String, StateTaxRules> catalog = new LinkedHashMap<>();
            for (JsonNode state : array(required(root, "jurisdictions"))) {
                StateTaxRules rules = state(state, year, version);
                require(catalog.putIfAbsent(rules.jurisdiction(), rules) == null,
                        "Duplicate jurisdiction " + rules.jurisdiction());
            }
            require(catalog.keySet().equals(CODES), "Exactly the supplied eight states are required");
            return Map.copyOf(catalog);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Invalid state tax dataset: " + exception.getMessage(), exception);
        }
    }

    private StateTaxRules state(JsonNode node, int year, String version) {
        object(node, "jurisdictionCode jurisdictionName jurisdictionType taxYear calculationStatus "
                + "taxBase taxSystem deductions incomeTreatment retirementAdjustments seniorAdjustments "
                + "additionalTaxes modeling sources scenarioBoundaries");
        String code = text(node, "jurisdictionCode");
        require(CODES.contains(code), "Unknown jurisdiction");
        text(node, "jurisdictionName");
        equal(node, "jurisdictionType", "STATE");
        require(integer(node, "taxYear") == year, "Mismatched state year");
        String readiness = choice(node, "calculationStatus",
                "READY_FOR_SIMPLIFIED_MODEL", "READY_WITH_LIMITATIONS", "REVIEW_REQUIRED");
        String base = choice(node, "taxBase", "NONE", "FEDERAL_AGI_WITH_ADJUSTMENTS",
                "FEDERAL_TAXABLE_INCOME_WITH_ADJUSTMENTS", "STATE_DEFINED_INCOME");
        JsonNode system = required(node, "taxSystem");
        object(system, "type rate brackets filingThreshold");
        String type = choice(system, "type", "NONE", "FLAT", "PROGRESSIVE");
        require(base.equals("NONE") == type.equals("NONE"), "Inconsistent no-tax system/base");
        BigDecimal rate = null;
        Map<FilingStatus, List<Bracket>> brackets = new EnumMap<>(FilingStatus.class);
        if (type.equals("FLAT")) {
            rate = rate(required(system, "rate"));
            absent(system, "brackets");
        } else if (type.equals("PROGRESSIVE")) {
            absent(system, "rate");
            JsonNode schedules = required(system, "brackets");
            statusKeys(schedules);
            for (FilingStatus status : STATUSES) {
                List<Bracket> schedule = new ArrayList<>();
                BigDecimal next = BigDecimal.ZERO;
                for (JsonNode bracket : array(required(schedules, status.name()))) {
                    object(bracket, "from to rate");
                    require(next != null, "Bracket after unlimited bracket");
                    BigDecimal from = money(required(bracket, "from"));
                    require(from.compareTo(next) == 0, "Bracket gap/overlap/unordered brackets");
                    JsonNode upper = requiredNullable(bracket, "to");
                    BigDecimal to = upper.isNull() ? null : money(upper);
                    require(to == null || to.compareTo(from) > 0, "Invalid bracket bounds");
                    schedule.add(new Bracket(from, to, rate(required(bracket, "rate"))));
                    next = to;
                }
                require(!schedule.isEmpty() && next == null, "Incomplete bracket coverage");
                brackets.put(status, schedule);
            }
        } else {
            absent(system, "rate");
            absent(system, "brackets");
        }
        Map<FilingStatus, BigDecimal> filing = optionalAmounts(system, "filingThreshold", false);
        require(filing.isEmpty() || base.equals("STATE_DEFINED_INCOME"), "Unspecified filing threshold income basis");
        JsonNode deductions = required(node, "deductions");
        object(deductions, "standardDeduction personalExemption");
        Map<FilingStatus, BigDecimal> standard = amounts(required(deductions, "standardDeduction"), false);
        statusKeys(required(deductions, "standardDeduction"));
        require(!base.equals("FEDERAL_TAXABLE_INCOME_WITH_ADJUSTMENTS")
                        || standard.values().stream().allMatch(value -> value.signum() == 0),
                "Federal taxable base already includes deductions; unsupported additional standard deduction");
        JsonNode exemption = required(deductions, "personalExemption");
        object(exemption, "type amount incomePhaseout federalAgiCeiling");
        String exemptionType = choice(exemption, "type", "NONE", "DEDUCTION", "CREDIT");
        Map<FilingStatus, BigDecimal> exemptions = amounts(required(exemption, "amount"), false);
        statusKeys(required(exemption, "amount"));
        if (exemptionType.equals("NONE")) {
            require(exemptions.values().stream().allMatch(value -> value.signum() == 0), "NONE exemption has amounts");
        }
        if (exemption.has("incomePhaseout")) {
            equal(exemption, "incomePhaseout", "NOT_MODELED");
        }
        Map<FilingStatus, BigDecimal> ceiling = optionalAmounts(exemption, "federalAgiCeiling", false);
        require(!exemptionType.equals("NONE") || ceiling.isEmpty(), "NONE exemption cannot have ceiling");
        JsonNode treatment = required(node, "incomeTreatment");
        object(treatment, String.join(" ", Arrays.stream(IncomeType.values()).map(Enum::name).toList()));
        Map<IncomeType, String> treatments = new EnumMap<>(IncomeType.class);
        for (IncomeType income : IncomeType.values()) {
            treatments.put(income, choice(treatment, income.name(), "TAXABLE", "EXEMPT"));
        }
        Set<String> ids = new HashSet<>();
        List<Adjustment> adjustments = new ArrayList<>();
        for (JsonNode adjustment : array(required(node, "retirementAdjustments"))) {
            adjustments.add(adjustment(adjustment, ids));
        }
        Map<String, String> groupScopes = new HashMap<>();
        for (Adjustment adjustment : adjustments) {
            require(!adjustment.coordinatedLimit() || adjustment.group() != null,
                    "Coordinated limit requires exclusion group");
            if (adjustment.coordinatedLimit()) {
                String previous = groupScopes.putIfAbsent(adjustment.group(), adjustment.scope());
                require(previous == null || previous.equals(adjustment.scope()), "Shared group scope mismatch");
            }
        }
        for (String group : groupScopes.keySet()) {
            List<Adjustment> members = adjustments.stream().filter(rule -> group.equals(rule.group())).toList();
            require(members.size() >= 2 && members.stream().allMatch(Adjustment::coordinatedLimit),
                    "Shared group must coordinate every member");
            require(members.stream().anyMatch(rule -> !rule.maximum().isEmpty() || !rule.ageBands().isEmpty()),
                    "Shared group needs a bounded allowance");
            List<Map<FilingStatus, BigDecimal>> caps = members.stream().map(Adjustment::maximum)
                    .filter(cap -> !cap.isEmpty()).toList();
            require(caps.stream().allMatch(cap -> cap.equals(caps.getFirst())), "Shared caps disagree");
        }
        List<Boundary> boundaries = new ArrayList<>();
        if (node.has("scenarioBoundaries")) {
            for (JsonNode boundary : array(required(node, "scenarioBoundaries"))) {
                object(boundary, "id stage basis trigger maximumIncome minimumAge incomeTypes pensionSources qualification reason");
                String boundaryId = id(boundary, ids);
                String stage = choice(boundary, "stage", "BEFORE_RETIREMENT", "AFTER_RETIREMENT");
                String basis = choice(boundary, "basis", "FEDERAL_AGI", "STARTING_STATE_BASE", "ADJUSTED_STATE_BASE");
                require(!basis.equals("ADJUSTED_STATE_BASE") || stage.equals("AFTER_RETIREMENT"),
                        "Adjusted base boundary requires completed exclusions");
                String trigger = choice(boundary, "trigger", "ABOVE_MAXIMUM", "PRESENT", "UNASSESSED_QUALIFICATION",
                        "BENEFIT_ELIGIBILITY_UNKNOWN", "SOCIAL_SECURITY_ALLOCATION_MISMATCH", "AGE_BELOW_MINIMUM");
                Map<FilingStatus, BigDecimal> maximum = optionalAmounts(boundary, "maximumIncome", false);
                require(!trigger.equals("ABOVE_MAXIMUM") || !maximum.isEmpty(), "Missing boundary maximum");
                BigDecimal minimumAge = boundary.has("minimumAge") ? age(required(boundary, "minimumAge")) : null;
                List<IncomeType> types = boundary.has("incomeTypes")
                        ? strings(required(boundary, "incomeTypes")).stream().map(IncomeType::valueOf).toList() : List.of();
                List<PensionSource> pensionSources = boundary.has("pensionSources")
                        ? strings(required(boundary, "pensionSources")).stream().map(PensionSource::valueOf).toList() : List.of();
                require(!trigger.equals("ABOVE_MAXIMUM") || minimumAge == null && types.isEmpty()
                        && pensionSources.isEmpty(), "Maximum boundary cannot ignore eligibility conditions");
                require(pensionSources.isEmpty() || types.equals(List.of(IncomeType.PENSION)), "Invalid boundary source reference");
                require(!trigger.equals("PRESENT") || minimumAge != null || !types.isEmpty(), "Unconditional unsupported boundary");
                require(!trigger.equals("AGE_BELOW_MINIMUM") || minimumAge != null && !types.isEmpty(),
                        "Below-age boundary requires age and income types");
                String qualification = boundary.has("qualification") ? text(boundary, "qualification") : null;
                require(trigger.equals("UNASSESSED_QUALIFICATION") == (qualification != null), "Invalid qualification boundary");
                require(qualification == null || QUALIFICATIONS.contains(qualification), "Unknown boundary qualification");
                require(qualification == null || !types.isEmpty(), "Qualification boundary requires income types");
                require(!trigger.equals("BENEFIT_ELIGIBILITY_UNKNOWN") || stage.equals("AFTER_RETIREMENT")
                        && minimumAge != null, "Benefit eligibility requires age and adjusted income");
                require(!trigger.equals("SOCIAL_SECURITY_ALLOCATION_MISMATCH") || types.equals(List.of(IncomeType.SOCIAL_SECURITY))
                        && maximum.isEmpty() && minimumAge == null, "Invalid Social Security allocation boundary");
                boundaries.add(new Boundary(boundaryId, stage, basis, trigger, maximum, minimumAge,
                        types, pensionSources, qualification, text(boundary, "reason")));
            }
        }
        List<Senior> seniors = new ArrayList<>();
        for (JsonNode senior : array(required(node, "seniorAdjustments"))) {
            object(senior, "id type scope minimumAge amount subjectToPersonalExemptionAgiCeiling");
            String id = id(senior, ids);
            equal(senior, "type", "DEDUCTION");
            equal(senior, "scope", "PER_PERSON");
            boolean subject = senior.has("subjectToPersonalExemptionAgiCeiling")
                    && bool(senior, "subjectToPersonalExemptionAgiCeiling");
            require(!subject || !ceiling.isEmpty(), "Missing referenced exemption ceiling");
            seniors.add(new Senior(id, age(required(senior, "minimumAge")),
                    money(required(senior, "amount")), subject));
        }
        List<Surcharge> surcharges = new ArrayList<>();
        for (JsonNode surcharge : array(required(node, "additionalTaxes"))) {
            object(surcharge, "id type basis threshold rate");
            String id = id(surcharge, ids);
            equal(surcharge, "type", "MARGINAL_SURCHARGE");
            equal(surcharge, "basis", "STATE_TAXABLE_INCOME");
            surcharges.add(new Surcharge(id, amounts(required(surcharge, "threshold"), false),
                    rate(required(surcharge, "rate"))));
        }
        JsonNode modeling = required(node, "modeling");
        object(modeling, "fullYearResidentOnly approximationLevel limitations");
        require(bool(modeling, "fullYearResidentOnly"), "Unsupported residency basis");
        equal(modeling, "approximationLevel", "RETIREMENT_PLANNING");
        List<String> limitations = new ArrayList<>(strings(required(modeling, "limitations")));
        if (type.equals("NONE")) {
            require(standard.values().stream().allMatch(value -> value.signum() == 0)
                            && exemptionType.equals("NONE") && adjustments.isEmpty()
                            && seniors.isEmpty() && surcharges.isEmpty() && filing.isEmpty()
                            && treatments.values().stream().allMatch(value -> value.equals("EXEMPT")),
                    "Conflicting no-tax record");
        }
        for (JsonNode adjustment : array(required(node, "retirementAdjustments"))) {
            if (adjustment.has("limitations")) {
                limitations.addAll(strings(required(adjustment, "limitations")));
            }
        }
        if (exemption.has("incomePhaseout")) {
            limitations.add("Exemption credit phaseout is explicitly NOT_MODELED.");
        }
        List<JsonNode> sources = array(required(node, "sources"));
        require(!sources.isEmpty(), "Missing research sources");
        for (JsonNode source : sources) {
            object(source, "publisher url");
            text(source, "publisher");
            require(text(source, "url").startsWith("https://"), "Invalid source URL");
        }
        return new StateTaxRules(code, year, version, readiness, base, type, rate, brackets,
                filing, standard, exemptionType, exemptions, ceiling, treatments,
                adjustments, seniors, surcharges, boundaries, limitations);
    }

    private Adjustment adjustment(JsonNode node, Set<String> ids) {
        object(node, "id type scope appliesTo qualification eligibility calculation exclusionGroup "
                + "limitations pensionSources priority preventDoubleExclusion ageBands coordinatedLimit");
        String id = id(node, ids);
        equal(node, "type", "INCOME_EXCLUSION");
        String scope = choice(node, "scope", "PER_PERSON", "HOUSEHOLD", "HOUSEHOLD_WITH_QUALIFIED_PERSON_INCOME");
        List<IncomeType> applies = strings(required(node, "appliesTo")).stream().map(IncomeType::valueOf).toList();
        require(!applies.isEmpty() && new HashSet<>(applies).size() == applies.size(), "Invalid adjustment references");
        String qualification = node.has("qualification") ? text(node, "qualification") : null;
        require(qualification == null || QUALIFICATIONS.contains(qualification), "Unknown qualification rule");
        BigDecimal minimumAge = null;
        List<String> events = List.of();
        if (node.has("eligibility")) {
            JsonNode eligibility = required(node, "eligibility");
            object(eligibility, "minimumAge alternativeQualifyingEvents ageTest incomeBasis federalAgiMaximum");
            minimumAge = age(required(eligibility, "minimumAge"));
            if (eligibility.has("alternativeQualifyingEvents")) {
                events = strings(required(eligibility, "alternativeQualifyingEvents"));
                require(Set.of("DEATH", "DISABILITY").containsAll(events), "Unknown alternative event");
            }
            if (eligibility.has("ageTest")) {
                equal(eligibility, "ageTest", "EITHER_SPOUSE");
                require(scope.equals("HOUSEHOLD_WITH_QUALIFIED_PERSON_INCOME"), "Unsupported age test scope");
            }
            if (eligibility.has("incomeBasis")) {
                equal(eligibility, "incomeBasis", "NJ_TOTAL_INCOME");
            }
        }
        List<PensionSource> sources = node.has("pensionSources")
                ? strings(required(node, "pensionSources")).stream().map(PensionSource::valueOf).toList() : List.of();
        require(sources.isEmpty() || applies.equals(List.of(IncomeType.PENSION)), "Invalid pension source reference");
        String group = node.has("exclusionGroup") ? text(node, "exclusionGroup") : null;
        int priority = node.has("priority") ? integer(node, "priority") : Integer.MAX_VALUE;
        require(priority >= 0, "Negative priority");
        if (node.has("preventDoubleExclusion")) {
            require(bool(node, "preventDoubleExclusion"), "Overlapping exclusions cannot be enabled");
        }
        if (node.has("limitations")) {
            strings(required(node, "limitations"));
        }
        JsonNode calculation = required(node, "calculation");
        object(calculation, "method maximumAmount bands incomeBandEndpoints aboveFinalBand");
        String method = choice(calculation, "method", "CAPPED_ELIGIBLE_INCOME", "UNLIMITED_ELIGIBLE_INCOME",
                "AGE_BAND_CAPPED_ELIGIBLE_INCOME", "UNLIMITED_FEDERALLY_TAXABLE_SOCIAL_SECURITY", "STEPPED_INCOME_EXCLUSION");
        Map<FilingStatus, BigDecimal> maximum = Map.of();
        List<Band> ages = List.of();
        List<Band> bands = List.of();
        if (method.equals("CAPPED_ELIGIBLE_INCOME")) {
            maximum = amounts(required(calculation, "maximumAmount"), false);
        } else {
            absent(calculation, "maximumAmount");
        }
        if (method.equals("AGE_BAND_CAPPED_ELIGIBLE_INCOME")) {
            require(scope.equals("PER_PERSON"), "Age bands require person scope");
            ages = ageBands(required(node, "ageBands"));
        } else {
            absent(node, "ageBands");
        }
        if (method.equals("STEPPED_INCOME_EXCLUSION")) {
            require(scope.equals("HOUSEHOLD_WITH_QUALIFIED_PERSON_INCOME") && minimumAge != null,
                    "Stepped exclusion requires age-qualified person scope");
            require(node.has("eligibility") && node.get("eligibility").has("incomeBasis"), "Missing income-band basis");
            bands = incomeBands(required(calculation, "bands"));
        } else {
            absent(calculation, "bands");
        }
        if (method.equals("UNLIMITED_FEDERALLY_TAXABLE_SOCIAL_SECURITY")) {
            require(applies.equals(List.of(IncomeType.SOCIAL_SECURITY)), "Invalid SS adjustment reference");
        }
        Map<FilingStatus, BigDecimal> agiMaximum = node.has("eligibility")
                ? optionalAmounts(node.get("eligibility"), "federalAgiMaximum", false) : Map.of();
        require(agiMaximum.isEmpty() || method.equals("UNLIMITED_FEDERALLY_TAXABLE_SOCIAL_SECURITY"),
                "AGI eligibility ceiling only supported for full Social Security subtraction");
        boolean inclusive = calculation.has("incomeBandEndpoints");
        boolean zeroAbove = calculation.has("aboveFinalBand");
        require(!inclusive && !zeroAbove || method.equals("STEPPED_INCOME_EXCLUSION"), "Income band policy without bands");
        if (inclusive) {
            equal(calculation, "incomeBandEndpoints", "UPPER_INCLUSIVE");
        }
        if (zeroAbove) {
            equal(calculation, "aboveFinalBand", "NO_EXCLUSION");
        }
        boolean coordinated = node.has("coordinatedLimit") && bool(node, "coordinatedLimit");
        return new Adjustment(id, scope, applies, qualification, minimumAge, events, sources,
                method, maximum, ages, bands, group, priority, coordinated, agiMaximum, inclusive, zeroAbove);
    }

    private List<Band> ageBands(JsonNode node) {
        List<Band> bands = new ArrayList<>();
        BigDecimal previous = null;
        boolean unlimited = false;
        for (JsonNode band : array(node)) {
            object(band, "minimumAge maximumAge maximumExclusion");
            require(!unlimited, "Age band after open end");
            BigDecimal from = age(required(band, "minimumAge"));
            JsonNode upper = requiredNullable(band, "maximumAge");
            BigDecimal to = upper.isNull() ? null : age(upper);
            require(from.stripTrailingZeros().scale() <= 0 && (to == null || to.stripTrailingZeros().scale() <= 0),
                    "Age bands express completed whole ages");
            require(to == null || to.compareTo(from) >= 0, "Invalid age band");
            require(previous == null || from.compareTo(previous.add(BigDecimal.ONE)) == 0,
                    "Age bands overlap or have missing whole ages");
            bands.add(new Band(from, to, "CAPPED_ELIGIBLE_INCOME",
                    amounts(required(band, "maximumExclusion"), false)));
            previous = to;
            unlimited = to == null;
        }
        require(!bands.isEmpty() && unlimited, "Incomplete age band coverage");
        return bands;
    }

    private List<Band> incomeBands(JsonNode node) {
        List<Band> bands = new ArrayList<>();
        BigDecimal next = BigDecimal.ZERO;
        for (JsonNode band : array(node)) {
            object(band, "incomeFrom incomeTo method maximumAmount percentage");
            BigDecimal from = money(required(band, "incomeFrom"));
            BigDecimal to = money(required(band, "incomeTo"));
            require(from.compareTo(next) == 0 && to.compareTo(from) > 0, "Income band gap/overlap/order");
            String method = choice(band, "method", "CAPPED_ELIGIBLE_INCOME", "PERCENT_OF_ELIGIBLE_INCOME");
            boolean percentage = method.equals("PERCENT_OF_ELIGIBLE_INCOME");
            String key = percentage ? "percentage" : "maximumAmount";
            absent(band, percentage ? "maximumAmount" : "percentage");
            bands.add(new Band(from, to, method, amounts(required(band, key), percentage)));
            next = to;
        }
        require(!bands.isEmpty(), "Missing income bands");
        return bands;
    }

    private static Map<FilingStatus, BigDecimal> optionalAmounts(JsonNode node, String key, boolean rates) {
        return node.has(key) ? amounts(required(node, key), rates) : Map.of();
    }

    private static Map<FilingStatus, BigDecimal> amounts(JsonNode node, boolean rates) {
        Map<FilingStatus, BigDecimal> values = new EnumMap<>(FilingStatus.class);
        if (!node.isNumber()) {
            statusKeys(node);
        }
        for (FilingStatus status : STATUSES) {
            JsonNode value = node.isNumber() ? node : required(node, status.name());
            values.put(status, rates ? rate(value) : money(value));
        }
        return values;
    }

    private static void statusKeys(JsonNode node) {
        object(node, "SINGLE MARRIED_FILING_JOINTLY");
        require(node.size() == 2, "Missing filing status amounts/schedules");
    }

    private static String id(JsonNode node, Set<String> ids) {
        String id = text(node, "id");
        require(ids.add(id), "Duplicate rule id " + id);
        return id;
    }

    private static void object(JsonNode node, String allowed) {
        require(node != null && node.isObject(), "Expected object");
        Set<String> keys = Set.of(allowed.split(" "));
        node.fieldNames().forEachRemaining(key -> require(keys.contains(key), "Unknown field " + key));
    }

    private static JsonNode requiredNullable(JsonNode node, String key) {
        require(node.has(key), "Missing " + key);
        return node.get(key);
    }

    private static JsonNode required(JsonNode node, String key) {
        JsonNode value = requiredNullable(node, key);
        require(!value.isNull(), "Null " + key);
        return value;
    }

    private static void absent(JsonNode node, String key) {
        require(!node.has(key), "Unexpected " + key);
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = required(node, key);
        require(value.isTextual() && !value.textValue().isBlank(), "Invalid text " + key);
        return value.textValue();
    }

    private static String choice(JsonNode node, String key, String... choices) {
        String value = text(node, key);
        require(Set.of(choices).contains(value), "Unsupported " + key + ": " + value);
        return value;
    }

    private static void equal(JsonNode node, String key, String expected) {
        require(text(node, key).equals(expected), "Unsupported " + key);
    }

    private static int integer(JsonNode node, String key) {
        JsonNode value = required(node, key);
        require(value.isIntegralNumber() && value.canConvertToInt(), "Invalid integer " + key);
        return value.intValue();
    }

    private static boolean bool(JsonNode node, String key) {
        JsonNode value = required(node, key);
        require(value.isBoolean(), "Invalid boolean " + key);
        return value.booleanValue();
    }

    private static BigDecimal money(JsonNode value) {
        require(value.isNumber(), "Invalid decimal");
        BigDecimal amount = value.decimalValue();
        require(amount.signum() >= 0, "Negative amount");
        return amount;
    }

    private static BigDecimal rate(JsonNode value) {
        BigDecimal amount = money(value);
        require(amount.compareTo(BigDecimal.ONE) <= 0, "Invalid rate/percentage");
        return amount;
    }

    private static BigDecimal age(JsonNode value) {
        BigDecimal age = money(value);
        require(age.compareTo(new BigDecimal("130")) <= 0, "Invalid age");
        return age;
    }

    private static List<JsonNode> array(JsonNode node) {
        require(node.isArray(), "Expected array");
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array(node)) {
            require(value.isTextual() && !value.textValue().isBlank(), "Invalid string list");
            values.add(value.textValue());
        }
        require(new HashSet<>(values).size() == values.size(), "Duplicate string-list value");
        return values;
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
