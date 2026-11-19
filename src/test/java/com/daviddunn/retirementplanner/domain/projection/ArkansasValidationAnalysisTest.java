package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.ArkansasValidationReport;
import com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter;
import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyEvaluator;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasValidationAnalysisTest {
    @Test
    void frozenValidationExportsRetainAmountsAndExplicitNonproductionIdentity(@TempDir Path output) throws Exception {
        var plan = surplusPlan("1000000", 1);
        var projection = validationEngine().project(plan);
        var frozen = ArkansasValidationReport.capture(projection);
        var csv = output.resolve("validation.csv");
        var pdf = output.resolve("validation.pdf");
        new ProjectionCsvExporter().export(projection, List.of(), csv);
        new ProjectionPdfExporter().export(plan, projection, List.of(), null, pdf);
        var csvBefore = Files.readString(csv);
        assertTrue(csvBefore.contains("Arkansas validation only"));
        assertTrue(csvBefore.contains("16974.00"));
        assertTrue(csvBefore.matches("(?s).*16974.00,16974.00,0,1.3400.*"));
        assertTrue(csvBefore.contains("arkansas-income-tax-rules"));
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            var content = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(content.contains("Retained from tax-funding surplus: $1.34"), content);
            assertTrue(content.contains("P_STATUTORY_CENTS"));
            assertTrue(content.contains("Executed Roth conversion: $0.00"));
        }
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(b("7777777"));
        frozen.writeCsv(csv);
        assertEquals(csvBefore, Files.readString(csv));
        var versions = frozen.years().getFirst().state();
        assertTrue(versions.currentFor(ArkansasValidationTax.reviewedVersions()));
        var changed = new HashMap<>(versions.versions());
        changed.put("arkansas-roth-target", "different-version");
        assertFalse(versions.currentFor(changed));
        assertThrows(IllegalArgumentException.class, () -> RetainedProjectionSnapshot.capture(projection));
        var historical = retainedRoundTrip(projection);
        long exportStarted = System.nanoTime();
        new ProjectionCsvExporter().export(historical, List.of(), csv);
        assertEquals(csvBefore.replace("Arkansas validation only", "Arkansas retained history"), Files.readString(csv));
        new ProjectionPdfExporter().export(plan, historical, List.of(), null, pdf);
        System.out.println("Arkansas historical PDF and CSV us=" + (System.nanoTime() - exportStarted) / 1000);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            var content = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(content.contains("Arkansas retained history"));
            assertTrue(content.contains("Retained from tax-funding surplus: $1.34"));
            assertTrue(content.contains("Arkansas retirement exclusion: $6000.00"));
        }
    }

    @Test
    void twoControlledWorldsPreserveRealShortfallAndUnsupportedWorldAborts() {
        var plan = surplusPlan("106000", 1);
        var engine = validationEngine();
        var baseline = engine.project(plan);
        var settings = new MonteCarloSettings(2, 41L, ZERO, ZERO);
        var analyzer = new MonteCarloAnalyzer(engine);
        long start = System.nanoTime();
        var run = analyzer.analyze(plan, settings, baseline,
                index -> ProjectionEconomicPath.constant(index == 0 ? b(".04") : b("-.02")));
        assertEquals(2, run.outcomes().size());
        assertTrue(run.outcomes().stream().noneMatch(o -> o.fundingFailure().isPresent()));
        var down = analyzer.analyze(surplusPlan("104000", 1), new MonteCarloSettings(1, 42L, ZERO, ZERO),
                engine.project(surplusPlan("104000", 1)), i -> ProjectionEconomicPath.constant(b("-.02")));
        assertTrue(down.outcomes().getFirst().fundingFailure().isPresent());
        assertTrue(down.endingInvestableAssets().isEmpty());
        System.out.println("Arkansas validation small Monte Carlo paths us=" + (System.nanoTime() - start) / 1000);

        var early = single(LocalDate.of(1980, 1, 1), OPENING, 1, TAXABLE_FIRST, "30000");
        early.getAccountPortfolio().addAccount(new CheckingAccount("Limited cash", PRIMARY, b("5200")));
        var interest = interestEngine("50000");
        var reference = interest.project(early);
        var failure = assertThrows(MonteCarloExecutionException.class, () ->
                new MonteCarloAnalyzer(interest).analyze(early, settings, reference,
                        index -> ProjectionEconomicPath.constant(index == 0 ? b(".04") : b("-.02"))));
        assertInstanceOf(ArkansasProjectionUnavailableException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("5329"));
        money("5200", early.getAccountPortfolio().getAccounts().getLast().getCurrentBalance());
    }

    @Test
    void pairedMonteCarloUsesMatchedPathsAndIndependentOwnerPortfolios(@TempDir Path output) throws Exception {
        var original = couple(OPENING, 1, DeathScenario.BOTH_SURVIVE);
        var alternative = new RetirementPlanScenarioCopyService().copy(original);
        var accounts = alternative.getAccountPortfolio();
        var first = accounts.getAccounts().getFirst();
        accounts.removeAccount(first);
        accounts.addAccount(first);
        var settings = new MonteCarloSettings(2, 2026L, ZERO, ZERO);
        var fixed = new MonteCarloStrategyComparisonRequest.Fixed(settings, OPENING, 2026,
                HouseholdLifetimeScenario.bothSurvive());
        var request = new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("Primary first", original),
                new MonteCarloStrategyCandidate("Spouse first", alternative), fixed);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var engine = interestEngine("80000");
        var owner = java.util.UUID.randomUUID();
        var started = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var completed = started.plusSeconds(1);
        var a = CompleteProjectionSnapshot.captureArkansas(owner, original, engine.project(original), started);
        var b = CompleteProjectionSnapshot.captureArkansas(owner, alternative, engine.project(alternative), started);
        var kind = AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_FIXED;
        long pairedStarted = System.nanoTime();
        var captured = ArkansasAnalysisProvenance.capture(engine, kind, List.of(a, b), () -> {
            var run = new MonteCarloStrategyComparisonAnalyzer(engine).analyzeComparison(request, index -> {
                calls.incrementAndGet();
                return new MonteCarloComparisonWorld(index,
                        ProjectionEconomicPath.constant(index == 0 ? b(".04") : b("-.02")),
                        HouseholdLifetimeScenario.bothSurvive(), Optional.empty());
            }, com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                    com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
            assertEquals(2, run.outcomes().size());
            return MonteCarloSnapshotMapping.comparison(run,
                    new MonteCarloSnapshot.Display(null, "Primary", "Spouse", List.of(),
                            new MonteCarloSnapshot.Chart(List.of(), List.of(), List.of(), List.of()), 2026, 2026, false, 0, "Two shared worlds."),
                    started, completed, "a".repeat(64));
        });
        System.out.println("Arkansas controlled paired worlds and provenance us=" + (System.nanoTime() - pairedStarted) / 1000);
        var payload = (MonteCarloSnapshot) captured.payload();
        var fingerprint = new MonteCarloFingerprintService().storedFingerprint(payload);
        var completion = new AcceptedAnalysisCompletion(owner, output.resolve("paired.json"), kind, java.util.UUID.randomUUID(),
                completed, fingerprint.fingerprint(), fingerprint.contextFingerprint(), AnalysisVersions.calculatedAlgorithms(kind, payload),
                AnalysisVersions.references(kind), payload,
                new SnapshotContext("Couple", "Primary", "Spouse", started, List.of(), List.of(), List.of(), java.util.Map.of()))
                .withArkansasExecution(captured);
        var codec = new com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec();
        var snapshot = codec.decode(codec.encode(completion.snapshot(codec)));
        assertEquals(List.of(a.id(), b.id()), snapshot.arkansasExecution().dependencies().stream()
                .map(ArkansasAnalysisProvenance.Dependency::projectionId).toList());
        snapshot.arkansasExecution().validateDependency(a);
        snapshot.arkansasExecution().validateDependency(b);
        var association = new com.daviddunn.retirementplanner.app.projection.AnalyzerAssociation(2, owner, a.id(), a.inputDigest(),
                kind, snapshot.runId(), snapshot.inputFingerprint(), snapshot.contextFingerprint(), snapshot.payloadDigest(),
                "0".repeat(64), completed, b.id());
        association.validate(snapshot);
        var originalRole = new com.daviddunn.retirementplanner.app.projection.ProjectionRoleManifest.Reference(a.id(), "1".repeat(64));
        var alternativeRole = new com.daviddunn.retirementplanner.app.projection.ProjectionRoleManifest.Reference(b.id(), "2".repeat(64));
        var manifest = new com.daviddunn.retirementplanner.app.projection.ProjectionRoleManifest(2, owner,
                originalRole, alternativeRole, List.of(association));
        assertTrue(manifest.withRoles(owner, originalRole, null).pinnedProjections().contains(alternativeRole));
        original.assignPlanId(owner);
        original.setBaseline(new com.daviddunn.retirementplanner.domain.baseline.ProjectionBaseline(
                b.restoreInputs(), java.time.LocalDateTime.of(2026, 1, 1, 0, 0), "Original alternative", b.id()));
        original.installProjectionComponents(java.util.Map.of(a.id(), a, b.id(), b));
        original.installAssociatedAnalyzerResults(java.util.Map.of(snapshot.runId(), snapshot));
        original.setProjectionState(manifest);
        var repository = new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository();
        var file = output.resolve("paired.json");
        long savedAt = System.nanoTime();
        repository.saveWithIdentity(original, file, owner);
        long reloadedAt = System.nanoTime();
        var reopened = repository.load(file);
        var reloadedManifest = reopened.getProjectionState();
        var components = new com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents();
        var savedAssociation = reloadedManifest.associations().getFirst();
        var saved = components.readAnalyzer(file, savedAssociation);
        components.validateArkansasAnalyzer(saved, a, components.load(file, reloadedManifest));
        System.out.println("Arkansas paired history saveUs=" + (reloadedAt - savedAt) / 1000
                + " reloadAndVerifyUs=" + (System.nanoTime() - reloadedAt) / 1000);
        assertEquals(b.id(), savedAssociation.alternativeProjectionId());
        assertEquals(snapshot.arkansasExecution(), saved.arkansasExecution());
        assertEquals(payload, saved.payload());
        assertEquals(2, calls.get());
        money("250000", original.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
        money("250000", alternative.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
    }

    @Test
    void singleAndCoupleIntegratedClaimingUseValidationEngineAndDoNotMutateInputs() {
        var single = single(LocalDate.of(1960, 1, 1), OPENING, 2, TAXABLE_FIRST, "400000");
        var couple = couple(OPENING, 2, DeathScenario.BOTH_SURVIVE);
        for (var plan : List.of(single, couple)) {
            var primary = plan.getHousehold().getPrimaryPerson();
            primary.addIncomeSource(new SocialSecurityIncome("Primary SS", PRIMARY,
                    primary.getBirthDate().plusYears(62), null, b("3000"), 62, ZERO, 2026));
            if (plan.getHousehold().hasSpouse()) {
                var spouse = plan.getHousehold().getSpouse();
                spouse.addIncomeSource(new SocialSecurityIncome("Spouse SS", SPOUSE,
                        spouse.getBirthDate().plusYears(62), null, b("1500"), 62, ZERO, 2026));
            }
            var evaluator = new IntegratedSocialSecurityStrategyEvaluator(interestEngine("80000"));
            var inputs = CompleteProjectionSnapshot.freeze(plan);
            var result = evaluator.evaluateCurrentStrategy(plan);
            for (var year : result.projection().getYears()) {
                money(b("80000").add(year.arkansasValidationTax().orElseThrow().taxableFundingIncome())
                        .add(year.getTaxableSocialSecurity()).toPlainString(), year.getAdjustedGrossIncome());
                cashBalances(year);
            }
            assertEquals(inputs, CompleteProjectionSnapshot.freeze(plan));
            assertThrows(IllegalArgumentException.class, () -> RetainedProjectionSnapshot.capture(result));
            assertThrows(ArkansasProjectionUnavailableException.class,
                    () -> new IntegratedSocialSecurityStrategyEvaluator(interestEngine("18000")).evaluateCurrentStrategy(plan));
        }
    }

    @Test
    void measuredThirtyYearProjectionCarriesActualRetainedAnnualValues(@TempDir Path folder) throws Exception {
        var plan = single(LocalDate.of(1953, 1, 1), OPENING, 30, TAXABLE_FIRST, "500000");
        plan.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2026, b("500000"), ZERO));
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Tax funding reserve", PRIMARY, b("1200000")));
        long start = System.nanoTime();
        var projection = interestEngine("180000").project(plan);
        assertEquals(30, projection.getYearsProjected());
        projection.getYears().forEach(ArkansasProjectionIntegrationTest::cashBalances);
        int candidates = projection.getYears().stream().mapToInt(y -> y.arkansasValidationTax().orElseThrow()
                .fundingMetrics().candidates()).sum();
        System.out.println("Arkansas validation thirty years candidates=" + candidates + " us=" + (System.nanoTime() - start) / 1000);
        var codec = new com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec();
        var retained = RetainedProjectionSnapshot.captureArkansas(projection);
        start = System.nanoTime();
        var encoded = codec.encode(retained);
        long encodeUs = (System.nanoTime() - start) / 1000;
        start = System.nanoTime();
        assertEquals(retained, codec.decode(encoded.bytes()));
        long decodeUs = (System.nanoTime() - start) / 1000;
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        long sources = 0;
        for (var year : retained.arkansas().years()) {
            sources += mapper.writeValueAsBytes(year.sourceEvidence()).length;
        }
        System.out.println("Arkansas durable thirty years rawBytes=" + encoded.rawBytes()
                + " gzipBytes=" + encoded.bytes().length + " sourceEvidenceBytes=" + sources
                + " encodeUs=" + encodeUs + " decodeUs=" + decodeUs);
        var owner = java.util.UUID.randomUUID();
        var complete = CompleteProjectionSnapshot.captureArkansas(owner, plan, projection, java.time.Instant.now());
        plan.assignPlanId(owner);
        plan.installProjectionComponents(Map.of(complete.id(), complete));
        plan.setProjectionState(new com.daviddunn.retirementplanner.app.projection.ProjectionRoleManifest(1, owner,
                new com.daviddunn.retirementplanner.app.projection.ProjectionRoleManifest.Reference(complete.id(), "0".repeat(64)), null));
        var file = folder.resolve("arkansas-thirty-year.json");
        var repo = new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository();
        start = System.nanoTime();
        repo.saveWithIdentity(plan, file, owner);
        long saveUs = (System.nanoTime() - start) / 1000;
        start = System.nanoTime();
        var reopened = repo.load(file);
        var store = new com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents();
        var restored = store.read(file, owner, reopened.getProjectionState().current());
        long loadUs = (System.nanoTime() - start) / 1000;
        assertEquals(complete, restored);
        var component = folder.resolve(".retirement-planner-projections").resolve(owner.toString())
                .resolve(complete.id() + ".json.gz");
        try (var gzip = new java.util.zip.GZIPInputStream(Files.newInputStream(component))) {
            System.out.println("Arkansas complete thirty years rawBytes=" + gzip.readAllBytes().length
                    + " gzipBytes=" + Files.size(component) + " planBytes=" + Files.size(file)
                    + " saveUs=" + saveUs + " loadUs=" + loadUs);
        }
    }

    @Test
    void alteredArkansasHistoryFailsVersionAndAccountingValidation() throws Exception {
        var codec = new com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec();
        var retained = RetainedProjectionSnapshot.captureArkansas(validationEngine().project(surplusPlan("1000000", 1)));
        String raw;
        try (var gzip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(codec.encode(retained).bytes()))) {
            raw = new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var invalid = List.of(
                raw.replace("\"jurisdiction\":\"AR\"", "\"jurisdiction\":\"MI\""),
                raw.replace("AR-2026-P2-1", "AR-UNREVIEWED"),
                raw.replace("\"arkansas-tax-funding\":\"AR-FUNDING-P2-1\"", "\"unknown-funding\":\"AR-FUNDING-P2-1\""),
                raw.replace("AR-TARGET-P3A-1", "AR-TARGET-UNKNOWN"),
                raw.replace("\"schemaVersion\":3", "\"schemaVersion\":99"),
                raw.replace("\"schemaVersion\":3", "\"schemaVersion\":3,\"schemaVersion\":3"),
                raw.replace("\"retainedFromTaxFundingSurplus\":1.3400", "\"retainedFromTaxFundingSurplus\":1.3500"),
                raw.replace("\"taxFunding\":16974.00", "\"taxFunding\":0"),
                raw.replace("\"retainedEnding\":1.3400", "\"retainedEnding\":2.6800"),
                raw.replace("\"federalTax\":", "\"missingFederalTax\":"),
                raw.replace("\"stateTax\":", "\"missingStateTax\":"),
                raw.replace("\"election\":", "\"missingElection\":"),
                raw.replace("\"sourceIndex\":0", "\"sourceIndex\":999"));
        for (var changed : invalid) {
            assertNotEquals(raw, changed);
            var encoded = new java.io.ByteArrayOutputStream();
            try (var gzip = new java.util.zip.GZIPOutputStream(encoded)) {
                gzip.write(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            assertThrows(java.io.IOException.class, () -> codec.decode(encoded.toByteArray()));
        }
    }
}
