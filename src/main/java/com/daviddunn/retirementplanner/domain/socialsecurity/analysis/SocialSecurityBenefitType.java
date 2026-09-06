package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

/**
 * Auditable benefit components in a monthly strategy result. A person may
 * have OWN_RETIREMENT plus a SPOUSAL or SURVIVOR excess component in the
 * same month.
 */
public enum SocialSecurityBenefitType {
    NONE,
    OWN_RETIREMENT,
    SPOUSAL,
    SURVIVOR
}
