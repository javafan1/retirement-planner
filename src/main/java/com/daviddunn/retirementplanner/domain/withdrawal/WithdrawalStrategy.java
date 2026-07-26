package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import java.util.List;

public interface WithdrawalStrategy {

    List<ProjectedAccountBalance> orderAccounts(
            ProjectedPortfolio portfolio);
}