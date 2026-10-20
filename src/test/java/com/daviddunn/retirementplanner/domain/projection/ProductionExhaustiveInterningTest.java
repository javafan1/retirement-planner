package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionExhaustiveInterningTest {
    @ParameterizedTest @ValueSource(ints = {1024, 1070, 4096, 4097})
    void internedDecimalsRetainExactScaleAndRejectOverLimit(int length) throws Exception {
        var payload = ExhaustiveSnapshotTest.payload(ExhaustiveSnapshotTest.actualResult());
        var fields = com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics.class.getRecordComponents();
        var metric = payload.entries().getFirst().differences();
        var value = new BigDecimal("1234." + "1".repeat(length - 5));
        var changed = ExhaustiveSnapshotTest.replace(metric, fields[0].getName(), value);
        var entries = new ArrayList<>(payload.entries());
        entries.set(0, ExhaustiveSnapshotTest.replace(entries.getFirst(), "differences", changed));
        var snapshot = new ExhaustiveSnapshotTest().envelope(ExhaustiveSnapshotTest.replace(payload, "entries", entries));
        var codec = new AnalysisSnapshotCodec();
        if (length > AnalysisSnapshotCodec.MAX_NUMBER_LENGTH) {
            assertThrows(java.io.IOException.class, () -> codec.encode(snapshot));
        } else {
            var copy = codec.decode(codec.encode(snapshot));
            assertEquals(snapshot, copy);
            var exact = fields[0].getAccessor().invoke(((ExhaustiveSnapshot) copy.payload()).entries().getFirst().differences());
            assertEquals(value, exact);
        }
    }

    @ParameterizedTest @EnumSource(StateTaxJurisdiction.class)
    void optimizedPayloadPreservesActualStateAccountAndAttributionGraphs(StateTaxJurisdiction state) throws Exception {
        var plan = state == StateTaxJurisdiction.PA || state == StateTaxJurisdiction.IA
                ? PennsylvaniaProductionProjectionTest.plan(true, state) : FloridaProductionProjectionTest.plan(true, state);
        if (state == StateTaxJurisdiction.FL) {
            var inherited = new InheritedAccountInformation(LocalDate.of(1930, 1, 1),
                    LocalDate.of(2018, 1, 1), BeneficiaryRelationship.CHILD);
            for (var type : AccountFactory.getSupportedTypes()) {
                Account account = type == AccountType.INHERITED_ROTH_IRA || type == AccountType.INHERITED_TRADITIONAL_IRA
                        ? AccountFactory.createInherited(type, "Equal-looking account", AccountOwnership.PRIMARY, new BigDecimal("1000000.0000"), inherited)
                        : AccountFactory.create(type, "Equal-looking account", AccountOwnership.PRIMARY, new BigDecimal("1000000.0000"));
                if (type.isSubjectToOwnerRmd()) account.setOpeningRmdAccountData(new OpeningRmdAccountData(
                        2027, new BigDecimal("1100000.0000"), new BigDecimal("500.00")));
                if (account.supportsRetirementParticipantFacts()) account.setRetirementParticipantFacts(new RetirementParticipantFacts(
                        RetirementParticipantFacts.SeparationStatus.SEPARATED, LocalDate.of(2020, 1, 1),
                        RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2020, 1, 2)));
                plan.getAccountPortfolio().addAccount(account);
            }
            // Equal attributes do not mean equal mutable account identity.
            for (int i = 0; i < 2; i++) {
                var account = AccountFactory.create(AccountType.TRADITIONAL_IRA, "Identical", AccountOwnership.PRIMARY, new BigDecimal("1000000.0000"));
                account.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, new BigDecimal("1100000.0000"), new BigDecimal("500.00")));
                plan.getAccountPortfolio().addAccount(account);
            }
        }
        var baseline = new IntegratedSocialSecurityStrategyEvaluator().evaluateCurrentStrategy(plan);
        var entry = new IntegratedSocialSecurityCompleteStrategySearchEntry(1, baseline.evaluatedStrategy(), OptionalInt.of(1),
                Optional.of(baseline.metrics()), Optional.of(ExhaustiveSnapshotTest.differences(baseline.metrics(), baseline.metrics())),
                Optional.of(baseline), Optional.empty());
        var result = new IntegratedSocialSecurityCompleteStrategySearchResult(baseline, IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,
                List.of(entry), List.of(entry), OptionalInt.of(1));
        var envelope = new ExhaustiveSnapshotTest().envelope(ExhaustiveSnapshotTest.payload(result));
        var codec = new AnalysisSnapshotCodec();
        var decoded = codec.decode(codec.encode(envelope));
        assertEquals(envelope, decoded);
        var restored = ((ExhaustiveSnapshot) decoded.payload()).restore();
        RetainedProjectionCodecTest.assertGraph(baseline, restored.currentPlanBaseline(), new IdentityHashMap<>(), "baseline");
        RetainedProjectionCodecTest.assertGraph(baseline, restored.entries().getFirst().retainedDetail().orElseThrow(), new IdentityHashMap<>(), "detail");
        var accounts = restored.currentPlanBaseline().projection().getFirstYear().getEndingAccountSnapshots();
        assertNotSame(accounts.getFirst().getAccount(), restored.entries().getFirst().retainedDetail().orElseThrow()
                .projection().getFirstYear().getEndingAccountSnapshots().getFirst().getAccount());
        if (state == StateTaxJurisdiction.FL) {
            var last = accounts.size() - 1;
            assertNotSame(accounts.get(last).getAccount(), accounts.get(last - 1).getAccount());
            assertEquals(accounts.get(last).getAccount().getCurrentBalance(), accounts.get(last - 1).getAccount().getCurrentBalance());
        }
    }

    @Test void legacyExhaustiveBytesRemainReadableWithUnchangedLogicalDigest() throws Exception {
        var snapshot = new ExhaustiveSnapshotTest().envelope(ExhaustiveSnapshotTest.payload(ExhaustiveSnapshotTest.actualResult()));
        var mapper = ExhaustiveCapacityInvestigationTest.mapper();
        byte[] old = mapper.writeValueAsBytes(snapshot);
        var codec = new AnalysisSnapshotCodec();
        var copy = codec.decode(ExhaustiveCapacityInvestigationTest.gzip(old));
        assertEquals(snapshot, copy);
        assertArrayEquals(old, mapper.writeValueAsBytes(copy));
        assertEquals(snapshot.payloadDigest(), copy.payloadDigest());
        byte[] optimized = codec.raw(copy);
        assertEquals(2, mapper.readTree(optimized).path("payload").path("exhaustiveRepresentation").intValue());
        assertThrows(java.io.IOException.class, () -> codec.decode(ExhaustiveCapacityInvestigationTest.gzip(
                (new String(optimized, java.nio.charset.StandardCharsets.UTF_8) + " {}").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var tampered = mapper.readTree(optimized);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tampered.path("payload").path("tables").path("metrics").get(0))
                .put("afterTaxEstate", new BigDecimal("1.2345678900"));
        assertThrows(java.io.IOException.class, () -> codec.decode(ExhaustiveCapacityInvestigationTest.gzip(mapper.writeValueAsBytes(tampered))));
    }
}
