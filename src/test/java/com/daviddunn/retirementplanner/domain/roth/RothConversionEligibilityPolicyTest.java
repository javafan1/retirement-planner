package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.roth.RothConversionEligibilityPolicy.*;
import static com.daviddunn.retirementplanner.domain.roth.RothEligibilityExecutionTest.account;
import static org.junit.jupiter.api.Assertions.*;

class RothConversionEligibilityPolicyTest {
    private final RothConversionEligibilityPolicy policy = new RothConversionEligibilityPolicy();
    private static final Set<AccountOwnership> OWNERS = Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE);
    private static final BigDecimal BALANCE = new BigDecimal("100000.00100");

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void accountTypeSupportDoesNotDependOnBalance(AccountType type) {
        var source = account(type, AccountOwnership.PRIMARY, BigDecimal.ZERO);
        var result = policy.assessUnderLegacyEmployerAssumption(source, destination(), OWNERS, BigDecimal.ZERO);
        boolean expected = Set.of(AccountType.TRADITIONAL_IRA, AccountType.ROLLOVER_IRA,
                AccountType.TRADITIONAL_401K, AccountType.TRADITIONAL_403B).contains(type);
        assertEquals(expected, result.supportedPretaxSource());
        assertTrue(result.reasons().contains(Reason.NO_AVAILABLE_BALANCE));
        assertFalse(result.executableUnderLegacyEmployerAssumption());
        assertThrows(UnsupportedOperationException.class, () -> result.reasons().clear());
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"TRADITIONAL_401K", "TRADITIONAL_403B"})
    void unknownEmployerAvailabilityIsRetainedEvenWithAffirmativeParticipantFacts(AccountType type) {
        Account source = account(type, AccountOwnership.PRIMARY, BALANCE);
        source.setRetirementParticipantFacts(new RetirementParticipantFacts(
                RetirementParticipantFacts.SeparationStatus.SEPARATED, LocalDate.of(2000, 1, 1),
                RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2000, 1, 1)));
        var assessment = policy.assessUnderLegacyEmployerAssumption(source, destination(), OWNERS, BALANCE);
        assertEquals(EmployerDistributionAvailability.UNKNOWN, assessment.employerDistributionAvailability());
        assertEquals(Set.of(Reason.EMPLOYER_DISTRIBUTION_UNKNOWN), assessment.reasons());
        assertTrue(assessment.executableUnderLegacyEmployerAssumption());
        assertTrue(assessment.usesLegacyEmployerAssumption());
        assertEquals(BALANCE, assessment.maximumConvertibleAmount());
        assertEquals(BALANCE, source.getCurrentBalance());
    }

    @ParameterizedTest
    @EnumSource(value = EmployerDistributionAvailability.class, names = {"ESTABLISHED", "UNAVAILABLE", "UNKNOWN"})
    void explicitEmployerAvailabilityRemainsDistinct(EmployerDistributionAvailability availability) {
        var assessment = policy.assess(account(AccountType.TRADITIONAL_401K, AccountOwnership.PRIMARY, BALANCE),
                destination(), OWNERS, BALANCE, availability);
        assertEquals(availability, assessment.employerDistributionAvailability());
        assertEquals(availability != EmployerDistributionAvailability.UNAVAILABLE,
                assessment.executableUnderLegacyEmployerAssumption());
        assertEquals(availability == EmployerDistributionAvailability.UNKNOWN, assessment.usesLegacyEmployerAssumption());
        assertEquals(availability == EmployerDistributionAvailability.UNAVAILABLE ? BigDecimal.ZERO : BALANCE,
                assessment.maximumConvertibleAmount());
    }

    @Test
    void missingDestinationWrongOwnerAbsentOwnerAndSameAccountHaveSeparateReasons() {
        var source = account(AccountType.TRADITIONAL_IRA, AccountOwnership.PRIMARY, BALANCE);
        var missing = policy.assessUnderLegacyEmployerAssumption(source, null, OWNERS, BALANCE);
        assertEquals(Set.of(Reason.MISSING_DESTINATION), missing.reasons());
        var wrongOwner = policy.assessUnderLegacyEmployerAssumption(source,
                account(AccountType.ROTH_IRA, AccountOwnership.SPOUSE, BigDecimal.ZERO), OWNERS, BALANCE);
        assertTrue(wrongOwner.reasons().contains(Reason.DIFFERENT_OWNER));
        assertTrue(wrongOwner.reasons().contains(Reason.UNSUPPORTED_TRANSACTION));
        var absent = policy.assessUnderLegacyEmployerAssumption(source, destination(), Set.of(), BALANCE);
        assertTrue(absent.reasons().contains(Reason.OWNER_NOT_PRESENT));
        assertFalse(absent.ownerPresent());
        var self = policy.assessUnderLegacyEmployerAssumption(source, source, OWNERS, BALANCE);
        assertTrue(self.reasons().contains(Reason.SAME_ACCOUNT));
        assertFalse(self.executableUnderLegacyEmployerAssumption());
        assertEquals(BigDecimal.ZERO, self.maximumConvertibleAmount());
    }

    @Test
    void balanceUsesProjectedAmountAndDoesNotMutatePersistedAccount() {
        var source = account(AccountType.ROLLOVER_IRA, AccountOwnership.PRIMARY, BALANCE);
        var assessment = policy.assessUnderLegacyEmployerAssumption(source, destination(), OWNERS, new BigDecimal("12.3400"));
        assertEquals(new BigDecimal("12.3400"), assessment.maximumConvertibleAmount());
        assertEquals(BALANCE, source.getCurrentBalance());
        assertFalse(assessment.usesLegacyEmployerAssumption());
        assertEquals(EmployerDistributionAvailability.NOT_APPLICABLE, assessment.employerDistributionAvailability());
        assertThrows(IllegalArgumentException.class, () -> policy.assess(source, destination(), OWNERS, BALANCE,
                EmployerDistributionAvailability.ESTABLISHED));
    }

    private Account destination() {
        return account(AccountType.ROTH_IRA, AccountOwnership.PRIMARY, BigDecimal.ZERO);
    }
}
