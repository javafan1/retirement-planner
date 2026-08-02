package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.rules.*;

import java.time.LocalDate;
import java.util.List;

public class GovernmentRulesBuilder {

    private String rulesVersion = "2026.1";

    private int taxYear = 2026;

    private LocalDate effectiveDate =
            LocalDate.of(2026, 1, 1);

    private List<FederalTaxRules> federalTaxRules =
            List.of();

    private List<SocialSecurityTaxRules> socialSecurityTaxRules =
            List.of();

    private MichiganTaxRules michiganTaxRules =
            MichiganTaxRulesBuilder
                    .aMichiganTaxRules()
                    .build();

    private IrmaaRules irmaaRules =
            IrmaaRulesBuilder
                    .anIrmaaRules()
                    .build();

    private RmdRules rmdRules =
            RmdRulesBuilder
                    .anRmdRules()
                    .build();

    public static GovernmentRulesBuilder
    aGovernmentRules() {

        return new GovernmentRulesBuilder();
    }

    private GovernmentRulesBuilder() {
    }

    public GovernmentRulesBuilder
    withFederalTaxRules(
            List<FederalTaxRules> rules) {

        this.federalTaxRules = rules;
        return this;
    }

    public GovernmentRulesBuilder
    withSocialSecurityTaxRules(
            List<SocialSecurityTaxRules> rules) {

        this.socialSecurityTaxRules = rules;
        return this;
    }

    public GovernmentRulesBuilder
    withMichiganTaxRules(
            MichiganTaxRules rules) {

        this.michiganTaxRules = rules;
        return this;
    }

    public GovernmentRulesBuilder
    withIrmaaRules(
            IrmaaRules rules) {

        this.irmaaRules = rules;
        return this;
    }

    public GovernmentRulesBuilder
    withRmdRules(
            RmdRules rules) {

        this.rmdRules = rules;
        return this;
    }

    public GovernmentRules build() {

        return new GovernmentRules(
                rulesVersion,
                taxYear,
                effectiveDate,
                federalTaxRules,
                michiganTaxRules,
                irmaaRules,
                socialSecurityTaxRules,
                rmdRules);
    }
}